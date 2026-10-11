import { categoryStyle } from '../categoryStyle'

/** The category's icon in a tinted rounded square. Decorative: the name is always shown next to it. */
export function CategoryIcon({ name, size = 'md' }: { name: string | null | undefined; size?: 'sm' | 'md' }) {
  const { icon: Icon, chip } = categoryStyle(name)
  const box = size === 'sm' ? 'size-6 rounded-md' : 'size-8 rounded-lg'
  return (
    <span aria-hidden="true" className={`grid shrink-0 place-items-center ${box} ${chip}`}>
      <Icon size={size === 'sm' ? 14 : 16} strokeWidth={2} />
    </span>
  )
}
