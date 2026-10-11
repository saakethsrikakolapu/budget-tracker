// Display helpers. Money math happens in the backend (exact decimals); these only format for display.

const currency = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

/** -4.75 -> "−$4.75", 250 -> "+$250.00" (signed) or "$250.00" (unsigned). */
export function formatMoney(amount: number, { signed = false } = {}): string {
  const text = currency.format(Math.abs(amount))
  if (!signed || amount === 0) return text
  return amount < 0 ? `−${text}` : `+${text}`
}

/**
 * "2026-09-28" -> "Sep 28, 2026".
 * Careful: new Date("2026-09-28") means midnight UTC, which is still Sep 27 in North Carolina,
 * so we build the date from its parts in the local time zone instead.
 */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

/** "2026-09" -> "September 2026" */
export function formatMonth(yearMonth: string): string {
  const [year, month] = yearMonth.split('-').map(Number)
  return new Date(year, month - 1, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

/** "2026-09" -> { from: "2026-09-01", to: "2026-09-30" } */
export function monthRange(yearMonth: string): { from: string; to: string } {
  const [year, month] = yearMonth.split('-').map(Number)
  const lastDay = new Date(year, month, 0).getDate() // day 0 of next month = last day of this month
  return { from: `${yearMonth}-01`, to: `${yearMonth}-${String(lastDay).padStart(2, '0')}` }
}

/** The current month in your time zone, e.g. "2026-10". */
export function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/** Valid money input: digits with at most 2 decimal places, e.g. "200" or "64.90". */
export function isMoneyInput(text: string): boolean {
  return /^\d{1,10}(\.\d{1,2})?$/.test(text.trim())
}

/** "2026-09" -> "Sep" (or "Sep 2026" with year: true) */
export function formatMonthShort(yearMonth: string, { year = false } = {}): string {
  const [y, m] = yearMonth.split('-').map(Number)
  return new Date(y, m - 1, 1).toLocaleDateString('en-US', year ? { month: 'short', year: 'numeric' } : { month: 'short' })
}

const CADENCE_LABELS: Record<string, string> = {
  WEEKLY: 'Weekly',
  BIWEEKLY: 'Every 2 weeks',
  MONTHLY: 'Monthly',
  QUARTERLY: 'Every 3 months',
  YEARLY: 'Yearly',
}

/** "BIWEEKLY" -> "Every 2 weeks" */
export function formatCadence(cadence: string): string {
  return CADENCE_LABELS[cadence] ?? cadence
}

/** "2026-09-26" -> "Sep 26" (built from parts in the local time zone, like formatDate). */
export function formatShortDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

/** "2026-09-26" -> "Friday, Sep 26" (adds the year only when it isn't this year). */
export function formatDayHeading(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return date.toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'short',
    day: 'numeric',
    ...(year !== new Date().getFullYear() && { year: 'numeric' }),
  })
}

/** Whole days from today to an ISO date: 0 = today, 3 = in three days, -2 = two days ago. */
export function daysFromToday(isoDate: string): number {
  const [year, month, day] = isoDate.split('-').map(Number)
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  return Math.round((new Date(year, month - 1, day).getTime() - today.getTime()) / 86_400_000)
}
