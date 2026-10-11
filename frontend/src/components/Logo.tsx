/**
 * The app's mark: three rising bars in an indigo square. Same drawing as public/favicon.svg.
 * Decorative: pair it with visible text or an aria-label on the parent.
 */
export function Logo({ size = 28, inverted = false }: { size?: number; inverted?: boolean }) {
  const square = inverted ? '#ffffff' : '#4f46e5'
  const bars = inverted ? '#4f46e5' : '#ffffff'
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" className="shrink-0">
      <rect width="32" height="32" rx="8" fill={square} />
      <rect x="8" y="17" width="4" height="7" rx="1.5" fill={bars} opacity=".6" />
      <rect x="14" y="12" width="4" height="12" rx="1.5" fill={bars} opacity=".8" />
      <rect x="20" y="8" width="4" height="16" rx="1.5" fill={bars} />
    </svg>
  )
}
