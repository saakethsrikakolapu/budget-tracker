/**
 * The Spendr mark: a receipt with a torn edge (the statement you upload) on a green square.
 * Same drawing as public/favicon.svg. Decorative: pair it with visible text or an aria-label.
 */
export function Logo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" className="shrink-0">
      <rect width="32" height="32" rx="8" fill="#0f7a4f" />
      <path d="M9 6.5H23V24.5L20.67 22.75L18.33 24.5L16 22.75L13.67 24.5L11.33 22.75L9 24.5Z" fill="#ffffff" />
      <rect x="12" y="10.5" width="8" height="2" rx="1" fill="#0f7a4f" />
      <rect x="12" y="15" width="5" height="2" rx="1" fill="#0f7a4f" opacity=".55" />
    </svg>
  )
}

/** Logo + "spendr" wordmark, used in the header and on the logged-out pages. */
export function Brand({ size = 28 }: { size?: number }) {
  return (
    <span className="flex items-center gap-2">
      <Logo size={size} />
      <span className="font-display text-xl font-bold tracking-tight text-stone-950">spendr</span>
    </span>
  )
}
