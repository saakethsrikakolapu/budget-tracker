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
