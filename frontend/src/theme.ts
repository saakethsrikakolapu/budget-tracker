import { useSyncExternalStore } from 'react'

/**
 * Light / dark mode. "system" (the default) follows the device setting and changes with it.
 * The choice is kept in localStorage (it's a per-device preference, not account data), and the
 * "dark" class on <html> switches the palette (see theme-dark.css). index.html applies the saved
 * choice before the page draws, so there's no white flash on load.
 */
export type ThemePreference = 'system' | 'light' | 'dark'

const STORAGE_KEY = 'theme'
const media = window.matchMedia('(prefers-color-scheme: dark)')
const listeners = new Set<() => void>()

export function getPreference(): ThemePreference {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved === 'light' || saved === 'dark' ? saved : 'system'
  } catch {
    return 'system' // storage blocked (private mode, strict settings)
  }
}

function isDark(): boolean {
  const preference = getPreference()
  return preference === 'dark' || (preference === 'system' && media.matches)
}

function apply() {
  document.documentElement.classList.toggle('dark', isDark())
  listeners.forEach((listener) => listener())
}

export function setPreference(preference: ThemePreference) {
  try {
    if (preference === 'system') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, preference)
  } catch {
    // Can't save; still switch for this visit.
  }
  apply()
}

media.addEventListener('change', apply)

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** True while the dark palette is showing; re-renders when it changes. For colors set in JS (charts). */
export function useIsDark(): boolean {
  return useSyncExternalStore(subscribe, isDark)
}

/** The saved choice ("system" / "light" / "dark"); re-renders when it changes. */
export function usePreference(): ThemePreference {
  return useSyncExternalStore(subscribe, getPreference)
}
