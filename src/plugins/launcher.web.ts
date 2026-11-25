import { WebPlugin } from "@capacitor/core";
import type { LauncherApp, LauncherPlugin } from "./launcher";

export class LauncherWeb extends WebPlugin implements LauncherPlugin {
  async getApps(): Promise<{ apps: LauncherApp[] }> {
    throw this.unavailable("Launcher プラグインは Android 環境専用です。");
  }

  async launchApp(): Promise<void> {
    throw this.unavailable("Launcher プラグインは Android 環境専用です。");
  }

  async isDefaultHome(): Promise<{ value: boolean }> {
    return { value: false };
  }

  async requestDefaultHome(): Promise<void> {
    throw this.unavailable("Launcher プラグインは Android 環境専用です。");
  }
}
