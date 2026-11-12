import { Pencil } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import * as api from '../api'
import { budgetStatus, remainingText, STATUS_TEXT } from '../budgetStatus'
import { BudgetMeter, HigherFlag } from '../components/BudgetMeter'
import { CategoryIcon } from '../components/CategoryIcon'
import { FirstRun } from '../components/FirstRun'
import { LoadError } from '../components/LoadError'
import { formatMoney, formatMonth, isMoneyInput } from '../format'

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
  /** Loading failed (not an action): shown with a Try again button. */
  const [loadError, setLoadError] = useState<string | null>(null)
  const [monthsAttempt, setMonthsAttempt] = useState(0)

  // Same rule as the dashboard: only months with transactions, newest first; the newest by default.
  const month = searchParams.get('month') ?? months?.[0] ?? null
  const monthOptions = months ?? []
  /** After "Remove from budget": what to put back if they tap Undo. */
  const [removed, setRemoved] = useState<api.BudgetLine | null>(null)

  useEffect(() => {
    api.fetchTransactionMonths()
      .then((list) => {
        setMonths(list)
        setLoadError(null)
      })
      .catch(() => setLoadError('your data'))
  }, [monthsAttempt])

  useEffect(() => {
    if (month === null) return
    let ignore = false
    Promise.all([api.fetchBudgets(month), api.fetchBudgetPlan(month, customTarget || undefined)])
      .then(([o, p]) => {
        if (!ignore) {
          setOverview(o)
          setPlan(p)
          setLoadError(null)
        }
      })
      .catch(() => {
        if (!ignore) setLoadError('your budget')
      })
    return () => {
      ignore = true
    }
  }, [month, customTarget, reloadKey])

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

  /** No confirm dialog: it's easy to put back (Undo here, or "Not in your budget"). */
  async function handleRemove(line: api.BudgetLine) {
    try {
      await api.deleteBudget(line.categoryId)
      setRemoved(line)
      setReloadKey((key) => key + 1)
    } catch {
      setError('Could not remove the budget.')
    }
  }

  async function handleUndoRemove() {
    if (!removed) return
    if (await save(removed.categoryId, removed.limit.toFixed(2))) setRemoved(null)
  }

  async function handleUsePlan() {
    setError(null)
    try {
      if (month === null) return
      await api.applyBudgetPlan(month, customTarget || undefined)
      setCustomTarget('')
      setReloadKey((key) => key + 1)
    } catch {
      setError('Could not save the budget. Please try again.')
    }
  }

  const hasBudget = (overview?.budgets.length ?? 0) > 0

  if (months !== null && months.length === 0) {
    return (
      <FirstRun title="Budget">
        <p>
          Your budget builds itself from your statements: what usually comes in, what usually goes out, and room
          for your regular bills. Nothing to type in.
        </p>
      </FirstRun>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-3xl font-bold text-stone-950">Budget</h1>
        {month && <label className="block">
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
        </label>}
      </div>

      {removed && (
        <p role="status" className="flex flex-wrap items-center gap-3 text-sm text-stone-700">
          Took {removed.categoryName} out of your budget.
          <button type="button" onClick={handleUndoRemove} className="font-medium text-accent underline underline-offset-4">
            Undo
          </button>
        </p>
      )}

      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-red-800">
          {error}
        </p>
      )}

      {loadError && (
        <LoadError
          what={loadError}
          onRetry={() => {
            setLoadError(null)
            if (months === null) setMonthsAttempt((n) => n + 1)
            else setReloadKey((key) => key + 1)
          }}
        />
      )}

      {(!overview || !plan) && !error && !loadError && <div className="skeleton h-64" aria-label="Loading your budget" />}

      {overview && plan && !hasBudget && (
        <div className="max-w-3xl">
        <Suggestion plan={plan} customTarget={customTarget} onTarget={setCustomTarget} onUse={handleUsePlan} />
        </div>
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
              About <span className="text-good tabular-nums">{formatMoney(plan.monthlyIncome ?? 0)}</span> comes in
              each month. Here&apos;s a budget that fits it.
            </>
          )}
          {plan.basis === 'USUAL' && (
            <>
              You usually spend about <span className="tabular-nums">{formatMoney(plan.target)}</span> a
              month. Here&apos;s a budget built around that.
            </>
          )}
          {plan.basis === 'CUSTOM' && (
            <>
              Here&apos;s a budget for <span className="tabular-nums">{formatMoney(plan.target)}</span> a
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
            That leaves <strong className="text-good tabular-nums">{formatMoney(leftOver)}</strong> a month to save.
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
        className="rounded-lg bg-button px-5 py-3 font-semibold text-button-ink transition-colors duration-150 hover:bg-button-hover"
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
      <button type="submit" className="rounded-lg bg-button px-3 py-1.5 font-medium text-button-ink hover:bg-button-hover">
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
  const monthName = formatMonth(overview.month).split(' ')[0]
  /** The one row whose editor is open. */
  const [editingId, setEditingId] = useState<number | null>(null)

  return (
    // Desktop: the budget on the left, the categories you could add beside it.
    <div
      className={`grid grid-cols-1 gap-8 lg:gap-12 ${notBudgeted.length > 0 ? 'lg:grid-cols-[minmax(0,1fr)_20rem]' : 'max-w-3xl'}`}
    >
      <div className="space-y-6">
        <div>
          <p className="max-w-[40ch] font-display text-2xl font-semibold leading-snug tracking-tight text-balance text-stone-950">
            {left >= 0 ? (
              <>
                <span className="text-good tabular-nums">{formatMoney(left)}</span> left of your{' '}
                {formatMoney(overview.totalBudgeted)} budget in {monthName}.
              </>
            ) : (
              <>
                <span className="text-over tabular-nums">{formatMoney(-left)}</span> over your{' '}
                {formatMoney(overview.totalBudgeted)} budget in {monthName}. It happens; next month starts fresh.
              </>
            )}
          </p>
          <PaceSentence plan={plan} overview={overview} />
        </div>

        <ul className="card divide-y divide-stone-100">
          {overview.budgets.map((line) => (
            <BudgetRow
              key={line.categoryId}
              line={line}
              planLine={planByCategory.get(line.categoryId)}
              editing={editingId === line.categoryId}
              onEdit={(open) => setEditingId(open ? line.categoryId : null)}
              onSave={onSave}
              onRemove={() => {
                setEditingId(null)
                onRemove(line)
              }}
            />
          ))}
        </ul>
      </div>

      {notBudgeted.length > 0 && (
        <section className="lg:border-l lg:border-stone-200 lg:pl-10">
          <h2 className="text-lg font-bold text-stone-950">Not in your budget</h2>
          <p className="mt-1 text-sm text-stone-600">You spend on these too. Add one with its usual amount.</p>
          <ul className="mt-3 divide-y divide-stone-200">
            {notBudgeted.map((l) => {
              const amount = Math.max(Math.ceil(l.usual), 1)
              return (
                <li key={l.categoryId} className="flex items-center gap-3 py-3">
                  <CategoryIcon name={l.categoryName} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-stone-900">{l.categoryName}</p>
                    <p className="text-xs text-stone-500">usually {formatMoney(l.usual)} a month</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => onSave(l.categoryId as number, String(amount))}
                    className="min-h-9 rounded-lg border border-stone-300 px-3 py-1.5 text-sm font-medium transition-colors duration-150 hover:bg-stone-100"
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
 * How this month compares with usual, by the same day of the month. Categories running higher are
 * flagged on their own rows, so this only speaks for the month as a whole.
 */
function PaceSentence({ plan, overview }: { plan: api.BudgetPlan; overview: api.BudgetOverview }) {
  if (plan.pace === 'UNKNOWN') return null
  // Over budget in an ordinary month means the budget is set below your usual spending (the
  // suggestion trims to fit your income or target). Say that, instead of two facts that seem to clash.
  const usualForBudgeted = plan.lines.filter((l) => l.hasBudget).reduce((sum, l) => sum + l.usual, 0)
  const over = overview.totalSpent > overview.totalBudgeted
  if (over && plan.pace !== 'HIGHER' && usualForBudgeted > overview.totalBudgeted) {
    return (
      <p className="mt-2 max-w-[60ch] text-sm text-stone-600">
        That&apos;s close to what you usually spend on these (about{' '}
        <span className="tabular-nums">{formatMoney(usualForBudgeted)}</span> a month). Your budget is set lower on
        purpose, so getting under it can take a few months.
      </p>
    )
  }
  if (plan.pace === 'NORMAL') {
    return <p className="mt-2 text-sm text-stone-600">Overall, you&apos;re spending about like you usually do.</p>
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

/**
 * One budgeted category. Tapping the row (or its Edit button) opens its editor (amount, Save, and
 * "Remove from budget"), so the list isn't a column of Change and Remove buttons.
 */
function BudgetRow({
  line,
  planLine,
  editing,
  onEdit,
  onSave,
  onRemove,
}: {
  line: api.BudgetLine
  planLine: api.PlanLine | undefined
  editing: boolean
  onEdit: (open: boolean) => void
  onSave: (categoryId: number, amount: string) => Promise<boolean>
  onRemove: () => void
}) {
  const [amount, setAmount] = useState(line.limit.toFixed(2))
  const status = budgetStatus(line)
  const editorId = `budget-editor-${line.categoryId}`
  const toggle = () => {
    setAmount(line.limit.toFixed(2))
    onEdit(!editing)
  }

  return (
    <li>
      {/* The row is a big tap target for the mouse and touch; the Edit button is the same action for
          keyboards and screen readers (a button can't contain the progress bar). */}
      <div onClick={toggle} className="cursor-pointer px-4 py-3 transition-colors duration-150 hover:bg-stone-50">
        <span className="flex items-center gap-2">
          <CategoryIcon name={line.categoryName} />
          <span className="min-w-0 flex-1 leading-snug font-medium break-words text-stone-900">{line.categoryName}</span>
          <span className="ml-auto shrink-0 text-sm tabular-nums text-stone-600">
            <strong className="font-semibold text-stone-950">{formatMoney(Math.max(line.spent, 0))}</strong> of{' '}
            {formatMoney(line.limit)}
          </span>
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation()
              toggle()
            }}
            aria-expanded={editing}
            aria-controls={editorId}
            aria-label={`Edit ${line.categoryName} budget`}
            className="-my-1 -mr-2 inline-flex shrink-0 items-center gap-1 rounded-md px-2 py-1 text-sm text-stone-600 transition-colors duration-150 hover:bg-stone-200 hover:text-stone-900"
          >
            <Pencil size={14} aria-hidden="true" />
            <span aria-hidden="true" className="hidden sm:inline">
              Edit
            </span>
          </button>
        </span>
        <BudgetMeter line={line} className="mt-2.5" />
        {/* The flag sits on the status line, so long category names keep their room on phones. */}
        <span className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
          <span className={`tabular-nums ${STATUS_TEXT[status]}`}>
            {remainingText(line)}
            {planLine && planLine.usual > 0 && (
              <span className="font-normal text-stone-500">, usually {formatMoney(planLine.usual)} a month</span>
            )}
          </span>
          {planLine?.pace === 'HIGHER' && <HigherFlag />}
        </span>
      </div>

      {editing && (
        <form
          id={editorId}
          onSubmit={async (event) => {
            event.preventDefault()
            if (await onSave(line.categoryId, amount)) onEdit(false)
          }}
          className="flex flex-wrap items-center gap-2 border-t border-stone-100 bg-stone-50 px-4 py-3 text-sm"
        >
          <label htmlFor={`${editorId}-amount`} className="text-stone-600">
            Monthly budget
          </label>
          <span className="text-stone-500">$</span>
          <input
            id={`${editorId}-amount`}
            type="text"
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            autoFocus
            className="min-h-9 w-24 rounded-md border border-stone-300 bg-white px-2 py-1.5"
          />
          <button
            type="submit"
            className="min-h-9 rounded-lg bg-button px-3 py-1.5 font-medium text-button-ink transition-colors duration-150 hover:bg-button-hover"
          >
            Save
          </button>
          <button type="button" onClick={() => onEdit(false)} className="min-h-9 px-2 py-1.5 text-stone-600">
            Cancel
          </button>
          {/* Not destructive (it can be added back from "Not in your budget", and there's Undo): a quiet text button. */}
          <button type="button" onClick={onRemove} className="ml-auto min-h-9 px-2 py-1.5 font-medium text-over hover:underline">
            Remove from budget
          </button>
        </form>
      )}
    </li>
  )
}

/** 1 -> "1st", 12 -> "12th", 22 -> "22nd". */
function ordinal(day: number): string {
  const suffix = day % 10 === 1 && day !== 11 ? 'st' : day % 10 === 2 && day !== 12 ? 'nd' : day % 10 === 3 && day !== 13 ? 'rd' : 'th'
  return `${day}${suffix}`
}
