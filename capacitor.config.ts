import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.mtsgi.vaunch",
  appName: "Vaunch",
  webDir: "dist",
  android: {
    allowMixedContent: false,
  },
  plugins: {
    SystemBars: {
      style: "DARK",
      initialViewportFitValueHint: "cover",
    },
  },
};

export default config;
