import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { CategoryIcon } from '../components/CategoryIcon'
import * as api from '../api'
import { currentMonth, formatMoney, formatMonth, isMoneyInput } from '../format'

/**
 * The monthly budget. Nobody has to type one in: with no budget yet, the page suggests one built
 * from the person's own statements (what comes in, or what usually goes out) and saves it in one
 * click. After that it shows how each category is doing, flags what's higher than usual, and any
 * amount can still be changed. The month lives in the URL (?month=2026-09).
 */
export function BudgetsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [months, setMonths] = useState<string[] | null>(null)
  const [overview, setOverview] = useState<api.BudgetOverview | null>(null)
  const [plan, setPlan] = useState<api.BudgetPlan | null>(null)
  /** The person's own monthly total for the suggestion ("" = use the suggested one). */
  const [customTarget, setCustomTarget] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const [error, setError] = useState<string | null>(null)

  // Default: the most recent month with transactions (or this month if you have none yet).
  const month = searchParams.get('month') ?? months?.[0] ?? currentMonth()
  const monthOptions = months === null ? [] : Array.from(new Set([currentMonth(), ...months])).sort().reverse()

  useEffect(() => {
    api.fetchTransactionMonths()
      .then(setMonths)
      .catch(() => setError('Could not load your data.'))
  }, [])

  useEffect(() => {
    if (months === null) return
    let ignore = false
    Promise.all([api.fetchBudgets(month), api.fetchBudgetPlan(month, customTarget || undefined)])
      .then(([o, p]) => {
        if (!ignore) {
          setOverview(o)
          setPlan(p)
        }
      })
      .catch(() => {
        if (!ignore) setError('Could not load your budget.')
      })
    return () => {
      ignore = true
    }
  }, [months, month, customTarget, reloadKey])

  async function save(categoryId: number, amount: string) {
    if (!isMoneyInput(amount) || Number(amount) <= 0) {
      setError('Enter an amount like 200 or 64.90.')
      return false
    }
    setError(null)
    try {
      await api.setBudget(categoryId, amount.trim())
      setReloadKey((key) => key + 1)
      return true
    } catch (err) {
      setError(err instanceof api.ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : 'Could not save.')
      return false
    }
  }

  async function handleRemove(line: api.BudgetLine) {
    if (!window.confirm(`Remove the budget for ${line.categoryName}?`)) return
    try {
      await api.deleteBudget(line.categoryId)
      setReloadKey((key) => key + 1)
    } catch {
      setError('Could not remove the budget.')
    }
  }

  async function handleUsePlan() {
    setError(null)
    try {
      await api.applyBudgetPlan(month, customTarget || undefined)
      setCustomTarget('')
      setReloadKey((key) => key + 1)
    } catch {
      setError('Could not save the budget. Please try again.')
    }
  }

  const hasBudget = (overview?.budgets.length ?? 0) > 0

  return (
    <div className="max-w-3xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-3xl font-bold text-stone-950">Budget</h1>
        <label className="block">
          <span className="sr-only">Month</span>
          <select
            value={month}
            onChange={(event) => setSearchParams({ month: event.target.value })}
            className="block rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm"
          >
            {monthOptions.map((m) => (
              <option key={m} value={m}>
                {formatMonth(m)}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-red-800">
          {error}
        </p>
      )}

      {(!overview || !plan) && !error && <div className="skeleton h-64" aria-label="Loading your budget" />}

      {overview && plan && !hasBudget && (
        <Suggestion plan={plan} customTarget={customTarget} onTarget={setCustomTarget} onUse={handleUsePlan} />
      )}

      {overview && plan && hasBudget && (
        <Tracking overview={overview} plan={plan} onSave={save} onRemove={handleRemove} />
      )}
    </div>
  )
}

/** No budget yet: the suggested one, ready to use in one click. */
function Suggestion({
  plan,
  customTarget,
  onTarget,
  onUse,
}: {
  plan: api.BudgetPlan
  customTarget: string
  onTarget: (target: string) => void
  onUse: () => void
}) {
  if (plan.fullMonths === 0 || plan.target === null) {
    return (
      <div className="card p-6">
        <p className="font-display text-2xl font-semibold tracking-tight text-stone-950">
          Your budget builds itself from your statements.
        </p>
        <p className="mt-2 text-stone-600">
          It needs at least one full month to see how you spend.{' '}
          <Link to="/import" className="font-medium text-accent hover:underline">
            Upload a statement
          </Link>{' '}
          that covers a whole month, and it&apos;ll be ready.
        </p>
      </div>
    )
  }

  const lines = plan.lines.filter((l) => l.categoryId !== null && l.suggested > 0)
  const uncategorized = plan.lines.find((l) => l.categoryId === null)
  const fixedTotal = plan.lines.reduce((sum, l) => sum + l.fixed, 0)
  const leftOver = Math.round((plan.target - plan.suggestedTotal) * 100) / 100

  return (
    <div className="space-y-5">
      <div>
        <p className="font-display text-2xl font-semibold leading-snug tracking-tight text-stone-950">
          {plan.basis === 'INCOME' && (
            <>
              About <span className="text-accent tabular-nums">{formatMoney(plan.monthlyIncome ?? 0)}</span> comes in
              each month. Here&apos;s a budget that fits it.
            </>
          )}
          {plan.basis === 'USUAL' && (
            <>
              You usually spend about <span className="text-accent tabular-nums">{formatMoney(plan.target)}</span> a
              month. Here&apos;s a budget built around that.
            </>
          )}
          {plan.basis === 'CUSTOM' && (
            <>
              Here&apos;s a budget for <span className="text-accent tabular-nums">{formatMoney(plan.target)}</span> a
              month.
            </>
          )}
        </p>
        <p className="mt-2 text-sm text-stone-600">
          Regular bills and subscriptions keep their full cost; everything else is shared out the way you usually
          spend.
          {plan.fullMonths === 1 && ' Based on one full month so far, so it gets better as you upload more.'}
        </p>
      </div>

      <TargetEditor plan={plan} customTarget={customTarget} onTarget={onTarget} />

      <ul className="card divide-y divide-stone-100">
        {lines.map((line) => (
          <li key={line.categoryId} className="flex items-center gap-3 px-4 py-3">
            <CategoryIcon name={line.categoryName} />
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-stone-900">{line.categoryName}</p>
              <p className="text-xs text-stone-500">
                {line.fixed > 0 ? 'Regular charge, usually ' : 'Usually '}{formatMoney(line.usual)}
              </p>
            </div>
            <span className="font-semibold tabular-nums text-stone-950">{formatMoney(line.suggested)}</span>
          </li>
        ))}
        <li className="flex items-center justify-between px-4 py-3">
          <span className="font-semibold text-stone-950">Total</span>
          <span className="font-display text-lg font-bold tabular-nums text-stone-950">
            {formatMoney(plan.suggestedTotal)}
          </span>
        </li>
      </ul>

      <div className="space-y-1 text-sm text-stone-600">
        {leftOver >= 1 && plan.basis !== 'USUAL' && (
          <p>
            That leaves <strong className="text-accent tabular-nums">{formatMoney(leftOver)}</strong> a month to save.
          </p>
        )}
        {fixedTotal > plan.target && (
          <p>Your regular bills alone are {formatMoney(fixedTotal)}, more than that total.</p>
        )}
        {uncategorized && uncategorized.usual > 0 && (
          <p>
            Plus about {formatMoney(uncategorized.usual)} a month that isn&apos;t categorized yet.{' '}
            <Link to="/transactions?category=uncategorized&month=all" className="font-medium text-accent hover:underline">
              Sort those
            </Link>{' '}
            to include them.
          </p>
        )}
      </div>

      <button
        type="button"
        onClick={onUse}
        className="rounded-lg bg-accent px-5 py-3 font-semibold text-accent-ink transition-colors duration-150 hover:bg-accent-hover"
      >
        Use this budget
      </button>
      <p className="text-xs text-stone-500">You can change any amount afterwards.</p>
    </div>
  )
}

/** "Monthly total: $850 [Change]": the one number someone might want to set themselves. */
function TargetEditor({
  plan,
  customTarget,
  onTarget,
}: {
  plan: api.BudgetPlan
  customTarget: string
  onTarget: (target: string) => void
}) {
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState('')
  const [invalid, setInvalid] = useState(false)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!isMoneyInput(text) || Number(text) <= 0) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    setEditing(false)
    onTarget(text.trim())
  }

  if (!editing) {
    return (
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-stone-600">
        <span>
          Monthly total: <strong className="tabular-nums text-stone-950">{formatMoney(plan.target ?? 0)}</strong>
        </span>
        <button
          type="button"
          onClick={() => {
            setText(String(Math.round(plan.target ?? 0)))
            setEditing(true)
          }}
          className="font-medium text-accent hover:underline"
        >
          Change
        </button>
        {customTarget && (
          <button type="button" onClick={() => onTarget('')} className="text-stone-500 underline">
            Back to the suggestion
          </button>
        )}
      </p>
    )
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-wrap items-center gap-2 text-sm">
      <label htmlFor="monthly-total" className="text-stone-600">
        Monthly total
      </label>
      <span className="text-stone-500">$</span>
      <input
        id="monthly-total"
        type="text"
        inputMode="decimal"
        value={text}
        onChange={(event) => setText(event.target.value)}
        autoFocus
        aria-invalid={invalid || undefined}
        aria-describedby={invalid ? 'monthly-total-error' : undefined}
        className="min-h-9 w-28 rounded-md border border-stone-300 bg-white px-2 py-1.5"
      />
      <button type="submit" className="rounded-lg bg-stone-950 px-3 py-1.5 font-medium text-white hover:bg-stone-800">
        Update
      </button>
      <button type="button" onClick={() => setEditing(false)} className="px-2 py-1.5 text-stone-600">
        Cancel
      </button>
      {invalid && (
        <span id="monthly-total-error" className="w-full text-red-600">
          Enter an amount like 600 or 650.50.
        </span>
      )}
    </form>
  )
}

/** A budget is set: how this month is going, category by category. */
function Tracking({
  overview,
  plan,
  onSave,
  onRemove,
}: {
  overview: api.BudgetOverview
  plan: api.BudgetPlan
  onSave: (categoryId: number, amount: string) => Promise<boolean>
  onRemove: (line: api.BudgetLine) => void
}) {
  const planByCategory = new Map(plan.lines.map((l) => [l.categoryId, l]))
  const notBudgeted = plan.lines.filter((l) => l.categoryId !== null && !l.hasBudget && l.usual >= 5)
  const left = overview.totalBudgeted - overview.totalSpent

  return (
    <div className="space-y-6">
      <div>
        <p className="font-display text-2xl font-semibold leading-snug tracking-tight text-stone-950">
          {left >= 0 ? (
            <>
              <span className="text-accent tabular-nums">{formatMoney(left)}</span> left of{' '}
              {formatMoney(overview.totalBudgeted)} this month.
            </>
          ) : (
            <>
              <span className="text-red-600 tabular-nums">{formatMoney(-left)}</span> over your{' '}
              {formatMoney(overview.totalBudgeted)} budget this month.
            </>
          )}
        </p>
        <PaceSentence plan={plan} />
      </div>

      <ul className="divide-y divide-stone-100 rounded-lg border border-stone-200 bg-white">
        {overview.budgets.map((line) => (
          <BudgetRow
            key={line.categoryId}
            line={line}
            planLine={planByCategory.get(line.categoryId)}
            onSave={onSave}
            onRemove={() => onRemove(line)}
          />
        ))}
      </ul>

      {notBudgeted.length > 0 && (
        <section>
          <h2 className="text-lg font-bold text-stone-950">Not in your budget</h2>
          <p className="mt-1 text-sm text-stone-600">You spend on these too. Add one with its usual amount.</p>
          <ul className="mt-3 divide-y divide-stone-100 rounded-lg border border-stone-200 bg-white">
            {notBudgeted.map((l) => {
              const amount = Math.max(Math.ceil(l.usual), 1)
              return (
                <li key={l.categoryId} className="flex items-center gap-3 px-4 py-3">
                  <CategoryIcon name={l.categoryName} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium text-stone-900">{l.categoryName}</p>
                    <p className="text-xs text-stone-500">usually {formatMoney(l.usual)} a month</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => onSave(l.categoryId as number, String(amount))}
                    className="min-h-9 rounded-lg border border-stone-300 px-3 py-1.5 text-sm font-medium hover:bg-stone-100"
                  >
                    Add {formatMoney(amount)}
                  </button>
                </li>
              )
            })}
          </ul>
        </section>
      )}
    </div>
  )
}

/**
 * How this month compares with usual, by the same day of the month: the categories that are
 * higher first (they're what to act on), then the month as a whole.
 */
function PaceSentence({ plan }: { plan: api.BudgetPlan }) {
  if (plan.pace === 'UNKNOWN') return null
  const higher = plan.lines.filter((l) => l.pace === 'HIGHER').map((l) => l.categoryName)
  if (higher.length > 0) {
    return (
      <p className="mt-2 text-sm text-stone-600">
        Higher than usual: <mark className="font-medium">{listOf(higher)}</mark>.
      </p>
    )
  }
  if (plan.pace === 'NORMAL') {
    return <p className="mt-2 text-sm text-stone-600">You&apos;re spending about like you usually do.</p>
  }
  const byWhen = plan.throughDay >= 28 ? 'in a usual month' : `by the ${ordinal(plan.throughDay)}`
  return (
    <p className="mt-2 text-sm text-stone-600">
      {plan.pace === 'HIGHER' ? 'Faster than usual: ' : 'Slower than usual: '}
      <strong className="tabular-nums text-stone-950">{formatMoney(plan.spent)}</strong> so far, compared with about{' '}
      {formatMoney(plan.usualSoFar)} {byWhen}.
    </p>
  )
}

function BudgetRow({
  line,
  planLine,
  onSave,
  onRemove,
}: {
  line: api.BudgetLine
  planLine: api.PlanLine | undefined
  onSave: (categoryId: number, amount: string) => Promise<boolean>
  onRemove: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [amount, setAmount] = useState(line.limit.toFixed(2))
  const over = line.remaining < 0
  // Green until the limit, red once over (the words below say it too).
  const barColor = over ? 'bg-red-600' : 'bg-accent'

  return (
    <li className="space-y-2 px-4 py-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <span className="flex items-center gap-2 font-medium text-stone-900">
          <CategoryIcon name={line.categoryName} />
          {line.categoryName}
          {planLine?.pace === 'HIGHER' && <mark className="text-xs font-medium">Higher than usual</mark>}
        </span>
        <span className="text-sm tabular-nums text-stone-600">
          <strong className="font-semibold text-stone-950">{formatMoney(Math.max(line.spent, 0))}</strong> of{' '}
          {formatMoney(line.limit)}
        </span>
      </div>
      <div
        className="h-1.5 w-full overflow-hidden rounded-full bg-stone-100"
        role="progressbar"
        aria-valuenow={line.percentUsed}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`${line.categoryName}: ${line.percentUsed}% of budget used`}
      >
        <div className={`h-full rounded-full ${barColor}`} style={{ width: `${Math.min(line.percentUsed, 100)}%` }} />
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className={over ? 'font-medium text-red-600' : 'text-stone-500'}>
          {over ? `${formatMoney(-line.remaining)} over budget` : `${formatMoney(line.remaining)} left`}
          {planLine && planLine.usual > 0 && `, usually ${formatMoney(planLine.usual)} a month`}
        </span>
        {editing ? (
          <form
            onSubmit={async (event) => {
              event.preventDefault()
              if (await onSave(line.categoryId, amount)) setEditing(false)
            }}
            className="flex items-center gap-2"
          >
            <span className="text-stone-500">$</span>
            <input
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              autoFocus
              aria-label={`Monthly budget for ${line.categoryName}`}
              className="min-h-9 w-24 rounded-md border border-stone-300 bg-white px-2 py-1.5 text-sm"
            />
            <button type="submit" className="font-medium text-stone-900">
              Save
            </button>
            <button type="button" onClick={() => setEditing(false)} className="text-stone-500">
              Cancel
            </button>
          </form>
        ) : (
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="min-h-9 rounded-lg border border-stone-300 px-3 py-1.5 hover:bg-stone-100"
            >
              Change
            </button>
            <button
              type="button"
              onClick={onRemove}
              className="min-h-9 rounded-lg border border-red-300 px-3 py-1.5 text-red-700 hover:bg-red-50"
            >
              Remove
            </button>
          </div>
        )}
      </div>
    </li>
  )
}

/** ["A"] -> "A", ["A", "B"] -> "A and B", ["A", "B", "C"] -> "A, B and C". */
function listOf(items: string[]): string {
  return items.length <= 1 ? (items[0] ?? '') : `${items.slice(0, -1).join(', ')} and ${items.at(-1)}`
}

/** 1 -> "1st", 12 -> "12th", 22 -> "22nd". */
function ordinal(day: number): string {
  const suffix = day % 10 === 1 && day !== 11 ? 'st' : day % 10 === 2 && day !== 12 ? 'nd' : day % 10 === 3 && day !== 13 ? 'rd' : 'th'
  return `${day}${suffix}`
}
