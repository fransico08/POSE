import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.spec.ts",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  globalSetup: "./e2e/global-setup.ts",
  globalTeardown: "./e2e/global-teardown.ts",
  use: {
    baseURL: "http://127.0.0.1:3011",
    browserName: "chromium",
    viewport: { width: 1280, height: 800 },
    trace: { mode: "retain-on-failure", snapshots: false, screenshots: true, sources: false },
    screenshot: "only-on-failure",
    video: "off",
  },
});
