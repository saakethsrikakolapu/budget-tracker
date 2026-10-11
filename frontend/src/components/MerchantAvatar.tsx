import { hash } from '../categoryStyle'

const TINTS = [
  'bg-indigo-100 text-indigo-700',
  'bg-emerald-100 text-emerald-700',
  'bg-orange-100 text-orange-700',
  'bg-sky-100 text-sky-700',
  'bg-pink-100 text-pink-700',
  'bg-violet-100 text-violet-700',
  'bg-amber-100 text-amber-800',
  'bg-teal-100 text-teal-700',
]

/** Card-processor prefixes banks put before the merchant name ("SQ *JOES COFFEE", "TST* PIZZA"). */
const PREFIXES = /^(sq|tst|pos|ach|purchase|debit|card|paypal|pp)\b[\s*#:-]*/i

/** "NETFLIX.COM" becomes an "N" in a colored circle. Same merchant, same color, on every page. */
export function MerchantAvatar({ description, size = 'md' }: { description: string; size?: 'sm' | 'md' }) {
  const cleaned = description.trim().replace(PREFIXES, '')
  const letter = (cleaned.match(/[a-z0-9]/i)?.[0] ?? '?').toUpperCase()
  const word = cleaned.split(/[\s.*#]+/)[0]?.toLowerCase() ?? ''
  const box = size === 'sm' ? 'size-7 text-xs' : 'size-9 text-sm'
  return (
    <span
      aria-hidden="true"
      className={`grid shrink-0 place-items-center rounded-full font-semibold ${box} ${TINTS[hash(word) % TINTS.length]}`}
    >
      {letter}
    </span>
  )
}
