<script setup lang="ts" vapor>
import { App as CapacitorApp } from "@capacitor/app";
import type { PluginListenerHandle } from "@capacitor/core";
import { computed, onMounted, onUnmounted, ref } from "vue";
import {
  createSingleFlightRunner,
  filterApps,
  formatMetadataEntries,
  sortApps,
} from "./launcher/logic";
import {
  getPluginErrorCode,
  getPluginErrorMessage,
  isAndroidNative,
  Launcher,
  type LauncherApp,
} from "./plugins/launcher";

const androidPlatform = isAndroidNative();

const apps = ref<LauncherApp[]>([]);
const query = ref("");
const isLoading = ref(false);
const isCheckingHome = ref(false);
const isRequestingHome = ref(false);
const isDefaultHome = ref<boolean | null>(null);
const roleUnavailable = ref(false);
const errorMessage = ref<string | null>(null);
const lastFetchMs = ref<number | null>(null);
const expandedComponent = ref<string | null>(null);

const filteredApps = computed(() => filterApps(apps.value, query.value));

const appCountText = computed(() => {
  const total = apps.value.length;
  const shown = filteredApps.value.length;
  if (query.value.trim().length > 0) {
    return `${shown} / ${total} 件`;
  }
  return `${total} 件`;
});

const homeStatusText = computed(() => {
  if (!androidPlatform) {
    return "Android 専用";
  }
  if (isCheckingHome.value && isDefaultHome.value === null) {
    return "HOME 確認中...";
  }
  return isDefaultHome.value ? "標準ホーム: 設定済み" : "標準ホーム: 未設定";
});

async function refreshHomeStatus(): Promise<void> {
  if (!androidPlatform || isCheckingHome.value) {
    return;
  }
  isCheckingHome.value = true;
  try {
    const result = await Launcher.isDefaultHome();
    isDefaultHome.value = result.value;
  } catch (error) {
    console.warn("[Vaunch] isDefaultHome failed:", error);
  } finally {
    isCheckingHome.value = false;
  }
}

const fetchAppsSingleFlight = createSingleFlightRunner(async () => {
  if (!androidPlatform) {
    return;
  }
  isLoading.value = true;
  errorMessage.value = null;
  const startedAt = performance.now();
  try {
    const result = await Launcher.getApps();
    apps.value = sortApps(result.apps);
    const elapsedMs = Math.round(performance.now() - startedAt);
    lastFetchMs.value = elapsedMs;
    console.info(`[VaunchPerf] js getApps count=${apps.value.length} elapsedMs=${elapsedMs}`);
  } catch (error) {
    errorMessage.value = `アプリ一覧の取得に失敗しました: ${getPluginErrorMessage(error)}`;
  } finally {
    isLoading.value = false;
  }
});

async function handleRefresh(): Promise<void> {
  await fetchAppsSingleFlight();
}

async function handleRequestDefaultHome(): Promise<void> {
  if (!androidPlatform || isRequestingHome.value) {
    return;
  }
  isRequestingHome.value = true;
  roleUnavailable.value = false;
  errorMessage.value = null;
  try {
    await Launcher.requestDefaultHome();
  } catch (error) {
    if (getPluginErrorCode(error) === "ROLE_UNAVAILABLE") {
      roleUnavailable.value = true;
    } else {
      errorMessage.value = `標準ホーム設定の開始に失敗しました: ${getPluginErrorMessage(error)}`;
    }
  } finally {
    isRequestingHome.value = false;
    await refreshHomeStatus();
  }
}

async function handleLaunchApp(app: LauncherApp): Promise<void> {
  if (!androidPlatform) {
    return;
  }
  errorMessage.value = null;
  try {
    await Launcher.launchApp({ componentName: app.componentName });
  } catch (error) {
    errorMessage.value = `「${app.label}」を起動できませんでした（${getPluginErrorMessage(error)}）。一覧を更新します。`;
    await fetchAppsSingleFlight();
  }
}

function toggleMetadata(componentName: string): void {
  expandedComponent.value = expandedComponent.value === componentName ? null : componentName;
}

function handleQueryInput(event: Event): void {
  const target = event.target as HTMLInputElement;
  query.value = target.value;
}

function clearQuery(): void {
  query.value = "";
}

let appStateListener: PluginListenerHandle | null = null;

function handleVisibilityChange(): void {
  if (document.visibilityState === "visible") {
    void refreshHomeStatus();
  }
}

onMounted(async () => {
  if (!androidPlatform) {
    return;
  }
  appStateListener = await CapacitorApp.addListener("appStateChange", ({ isActive }) => {
    if (isActive) {
      void refreshHomeStatus();
    }
  });
  document.addEventListener("visibilitychange", handleVisibilityChange);

  await Promise.all([refreshHomeStatus(), fetchAppsSingleFlight()]);
});

onUnmounted(() => {
  document.removeEventListener("visibilitychange", handleVisibilityChange);
  if (appStateListener) {
    void appStateListener.remove();
    appStateListener = null;
  }
});
</script>

<template>
  <main class="shell">
    <header class="header">
      <div class="header-row">
        <h1 class="title">Vaunch</h1>
        <div class="status-group">
          <span class="badge" :class="isDefaultHome ? 'badge-ok' : 'badge-warn'">
            {{ homeStatusText }}
          </span>
          <button
            v-if="androidPlatform && !isDefaultHome"
            type="button"
            class="btn btn-primary btn-small"
            :disabled="isRequestingHome"
            @click="handleRequestDefaultHome"
          >
            {{ isRequestingHome ? "設定画面を開いています..." : "標準ホームに設定" }}
          </button>
        </div>
      </div>

      <p v-if="!androidPlatform" class="notice">
        この環境は Android ネイティブではないため、アプリ一覧取得およびホーム設定機能は利用できません。Android 実機（APK）で検証してください。
      </p>

      <p v-if="roleUnavailable" class="notice">
        この端末では RoleManager による標準ホーム変更ダイアログを利用できません。一覧から端末の「設定」アプリを開き、「アプリ → デフォルトのアプリ → ホームアプリ」から Vaunch を選択してください。
      </p>

      <div v-if="errorMessage" class="notice notice-error" role="alert">
        <span>{{ errorMessage }}</span>
        <button
          v-if="androidPlatform"
          type="button"
          class="btn btn-small"
          :disabled="isLoading"
          @click="handleRefresh"
        >
          再試行
        </button>
      </div>
    </header>

    <section v-if="androidPlatform" aria-label="検索と操作" class="toolbar">
      <div class="search-row">
        <input
          class="search-input"
          type="search"
          :value="query"
          aria-label="アプリを検索"
          autocapitalize="off"
          autocomplete="off"
          placeholder="アプリ名またはパッケージ名で検索"
          spellcheck="false"
          @input="handleQueryInput"
        />
        <button v-if="query.length > 0" type="button" class="btn" @click="clearQuery">
          クリア
        </button>
      </div>

      <div class="meta-row">
        <span>
          {{ appCountText }}
          <template v-if="lastFetchMs !== null"> · {{ lastFetchMs }} ms</template>
        </span>
        <button type="button" class="btn btn-small" :disabled="isLoading" @click="handleRefresh">
          {{ isLoading ? "更新中..." : "手動更新" }}
        </button>
      </div>
    </section>

    <section v-if="androidPlatform" aria-label="アプリ一覧" class="list-container">
      <div v-if="isLoading && apps.length === 0" class="state-box">
        アプリ一覧を取得しています...
      </div>

      <div v-else-if="filteredApps.length === 0" class="state-box">
        <span v-if="query.trim().length > 0">
          「{{ query }}」に一致するアプリがありません。
        </span>
        <span v-else>起動可能なアプリが見つかりませんでした。</span>
      </div>

      <ul v-else class="app-list">
        <li v-for="app in filteredApps" :key="app.componentName" class="app-item">
          <div class="app-row">
            <button type="button" class="app-launch-btn" @click="handleLaunchApp(app)">
              <span class="app-label-line">
                <span class="app-label">{{ app.label }}</span>
                <span v-if="app.isWebApk" class="badge badge-webapk">
                  WebAPK
                </span>
              </span>
              <span v-if="app.isWebApk && app.webApk?.startUrl" class="app-sub">
                {{ app.webApk.startUrl }}
              </span>
            </button>

            <button
              v-if="app.isWebApk"
              type="button"
              class="btn btn-small"
              @click.stop="toggleMetadata(app.componentName)"
            >
              {{ expandedComponent === app.componentName ? "閉じる" : "詳細" }}
            </button>
          </div>

          <div
            v-if="app.isWebApk && expandedComponent === app.componentName"
            class="webapk-details"
          >
            <div class="webapk-summary">
              <div>package: {{ app.packageName }}</div>
              <div>startUrl: {{ app.webApk?.startUrl ?? "(未取得)" }}</div>
              <div>scope: {{ app.webApk?.scope ?? "(未取得)" }}</div>
              <div>themeColor: {{ app.webApk?.themeColor ?? "(未取得)" }}</div>
            </div>

            <div v-if="formatMetadataEntries(app.webApk).length === 0">
              GET_META_DATA: メタデータなし（または取得不可）
            </div>
            <ul v-else class="webapk-meta-list">
              <li v-for="entry in formatMetadataEntries(app.webApk)" :key="entry.key">
                {{ entry.key }} ({{ entry.type }}): {{ entry.value }}
              </li>
            </ul>
          </div>
        </li>
      </ul>
    </section>

    <p v-if="androidPlatform" class="footer-hint">
      元のホームへ戻すには一覧から「設定」を起動し、デフォルトのホームアプリを変更してください。
    </p>
  </main>
</template>
