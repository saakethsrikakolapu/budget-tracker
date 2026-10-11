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
 * How a category looks everywhere (chart slices, transaction rows, budgets): an icon, a solid
 * color for charts, and Tailwind classes for a tinted chip. Class names are written out in full
 * because Tailwind only generates classes it finds as complete strings in the source.
 */
export type CategoryStyle = {
  icon: LucideIcon
  /** Solid color for chart marks (>= 3:1 on white). */
  color: string
  /** Tinted chip: background + icon color. */
  chip: string
}

const PALETTE = {
  orange: { color: '#ea580c', chip: 'bg-orange-50 text-orange-700' },
  emerald: { color: '#059669', chip: 'bg-emerald-50 text-emerald-700' },
  pink: { color: '#db2777', chip: 'bg-pink-50 text-pink-700' },
  sky: { color: '#0284c7', chip: 'bg-sky-50 text-sky-700' },
  cyan: { color: '#0891b2', chip: 'bg-cyan-50 text-cyan-700' },
  violet: { color: '#7c3aed', chip: 'bg-violet-50 text-violet-700' },
  indigo: { color: '#4f46e5', chip: 'bg-indigo-50 text-indigo-700' },
  amber: { color: '#d97706', chip: 'bg-amber-50 text-amber-700' },
  blue: { color: '#2563eb', chip: 'bg-blue-50 text-blue-700' },
  rose: { color: '#e11d48', chip: 'bg-rose-50 text-rose-700' },
  red: { color: '#dc2626', chip: 'bg-red-50 text-red-700' },
  green: { color: '#16a34a', chip: 'bg-green-50 text-green-700' },
  slate: { color: '#64748b', chip: 'bg-slate-100 text-slate-700' },
  zinc: { color: '#71717a', chip: 'bg-zinc-100 text-zinc-600' },
  teal: { color: '#0d9488', chip: 'bg-teal-50 text-teal-700' },
  fuchsia: { color: '#c026d3', chip: 'bg-fuchsia-50 text-fuchsia-700' },
}

/** The default categories every user starts with (see DefaultCategories.java). */
const DEFAULTS: Record<string, CategoryStyle> = {
  'food & dining': { icon: UtensilsCrossed, ...PALETTE.orange },
  groceries: { icon: ShoppingCart, ...PALETTE.emerald },
  shopping: { icon: ShoppingBag, ...PALETTE.pink },
  transportation: { icon: Car, ...PALETTE.sky },
  travel: { icon: Plane, ...PALETTE.cyan },
  entertainment: { icon: Clapperboard, ...PALETTE.violet },
  subscriptions: { icon: Repeat, ...PALETTE.indigo },
  'bills & utilities': { icon: Zap, ...PALETTE.amber },
  education: { icon: GraduationCap, ...PALETTE.blue },
  'health & personal care': { icon: HeartPulse, ...PALETTE.rose },
  'fees & interest': { icon: Percent, ...PALETTE.red },
  income: { icon: Wallet, ...PALETTE.green },
  'payments & transfers': { icon: ArrowLeftRight, ...PALETTE.slate },
  other: { icon: Shapes, ...PALETTE.zinc },
}

const UNCATEGORIZED: CategoryStyle = { icon: CircleHelp, ...PALETTE.zinc }

/** Colors for categories the user created, picked by name so each keeps the same color. */
const CUSTOM_COLORS = [PALETTE.teal, PALETTE.fuchsia, PALETTE.sky, PALETTE.orange, PALETTE.violet, PALETTE.emerald]

export function categoryStyle(name: string | null | undefined): CategoryStyle {
  if (!name || name === 'Uncategorized') return UNCATEGORIZED
  return DEFAULTS[name.toLowerCase()] ?? { icon: Tag, ...CUSTOM_COLORS[hash(name) % CUSTOM_COLORS.length] }
}

/** Small, stable string hash (same text, same number) for picking colors. */
export function hash(text: string): number {
  let h = 0
  for (let i = 0; i < text.length; i++) h = (h * 31 + text.charCodeAt(i)) >>> 0
  return h
}
