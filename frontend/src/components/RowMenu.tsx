import { MoreHorizontal } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'

/**
 * A "⋯" button with a small menu, for actions that shouldn't sit on every row (like "Not a
 * subscription"). Closes on Escape, on a click outside, or after choosing. Follows the ARIA menu
 * button pattern: arrow keys move between items, focus returns to the button on close.
 */
export function RowMenu({ label, items }: { label: string; items: { label: string; onSelect: () => void }[] }) {
  const [open, setOpen] = useState(false)
  const menuId = useId()
  const wrapper = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    wrapper.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus()
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
    const entries = [...(wrapper.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])]
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

  return (
    <div ref={wrapper} className="relative">
      <button
        ref={button}
        type="button"
        onClick={() => setOpen((shown) => !shown)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label={label}
        className="rounded-md p-1.5 text-stone-500 transition-colors duration-150 hover:bg-stone-200 hover:text-stone-900"
      >
        <MoreHorizontal size={18} aria-hidden="true" />
      </button>
      {open && (
        <div
          id={menuId}
          role="menu"
          aria-label={label}
          onKeyDown={onMenuKey}
          className="absolute right-0 z-20 mt-1 min-w-48 rounded-lg border border-stone-200 bg-white p-1 shadow-[0_8px_24px_-8px_rgb(23_48_42/0.25)]"
        >
          {items.map((item) => (
            <button
              key={item.label}
              type="button"
              role="menuitem"
              onClick={() => {
                close()
                item.onSelect()
              }}
              className="block w-full rounded-md px-3 py-2 text-left text-sm text-stone-800 transition-colors duration-150 hover:bg-stone-100 focus:bg-stone-100"
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
