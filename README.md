# Vaunch (PoC)

Vaunch は Vue 3.6 Pure Vapor + Capacitor（Android）で構成したミニマルな Android ホームアプリの PoC です。
WebView ベースのアプリを Android の標準ホーム（ランチャー）として利用できるか、通常アプリおよび Chromium WebAPK を列挙・起動できるかを実機で検証することを目的としています。

- Application ID: `com.mtsgi.vaunch`
- アプリ名: `Vaunch`
- 対象 Issue: [#1](https://github.com/mtsgi/vaunch/issues/1)

## 採用バージョンと構成

| カテゴリ           | ツール / パッケージ                                       | 固定バージョン                                   | 備考                                                                              |
| :----------------- | :-------------------------------------------------------- | :----------------------------------------------- | :-------------------------------------------------------------------------------- |
| UI フレームワーク  | `vue`                                                     | `3.6.0-rc.16`                                    | Pure Vapor (`createVaporApp()`, `<script setup lang="ts" vapor>`)                 |
| 言語（Web）        | `typescript`                                              | `7.0.2`                                          | `strict: true`                                                                    |
| スタイリング       | Pure CSS                                                  | —                                                | Nuxt UI / Tailwind CSS 不使用（下記参照）                                         |
| 統合ツールチェーン | `vite-plus` (`vp`)                                        | `1.8.0`                                          | Vite 8.3.8 / Rolldown / Vitest 5.1.1 / Oxlint / Oxfmt                             |
| Vue ツールチェーン | `@vizejs/vite-plugin` (`vize`)                            | `0.797.0`                                        | `@vizejs/vite-plugin/vite-plus` 経由で Vapor コンパイル・lint・型チェック・format |
| パッケージ管理     | `npm`                                                     | Node 22 / npm 12                                 | `package-lock.json` をコミット                                                    |
| ネイティブブリッジ | `@capacitor/core`, `@capacitor/cli`, `@capacitor/android` | `8.6.2`                                          | Android のみ、ローカルバンドル (`webDir: 'dist'`)                                 |
| アプリ状態監視     | `@capacitor/app`                                          | `8.1.2`                                          | 復帰時 (`appStateChange`) の HOME 状態再取得                                      |
| Android ビルド     | Gradle Wrapper / AGP / Kotlin                             | Gradle `8.14.3` / AGP `8.13.0` / Kotlin `2.2.21` | JDK 21 / `minSdk 24` / `compileSdk 36` / `targetSdk 36`                           |
| CI                 | GitHub Actions (`voidzero-dev/setup-vp`)                  | `v1.22.0`                                        | PR / push / 手動実行で Debug APK を artifact 保存                                 |

### Issue #1 原案からの意図的な調整点と互換性メモ

1. **Nuxt UI・Tailwind CSS の不採用（Pure Vapor + Pure CSS 化）**
   - 本 PoC ではバンドルサイズとランタイムを最小化し、Vue 3.6 Pure Vapor (`createVaporApp()`) の素の挙動を検証するため、Nuxt UI および Tailwind CSS を導入せず Pure CSS を採用しました。
2. **アプリアイコン取得・表示の省略**
   - 本 PoC では列挙・起動・WebAPK 検証に集中するため、ネイティブ側での `Drawable` / `AdaptiveIconDrawable` 画像変換と UI 上のアイコン表示を省略しています（`LauncherApp.icon` は `null` を返却）。
3. **Vize Corsa 型チェッカーと `createVaporApp` の型互換性（暫定対応）**
   - `@vizejs/vite-plugin@0.797.0` のコンパイラは `<script setup lang="ts" vapor>` を `defineVaporComponent(__vapor: true)` として正しく Vapor 出力しますが、型チェッカー（Corsa 仮想プロジェクト）は `.vue` モジュールのエクスポート型を通常の VDOM コンポーネント型として生成します。
   - このため `src/main.ts` のみ `App as unknown as Parameters<typeof createVaporApp>[0]` の明示的な型適合を行っています。Vize 側の Vapor 型出力対応後に削除可能です。

## セットアップ

### 前提ツール

- Node.js 22 以上（npm 同梱）
- [Vite+ CLI (`vp`)](https://viteplus.dev/)
  ```bash
  curl -fsSL https://vite.plus | bash
  ```
- JDK 21（Android ビルド時）
- Android SDK Platform 36（`ANDROID_HOME` または `android/local.properties` を設定）

### 依存関係インストール

```bash
npm ci
```

## コマンド一覧

グローバルの `vp` CLI がない環境では `npx vp ...` でも実行できます。

| コマンド                                | 内容                                                                    |
| :-------------------------------------- | :---------------------------------------------------------------------- |
| `vp dev`                                | ローカル Web 開発サーバー起動（非 Android では非対応表示を確認可能）    |
| `vp run check`                          | 型チェック（Vize Corsa）・Vize lint・Oxlint・フォーマット検査を一括実行 |
| `vp run fmt`                            | Vue ファイル（Vize）とその他ファイル（Oxfmt）の自動フォーマット         |
| `vp test --run`                         | ユニットテスト実行（検索・並べ替え・WebAPK 判定・重複実行ガード）       |
| `vp build`                              | 本番用 Web バンドルを `dist/` に出力（外部サーバー不要）                |
| `npx cap sync android`                  | `dist/` と Capacitor 設定・プラグインを `android/` へ同期               |
| `cd android && ./gradlew assembleDebug` | Android Debug APK をビルド                                              |

## APK インストール手順

### 1. CI の Artifact から取得する場合

1. GitHub Actions の `CI` ワークフロー実行画面を開く。
2. `vaunch-debug-apk` アーティファクトをダウンロードし、`app-debug.apk` を展開する。
3. 端末へインストールする（USB デバッグまたは端末上のファイルからインストール）。

### 2. ローカルでビルドして `adb` インストールする場合

```bash
vp build
npx cap sync android
cd android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 標準ホーム（HOME）の切替・解除方法

### 標準ホームに設定する

1. Vaunch を起動する。
2. ヘッダーの「標準ホームに設定」ボタンをタップし、Android の `RoleManager.ROLE_HOME` 承認ダイアログで Vaunch を選択する。
3. 承認・キャンセルいずれの場合も、ダイアログ終了後に `isDefaultHome()` を自動再取得して状態表示を更新します。
4. `RoleManager` が利用できない端末では設定手順の案内が表示されるため、端末の「設定」アプリから手動で切り替えてください。

### 元のホームアプリ（Pixel Launcher 等）に戻す

1. Vaunch のアプリ一覧で「設定」（または `settings`）を検索して起動する。
2. `アプリ → デフォルトのアプリ → ホームアプリ` を開き、元のホームアプリを選択する。

## 既知の制約・設計仕様

- **戻る操作の無効化**: `MainActivity` で戻る操作を常に消費し、標準ホームとして画面が閉じない最小挙動としています（検索文字列のクリアも行いません）。
- **一覧の更新タイミング**: 起動時・手動更新ボタン押下時・アプリ起動失敗からの復旧時のみ `getApps()` を実行します。復帰時は `isDefaultHome()` のみ再確認し、検索状態やスクロール位置を妨げません。
- **パッケージ可視性**: `QUERY_ALL_PACKAGES` は追加せず、`ACTION_MAIN` + `CATEGORY_LAUNCHER` の最小 `<queries>` のみを `AndroidManifest.xml` に宣言しています。実機で列挙漏れが発生した場合は記録して見直します。
- **WebAPK 識別**: `org.chromium.webapk.` プレフィックスによるヒューリスティック識別です。`PackageManager.GET_META_DATA` で取得できた全メタデータキー・型・値は `adb logcat -s VaunchWebApk` と、各 WebAPK 行の「詳細」トグルの両方で確認できます。
- **対象外**: お気に入り、グリッド配置、アイコン表示、時計、ウィジェット、ジェスチャー、テーマ変更、永続化、仕事用プロファイル、プライベートスペース、ピン留め Web ショートカット、iOS、Google Play 公開。

## 実機検証手順（Pixel 10 Pro Fold / Pixel 6a）

### 機能チェックリスト

- [ ] APK のインストール、通常起動、標準ホーム候補への表示
- [ ] 「標準ホームに設定」での承認・キャンセル、ホームジェスチャーでの復帰
- [ ] 一覧から「設定」を起動して元のホームアプリへ戻せること
- [ ] 日本語を含むアプリ名の表示、インクリメンタル検索（全角/半角・大小無視・パッケージ名検索）、手動更新
- [ ] 通常アプリおよび WebAPK（例: Offtone 等）の起動
- [ ] WebAPK 行の「詳細」トグルまたは `adb logcat -s VaunchWebApk` でメタデータのキー・型・取得可否を確認し記録
- [ ] 他アプリのインストール / アンインストール後に手動更新で反映されること、また削除済みアプリをタップした際にクラッシュせずエラー表示＋一覧再取得で復旧すること
- [ ] プロセス終了・端末再起動・折りたたみ開閉（Pixel 10 Pro Fold）・機内モード（オフライン）後の起動・復帰

### パフォーマンス計測コマンド例

```bash
# 起動・復帰・一覧取得のログ監視
adb logcat -s VaunchPerf VaunchWebApk

# コールドスタート計測（プロセス停止後に起動）
adb shell am force-stop com.mtsgi.vaunch
adb shell am start -W -n com.mtsgi.vaunch/.MainActivity

# メモリ使用量計測
adb shell dumpsys meminfo com.mtsgi.vaunch
```

## 確認状況

### 確認済み（開発環境 / 自動チェック）

- `vp run check`（Vize Corsa 型チェック・Vize lint・Oxlint・Vize fmt・Oxfmt）
- `vp test --run`（検索・並べ替え・WebAPK 判定・重複実行ガードのユニットテスト）
- `vp build`（Vue 3.6 Pure Vapor ローカルバンドル出力）
- `npx cap sync android` および `./gradlew assembleDebug`（Debug APK 生成）

### 未確認（開発者による実機検証待ち）

- Pixel 10 Pro Fold / Pixel 6a 実機での全実機検証項目（HOME 登録・切替・復帰、WebAPK 起動および `GET_META_DATA` 取得結果、最小 `<queries>` での列挙網羅性、折りたたみ開閉、パフォーマンス実測）
