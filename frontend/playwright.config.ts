import { defineConfig, devices } from '@playwright/test'

/**
 * Browser tests (npm run test:e2e): a real Chromium drives the real app, frontend + backend +
 * Postgres. The backend must already be running (CI starts it; locally: docker compose up -d and
 * ./mvnw spring-boot:run). Playwright starts the frontend itself, unless E2E_BASE_URL points at
 * one that's already running.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5173'

export default defineConfig({
  testDir: 'e2e',
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL,
    // Evidence when something fails: a screenshot and a step-by-step trace.
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'phone', use: { ...devices['Pixel 7'] } },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'npm run dev -- --port 5173 --strictPort',
        url: baseURL,
        reuseExistingServer: !process.env.CI,
      },
})
