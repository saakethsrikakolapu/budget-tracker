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
 * Each category's icon. Categories stay quiet on purpose: no color per category, so the colors
 * that do appear (green fine, red over, highlighter "look here") always mean something.
 */
export type CategoryStyle = {
  icon: LucideIcon
}

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
  if (!name || name === 'Uncategorized') return { icon: CircleHelp }
  return { icon: DEFAULTS[name.toLowerCase()] ?? Tag }
}
