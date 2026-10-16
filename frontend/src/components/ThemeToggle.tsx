import { Moon, Sun, SunMoon } from 'lucide-react'
import { setPreference, usePreference, type ThemePreference } from '../theme'

const NEXT: Record<ThemePreference, ThemePreference> = { system: 'light', light: 'dark', dark: 'system' }
const LABEL: Record<ThemePreference, string> = { system: 'same as your device', light: 'light', dark: 'dark' }
const ICON = { system: SunMoon, light: Sun, dark: Moon }

/** One button that cycles device → light → dark. The label says the current mode and what's next. */
export function ThemeToggle() {
  const preference = usePreference()
  const Icon = ICON[preference]
  const label = `Light or dark mode: ${LABEL[preference]}. Click for ${LABEL[NEXT[preference]]}`
  return (
    <button
      type="button"
      onClick={() => setPreference(NEXT[preference])}
      aria-label={label}
      title={label}
      className="grid size-9 place-items-center rounded-full text-stone-600 transition-colors duration-150 hover:bg-stone-100 hover:text-stone-900"
    >
      <Icon size={18} aria-hidden="true" />
    </button>
  )
}
