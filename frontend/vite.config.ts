import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // In dev, forward /api requests to the Spring Boot backend so the browser
    // only ever talks to one origin (localhost:5173) and CORS never applies.
    // API_PROXY_TARGET lets you point at a backend on another port, e.g. to run two copies at once.
    proxy: {
      '/api': process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
    },
  },
  // Unit and component tests (npm test): run in Node with jsdom standing in for the browser.
  // The browser tests in e2e/ use Playwright instead (npm run test:e2e).
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['src/test/setup.ts'],
  },
})
