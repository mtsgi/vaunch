import type { LauncherApp, WebApkInfo } from "../plugins/launcher";

export const WEBAPK_PACKAGE_PREFIXES = ["org.chromium.webapk."] as const;

const jaCollator = new Intl.Collator("ja", { sensitivity: "base" });

export function isLikelyWebApkPackage(packageName: string): boolean {
  const normalized = packageName.trim().toLowerCase();
  return WEBAPK_PACKAGE_PREFIXES.some((prefix) => normalized.startsWith(prefix));
}

export function normalizeSearchText(text: string): string {
  return text.trim().normalize("NFKC").toLowerCase();
}

export function sortApps(apps: readonly LauncherApp[]): LauncherApp[] {
  return [...apps].sort((a, b) => {
    const labelDiff = jaCollator.compare(a.label, b.label);
    if (labelDiff !== 0) {
      return labelDiff;
    }
    if (a.componentName < b.componentName) {
      return -1;
    }
    if (a.componentName > b.componentName) {
      return 1;
    }
    return 0;
  });
}

export function filterApps(apps: readonly LauncherApp[], query: string): LauncherApp[] {
  const normalizedQuery = normalizeSearchText(query);
  if (normalizedQuery.length === 0) {
    return [...apps];
  }
  return apps.filter((app) => {
    const normalizedLabel = normalizeSearchText(app.label);
    if (normalizedLabel.includes(normalizedQuery)) {
      return true;
    }
    const normalizedPackage = normalizeSearchText(app.packageName);
    return normalizedPackage.includes(normalizedQuery);
  });
}

export interface FormattedMetadataEntry {
  key: string;
  type: string;
  value: string;
}

export function formatMetadataEntries(webApk?: WebApkInfo): FormattedMetadataEntry[] {
  const metadata = webApk?.metadata;
  if (!metadata) {
    return [];
  }
  return Object.entries(metadata)
    .map(([key, entry]) => ({
      key,
      type: entry.type,
      value: entry.value,
    }))
    .sort((a, b) => (a.key < b.key ? -1 : a.key > b.key ? 1 : 0));
}

export function createSingleFlightRunner<T>(task: () => Promise<T>): () => Promise<T | null> {
  let inFlight = false;
  return async () => {
    if (inFlight) {
      return null;
    }
    inFlight = true;
    try {
      return await task();
    } finally {
      inFlight = false;
    }
  };
}
