import { describe, expect, it } from "vite-plus/test";
import type { LauncherApp } from "../plugins/launcher";
import {
  createSingleFlightRunner,
  filterApps,
  formatMetadataEntries,
  isLikelyWebApkPackage,
  normalizeSearchText,
  sortApps,
} from "./logic";

function makeApp(overrides: Partial<LauncherApp>): LauncherApp {
  return {
    packageName: "com.example.app",
    componentName: "com.example.app/.MainActivity",
    label: "Example",
    icon: null,
    isWebApk: false,
    ...overrides,
  };
}

describe("isLikelyWebApkPackage", () => {
  it("identifies Chromium WebAPK package names case-insensitively", () => {
    expect(isLikelyWebApkPackage("org.chromium.webapk.a1b2c3d4")).toBe(true);
    expect(isLikelyWebApkPackage("ORG.CHROMIUM.WEBAPK.XYZ")).toBe(true);
    expect(isLikelyWebApkPackage("  org.chromium.webapk.offtone  ")).toBe(true);
  });

  it("returns false for standard Android packages", () => {
    expect(isLikelyWebApkPackage("com.android.settings")).toBe(false);
    expect(isLikelyWebApkPackage("com.mtsgi.vaunch")).toBe(false);
    expect(isLikelyWebApkPackage("org.chromium.chrome")).toBe(false);
  });
});

describe("sortApps", () => {
  it("sorts apps by Japanese-aware label order and tie-breaks by componentName", () => {
    const input = [
      makeApp({ label: "設定", componentName: "com.android.settings/.Settings" }),
      makeApp({ label: "Chrome", componentName: "com.android.chrome/.Main" }),
      makeApp({ label: "カメラ", componentName: "com.google.camera/.Main" }),
      makeApp({ label: "chrome", componentName: "com.beta.chrome/.Main" }),
      makeApp({ label: "あいう", componentName: "com.example.aiu/.Main" }),
    ];

    const sorted = sortApps(input);
    expect(sorted.map((a) => `${a.label}:${a.componentName}`)).toEqual([
      "Chrome:com.android.chrome/.Main",
      "chrome:com.beta.chrome/.Main",
      "あいう:com.example.aiu/.Main",
      "カメラ:com.google.camera/.Main",
      "設定:com.android.settings/.Settings",
    ]);
  });

  it("does not mutate the input array", () => {
    const input = [
      makeApp({ label: "Zeta", componentName: "z/.Main" }),
      makeApp({ label: "Alpha", componentName: "a/.Main" }),
    ];
    const sorted = sortApps(input);
    expect(input[0].label).toBe("Zeta");
    expect(sorted[0].label).toBe("Alpha");
  });
});

describe("filterApps & normalizeSearchText", () => {
  const apps = [
    makeApp({
      label: "設定",
      packageName: "com.android.settings",
      componentName: "com.android.settings/.Settings",
    }),
    makeApp({
      label: "Ｏｆｆｔｏｎｅ",
      packageName: "org.chromium.webapk.offtone123",
      componentName: "org.chromium.webapk.offtone123/.Main",
      isWebApk: true,
    }),
    makeApp({
      label: "Google マップ",
      packageName: "com.google.android.apps.maps",
      componentName: "com.google.android.apps.maps/.MapsActivity",
    }),
  ];

  it("normalizes NFKC width and case", () => {
    expect(normalizeSearchText("  ＡＢＣ１２３  ")).toBe("abc123");
  });

  it("returns all apps when query is empty or whitespace", () => {
    expect(filterApps(apps, "")).toHaveLength(3);
    expect(filterApps(apps, "   ")).toHaveLength(3);
  });

  it("matches labels with NFKC normalization and case-insensitivity", () => {
    expect(filterApps(apps, "offtone").map((a) => a.packageName)).toEqual([
      "org.chromium.webapk.offtone123",
    ]);
    expect(filterApps(apps, "マップ").map((a) => a.packageName)).toEqual([
      "com.google.android.apps.maps",
    ]);
  });

  it("matches packageName substrings", () => {
    expect(filterApps(apps, "webapk").map((a) => a.packageName)).toEqual([
      "org.chromium.webapk.offtone123",
    ]);
    expect(filterApps(apps, "settings").map((a) => a.packageName)).toEqual([
      "com.android.settings",
    ]);
  });
});

describe("formatMetadataEntries", () => {
  it("returns sorted metadata entries or empty list", () => {
    expect(formatMetadataEntries(undefined)).toEqual([]);
    expect(
      formatMetadataEntries({
        metadata: {
          "z.key": { type: "String", value: "z" },
          "a.key": { type: "Integer", value: "123" },
        },
      }),
    ).toEqual([
      { key: "a.key", type: "Integer", value: "123" },
      { key: "z.key", type: "String", value: "z" },
    ]);
  });

  it("supports LauncherApp with top-level metadata and webApk fallback", () => {
    const regularApp = makeApp({
      metadata: {
        "com.example.flag": { type: "Boolean", value: "true" },
      },
    });
    expect(formatMetadataEntries(regularApp)).toEqual([
      { key: "com.example.flag", type: "Boolean", value: "true" },
    ]);

    const legacyWebApk = makeApp({
      isWebApk: true,
      webApk: {
        metadata: {
          "org.chromium.webapk.shell_apk.startUrl": {
            type: "String",
            value: "https://example.com/",
          },
        },
      },
    });
    expect(formatMetadataEntries(legacyWebApk)).toEqual([
      {
        key: "org.chromium.webapk.shell_apk.startUrl",
        type: "String",
        value: "https://example.com/",
      },
    ]);
  });
});

describe("createSingleFlightRunner", () => {
  it("prevents overlapping executions and resets after completion", async () => {
    let calls = 0;
    let resolveTask: (() => void) | undefined;

    const run = createSingleFlightRunner(async () => {
      calls += 1;
      await new Promise<void>((resolve) => {
        resolveTask = resolve;
      });
      return calls;
    });

    const first = run();
    const second = run();

    expect(await second).toBeNull();
    resolveTask?.();
    expect(await first).toBe(1);

    const third = run();
    resolveTask?.();
    expect(await third).toBe(2);
  });
});
