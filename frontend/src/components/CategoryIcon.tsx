import { categoryStyle } from '../categoryStyle'

/** The category's icon, small and in ink. Decorative: the name is always shown next to it. */
export function CategoryIcon({ name, size = 'md' }: { name: string | null | undefined; size?: 'sm' | 'md' }) {
  const { icon: Icon } = categoryStyle(name)
  return <Icon aria-hidden="true" size={size === 'sm' ? 16 : 18} strokeWidth={1.75} className="shrink-0 text-stone-500" />
}
