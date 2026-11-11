import { Check, LogOut, Settings } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { setPreference, usePreference, type ThemePreference } from '../theme'

const THEMES: { value: ThemePreference; label: string }[] = [
  { value: 'system', label: 'Same as device' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

/**
 * The avatar in the header opens this menu: account settings, light or dark, and log out. It keeps
 * the header to logo, Upload and avatar (on phones there's no room for more). ARIA menu button
 * pattern: arrow keys move, Escape closes and returns focus to the avatar.
 */
export function AccountMenu({
  email,
  onAccount,
  onLogout,
}: {
  email: string
  onAccount: () => void
  onLogout: () => void
}) {
  const [open, setOpen] = useState(false)
  const preference = usePreference()
  const menuId = useId()
  const wrapper = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    wrapper.current?.querySelector<HTMLElement>('[role^="menuitem"]')?.focus()
    const onPointer = (event: PointerEvent) => {
      if (!wrapper.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointer)
    return () => document.removeEventListener('pointerdown', onPointer)
  }, [open])

  function close() {
    setOpen(false)
    button.current?.focus()
  }

  function onMenuKey(event: KeyboardEvent) {
    const entries = [...(wrapper.current?.querySelectorAll<HTMLElement>('[role^="menuitem"]') ?? [])]
    const index = entries.indexOf(document.activeElement as HTMLElement)
    if (event.key === 'Escape') {
      event.preventDefault()
      close()
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const step = event.key === 'ArrowDown' ? 1 : -1
      entries[(index + step + entries.length) % entries.length]?.focus()
    } else if (event.key === 'Tab') {
      setOpen(false)
    }
  }

  const item =
    'flex w-full items-center gap-2.5 rounded-md px-3 py-2 text-left text-sm text-stone-800 transition-colors duration-150 hover:bg-stone-100 focus:bg-stone-100 focus:outline-none'

  return (
    <div ref={wrapper} className="relative">
      <button
        ref={button}
        type="button"
        onClick={() => setOpen((shown) => !shown)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label="Your account"
        title={email}
        className="grid size-9 place-items-center rounded-full border border-stone-200 text-sm font-medium uppercase text-stone-700 transition-colors duration-150 hover:bg-stone-100"
      >
        {email.charAt(0)}
      </button>
      {open && (
        <div
          id={menuId}
          role="menu"
          aria-label="Your account"
          onKeyDown={onMenuKey}
          className="absolute right-0 z-30 mt-2 w-60 rounded-lg border border-stone-200 bg-white p-1 shadow-[0_12px_32px_-12px_rgb(23_48_42/0.3)]"
        >
          <p className="truncate px-3 pt-2 pb-1 text-xs text-stone-500">{email}</p>
          <button type="button" role="menuitem" className={item} onClick={() => {
              close()
              onAccount()
            }}>
            <Settings size={16} aria-hidden="true" className="text-stone-500" />
            Account settings
          </button>

          <div role="group" aria-label="Appearance" className="mt-1 border-t border-stone-100 pt-1">
            <p className="px-3 pt-1.5 pb-1 text-xs text-stone-500" aria-hidden="true">
              Appearance
            </p>
            {THEMES.map((theme) => (
              <button
                key={theme.value}
                type="button"
                role="menuitemradio"
                aria-checked={preference === theme.value}
                className={item}
                // Changing the theme keeps the menu open, so you can see it and switch back.
                onClick={() => setPreference(theme.value)}
              >
                <Check
                  size={16}
                  aria-hidden="true"
                  className={preference === theme.value ? 'text-accent' : 'invisible'}
                />
                {theme.label}
              </button>
            ))}
          </div>

          <div className="mt-1 border-t border-stone-100 pt-1">
            <button type="button" role="menuitem" className={item} onClick={() => {
                close()
                onLogout()
              }}>
              <LogOut size={16} aria-hidden="true" className="text-stone-500" />
              Log out
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
