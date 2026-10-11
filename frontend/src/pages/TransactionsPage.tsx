import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import * as api from '../api'
import { CategoryIcon } from '../components/CategoryIcon'
import { MerchantAvatar } from '../components/MerchantAvatar'
import { formatDayHeading, formatMoney, formatMonth, monthRange } from '../format'

const ALL_TIME = 'all'

/**
 * Your transactions, filterable by account, month, category, and merchant, with a category
 * dropdown on each row. Filters live in the URL (e.g. /transactions?account=3&month=2026-09&category=7&q=coffee&page=2),
 * so refresh, the back
 * button, and bookmarks all keep them.
 */
export function TransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [accounts, setAccounts] = useState<api.Account[]>([])
  const [categories, setCategories] = useState<api.Category[]>([])
  // Bumped after a category change so the totals are re-fetched.
  const [reloadKey, setReloadKey] = useState(0)
  // After a manual category change, offer to turn it into a rule.
  const [suggestion, setSuggestion] = useState<{
    pattern: string
    otherMatches: number
    categoryId: number
    categoryName: string
  } | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  /** How to reverse the last "always" choice: delete the new rule, or point the old one back. */
  const [undo, setUndo] = useState<{ ruleId: number; pattern: string; previousCategoryId: number | null } | null>(null)
  const [months, setMonths] = useState<string[] | null>(null)
  const [data, setData] = useState<api.TransactionPage | null>(null)
  const [error, setError] = useState<string | null>(null)

  const accountParam = searchParams.get('account') ?? ''
  const qParam = searchParams.get('q') ?? ''
  const categoryParam = searchParams.get('category') ?? ''
  const pageParam = Math.max(1, Number(searchParams.get('page')) || 1) // 1-based in the URL, for humans
  // No month in the URL means "the most recent month you have data for".
  const monthParam = searchParams.get('month') ?? months?.[0] ?? ALL_TIME

  const [searchText, setSearchText] = useState(qParam)
  // Keep the box in sync when the URL changes some other way (e.g. the back button).
  const [syncedQ, setSyncedQ] = useState(qParam)
  if (syncedQ !== qParam) {
    setSyncedQ(qParam)
    setSearchText(qParam)
  }

  // Load the dropdown options once.
  useEffect(() => {
    Promise.all([api.fetchAccounts(), api.fetchTransactionMonths(), api.fetchCategories()])
      .then(([accountList, monthList, categoryList]) => {
        setAccounts(accountList)
        setMonths(monthList)
        setCategories(categoryList)
      })
      .catch(() => setError('Could not load your accounts.'))
  }, [])

  // Re-fetch whenever the filters change.
  useEffect(() => {
    if (months === null) return // wait until we know the default month
    // If filters change quickly, an older (slower) response could arrive after a newer one and
    // overwrite it. "ignore" makes sure only the latest request updates the page.
    let ignore = false
    const range = monthParam === ALL_TIME ? {} : monthRange(monthParam)
    api.fetchTransactions({
      accountId: accountParam ? Number(accountParam) : undefined,
      ...range,
      q: qParam || undefined,
      category: categoryParam || undefined,
      page: pageParam - 1,
    })
      .then((result) => {
        if (!ignore) {
          setData(result)
          setError(null)
        }
      })
      .catch(() => {
        if (!ignore) setError('Could not load transactions.')
      })
    return () => {
      ignore = true
    }
  }, [months, accountParam, monthParam, qParam, categoryParam, pageParam, reloadKey])

  async function handleCategoryChange(transaction: api.Transaction, value: string) {
    try {
      const updated = await api.setTransactionCategory(transaction.id, value === '' ? null : Number(value))
      setData((previous) =>
        previous && { ...previous, items: previous.items.map((t) => (t.id === updated.id ? updated : t)) },
      )
      setReloadKey((key) => key + 1) // totals may change (e.g. moved into "Payments & Transfers")
      setNotice(null)
      setUndo(null)
      setSuggestion(null)
      if (updated.categoryId !== null) {
        // Ask the server which merchant text to offer: it knows every way this merchant appears.
        // If this fails, the category change still stands; there's just no prompt.
        const categoryId = updated.categoryId
        api.fetchRuleSuggestion(updated.id)
          .then(({ pattern, otherMatches }) =>
            setSuggestion({ pattern, otherMatches, categoryId, categoryName: updated.categoryName ?? '' }),
          )
          .catch(() => setSuggestion(null))
      }
    } catch {
      setError('Could not change the category. Please try again.')
    }
  }

  /**
   * "Yes, always": every transaction from this merchant goes to this category, now and in future
   * imports. Behind the scenes that's a rule; if one already exists for this merchant it's updated
   * instead, so the user never sees a "rule already exists" error.
   */
  async function handleAlways() {
    if (!suggestion) return
    try {
      let change: api.RuleChange
      let previousCategoryId: number | null = null
      try {
        change = await api.createRule(suggestion.pattern, suggestion.categoryId)
      } catch (err) {
        if (!(err instanceof api.ApiError && err.status === 409)) throw err
        const existing = (await api.fetchRules()).find((r) => r.pattern === suggestion.pattern.toUpperCase())
        if (!existing) throw err
        previousCategoryId = existing.categoryId
        change = await api.updateRule(existing.id, existing.pattern, suggestion.categoryId)
      }
      const others = change.recategorizedCount
      setNotice(
        `"${change.rule.pattern}" will always go to ${change.rule.categoryName}.` +
          (others > 0 ? ` ${others} other transaction${others === 1 ? '' : 's'} moved too.` : ''),
      )
      setUndo({ ruleId: change.rule.id, pattern: change.rule.pattern, previousCategoryId })
      setSuggestion(null)
      setReloadKey((key) => key + 1)
    } catch {
      setError('Could not save that. Please try again.')
    }
  }

  async function handleUndo() {
    if (!undo) return
    try {
      if (undo.previousCategoryId === null) {
        await api.deleteRule(undo.ruleId)
      } else {
        await api.updateRule(undo.ruleId, undo.pattern, undo.previousCategoryId)
      }
      setNotice('Undone.')
      setUndo(null)
      setReloadKey((key) => key + 1)
    } catch {
      setError('Could not undo. Please try again.')
    }
  }


  /** Change one filter; any filter change goes back to page 1. */
  function updateParam(key: string, value: string) {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key !== 'page') next.delete('page')
    setSearchParams(next)
  }

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    updateParam('q', searchText.trim())
  }

  if (months !== null && months.length === 0) {
    return (
      <div className="max-w-xl">
        <h1 className="text-3xl font-semibold tracking-tight text-zinc-950">Transactions</h1>
        <p className="mt-4 text-zinc-600">
          No transactions yet.{' '}
          <Link to="/import" className="font-medium text-accent hover:underline">
            Upload your first statement
          </Link>{' '}
          to get started.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <h1 className="text-3xl font-semibold tracking-tight text-zinc-950">Transactions</h1>

      {/* Filters */}
      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="text-xs font-medium text-zinc-600">Account</span>
          <select
            value={accountParam}
            onChange={(event) => updateParam('account', event.target.value)}
            className="mt-1 block rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">All accounts</option>
            {accounts.map((account) => (
              <option key={account.id} value={String(account.id)}>
                {account.name}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="text-xs font-medium text-zinc-600">Category</span>
          <select
            value={categoryParam}
            onChange={(event) => updateParam('category', event.target.value)}
            className="mt-1 block rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">All categories</option>
            <option value="uncategorized">Uncategorized</option>
            {categories.map((category) => (
              <option key={category.id} value={String(category.id)}>
                {category.name}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="text-xs font-medium text-zinc-600">Month</span>
          <select
            value={monthParam}
            onChange={(event) => updateParam('month', event.target.value)}
            className="mt-1 block rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm"
          >
            <option value={ALL_TIME}>All time</option>
            {months?.map((month) => (
              <option key={month} value={month}>
                {formatMonth(month)}
              </option>
            ))}
          </select>
        </label>

        <form onSubmit={handleSearch} className="flex items-end gap-2">
          <label className="block">
            <span className="text-xs font-medium text-zinc-600">Merchant</span>
            {/* Searches the Description column only; category filtering comes in Stage 2. */}
            <input
              type="search"
              value={searchText}
              onChange={(event) => setSearchText(event.target.value)}
              placeholder="Search by merchant name"
              maxLength={100}
              className="mt-1 block w-56 rounded-lg border border-zinc-300 px-3 py-2 text-sm"
            />
          </label>
          <button type="submit" className="rounded-lg bg-zinc-950 px-3 py-2 text-sm font-medium text-white transition-colors duration-150 hover:bg-zinc-800">
            Search
          </button>
          {qParam && (
            <button
              type="button"
              onClick={() => {
                setSearchText('')
                updateParam('q', '')
              }}
              className="px-2 py-2 text-sm text-zinc-600 underline"
            >
              Clear
            </button>
          )}
        </form>
      </div>

      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-red-800">
          {error}
        </p>
      )}

      {suggestion && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-indigo-100 bg-indigo-50 px-4 py-3">
          <div className="flex items-center gap-3">
            <CategoryIcon name={suggestion.categoryName} />
            <div>
              <p className="text-sm font-medium text-zinc-900">
                Always put &ldquo;{suggestion.pattern}&rdquo; in {suggestion.categoryName}?
              </p>
              <p className="text-xs text-zinc-600">
                {suggestion.otherMatches > 0
                  ? `${suggestion.otherMatches} other transaction${suggestion.otherMatches === 1 ? '' : 's'} and future ones will move too.`
                  : 'Future transactions from this merchant will go there too.'}
              </p>
            </div>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handleAlways}
              className="rounded-lg bg-zinc-950 px-3 py-1.5 text-sm font-medium text-white transition-colors duration-150 hover:bg-zinc-800"
            >
              Yes, always
            </button>
            <button
              type="button"
              onClick={() => setSuggestion(null)}
              className="rounded-lg px-3 py-1.5 text-sm text-zinc-700 transition-colors duration-150 hover:bg-indigo-100"
            >
              Just this one
            </button>
          </div>
        </div>
      )}
      {notice && (
        <p role="status" className="flex flex-wrap items-center gap-3 rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          {notice}
          {undo && (
            <button type="button" onClick={handleUndo} className="font-medium underline">
              Undo
            </button>
          )}
        </p>
      )}
      {data && (
        <>
          {/* Totals over all matching transactions, from the backend */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Stat label="Spent" value={formatMoney(data.totals.spent)} />
            <Stat label="Refunds" value={formatMoney(data.totals.refunds)} />
            <Stat label="Net spending" value={formatMoney(data.totals.netSpending)} />
            <Stat label="Transactions" value={String(data.totals.count)} />
          </div>
          {data.totals.excludedCount > 0 && (
            <p className="text-xs text-zinc-500">
              {data.totals.excludedCount} transaction{data.totals.excludedCount === 1 ? '' : 's'} (like card payments)
              {data.totals.excludedCount === 1 ? ' is' : ' are'} in categories that don&apos;t count as spending, so{' '}
              {data.totals.excludedCount === 1 ? "it's" : "they're"} left out of these totals.
            </p>
          )}

          {data.items.length === 0 ? (
            <p className="rounded-xl border border-zinc-200 bg-white px-4 py-8 text-center text-zinc-500">
              No transactions match these filters.
            </p>
          ) : (
            // Grouped by day, newest first (the backend already sorts by date).
            <div className="card divide-y divide-zinc-100 overflow-hidden">
              {groupByDay(data.items).map(([day, items]) => (
                <section key={day} aria-label={formatDayHeading(day)}>
                  <h2 className="bg-zinc-50/80 px-4 py-2 text-xs font-medium text-zinc-500">{formatDayHeading(day)}</h2>
                  <ul className="divide-y divide-zinc-100">
                    {items.map((t) => (
                      <li key={t.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3 sm:flex-nowrap">
                        <MerchantAvatar description={t.description} />
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-medium text-zinc-900">{t.description}</p>
                          <p className="truncate text-xs text-zinc-500">{t.accountName}</p>
                        </div>
                        {/* The category is a quiet dropdown: looks like a label, changes on click. */}
                        <div className="order-last flex w-full items-center gap-2 pl-12 sm:order-none sm:w-56 sm:pl-0">
                          <CategoryIcon name={t.categoryName} size="sm" />
                          <select
                            value={t.categoryId === null ? '' : String(t.categoryId)}
                            onChange={(event) => handleCategoryChange(t, event.target.value)}
                            title={t.bankCategory ? `Bank's label: ${t.bankCategory}` : undefined}
                            aria-label={`Category for ${t.description}`}
                            className={`min-h-9 min-w-0 flex-1 truncate rounded-md border border-transparent bg-transparent py-1.5 text-sm transition-colors duration-150 hover:border-zinc-300 ${
                              t.categoryId === null ? 'italic text-zinc-500' : 'text-zinc-700'
                            }`}
                          >
                            <option value="">Uncategorized</option>
                            {categories.map((category) => (
                              <option key={category.id} value={String(category.id)}>
                                {category.name}
                              </option>
                            ))}
                          </select>
                        </div>
                        <span
                          className={`w-24 text-right text-sm font-medium tabular-nums ${
                            t.amount < 0 ? 'text-zinc-900' : 'text-emerald-700'
                          }`}
                        >
                          {formatMoney(t.amount, { signed: true })}
                        </span>
                      </li>
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          )}

          {data.totalPages > 1 && (
            <div className="flex items-center justify-end gap-3 text-sm">
              <button
                type="button"
                disabled={pageParam <= 1}
                onClick={() => updateParam('page', String(pageParam - 1))}
                className="rounded-lg border border-zinc-300 px-3 py-1.5 hover:bg-zinc-100 disabled:opacity-40"
              >
                ‹ Prev
              </button>
              <span className="text-zinc-600">
                Page {pageParam} of {data.totalPages}
              </span>
              <button
                type="button"
                disabled={pageParam >= data.totalPages}
                onClick={() => updateParam('page', String(pageParam + 1))}
                className="rounded-lg border border-zinc-300 px-3 py-1.5 hover:bg-zinc-100 disabled:opacity-40"
              >
                Next ›
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}

/** [day, transactions] pairs in the order the days first appear. */
function groupByDay(items: api.Transaction[]): [string, api.Transaction[]][] {
  const groups = new Map<string, api.Transaction[]>()
  for (const t of items) {
    groups.set(t.transactionDate, [...(groups.get(t.transactionDate) ?? []), t])
  }
  return [...groups.entries()]
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-zinc-200 bg-white px-4 py-3">
      <p className="kicker">{label}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums text-zinc-900">{value}</p>
    </div>
  )
}
