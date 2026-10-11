import { ArrowDownRight, ArrowUpRight, CalendarClock, PiggyBank, Tags, Upload } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import * as api from '../api'
import { categoryStyle } from '../categoryStyle'
import { CategoryIcon } from '../components/CategoryIcon'
import { MerchantAvatar } from '../components/MerchantAvatar'
import {
  daysFromToday,
  formatCadence,
  formatMoney,
  formatMonth,
  formatMonthShort,
  formatShortDate,
} from '../format'
import { useCountUp } from '../hooks/useCountUp'

// Chart colors: the accent (indigo-600, 6.3:1 on white) for the data that matters, a lighter
// indigo for context, zinc neutrals for everything else. Category charts use each category's color.
const ACCENT = '#4f46e5'
const ACCENT_MUTED = '#a5b4fc' // indigo-300: the months that aren't selected (>= 2:1 on white)
const GRID = '#e4e4e7' // zinc-200: recessive gridlines
const AXIS_TEXT = '#71717a' // zinc-500: axis labels wear text colors, never the series color
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
    return <Welcome />
  }

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

      {!dashboard && !error && <DashboardSkeleton />}

      {dashboard && (
        // Bento grid: tiles of different sizes on a 12-column grid (one column on phones).
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
          <NetSpending dashboard={dashboard} seeAllLink={transactionsLink()} />
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
          <div className="lg:col-span-5">{recurring && <UpcomingCharges overview={recurring} />}</div>

          <div className="lg:col-span-12">{budgets && <BudgetRings overview={budgets} />}</div>
        </div>
      )}
    </div>
  )
}

function NetSpending({ dashboard, seeAllLink }: { dashboard: api.Dashboard; seeAllLink: string }) {
  const shown = useCountUp(dashboard.totals.netSpending)
  const previous = dashboard.monthlyTrend.at(-2)
  return (
    <section className="card flex flex-col p-6 lg:col-span-5">
      <p className="kicker">Net spending</p>
      <p className="mt-2 text-5xl font-semibold tracking-tight tabular-nums text-zinc-950">
        {/* Screen readers get the exact value at once, not the animation. */}
        <span aria-hidden="true">{formatMoney(shown)}</span>
        <span className="sr-only">{formatMoney(dashboard.totals.netSpending)}</span>
      </p>
      {previous && <ChangeVsPrevious current={dashboard.totals.netSpending} previous={previous} />}

      {/* Sparkline: the same 6 months as the trend chart, as a quiet shape. */}
      <div className="mt-4 h-14" aria-hidden="true">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={dashboard.monthlyTrend} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id="spark" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={ACCENT} stopOpacity={0.18} />
                <stop offset="100%" stopColor={ACCENT} stopOpacity={0} />
              </linearGradient>
            </defs>
            <Area type="monotone" dataKey="netSpending" stroke={ACCENT} strokeWidth={2} fill="url(#spark)" isAnimationActive={false} />
          </AreaChart>
        </ResponsiveContainer>
      </div>

      <dl className="mt-4 grid grid-cols-2 border-t border-zinc-100 pt-4">
        <div>
          <dt className="text-xs text-zinc-500">Spent</dt>
          <dd className="mt-0.5 font-medium tabular-nums text-zinc-900">{formatMoney(dashboard.totals.spent)}</dd>
        </div>
        <div>
          <dt className="text-xs text-zinc-500">Refunds</dt>
          <dd className="mt-0.5 font-medium tabular-nums text-zinc-900">{formatMoney(dashboard.totals.refunds)}</dd>
        </div>
      </dl>
      <Link to={seeAllLink} className="mt-4 text-sm font-medium text-accent hover:underline">
        See all transactions →
      </Link>
    </section>
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
  const Arrow = diff > 0 ? ArrowUpRight : ArrowDownRight
  // Spending less is good news (green); spending more is shown neutrally, not as an alarm.
  return (
    <p
      className={`mt-3 inline-flex w-fit items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium ${
        diff > 0 ? 'bg-zinc-100 text-zinc-700' : 'bg-emerald-50 text-emerald-700'
      }`}
    >
      <Arrow size={14} aria-hidden="true" />
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
  const total = data.reduce((sum, d) => sum + d.amount, 0)
  return (
    <section className="card h-full p-6">
      <h2 className="text-sm font-semibold text-zinc-950">Where your money went</h2>
      <p className="mt-0.5 text-xs text-zinc-500">Net spending by category. Pick one to see its transactions.</p>
      {data.length === 0 ? (
        <p className="py-10 text-center text-sm text-zinc-500">No spending this month.</p>
      ) : (
        <div className="mt-4 flex flex-col items-center gap-6 sm:flex-row sm:items-start">
          {/* Donut: decorative summary; the list beside it has every exact value. */}
          <div className="relative size-44 shrink-0" aria-hidden="true">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={data}
                  dataKey="amount"
                  nameKey="categoryName"
                  innerRadius="68%"
                  outerRadius="100%"
                  paddingAngle={2}
                  stroke="none"
                  isAnimationActive={false}
                >
                  {data.map((d) => (
                    <Cell key={d.categoryName} fill={categoryStyle(d.categoryName).color} />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
            <div className="pointer-events-none absolute inset-0 grid place-items-center text-center">
              <div>
                <p className="text-[11px] text-zinc-500">{data.length} categories</p>
                <p className="text-lg font-semibold tabular-nums text-zinc-950">{formatMoney(total)}</p>
              </div>
            </div>
          </div>

          <ul className="w-full flex-1 space-y-1">
            {data.map((d) => {
              const percent = total > 0 ? Math.round((d.amount / total) * 100) : 0
              return (
                <li key={d.categoryName}>
                  <button
                    type="button"
                    onClick={() => onSelect(d.categoryId)}
                    className="flex w-full items-center gap-3 rounded-lg px-2 py-1.5 text-left transition-colors duration-150 hover:bg-zinc-50"
                  >
                    <CategoryIcon name={d.categoryName} size="sm" />
                    <span className="flex-1 truncate text-sm text-zinc-800">{d.categoryName}</span>
                    <span className="text-xs tabular-nums text-zinc-500">{percent}%</span>
                    <span className="w-20 text-right text-sm font-medium tabular-nums text-zinc-900">
                      {formatMoney(d.amount)}
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
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
            <Bar dataKey="netSpending" radius={[6, 6, 0, 0]} maxBarSize={40}>
              {rows.map((row) => (
                <Cell key={row.month} fill={row.month === selected ? ACCENT : ACCENT_MUTED} />
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

/** Subscriptions due next, soonest first. */
function UpcomingCharges({ overview }: { overview: api.RecurringOverview }) {
  const upcoming = overview.charges
    .filter((c) => c.active)
    .sort((a, b) => a.nextExpected.localeCompare(b.nextExpected))
    .slice(0, 5)
  return (
    <section className="card h-full p-6">
      <div className="flex items-baseline justify-between">
        <h2 className="text-sm font-semibold text-zinc-950">Upcoming charges</h2>
        <Link to="/subscriptions" className="text-xs font-medium text-accent hover:underline">
          All subscriptions
        </Link>
      </div>
      {upcoming.length === 0 ? (
        <div className="mt-6 flex flex-col items-center text-center">
          <span className="grid size-10 place-items-center rounded-full bg-zinc-100 text-zinc-500">
            <CalendarClock size={18} aria-hidden="true" />
          </span>
          <p className="mt-3 text-sm text-zinc-600">No subscriptions found yet.</p>
          <p className="mt-1 text-xs text-zinc-500">They show up once a few months of statements are uploaded.</p>
        </div>
      ) : (
        <>
          <p className="mt-1 text-sm text-zinc-600">
            <span className="font-semibold tabular-nums text-zinc-950">{formatMoney(overview.activeMonthlyTotal)}</span>{' '}
            a month in subscriptions
          </p>
          <ul className="mt-4 space-y-3">
            {upcoming.map((c) => (
              <li key={c.merchantKey} className="flex items-center gap-3">
                <MerchantAvatar description={c.description} size="sm" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-zinc-900">{c.name}</p>
                  <p className="text-xs text-zinc-500">
                    {formatCadence(c.cadence)} · {dueLabel(c.nextExpected)}
                  </p>
                </div>
                <span className="text-sm font-medium tabular-nums text-zinc-900">{formatMoney(c.amount)}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  )
}

function dueLabel(isoDate: string): string {
  const days = daysFromToday(isoDate)
  if (days === 0) return 'due today'
  if (days === 1) return 'due tomorrow'
  if (days > 1 && days <= 14) return `due in ${days} days`
  return days < 0 ? `was due ${formatShortDate(isoDate)}` : `due ${formatShortDate(isoDate)}`
}

/** Each budget as a ring: how much of the monthly limit is used. */
function BudgetRings({ overview }: { overview: api.BudgetOverview }) {
  const top = overview.budgets.slice(0, 6) // already sorted: most used first
  return (
    <section className="card p-6">
      <div className="flex items-baseline justify-between">
        <h2 className="text-sm font-semibold text-zinc-950">Budgets</h2>
        <Link to={`/budgets?month=${overview.month}`} className="text-xs font-medium text-accent hover:underline">
          Manage budgets
        </Link>
      </div>
      {top.length === 0 ? (
        <div className="mt-4 flex items-center gap-3 rounded-lg bg-zinc-50 p-4">
          <PiggyBank size={20} className="text-zinc-500" aria-hidden="true" />
          <p className="text-sm text-zinc-600">
            No budgets yet.{' '}
            <Link to="/budgets" className="font-medium text-accent hover:underline">
              Set a monthly limit
            </Link>{' '}
            for a category to see how much is left.
          </p>
        </div>
      ) : (
        <ul className="mt-5 grid grid-cols-[repeat(auto-fit,minmax(150px,1fr))] gap-6">
          {top.map((line) => (
            <li key={line.categoryId} className="flex flex-col items-center text-center">
              <Ring percent={line.percentUsed} over={line.remaining < 0} />
              <p className="mt-2 flex items-center gap-1.5 text-sm font-medium text-zinc-900">
                <CategoryIcon name={line.categoryName} size="sm" />
                <span className="truncate">{line.categoryName}</span>
              </p>
              <p className="mt-0.5 text-xs tabular-nums text-zinc-500">
                {formatMoney(Math.max(line.spent, 0))} of {formatMoney(line.limit)}
              </p>
              {/* Status in words too, never by color alone. */}
              {line.remaining < 0 && <p className="mt-1 text-xs font-medium text-red-600">Over budget</p>}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function Ring({ percent, over }: { percent: number; over: boolean }) {
  const radius = 30
  const circumference = 2 * Math.PI * radius
  const filled = Math.min(Math.max(percent, 0), 100) / 100
  const color = over ? '#dc2626' : percent >= 80 ? '#d97706' : '#059669'
  return (
    <div className="relative size-20">
      <svg viewBox="0 0 72 72" className="size-20 -rotate-90" aria-hidden="true">
        <circle cx="36" cy="36" r={radius} fill="none" stroke="#f4f4f5" strokeWidth="7" />
        <circle
          cx="36"
          cy="36"
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth="7"
          strokeLinecap="round"
          strokeDasharray={`${filled * circumference} ${circumference}`}
        />
      </svg>
      <span className="absolute inset-0 grid place-items-center text-sm font-semibold tabular-nums text-zinc-900">
        {percent}%
      </span>
    </div>
  )
}

/** First visit: no statements yet. Three steps instead of an empty page. */
function Welcome() {
  const steps = [
    { icon: Upload, title: 'Upload a statement', text: 'Export a CSV from any bank or card. Columns are figured out for you.' },
    { icon: Tags, title: 'Check the categories', text: 'Most transactions are sorted automatically. Fix any in one click.' },
    { icon: PiggyBank, title: 'Set a budget', text: 'Pick a monthly limit for a category and watch what is left.' },
  ]
  return (
    <div className="mx-auto max-w-3xl py-8 text-center">
      <p className="kicker">Welcome</p>
      <h1 className="mt-2 text-4xl font-semibold tracking-tight text-zinc-950">Let&apos;s see where your money goes</h1>
      <p className="mx-auto mt-3 max-w-lg text-zinc-600">
        No bank login needed. Everything starts with one statement file.
      </p>
      <ol className="mt-10 grid gap-4 text-left sm:grid-cols-3">
        {steps.map((step, i) => (
          <li key={step.title} className="card p-5">
            <span className="grid size-9 place-items-center rounded-lg bg-accent-soft text-accent">
              <step.icon size={18} aria-hidden="true" />
            </span>
            <p className="mt-4 text-xs font-medium text-zinc-500">Step {i + 1}</p>
            <p className="mt-0.5 font-semibold text-zinc-950">{step.title}</p>
            <p className="mt-1 text-sm text-zinc-600">{step.text}</p>
          </li>
        ))}
      </ol>
      <Link
        to="/import"
        className="mt-8 inline-flex items-center gap-2 rounded-lg bg-zinc-950 px-5 py-3 font-medium text-white transition-colors duration-150 hover:bg-zinc-800"
      >
        <Upload size={18} aria-hidden="true" />
        Upload your first statement
      </Link>
    </div>
  )
}

/** Gray shapes where the tiles will be, so the page doesn't jump when data arrives. */
function DashboardSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-12" aria-busy="true" aria-label="Loading dashboard">
      <div className="card space-y-4 p-6 lg:col-span-5">
        <div className="skeleton h-3 w-24" />
        <div className="skeleton h-12 w-48" />
        <div className="skeleton h-14 w-full" />
        <div className="skeleton h-4 w-32" />
      </div>
      <div className="card p-6 lg:col-span-7">
        <div className="skeleton h-3 w-32" />
        <div className="skeleton mt-6 h-52 w-full" />
      </div>
      <div className="card flex gap-6 p-6 lg:col-span-7">
        <div className="skeleton size-44 rounded-full" />
        <div className="flex-1 space-y-3">
          {[0, 1, 2, 3, 4].map((i) => (
            <div key={i} className="skeleton h-6 w-full" />
          ))}
        </div>
      </div>
      <div className="card space-y-4 p-6 lg:col-span-5">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="skeleton h-8 w-full" />
        ))}
      </div>
    </div>
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
