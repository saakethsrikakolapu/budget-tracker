/**
 * A readable merchant name from a bank's description, for display only (the raw text is always
 * shown too, and rules and search still use the raw text).
 *
 *   "Debit Card Purchase - CHICK FIL A 04954 CHAPEL HILL NC"  -> "Chick Fil A"
 *   "PURCHASE AUTHORIZED ON 10/21 STREAMFLIX CARD 1234"       -> "Streamflix"
 *   "SQ *JOES COFFEE"                                          -> "Joes Coffee"
 *   "NETFLIX.COM"                                              -> "Netflix.com"
 *   "Zelle money received from Jane Doe"                       -> unchanged (already readable)
 *
 * Steps: drop the bank's lead-in ("Debit Card Purchase -", "POS", "SQ *"), cut at the first
 * store number / phone number / reference code or legal suffix (LLC, INC), then turn SHOUTING into
 * Title Case. Text that already has lowercase letters was written for humans, so it's left alone.
 */

const LEAD_IN =
  /^(?:(?:debit|credit|digital|check|card|purchase|recurring|pos|authorized|on|visa|mastercard|sq|tst|pp|paypal|\d{1,2}\/\d{1,2}(?:\/\d{2,4})?)\b[\s*#:.-]*|[*#-]\s*)+/i

/** Words that end the merchant name: what follows is location or reference noise. */
const STOP_WORDS = new Set(['LLC', 'INC', 'CORP', 'CO', 'LTD', 'CARD', 'PURCHASE', 'AUTHORIZED', 'ON'])

/** Short words kept in capitals: they're usually acronyms (CVS, UNC, AMC). */
const LOWER_SHORT_WORDS = new Set(['A', 'AN', 'OF', 'THE', 'AND', 'TO', 'AT', 'FIL', 'BAR', 'BBQ', 'CAFE', 'EAT', 'BUS', 'GAS'])

export function merchantName(description: string): string {
  const original = description.trim().replace(/\s+/g, ' ')
  const withoutLeadIn = original.replace(LEAD_IN, '').trim()
  if (withoutLeadIn === '') return original
  if (/[a-z]/.test(withoutLeadIn)) return withoutLeadIn // already human-written

  const kept: string[] = []
  for (const word of withoutLeadIn.split(/[\s/]+/)) {
    const bare = word.replace(/[^A-Z0-9.&']/gi, '')
    if (word === '-' && kept.length > 0) break // "AUTOMATIC PAYMENT - THANK YOU": the part after a dash is detail
    if (kept.length > 0 && (/\d/.test(bare) || STOP_WORDS.has(bare.replace(/\./g, '')))) break
    if (bare === '' || /^#?\d+$/.test(bare)) continue
    if (bare === 'COM' && kept.length > 0) {
      kept[kept.length - 1] += '.com' // "APPLE COM BILL" is apple.com/bill with the punctuation lost
      break
    }
    kept.push(bare)
    if (kept.length === 3) break // merchant names are short; the rest is usually the city
  }
  if (kept.length === 0) return original
  return kept.map(titleCase).join(' ')
}

function titleCase(word: string): string {
  if (word.length <= 3 && !LOWER_SHORT_WORDS.has(word) && !/[.']/.test(word)) return word
  return word.charAt(0) + word.slice(1).toLowerCase()
}
