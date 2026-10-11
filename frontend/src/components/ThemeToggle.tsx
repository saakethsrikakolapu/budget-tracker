import { Monitor, Moon, Sun } from 'lucide-react'
import { setPreference, usePreference, type ThemePreference } from '../theme'

const NEXT: Record<ThemePreference, ThemePreference> = { system: 'light', light: 'dark', dark: 'system' }
const LABEL: Record<ThemePreference, string> = { system: 'System', light: 'Light', dark: 'Dark' }
const ICON = { system: Monitor, light: Sun, dark: Moon }

/** One button that cycles System → Light → Dark. The label says the current mode and what's next. */
export function ThemeToggle() {
  const preference = usePreference()
  const Icon = ICON[preference]
  const label = `Theme: ${LABEL[preference]}. Switch to ${LABEL[NEXT[preference]]}`
  return (
    <button
      type="button"
      onClick={() => setPreference(NEXT[preference])}
      aria-label={label}
      title={label}
      className="grid size-9 place-items-center rounded-full text-zinc-600 transition-colors duration-150 hover:bg-zinc-100 hover:text-zinc-900"
    >
      <Icon size={18} aria-hidden="true" />
    </button>
  )
}
