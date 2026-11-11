import type { BudgetLine } from './api'
import { formatMoney } from './format'

/**
 * What a budget line means, in one word. Green = room left, amber = under 10% left, red = over.
 * Shared by the dashboard and the Budget page so the same numbers always get the same color.
 */
export type BudgetStatus = 'good' | 'close' | 'over'

export function budgetStatus(line: Pick<BudgetLine, 'limit' | 'remaining'>): BudgetStatus {
  if (line.remaining < 0) return 'over'
  if (line.limit > 0 && line.remaining < line.limit * 0.1) return 'close'
  return 'good'
}

/** Text color for a status (the words next to the bar). */
export const STATUS_TEXT: Record<BudgetStatus, string> = {
  good: 'text-stone-500',
  close: 'font-medium text-close',
  over: 'font-medium text-over',
}

/** Bar fill for a status. */
export const STATUS_FILL: Record<BudgetStatus, string> = { good: 'bg-good', close: 'bg-close-bar', over: 'bg-over' }

/** "$5.02 left" / "$70.03 over": the status in words, so it never depends on color. */
export function remainingText(line: Pick<BudgetLine, 'remaining'>): string {
  return line.remaining < 0 ? `${formatMoney(-line.remaining)} over` : `${formatMoney(line.remaining)} left`
}
