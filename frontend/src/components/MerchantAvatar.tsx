/** Card-processor prefixes banks put before the merchant name ("SQ *JOES COFFEE", "TST* PIZZA"). */
const PREFIXES = /^(sq|tst|pos|ach|purchase|debit|card|paypal|pp)\b[\s*#:-]*/i

/** "NETFLIX.COM" becomes an "N" in an ink circle: a quiet marker, not decoration. */
export function MerchantAvatar({ description, size = 'md' }: { description: string; size?: 'sm' | 'md' }) {
  const cleaned = description.trim().replace(PREFIXES, '')
  const letter = (cleaned.match(/[a-z0-9]/i)?.[0] ?? '?').toUpperCase()
  const box = size === 'sm' ? 'size-7 text-xs' : 'size-9 text-sm'
  return (
    <span
      aria-hidden="true"
      className={`grid shrink-0 place-items-center rounded-full border border-stone-300 font-display font-semibold text-stone-700 ${box}`}
    >
      {letter}
    </span>
  )
}
