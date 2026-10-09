import type { ColumnMapping } from './api'

/** A reasonable starting point when the columns couldn't be worked out automatically. */
export function startingMapping(columnCount: number): ColumnMapping {
  const last = Math.max(columnCount - 1, 0)
  return {
    dateColumn: 0,
    postedDateColumn: null,
    descriptionColumn: Math.min(1, last),
    categoryColumn: null,
    amountStyle: 'SIGNED',
    amountColumn: Math.min(2, last),
    debitColumn: null,
    creditColumn: null,
    typeColumn: null,
    positiveIsSpending: false,
    dateFormat: 'M/d/uuuu',
  }
}
