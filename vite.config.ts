import { defineConfig } from "@vizejs/vite-plugin/vite-plus";

export default defineConfig({
  base: "./",
  build: {
    outDir: "dist",
  },
  lint: {
    ignorePatterns: ["dist/**", "android/**"],
  },
  fmt: {
    ignorePatterns: ["dist/**", "android/**"],
  },
  test: {
    include: ["src/**/*.test.ts"],
  },
}).withVue({
  compiler: {
    vapor: true,
  },
});
