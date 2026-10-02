import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // In dev, forward /api requests to the Spring Boot backend so the browser
    // only ever talks to one origin (localhost:5173) and CORS never applies.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
