import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { Check, ChevronRight, SlidersHorizontal, Sparkles } from 'lucide-react'
import * as api from '../api'
import { CategoryIcon } from '../components/CategoryIcon'
import { CategoryPicker } from '../components/CategoryPicker'
import { FirstRun } from '../components/FirstRun'
import { useMediaQuery } from '../hooks/useMediaQuery'
import { formatDayHeading, formatMoney, formatMonth, formatShortDate, monthRange } from '../format'
import { merchantName } from '../merchant'

const ALL_TIME = 'all'

/**
 * Your transactions, filterable by account, month, category, and merchant, with a category
 * chip on each row. Filters live in the URL (e.g. /transactions?account=3&month=2026-09&category=7&q=coffee&page=2),
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
  /** Phones only: the account/category/month dropdowns stay folded away until asked for. */
  const [showFilters, setShowFilters] = useState(false)
  /** The row showing its details (the bank's own text, account, bank's label). */
  const [openId, setOpenId] = useState<number | null>(null)
  /** Desktop gets a real table; phones get a compact list grouped by day. */
  const wide = useMediaQuery('(min-width: 1024px)')
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

  const activeFilters = [accountParam, categoryParam, searchParams.get('month')].filter(Boolean).length

  if (months !== null && months.length === 0) {
    return (
      <FirstRun title="Transactions">
        <p>
          Every purchase from your statements shows up here, sorted into categories automatically. Download a
          CSV from your bank&apos;s website and drop it in.
        </p>
      </FirstRun>
    )
  }

  const rowProps: RowProps = {
    categories,
    showAccount: accounts.length > 1,
    openId,
    onToggle: (id) => setOpenId((current) => (current === id ? null : id)),
    onCategoryChange: handleCategoryChange,
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-3xl font-bold text-stone-950">Transactions</h1>
        {/* Totals over every matching transaction (all pages), from the backend, as one sentence. */}
        {data && (
          <p className="mt-1 text-stone-600">
            <strong className="font-semibold text-stone-950 tabular-nums">{formatMoney(data.totals.spent)}</strong> spent
            across {data.totals.count} {data.totals.count === 1 ? 'transaction' : 'transactions'}
            {data.totals.refunds > 0 && (
              <>
                , plus <strong className="font-semibold text-stone-950 tabular-nums">{formatMoney(data.totals.refunds)}</strong>{' '}
                back in refunds
              </>
            )}
          </p>
        )}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-end gap-3">
        <label className={`${showFilters ? 'block' : 'hidden'} w-full sm:block sm:w-auto`}>
          <span className="text-xs font-medium text-stone-600">Account</span>
          <select
            value={accountParam}
            onChange={(event) => updateParam('account', event.target.value)}
            className="mt-1 block w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm sm:w-auto sm:max-w-56"
          >
            <option value="">All accounts</option>
            {accounts.map((account) => (
              <option key={account.id} value={String(account.id)}>
                {account.name}
              </option>
            ))}
          </select>
        </label>

        <label className={`${showFilters ? 'block' : 'hidden'} w-full sm:block sm:w-auto`}>
          <span className="text-xs font-medium text-stone-600">Category</span>
          <select
            value={categoryParam}
            onChange={(event) => updateParam('category', event.target.value)}
            className="mt-1 block w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm sm:w-auto"
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

        <label className={`${showFilters ? 'block' : 'hidden'} w-full sm:block sm:w-auto`}>
          <span className="text-xs font-medium text-stone-600">Month</span>
          <select
            value={monthParam}
            onChange={(event) => updateParam('month', event.target.value)}
            className="mt-1 block w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm sm:w-auto"
          >
            <option value={ALL_TIME}>All time</option>
            {months?.map((month) => (
              <option key={month} value={month}>
                {formatMonth(month)}
              </option>
            ))}
          </select>
        </label>

        <form onSubmit={handleSearch} className="order-first flex w-full items-end gap-2 sm:order-none sm:w-auto">
          <label className="block min-w-0 flex-1 sm:flex-none">
            <span className="text-xs font-medium text-stone-600">Merchant</span>
            {/* Searches the bank's description text. */}
            <input
              type="search"
              value={searchText}
              onChange={(event) => setSearchText(event.target.value)}
              placeholder="Search"
              maxLength={100}
              className="mt-1 block w-full rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm sm:w-56"
            />
          </label>
          <button type="submit" className="rounded-lg bg-button px-3 py-2 text-sm font-medium text-button-ink transition-colors duration-150 hover:bg-button-hover">
            Search
          </button>
          <button
            type="button"
            onClick={() => setShowFilters((shown) => !shown)}
            aria-expanded={showFilters}
            className="inline-flex items-center gap-1.5 rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm font-medium text-stone-800 sm:hidden"
          >
            <SlidersHorizontal size={16} aria-hidden="true" />
            Filters{activeFilters > 0 && ` (${activeFilters})`}
          </button>
          {qParam && (
            <button
              type="button"
              onClick={() => {
                setSearchText('')
                updateParam('q', '')
              }}
              className="px-2 py-2 text-sm text-stone-600 underline"
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
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-stone-200 bg-accent-soft px-4 py-3">
          <div className="flex items-center gap-3">
            <CategoryIcon name={suggestion.categoryName} />
            <div>
              <p className="text-sm font-medium text-stone-900">
                Always put &ldquo;{suggestion.pattern}&rdquo; in {suggestion.categoryName}?
              </p>
              <p className="text-xs text-stone-600">
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
              className="rounded-lg bg-button px-3 py-1.5 text-sm font-medium text-button-ink transition-colors duration-150 hover:bg-button-hover"
            >
              Yes, always
            </button>
            <button
              type="button"
              onClick={() => setSuggestion(null)}
              className="rounded-lg px-3 py-1.5 text-sm text-stone-700 transition-colors duration-150 hover:bg-white/60"
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
          {data.totals.excludedCount > 0 && (
            <p className="text-xs text-stone-500">
              {data.totals.excludedCount} transaction{data.totals.excludedCount === 1 ? '' : 's'} (like card payments)
              {data.totals.excludedCount === 1 ? ' is' : ' are'} in categories that don&apos;t count as spending, so{' '}
              {data.totals.excludedCount === 1 ? "it's" : "they're"} left out of these totals.
            </p>
          )}

          {data.items.length === 0 ? (
            <p className="rounded-lg border border-stone-200 bg-white px-4 py-8 text-center text-stone-500">
              No transactions match these filters. Try All time, or clear the search.
            </p>
          ) : wide ? (
            <TransactionTable items={data.items} {...rowProps} />
          ) : (
            <TransactionList items={data.items} {...rowProps} />
          )}

          {data.totalPages > 1 && (
            <div className="flex items-center justify-end gap-3 text-sm">
              <button
                type="button"
                disabled={pageParam <= 1}
                onClick={() => updateParam('page', String(pageParam - 1))}
                className="rounded-lg border border-stone-300 px-3 py-1.5 hover:bg-stone-100 disabled:opacity-40"
              >
                ‹ Prev
              </button>
              <span className="text-stone-600">
                Page {pageParam} of {data.totalPages}
              </span>
              <button
                type="button"
                disabled={pageParam >= data.totalPages}
                onClick={() => updateParam('page', String(pageParam + 1))}
                className="rounded-lg border border-stone-300 px-3 py-1.5 hover:bg-stone-100 disabled:opacity-40"
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

type RowProps = {
  categories: api.Category[]
  /** Only with more than one bank account: with one, the name says nothing. */
  showAccount: boolean
  openId: number | null
  onToggle: (id: number) => void
  onCategoryChange: (transaction: api.Transaction, value: string) => void
}

/** Money in (refunds, income) reads green with a plus; money out stays plain ink. */
function Amount({ t }: { t: api.Transaction }) {
  return (
    <span className={`text-sm font-medium tabular-nums ${t.amount < 0 ? 'text-stone-950' : 'text-good'}`}>
      {formatMoney(t.amount, { signed: true })}
    </span>
  )
}

/** The category chip, plus a one-click confirm when the model guessed it. */
function CategoryCell({
  t,
  categories,
  onCategoryChange,
}: { t: api.Transaction } & Pick<RowProps, 'categories' | 'onCategoryChange'>) {
  const name = merchantName(t.description)
  return (
    <span className="flex max-w-full shrink-0 items-center gap-1.5">
      <CategoryPicker
        value={t.categoryId}
        categoryName={t.categoryName}
        categories={categories}
        label={`Category for ${name}`}
        onChange={(value) => onCategoryChange(t, value)}
        title={t.bankCategory ? `Bank's label: ${t.bankCategory}` : undefined}
      />
      {/* A guess by the model: one click to keep it (it then counts as the person's choice). */}
      {t.categorySource === 'SUGGESTED' && t.categoryId !== null && (
        <button
          type="button"
          onClick={() => onCategoryChange(t, String(t.categoryId))}
          title="Suggested automatically. Click to confirm, or pick another category."
          aria-label={`Confirm suggested category ${t.categoryName} for ${name}`}
          className="inline-flex shrink-0 items-center gap-1 rounded-md bg-accent-soft px-1.5 py-0.5 text-[11px] font-medium text-stone-800 transition-colors duration-150 hover:bg-stone-200"
        >
          <Sparkles size={11} aria-hidden="true" />
          <span className="hidden sm:inline">Suggested</span>
          <Check size={11} aria-hidden="true" />
        </button>
      )}
    </span>
  )
}

/** The merchant name; clicking it shows the row's details. */
function MerchantButton({ t, open, onToggle }: { t: api.Transaction; open: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={open}
      className="group flex max-w-full min-w-0 items-center gap-1 text-left text-sm font-medium text-stone-900"
    >
      <span className="truncate">{merchantName(t.description)}</span>
      <ChevronRight
        size={14}
        aria-hidden="true"
        className={`shrink-0 text-stone-400 transition-transform duration-150 group-hover:text-stone-600 ${open ? 'rotate-90' : ''}`}
      />
    </button>
  )
}

/** What the bank wrote, which account, and the bank's own category: shown on request. */
function Details({ t }: { t: api.Transaction }) {
  return (
    <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1 text-xs text-stone-600">
      <dt className="text-stone-500">Bank&apos;s text</dt>
      <dd className="break-words text-stone-800">{t.description}</dd>
      <dt className="text-stone-500">Account</dt>
      <dd className="text-stone-800">{t.accountName}</dd>
      {t.bankCategory && (
        <>
          <dt className="text-stone-500">Bank&apos;s label</dt>
          <dd className="text-stone-800">{t.bankCategory}</dd>
        </>
      )}
    </dl>
  )
}

/**
 * Phones: grouped by day, two short lines per row (merchant, then the category chip) with the
 * amount on the right, so 6 to 8 rows fit on a screen.
 */
function TransactionList({ items, ...props }: { items: api.Transaction[] } & RowProps) {
  return (
    <div className="card divide-y divide-stone-100 overflow-hidden">
      {groupByDay(items).map(([day, dayItems]) => (
        <section key={day} aria-label={formatDayHeading(day)}>
          <h2 className="bg-stone-50/80 px-4 py-1.5 font-sans text-xs font-semibold tracking-normal text-stone-600">
            {formatDayHeading(day)}
          </h2>
          <ul className="divide-y divide-stone-100">
            {dayItems.map((t) => {
              const open = props.openId === t.id
              return (
                <li key={t.id} className="px-4 py-2">
                  <div className="flex items-center gap-3">
                    <CategoryIcon name={t.categoryName} />
                    <div className="min-w-0 flex-1">
                      <MerchantButton t={t} open={open} onToggle={() => props.onToggle(t.id)} />
                      <div className="mt-0.5 flex min-w-0 items-center gap-2">
                        <CategoryCell t={t} categories={props.categories} onCategoryChange={props.onCategoryChange} />
                        {props.showAccount && <span className="min-w-0 truncate text-xs text-stone-500">{t.accountName}</span>}
                      </div>
                    </div>
                    <Amount t={t} />
                  </div>
                  {open && (
                    <div className="mt-2 pl-[30px]">
                      <Details t={t} />
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        </section>
      ))}
    </div>
  )
}

/** Desktop: a real table, one line per transaction, with the date as a column. */
function TransactionTable({ items, ...props }: { items: api.Transaction[] } & RowProps) {
  const columns = props.showAccount ? 5 : 4
  return (
    <div className="card overflow-hidden">
      <table className="w-full table-fixed text-left text-sm">
        <caption className="sr-only">Transactions, newest first</caption>
        <thead className="border-b border-stone-200 bg-stone-50/80 text-xs text-stone-600">
          <tr>
            <th scope="col" className="w-24 px-4 py-2 font-semibold">
              Date
            </th>
            <th scope="col" className="px-4 py-2 font-semibold">
              Merchant
            </th>
            <th scope="col" className="w-60 px-4 py-2 font-semibold">
              Category
            </th>
            {props.showAccount && (
              <th scope="col" className="w-44 px-4 py-2 font-semibold">
                Account
              </th>
            )}
            <th scope="col" className="w-32 px-4 py-2 text-right font-semibold">
              Amount
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-stone-100">
          {items.map((t) => {
            const open = props.openId === t.id
            return (
              <Fragment key={t.id}>
                <tr className="transition-colors duration-150 hover:bg-stone-50/60">
                  <td className="px-4 py-2 tabular-nums text-stone-600">{formatShortDate(t.transactionDate)}</td>
                  <td className="px-4 py-2">
                    <span className="flex min-w-0 items-center gap-2.5">
                      <CategoryIcon name={t.categoryName} size="sm" />
                      <MerchantButton t={t} open={open} onToggle={() => props.onToggle(t.id)} />
                    </span>
                  </td>
                  <td className="px-4 py-2">
                    <CategoryCell t={t} categories={props.categories} onCategoryChange={props.onCategoryChange} />
                  </td>
                  {props.showAccount && <td className="truncate px-4 py-2 text-stone-600">{t.accountName}</td>}
                  <td className="px-4 py-2 text-right">
                    <Amount t={t} />
                  </td>
                </tr>
                {open && (
                  <tr className="bg-stone-50/60">
                    <td />
                    <td colSpan={columns - 1} className="px-4 pt-1 pb-3">
                      <Details t={t} />
                    </td>
                  </tr>
                )}
              </Fragment>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
