import { ChevronDown } from 'lucide-react'
import type { Category } from '../api'

/**
 * A transaction's category as a small chip; tapping it opens the category list. Under the chip is
 * a real <select> stretched over it and made transparent, so phones open their native picker,
 * keyboards and screen readers get a normal dropdown, and no extra library is needed.
 */
export function CategoryPicker({
  value,
  categoryName,
  categories,
  label,
  onChange,
  title,
}: {
  value: number | null
  categoryName: string | null
  categories: Category[]
  /** Accessible name, e.g. "Category for Starbucks". */
  label: string
  onChange: (value: string) => void
  title?: string
}) {
  const uncategorized = value === null
  return (
    <span
      title={title}
      className={`relative inline-flex max-w-full min-w-0 items-center gap-1 rounded-md border px-2 py-0.5 text-xs transition-colors duration-150 focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-accent hover:border-stone-400 ${
        uncategorized ? 'border-dashed border-stone-400 italic text-stone-600' : 'border-stone-200 text-stone-700'
      }`}
    >
      <span className="truncate">{categoryName ?? 'Uncategorized'}</span>
      <ChevronDown size={12} aria-hidden="true" className="shrink-0 text-stone-500" />
      <select
        value={uncategorized ? '' : String(value)}
        onChange={(event) => onChange(event.target.value)}
        aria-label={label}
        // Invisible but on top: every tap or click on the chip lands on the select.
        className="absolute inset-0 h-full w-full cursor-pointer opacity-0 focus:outline-none"
      >
        <option value="">Uncategorized</option>
        {categories.map((category) => (
          <option key={category.id} value={String(category.id)}>
            {category.name}
          </option>
        ))}
      </select>
    </span>
  )
}
