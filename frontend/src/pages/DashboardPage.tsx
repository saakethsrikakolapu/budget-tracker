import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Bar, BarChart, CartesianGrid, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import * as api from '../api'
import { formatCadence, formatMoney, formatMonth, formatMonthShort } from '../format'

// Chart colors: the design system's single accent (blue-600, 5.2:1 on white) for the data that
// matters, a lighter blue for context, and zinc neutrals for everything else.
const BAR = '#2563eb'
const BAR_MUTED = '#93c5fd' // blue-300: the months that aren't selected (>= 2:1 on white)
const GRID = '#e4e4e7' // zinc-200: recessive gridlines
const AXIS_TEXT = '#71717a' // zinc-500: axis labels wear text colors, never the series color
const LABEL_TEXT = '#3f3f46' // zinc-700
const HOVER = '#f4f4f5' // zinc-100

/** The home page: one month's spending at a glance. The month lives in the URL (?month=2026-09). */
export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const [months, setMonths] = useState<string[] | null>(null)
  const [dashboard, setDashboard] = useState<api.Dashboard | null>(null)
  const [budgets, setBudgets] = useState<api.BudgetOverview | null>(null)
  const [recurring, setRecurring] = useState<api.RecurringOverview | null>(null)
  const [error, setError] = useState<string | null>(null)

  const month = searchParams.get('month') ?? months?.[0] ?? null

  useEffect(() => {
    api.fetchTransactionMonths()
      .then(setMonths)
      .catch(() => setError('Could not load your data.'))
    // Subscriptions don't depend on the month: load once. The card just stays hidden if this fails.
    api.fetchRecurring()
      .then(setRecurring)
      .catch(() => setRecurring(null))
  }, [])

  useEffect(() => {
    if (month === null) return
    let ignore = false
    Promise.all([api.fetchDashboard(month), api.fetchBudgets(month)])
      .then(([d, b]) => {
        if (!ignore) {
          setDashboard(d)
          setBudgets(b)
        }
      })
      .catch(() => {
        if (!ignore) setError('Could not load the dashboard.')
      })
    return () => {
      ignore = true
    }
  }, [month])

  if (months !== null && months.length === 0) {
    return (
      <div className="max-w-xl">
        <h1 className="text-3xl font-semibold tracking-tight text-zinc-950">Welcome!</h1>
        <p className="mt-2 text-zinc-600">
          <Link to="/import" className="font-medium text-accent hover:underline">
            Upload a statement
          </Link>{' '}
          to see where your money goes.
        </p>
      </div>
    )
  }

  const previous = dashboard?.monthlyTrend.at(-2)
  const transactionsLink = (extra: Record<string, string> = {}) =>
    `/transactions?${new URLSearchParams({ month: month ?? '', ...extra })}`

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="kicker">Overview</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight text-zinc-950">
            {month ? formatMonth(month) : 'Dashboard'}
          </h1>
        </div>
        {months && month && (
          <label className="block">
            <span className="sr-only">Month</span>
            <select
              value={month}
              onChange={(event) => setSearchParams({ month: event.target.value })}
              className="block rounded-lg border border-zinc-300 bg-white px-3 py-2 text-sm"
            >
              {months.map((m) => (
                <option key={m} value={m}>
                  {formatMonth(m)}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-red-800">
          {error}
        </p>
      )}

      {dashboard && (
        // Bento grid: tiles of different sizes on a 12-column grid (one column on phones).
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
          <section className="card flex flex-col justify-between p-6 lg:col-span-5">
            <div>
              <p className="kicker">Net spending</p>
              <p className="mt-2 text-5xl font-semibold tracking-tight tabular-nums text-zinc-950">
                {formatMoney(dashboard.totals.netSpending)}
              </p>
              {previous && <ChangeVsPrevious current={dashboard.totals.netSpending} previous={previous} />}
            </div>
            <dl className="mt-6 grid grid-cols-2 border-t border-zinc-100 pt-4">
              <div>
                <dt className="text-xs text-zinc-500">Spent</dt>
                <dd className="mt-0.5 font-medium tabular-nums text-zinc-900">{formatMoney(dashboard.totals.spent)}</dd>
              </div>
              <div>
                <dt className="text-xs text-zinc-500">Refunds</dt>
                <dd className="mt-0.5 font-medium tabular-nums text-zinc-900">{formatMoney(dashboard.totals.refunds)}</dd>
              </div>
            </dl>
            <Link to={transactionsLink()} className="mt-4 text-sm font-medium text-accent hover:underline">
              See all transactions →
            </Link>
          </section>

          <div className="lg:col-span-7">
            <MonthlyTrend data={dashboard.monthlyTrend} selected={dashboard.month} />
          </div>

          <div className="lg:col-span-7">
            <SpendingByCategory
              data={dashboard.spendingByCategory}
              onSelect={(categoryId) =>
                navigate(transactionsLink({ category: categoryId === null ? 'uncategorized' : String(categoryId) }))
              }
            />
          </div>

          <div className="grid content-start gap-4 lg:col-span-5">
            {budgets && <BudgetSnapshot overview={budgets} />}
            {recurring && <SubscriptionSnapshot overview={recurring} />}
          </div>
        </div>
      )}
    </div>
  )
}

function ChangeVsPrevious({ current, previous }: { current: number; previous: api.Dashboard['monthlyTrend'][number] }) {
  // Display-only arithmetic on two amounts the backend already summed exactly.
  const diff = Math.round((current - previous.netSpending) * 100) / 100
  const label = formatMonthShort(previous.month)
  if (previous.netSpending === 0) {
    return <p className="mt-3 text-sm text-zinc-500">No spending in {label}</p>
  }
  if (diff === 0) {
    return <p className="mt-3 text-sm text-zinc-500">Same as {label}</p>
  }
  // Spending less is good news (green); spending more is shown neutrally, not as an alarm.
  return (
    <p
      className={`mt-3 inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium ${
        diff > 0 ? 'bg-zinc-100 text-zinc-700' : 'bg-emerald-50 text-emerald-700'
      }`}
    >
      <span aria-hidden="true">{diff > 0 ? '↑' : '↓'}</span>
      {formatMoney(Math.abs(diff))} {diff > 0 ? 'more' : 'less'} than {label}
    </p>
  )
}

function SpendingByCategory({
  data,
  onSelect,
}: {
  data: api.Dashboard['spendingByCategory']
  onSelect: (categoryId: number | null) => void
}) {
  return (
    <section className="card h-full p-6">
      <h2 className="text-sm font-semibold text-zinc-950">Where your money went</h2>
      <p className="mt-0.5 text-xs text-zinc-500">Net spending by category. Click a bar to see its transactions.</p>
      {data.length === 0 ? (
        <p className="py-10 text-center text-sm text-zinc-500">No spending this month.</p>
      ) : (
        <>
          <div className="mt-4" style={{ height: Math.max(160, data.length * 36) }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data} layout="vertical" margin={{ top: 0, right: 72, bottom: 0, left: 0 }} barCategoryGap={6}>
                <XAxis type="number" hide />
                <YAxis
                  type="category"
                  dataKey="categoryName"
                  width={150}
                  tickLine={false}
                  axisLine={false}
                  tick={{ fill: AXIS_TEXT, fontSize: 12 }}
                />
                <Tooltip
                  cursor={{ fill: HOVER }}
                  formatter={(value) => [formatMoney(Number(value)), 'Net spending']}
                />
                <Bar
                  dataKey="amount"
                  fill={BAR}
                  radius={[0, 4, 4, 0]}
                  maxBarSize={22}
                  cursor="pointer"
                  onClick={(entry) => onSelect((entry as unknown as { categoryId: number | null }).categoryId)}
                >
                  {/* Direct labels: each bar's amount, in text color (not the bar color). */}
                  <LabelList
                    dataKey="amount"
                    position="right"
                    formatter={(value: unknown) => formatMoney(Number(value))}
                    style={{ fill: LABEL_TEXT, fontSize: 12 }}
                  />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
          <TableView
            headers={['Category', 'Net spending']}
            rows={data.map((d) => [d.categoryName, formatMoney(d.amount)])}
          />
        </>
      )}
    </section>
  )
}

function MonthlyTrend({ data, selected }: { data: api.Dashboard['monthlyTrend']; selected: string }) {
  const rows = data.map((d) => ({ ...d, label: formatMonthShort(d.month) }))
  return (
    <section className="card h-full p-6">
      <h2 className="text-sm font-semibold text-zinc-950">Spending by month</h2>
      <p className="mt-0.5 text-xs text-zinc-500">Net spending, last 6 months. The selected month is highlighted.</p>
      <div className="mt-4 h-56">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 8 }} barCategoryGap="30%">
            <CartesianGrid vertical={false} stroke={GRID} />
            <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: AXIS_TEXT, fontSize: 12 }} />
            <YAxis
              tickLine={false}
              axisLine={false}
              width={56}
              tick={{ fill: AXIS_TEXT, fontSize: 12 }}
              tickFormatter={(value: number) => `$${value.toLocaleString('en-US')}`}
            />
            <Tooltip
              cursor={{ fill: HOVER }}
              formatter={(value) => [formatMoney(Number(value)), 'Net spending']}
              labelFormatter={(_, payload) =>
                payload?.[0] ? formatMonthShort(String(payload[0].payload.month), { year: true }) : ''
              }
            />
            <Bar dataKey="netSpending" radius={[4, 4, 0, 0]} maxBarSize={40}>
              {rows.map((row) => (
                <Cell key={row.month} fill={row.month === selected ? BAR : BAR_MUTED} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <TableView
        headers={['Month', 'Net spending']}
        rows={data.map((d) => [formatMonthShort(d.month, { year: true }), formatMoney(d.netSpending)])}
      />
    </section>
  )
}

function BudgetSnapshot({ overview }: { overview: api.BudgetOverview }) {
  const top = overview.budgets.slice(0, 4) // already sorted: most used first
  return (
    <section className="card h-full p-6">
      <div className="flex items-baseline justify-between">
        <h2 className="text-sm font-semibold text-zinc-950">Budgets</h2>
        <Link to={`/budgets?month=${overview.month}`} className="text-xs font-medium text-accent hover:underline">
          Manage budgets
        </Link>
      </div>
      {top.length === 0 ? (
        <p className="mt-2 text-sm text-zinc-500">
          No budgets yet.{' '}
          <Link to="/budgets" className="underline">
            Set one up
          </Link>{' '}
          to track spending against a monthly limit.
        </p>
      ) : (
        <ul className="mt-3 space-y-3">
          {top.map((line) => {
            const over = line.remaining < 0
            const color = over ? 'bg-red-500' : line.percentUsed >= 80 ? 'bg-amber-500' : 'bg-emerald-500'
            return (
              <li key={line.categoryId}>
                <div className="flex justify-between text-sm">
                  <span className="text-zinc-800">{line.categoryName}</span>
                  <span className="tabular-nums text-zinc-600">
                    {/* Status is spelled out in text, never shown by color alone. */}
                    {over && <span className="mr-2 font-medium text-red-600">⚠ Over budget</span>}
                    {formatMoney(Math.max(line.spent, 0))} of {formatMoney(line.limit)}
                  </span>
                </div>
                <div className="mt-1 h-2 overflow-hidden rounded-full bg-zinc-100">
                  <div className={`h-full ${color}`} style={{ width: `${Math.min(line.percentUsed, 100)}%` }} />
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

function SubscriptionSnapshot({ overview }: { overview: api.RecurringOverview }) {
  const active = overview.charges.filter((c) => c.active)
  const top = active.slice(0, 4) // already sorted: most expensive per month first
  return (
    <section className="card h-full p-6">
      <div className="flex items-baseline justify-between">
        <h2 className="text-sm font-semibold text-zinc-950">Subscriptions</h2>
        <Link to="/subscriptions" className="text-xs font-medium text-accent hover:underline">
          See all
        </Link>
      </div>
      {top.length === 0 ? (
        <p className="mt-2 text-sm text-zinc-500">
          None found yet. Subscriptions show up once a few months of statements are uploaded.
        </p>
      ) : (
        <>
          <p className="mt-1 text-sm text-zinc-600">
            <span className="font-semibold tabular-nums text-zinc-900">{formatMoney(overview.activeMonthlyTotal)}</span>{' '}
            a month across {active.length} active
          </p>
          <ul className="mt-3 divide-y divide-zinc-100">
            {top.map((c) => (
              <li key={c.merchantKey} className="flex justify-between py-2 text-sm">
                <span className="text-zinc-800">
                  {c.name} <span className="text-zinc-500">· {formatCadence(c.cadence)}</span>
                </span>
                <span className="tabular-nums text-zinc-700">{formatMoney(c.amount)}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}

/** The same numbers as a table, for screen readers and anyone who prefers exact values. */
function TableView({ headers, rows }: { headers: string[]; rows: string[][] }) {
  return (
    <details className="mt-3 text-sm">
      <summary className="cursor-pointer text-zinc-500">Show as table</summary>
      <table className="mt-2 w-full text-left">
        <thead className="text-xs uppercase text-zinc-500">
          <tr>
            {headers.map((h, i) => (
              <th key={h} className={`py-1 font-medium ${i > 0 ? 'text-right' : ''}`}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-zinc-100">
          {rows.map((row) => (
            <tr key={row[0]}>
              {row.map((cell, i) => (
                <td key={i} className={`py-1 ${i > 0 ? 'text-right tabular-nums' : ''}`}>
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  )
}
