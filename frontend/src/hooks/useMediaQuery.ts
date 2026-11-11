import { useSyncExternalStore } from 'react'

/**
 * True while a CSS media query matches, e.g. useMediaQuery('(min-width: 1024px)'). Used where the
 * phone and desktop layouts are different markup (a list vs. a table), not just different styles.
 * Without matchMedia (tests), it reports false: the phone layout.
 */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      if (!window.matchMedia) return () => {}
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => list.removeEventListener('change', onChange)
    },
    () => window.matchMedia?.(query).matches ?? false,
  )
}
