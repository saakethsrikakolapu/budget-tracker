import {
  ArrowLeftRight,
  Car,
  CircleHelp,
  Clapperboard,
  GraduationCap,
  HeartPulse,
  Percent,
  Plane,
  Repeat,
  Shapes,
  ShoppingBag,
  ShoppingCart,
  Tag,
  UtensilsCrossed,
  Wallet,
  Zap,
  type LucideIcon,
} from 'lucide-react'

/**
 * Each category's icon and color. The color is one of eight "banknote inks" (CSS variables in
 * index.css, lightened in dark mode), used the same way everywhere: the dashboard strip, its legend
 * dots and the transaction icons. Rarer and custom categories share a quiet gray.
 */
export type CategoryStyle = {
  icon: LucideIcon
  /** A CSS color (a variable), for backgrounds, dots and icon strokes. */
  color: string
}

const COLORS: Record<string, string> = {
  groceries: 'var(--color-cat-groceries)',
  'food & dining': 'var(--color-cat-food)',
  'bills & utilities': 'var(--color-cat-bills)',
  entertainment: 'var(--color-cat-entertainment)',
  shopping: 'var(--color-cat-shopping)',
  subscriptions: 'var(--color-cat-subscriptions)',
  'health & personal care': 'var(--color-cat-health)',
  transportation: 'var(--color-cat-transport)',
}
export const OTHER_COLOR = 'var(--color-cat-other)'

/** The default categories every user starts with (see DefaultCategories.java). */
const DEFAULTS: Record<string, LucideIcon> = {
  'food & dining': UtensilsCrossed,
  groceries: ShoppingCart,
  shopping: ShoppingBag,
  transportation: Car,
  travel: Plane,
  entertainment: Clapperboard,
  subscriptions: Repeat,
  'bills & utilities': Zap,
  education: GraduationCap,
  'health & personal care': HeartPulse,
  'fees & interest': Percent,
  income: Wallet,
  'payments & transfers': ArrowLeftRight,
  other: Shapes,
}

export function categoryStyle(name: string | null | undefined): CategoryStyle {
  if (!name || name === 'Uncategorized') return { icon: CircleHelp, color: OTHER_COLOR }
  const key = name.toLowerCase()
  return { icon: DEFAULTS[key] ?? Tag, color: COLORS[key] ?? OTHER_COLOR }
}
