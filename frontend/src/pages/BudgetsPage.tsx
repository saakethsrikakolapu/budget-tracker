import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import * as api from '../api'
import { currentMonth, formatMoney, formatMonth, isMoneyInput } from '../format'

/**
 * Monthly limits per category and how much of each you've used. A budget repeats every month;
 * pick a month to see that month's spending against it. The month lives in the URL (?month=2026-09).
 */
export function BudgetsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [months, setMonths] = useState<string[] | null>(null)
  const [categories, setCategories] = useState<api.Category[]>([])
  const [overview, setOverview] = useState<api.BudgetOverview | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [error, setError] = useState<string | null>(null)

  const [newCategoryId, setNewCategoryId] = useState('')
  const [newAmount, setNewAmount] = useState('')

  // Default: the most recent month with transactions (or this month if you have none yet).
  const month = searchParams.get('month') ?? months?.[0] ?? currentMonth()
  const monthOptions = months === null ? [] : Array.from(new Set([currentMonth(), ...months])).sort().reverse()

  useEffect(() => {
    Promise.all([api.fetchTransactionMonths(), api.fetchCategories()])
      .then(([monthList, categoryList]) => {
        setMonths(monthList)
        setCategories(categoryList)
      })
      .catch(() => setError('Could not load your categories.'))
  }, [])

  useEffect(() => {
    if (months === null) return
    let ignore = false
    api.fetchBudgets(month)
      .then((result) => {
        if (!ignore) setOverview(result)
      })
      .catch(() => {
        if (!ignore) setError('Could not load budgets.')
      })
    return () => {
      ignore = true
    }
  }, [months, month, reloadKey])

  const budgetedIds = new Set(overview?.budgets.map((b) => b.categoryId))
  const addable = categories.filter((c) => c.countsAsSpending && !budgetedIds.has(c.id))

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

  async function handleAdd(event: FormEvent) {
    event.preventDefault()
    if (newCategoryId === '') return
    if (await save(Number(newCategoryId), newAmount)) {
      setNewCategoryId('')
      setNewAmount('')
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

  return (
    <div className="max-w-3xl space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Budgets</h1>
          <p className="mt-1 text-sm text-slate-600">
            Monthly limits repeat every month. Spending is purchases minus refunds.
          </p>
        </div>
        <label className="block">
          <span className="text-xs font-medium text-slate-600">Month</span>
          <select
            value={month}
            onChange={(event) => setSearchParams({ month: event.target.value })}
            className="mt-1 block rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
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

      {overview && (
        <>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <Stat label="Budgeted" value={formatMoney(overview.totalBudgeted)} />
            <Stat label="Spent in budgets" value={formatMoney(overview.totalSpent)} />
            <Stat label="Unbudgeted spending" value={formatMoney(overview.unbudgetedSpending)} />
          </div>

          {overview.budgets.length === 0 ? (
            <p className="rounded-xl bg-white px-4 py-8 text-center text-slate-500 shadow">
              No budgets yet. Add one below, for example Food &amp; Dining at $200 a month.
            </p>
          ) : (
            <ul className="divide-y divide-slate-100 rounded-xl bg-white shadow">
              {overview.budgets.map((line) => (
                <BudgetRow key={line.categoryId} line={line} onSave={save} onRemove={() => handleRemove(line)} />
              ))}
            </ul>
          )}
        </>
      )}

      <form onSubmit={handleAdd} className="flex flex-wrap items-end gap-3 rounded-xl bg-white p-4 shadow">
        <label className="block">
          <span className="text-xs font-medium text-slate-600">Add a budget for</span>
          <select
            value={newCategoryId}
            onChange={(event) => setNewCategoryId(event.target.value)}
            className="mt-1 block rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">Choose a category…</option>
            {addable.map((c) => (
              <option key={c.id} value={String(c.id)}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="text-xs font-medium text-slate-600">Per month ($)</span>
          <input
            type="text"
            inputMode="decimal"
            value={newAmount}
            onChange={(event) => setNewAmount(event.target.value)}
            placeholder="200"
            className="mt-1 block w-32 rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
        </label>
        <button
          type="submit"
          disabled={newCategoryId === '' || newAmount.trim() === ''}
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          Add budget
        </button>
      </form>
    </div>
  )
}

function BudgetRow({
  line,
  onSave,
  onRemove,
}: {
  line: api.BudgetLine
  onSave: (categoryId: number, amount: string) => Promise<boolean>
  onRemove: () => void
}) {
  const [editing, setEditing] = useState(false)
  const [amount, setAmount] = useState(line.limit.toFixed(2))
  const over = line.remaining < 0
  // Green under 80%, amber 80-100%, red over budget.
  const barColor = over ? 'bg-red-500' : line.percentUsed >= 80 ? 'bg-amber-500' : 'bg-emerald-500'

  return (
    <li className="space-y-2 px-4 py-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <span className="font-medium text-slate-900">{line.categoryName}</span>
        <span className="text-sm tabular-nums text-slate-600">
          {formatMoney(Math.max(line.spent, 0))} of {formatMoney(line.limit)}
        </span>
      </div>
      <div
        className="h-2 w-full overflow-hidden rounded-full bg-slate-100"
        role="progressbar"
        aria-valuenow={line.percentUsed}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`${line.categoryName}: ${line.percentUsed}% of budget used`}
      >
        <div className={`h-full ${barColor}`} style={{ width: `${Math.min(line.percentUsed, 100)}%` }} />
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className={over ? 'font-medium text-red-600' : 'text-slate-500'}>
          {over ? `${formatMoney(-line.remaining)} over budget` : `${formatMoney(line.remaining)} left`} ·{' '}
          {line.percentUsed}% used
        </span>
        {editing ? (
          <form
            onSubmit={async (event) => {
              event.preventDefault()
              if (await onSave(line.categoryId, amount)) setEditing(false)
            }}
            className="flex items-center gap-2"
          >
            <span className="text-slate-500">$</span>
            <input
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              autoFocus
              aria-label={`Monthly budget for ${line.categoryName}`}
              className="w-24 rounded-md border border-slate-300 px-2 py-1 text-sm"
            />
            <button type="submit" className="font-medium text-slate-900">
              Save
            </button>
            <button type="button" onClick={() => setEditing(false)} className="text-slate-500">
              Cancel
            </button>
          </form>
        ) : (
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="rounded-lg border border-slate-300 px-3 py-1 hover:bg-slate-100"
            >
              Change
            </button>
            <button
              type="button"
              onClick={onRemove}
              className="rounded-lg border border-red-300 px-3 py-1 text-red-700 hover:bg-red-50"
            >
              Remove
            </button>
          </div>
        )}
      </div>
    </li>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-white px-4 py-3 shadow">
      <p className="text-xs font-medium uppercase text-slate-500">{label}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums text-slate-900">{value}</p>
    </div>
  )
}
