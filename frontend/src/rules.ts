/**
 * Guess the merchant part of a bank description, to pre-fill a new rule:
 *   "DEPOP* P470816315920"   -> "DEPOP"
 *   "MERCARI*888-325-2168"   -> "MERCARI"
 *   "WALGREENS #7365"        -> "WALGREENS"
 *   "UBER   TRIP 1234"       -> "UBER TRIP"
 * Bank descriptions often end with order numbers or store numbers that change every time, which
 * would make a rule match only one transaction. The user can always edit the suggestion.
 */
export function suggestRulePattern(description: string): string {
  const full = description.trim().replace(/\s+/g, ' ').toUpperCase()
  const merchant = full
    .split(/[*#]/)[0] // text before "*" or "#" (order/store numbers usually follow)
    .replace(/[\d\s-]+$/, '') // trailing numbers, spaces, dashes
    .trim()
  return merchant.length >= 2 ? merchant : full
}

/** "3 transactions changed category." */
export function changedText(count: number): string {
  if (count === 0) return 'No transactions changed category.'
  return `${count} transaction${count === 1 ? '' : 's'} changed category.`
}
