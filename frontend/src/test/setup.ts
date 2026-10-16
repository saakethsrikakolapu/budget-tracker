// Runs before every test file (see vite.config.ts).
import '@testing-library/jest-dom/vitest' // adds expect(...).toBeInTheDocument() and friends
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Unmount whatever a test rendered, so the next test starts with an empty page.
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

// jsdom has no matchMedia; theme.ts asks it whether the device prefers dark mode.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  }),
})
