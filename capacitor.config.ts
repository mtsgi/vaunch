import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "com.mtsgi.vaunch",
  appName: "Vaunch",
  webDir: "dist",
  android: {
    allowMixedContent: false,
  },
};

export default config;
