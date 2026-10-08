import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Bar, BarChart, CartesianGrid, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import * as api from '../api'
import { formatMoney, formatMonth, formatMonthShort } from '../format'

// Chart colors from the validated reference palette (blue ramp). Checked with the dataviz
// validator against the white card surface: all checks pass, contrast >= 3:1.
const BAR = '#2a78d6'
const BAR_MUTED = '#86b6ef' // lighter step for the months that aren't selected (>= 2:1 on white)
const GRID = '#e2e8f0' // slate-200: recessive gridlines
const AXIS_TEXT = '#64748b' // slate-500: axis labels wear text colors, never the series color

/** The home page: one month's spending at a glance. The month lives in the URL (?month=2026-09). */
export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const [months, setMonths] = useState<string[] | null>(null)
  const [dashboard, setDashboard] = useState<api.Dashboard | null>(null)
  const [budgets, setBudgets] = useState<api.BudgetOverview | null>(null)
  const [error, setError] = useState<string | null>(null)

  const month = searchParams.get('month') ?? months?.[0] ?? null

  useEffect(() => {
    api.fetchTransactionMonths()
      .then(setMonths)
      .catch(() => setError('Could not load your data.'))
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
        <h1 className="text-2xl font-semibold text-slate-900">Welcome!</h1>
        <p className="mt-2 text-slate-600">
          <Link to="/import" className="font-medium text-slate-900 underline">
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
    <div className="max-w-5xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>
        {months && month && (
          <label className="block">
            <span className="text-xs font-medium text-slate-600">Month</span>
            <select
              value={month}
              onChange={(event) => setSearchParams({ month: event.target.value })}
              className="mt-1 block rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
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
        <>
          {/* Headline numbers */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <div className="rounded-xl bg-white px-5 py-4 shadow">
              <p className="text-xs font-medium uppercase text-slate-500">Net spending</p>
              <p className="mt-1 text-3xl font-semibold tabular-nums text-slate-900">
                {formatMoney(dashboard.totals.netSpending)}
              </p>
              {previous && <ChangeVsPrevious current={dashboard.totals.netSpending} previous={previous} />}
            </div>
            <Stat label="Spent" value={formatMoney(dashboard.totals.spent)} />
            <Stat label="Refunds" value={formatMoney(dashboard.totals.refunds)} />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <SpendingByCategory
              data={dashboard.spendingByCategory}
              onSelect={(categoryId) =>
                navigate(transactionsLink({ category: categoryId === null ? 'uncategorized' : String(categoryId) }))
              }
            />
            <MonthlyTrend data={dashboard.monthlyTrend} selected={dashboard.month} />
          </div>

          {budgets && <BudgetSnapshot overview={budgets} />}

          <p className="text-sm">
            <Link to={transactionsLink()} className="font-medium text-slate-900 underline">
              See all transactions for {formatMonth(dashboard.month)} →
            </Link>
          </p>
        </>
      )}
    </div>
  )
}

function ChangeVsPrevious({ current, previous }: { current: number; previous: api.Dashboard['monthlyTrend'][number] }) {
  // Display-only arithmetic on two amounts the backend already summed exactly.
  const diff = Math.round((current - previous.netSpending) * 100) / 100
  const label = formatMonthShort(previous.month)
  if (previous.netSpending === 0) {
    return <p className="mt-1 text-sm text-slate-500">No spending in {label}</p>
  }
  if (diff === 0) {
    return <p className="mt-1 text-sm text-slate-500">Same as {label}</p>
  }
  return (
    <p className="mt-1 text-sm text-slate-600">
      {diff > 0 ? '▲' : '▼'} {formatMoney(Math.abs(diff))} {diff > 0 ? 'more' : 'less'} than {label}
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
    <section className="rounded-xl bg-white p-5 shadow">
      <h2 className="font-semibold text-slate-900">Where your money went</h2>
      <p className="text-xs text-slate-500">Net spending by category. Click a bar to see its transactions.</p>
      {data.length === 0 ? (
        <p className="py-10 text-center text-sm text-slate-500">No spending this month.</p>
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
                  cursor={{ fill: '#f1f5f9' }}
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
                    style={{ fill: '#334155', fontSize: 12 }}
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
    <section className="rounded-xl bg-white p-5 shadow">
      <h2 className="font-semibold text-slate-900">Spending by month</h2>
      <p className="text-xs text-slate-500">Net spending, last 6 months. The selected month is highlighted.</p>
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
              cursor={{ fill: '#f1f5f9' }}
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
    <section className="rounded-xl bg-white p-5 shadow">
      <div className="flex items-baseline justify-between">
        <h2 className="font-semibold text-slate-900">Budgets</h2>
        <Link to={`/budgets?month=${overview.month}`} className="text-sm text-slate-600 underline">
          Manage budgets
        </Link>
      </div>
      {top.length === 0 ? (
        <p className="mt-2 text-sm text-slate-500">
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
                  <span className="text-slate-800">{line.categoryName}</span>
                  <span className="tabular-nums text-slate-600">
                    {/* Status is spelled out in text, never shown by color alone. */}
                    {over && <span className="mr-2 font-medium text-red-600">⚠ Over budget</span>}
                    {formatMoney(Math.max(line.spent, 0))} of {formatMoney(line.limit)}
                  </span>
                </div>
                <div className="mt-1 h-2 overflow-hidden rounded-full bg-slate-100">
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

/** The same numbers as a table, for screen readers and anyone who prefers exact values. */
function TableView({ headers, rows }: { headers: string[]; rows: string[][] }) {
  return (
    <details className="mt-3 text-sm">
      <summary className="cursor-pointer text-slate-500">Show as table</summary>
      <table className="mt-2 w-full text-left">
        <thead className="text-xs uppercase text-slate-500">
          <tr>
            {headers.map((h, i) => (
              <th key={h} className={`py-1 font-medium ${i > 0 ? 'text-right' : ''}`}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
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

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl bg-white px-5 py-4 shadow">
      <p className="text-xs font-medium uppercase text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-semibold tabular-nums text-slate-900">{value}</p>
    </div>
  )
}
