import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import * as api from '../api'
import { formatDate, formatMoney, formatMonth, monthRange } from '../format'

const ALL_TIME = 'all'

/**
 * Your transactions, filterable by account, month, and search text.
 * Filters live in the URL (e.g. /?account=3&month=2026-09&q=coffee&page=2), so refresh, the back
 * button, and bookmarks all keep them.
 */
export function TransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [accounts, setAccounts] = useState<api.Account[]>([])
  const [months, setMonths] = useState<string[] | null>(null)
  const [data, setData] = useState<api.TransactionPage | null>(null)
  const [error, setError] = useState<string | null>(null)

  const accountParam = searchParams.get('account') ?? ''
  const qParam = searchParams.get('q') ?? ''
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
    Promise.all([api.fetchAccounts(), api.fetchTransactionMonths()])
      .then(([accountList, monthList]) => {
        setAccounts(accountList)
        setMonths(monthList)
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
  }, [months, accountParam, monthParam, qParam, pageParam])

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
        <h1 className="text-2xl font-semibold text-slate-900">Transactions</h1>
        <p className="mt-4 text-slate-600">
          No transactions yet.{' '}
          <Link to="/import" className="font-medium text-slate-900 underline">
            Upload your first statement
          </Link>{' '}
          to get started.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-semibold text-slate-900">Transactions</h1>

      {/* Filters */}
      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="text-xs font-medium text-slate-600">Account</span>
          <select
            value={accountParam}
            onChange={(event) => updateParam('account', event.target.value)}
            className="mt-1 block rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
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
          <span className="text-xs font-medium text-slate-600">Month</span>
          <select
            value={monthParam}
            onChange={(event) => updateParam('month', event.target.value)}
            className="mt-1 block rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
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
            <span className="text-xs font-medium text-slate-600">Merchant</span>
            {/* Searches the Description column only; category filtering comes in Stage 2. */}
            <input
              type="search"
              value={searchText}
              onChange={(event) => setSearchText(event.target.value)}
              placeholder="Search by merchant name"
              maxLength={100}
              className="mt-1 block w-56 rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
          </label>
          <button type="submit" className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700">
            Search
          </button>
          {qParam && (
            <button
              type="button"
              onClick={() => {
                setSearchText('')
                updateParam('q', '')
              }}
              className="px-2 py-2 text-sm text-slate-600 underline"
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

      {data && (
        <>
          {/* Totals over all matching transactions, from the backend */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Stat label="Spent" value={formatMoney(data.totals.spent)} />
            <Stat label="Money in" value={formatMoney(data.totals.received)} />
            <Stat label="Net" value={formatMoney(data.totals.net, { signed: true })} />
            <Stat label="Transactions" value={String(data.totals.count)} />
          </div>

          {data.items.length === 0 ? (
            <p className="rounded-xl bg-white px-4 py-8 text-center text-slate-500 shadow">
              No transactions match these filters.
            </p>
          ) : (
            <div className="overflow-x-auto rounded-xl bg-white shadow">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-4 py-3 font-medium">Date</th>
                    <th className="px-4 py-3 font-medium">Description</th>
                    <th className="px-4 py-3 font-medium">Category</th>
                    <th className="px-4 py-3 font-medium">Account</th>
                    <th className="px-4 py-3 text-right font-medium">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {data.items.map((t) => (
                    <tr key={t.id}>
                      <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDate(t.transactionDate)}</td>
                      <td className="px-4 py-3 font-medium text-slate-900">{t.description}</td>
                      <td className="px-4 py-3 text-slate-600">{t.bankCategory ?? '—'}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-slate-600">{t.accountName}</td>
                      <td
                        className={`whitespace-nowrap px-4 py-3 text-right font-medium tabular-nums ${
                          t.amount < 0 ? 'text-slate-900' : 'text-green-700'
                        }`}
                      >
                        {formatMoney(t.amount, { signed: true })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {data.totalPages > 1 && (
            <div className="flex items-center justify-end gap-3 text-sm">
              <button
                type="button"
                disabled={pageParam <= 1}
                onClick={() => updateParam('page', String(pageParam - 1))}
                className="rounded-lg border border-slate-300 px-3 py-1.5 hover:bg-slate-100 disabled:opacity-40"
              >
                ‹ Prev
              </button>
              <span className="text-slate-600">
                Page {pageParam} of {data.totalPages}
              </span>
              <button
                type="button"
                disabled={pageParam >= data.totalPages}
                onClick={() => updateParam('page', String(pageParam + 1))}
                className="rounded-lg border border-slate-300 px-3 py-1.5 hover:bg-slate-100 disabled:opacity-40"
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

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-white px-4 py-3 shadow">
      <p className="text-xs font-medium uppercase text-slate-500">{label}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums text-slate-900">{value}</p>
    </div>
  )
}
