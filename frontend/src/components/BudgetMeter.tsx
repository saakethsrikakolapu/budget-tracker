import type { BudgetLine } from '../api'
import { budgetStatus, STATUS_FILL } from '../budgetStatus'

/**
 * A thin bar for one budget. Under budget it fills toward the end. Over budget, the bar shows the
 * whole amount spent: a tick marks where the budget ended and the part past it is the overflow, so
 * you can see how far over (capped at twice the budget so the tick stays visible).
 */
export function BudgetMeter({ line, className = '' }: { line: BudgetLine; className?: string }) {
  const status = budgetStatus(line)
  const spent = Math.max(line.spent, 0)
  const over = status === 'over' && line.limit > 0
  const scale = over ? Math.min(spent, line.limit * 2) : line.limit
  const pct = (amount: number) => (scale > 0 ? Math.min((amount / scale) * 100, 100) : 0)
  const mark = over ? pct(line.limit) : 100
  return (
    <div
      className={`relative h-1.5 rounded-full bg-stone-200 ${className}`}
      role="progressbar"
      aria-valuenow={line.percentUsed}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={`${line.categoryName}: ${line.percentUsed}% of budget used`}
    >
      {over ? (
        <>
          {/* Up to the budget: the spending that was allowed, in a lighter red. */}
          <div className="absolute inset-y-0 left-0 rounded-l-full bg-over/40" style={{ width: `${mark}%` }} />
          {/* Past the budget: the overflow, in full red. */}
          <div className="absolute inset-y-0 right-0 rounded-r-full bg-over" style={{ left: `${mark}%` }} />
          {/* Where the budget ended. */}
          <div className="absolute -inset-y-1 w-0.5 rounded-full bg-stone-950" style={{ left: `calc(${mark}% - 1px)` }} />
        </>
      ) : (
        <div className={`h-full rounded-full ${STATUS_FILL[status]}`} style={{ width: `${pct(spent)}%` }} />
      )}
    </div>
  )
}

/** The amber "Higher than usual" flag. Shown once per item, next to its name. */
export function HigherFlag() {
  return (
    <span className="shrink-0 rounded px-1.5 py-0.5 text-xs font-medium whitespace-nowrap bg-flag text-flag-ink">
      Higher than usual
    </span>
  )
}
