import { Capacitor, registerPlugin } from "@capacitor/core";

export interface AppMetadataEntry {
  type: string;
  value: string;
}

export type WebApkMetadataEntry = AppMetadataEntry;

export interface WebApkInfo {
  startUrl?: string;
  scope?: string;
  themeColor?: string;
  metadata?: Record<string, AppMetadataEntry>;
}

export interface LauncherApp {
  packageName: string;
  componentName: string;
  label: string;
  icon: string | null;
  isWebApk: boolean;
  isSystemApp?: boolean;
  targetSdkVersion?: number;
  metadata?: Record<string, AppMetadataEntry>;
  webApk?: WebApkInfo;
}

export interface LauncherPlugin {
  getApps(): Promise<{ apps: LauncherApp[] }>;
  launchApp(options: { componentName: string }): Promise<void>;
  isDefaultHome(): Promise<{ value: boolean }>;
  requestDefaultHome(): Promise<void>;
}

export const Launcher = registerPlugin<LauncherPlugin>("Launcher", {
  web: () => import("./launcher.web").then((m) => new m.LauncherWeb()),
});

export function isAndroidNative(): boolean {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === "android";
}

export function getPluginErrorCode(error: unknown): string | undefined {
  if (typeof error === "object" && error !== null && "code" in error) {
    const code = (error as { code?: unknown }).code;
    if (typeof code === "string" && code.length > 0) {
      return code;
    }
  }
  return undefined;
}

export function getPluginErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.length > 0) {
    return error.message;
  }
  if (typeof error === "object" && error !== null && "message" in error) {
    const message = (error as { message?: unknown }).message;
    if (typeof message === "string" && message.length > 0) {
      return message;
    }
  }
  return "不明なエラーが発生しました。";
}
