import { defineConfig, devices } from "@playwright/test";

import { WEB_URL } from "./e2e/support/stack";

/**
 * EN: End-to-end tests against a running stack (README → End-to-end tests). Each run creates its own users
 *     and lots, so it can run again on the same database.
 * VI: Test end-to-end chạy trên một stack đang chạy (README → End-to-end tests). Mỗi lần chạy tự tạo user và
 *     lô riêng, nên chạy lại trên cùng database được.
 */
export default defineConfig({
  testDir: "./e2e",
  globalSetup: "./e2e/global-setup.ts",
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: WEB_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      // EN: E2E_BROWSER_CHANNEL=chrome uses the installed Chrome instead of Playwright's download.
      // VI: E2E_BROWSER_CHANNEL=chrome dùng Chrome đã cài thay vì bản Playwright tải về.
      use: { ...devices["Desktop Chrome"], channel: process.env.E2E_BROWSER_CHANNEL || undefined },
    },
  ],
});
