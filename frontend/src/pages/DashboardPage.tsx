import { CalendarClock, PiggyBank, Tags, Upload } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Bar, BarChart, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis } from 'recharts'
import * as api from '../api'
import { categoryStyle } from '../categoryStyle'
import { CategoryIcon } from '../components/CategoryIcon'
import {
  currentMonth,
  daysFromToday,
  formatCadence,
  formatMoney,
  formatMonth,
  formatMonthShort,
  formatShortDate,
} from '../format'
import { useCountUp } from '../hooks/useCountUp'
import { useIsDark } from '../theme'

/**
 * Chart colors, set in JS because Recharts draws SVG with these values. The selected month is the
 * Spendr green; the other months are a pale green for context. Dark mode uses the same roles with
 * steps that still stand out on a charcoal card.
 */
function chartColors(dark: boolean) {
  return dark
    ? { accent: '#4cc98a', accentMuted: '#2c4a3a', axis: '#a8a29e', label: '#e7e5e4', hover: '#2a2724',
        tooltip: { background: '#1f1d1b', border: '1px solid #3a3632', color: '#f5f5f4' } }
    : { accent: '#0f7a4f', accentMuted: '#b9dcc8', axis: '#6b655d', label: '#1c1917', hover: '#f0ede5',
        tooltip: { background: '#ffffff', border: '1px solid #e7e5e4', color: '#1c1917' } }
}

/** The home page: one month's spending at a glance. The month lives in the URL (?month=2026-09). */
export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const [months, setMonths] = useState<string[] | null>(null)
  const [dashboard, setDashboard] = useState<api.Dashboard | null>(null)
  const [budgets, setBudgets] = useState<api.BudgetOverview | null>(null)
  const [plan, setPlan] = useState<api.BudgetPlan | null>(null)
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
    // "Higher than usual" and the budget suggestion are extras: if they fail, they just don't show.
    api.fetchBudgetPlan(month)
      .then((p) => {
        if (!ignore) setPlan(p)
      })
      .catch(() => {
        if (!ignore) setPlan(null)
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
    <div className="space-y-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        {dashboard ? (
          <Headline dashboard={dashboard} plan={plan} seeAllLink={transactionsLink()} />
        ) : (
          <h1 className="text-4xl font-bold text-stone-950">{month ? formatMonth(month) : 'Dashboard'}</h1>
        )}
        {months && month && (
          <label className="block">
            <span className="sr-only">Month</span>
            <select
              value={month}
              onChange={(event) => setSearchParams({ month: event.target.value })}
              className="block rounded-lg border border-stone-300 bg-white px-3 py-2 text-sm"
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
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
            <div className="lg:col-span-7">
              <MonthlyTrend data={dashboard.monthlyTrend} selected={dashboard.month} />
            </div>
            <div className="lg:col-span-5">
              <SpendingByCategory
                data={dashboard.spendingByCategory}
                onSelect={(categoryId) =>
                  navigate(transactionsLink({ category: categoryId === null ? 'uncategorized' : String(categoryId) }))
                }
              />
            </div>
          </div>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            {budgets && <BudgetList overview={budgets} plan={plan} />}
            {recurring && <UpcomingCharges overview={recurring} />}
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * The month in one sentence instead of a label, a number and a badge:
 * "You've spent $143.07 in October." and how that compares with the month before.
 */
function Headline({
  dashboard,
  plan,
  seeAllLink,
}: {
  dashboard: api.Dashboard
  plan: api.BudgetPlan | null
  seeAllLink: string
}) {
  const net = dashboard.totals.netSpending
  const shown = useCountUp(Math.abs(net))
  const monthName = formatMonth(dashboard.month).split(' ')[0]
  const amount = (
    <span className="text-accent">
      {/* Screen readers get the exact value at once, not the animation. */}
      <span aria-hidden="true">{formatMoney(shown)}</span>
      <span className="sr-only">{formatMoney(Math.abs(net))}</span>
    </span>
  )
  return (
    <div className="max-w-3xl">
      <h1 className="text-4xl font-bold leading-tight text-stone-950 tabular-nums sm:text-5xl">
        {net < 0 ? (
          <>
            You got back {amount} more than you spent in {monthName}.
          </>
        ) : (
          <>
            {dashboard.month === currentMonth() ? 'You’ve spent' : 'You spent'} {amount} in {monthName}.
          </>
        )}
      </h1>
      <p className="mt-3 text-stone-600">
        <Comparison dashboard={dashboard} />
        {dashboard.totals.refunds > 0 && <> {formatMoney(dashboard.totals.refunds)} came back in refunds.</>}{' '}
        <Link to={seeAllLink} className="whitespace-nowrap font-medium text-accent hover:underline">
          See transactions →
        </Link>
      </p>
      {plan && <HigherThanUsual plan={plan} />}
    </div>
  )
}

/** "Food & Dining is $60 higher than usual so far." The two biggest jumps, compared by day of month. */
function HigherThanUsual({ plan }: { plan: api.BudgetPlan }) {
  const higher = plan.lines
    .filter((l) => l.pace === 'HIGHER')
    .sort((a, b) => b.spent - b.usualSoFar - (a.spent - a.usualSoFar))
    .slice(0, 2)
  if (higher.length === 0) return null
  const soFar = plan.throughDay < 28 ? ' so far' : ''
  return (
    <p className="mt-2 text-stone-600">
      {higher.map((l, i) => (
        <span key={l.categoryName}>
          {i > 0 && ' '}
          <strong className="font-semibold text-stone-950">{l.categoryName}</strong> is{' '}
          <strong className="font-semibold text-amber-700 tabular-nums">{formatMoney(l.spent - l.usualSoFar)} higher</strong>{' '}
          than usual{soFar}.
        </span>
      ))}
    </p>
  )
}

function Comparison({ dashboard }: { dashboard: api.Dashboard }) {
  const previous = dashboard.monthlyTrend.at(-2)
  if (!previous) return null
  const label = formatMonth(previous.month).split(' ')[0]
  if (previous.netSpending === 0) return <>Nothing to compare with in {label}.</>
  // Display-only arithmetic on two amounts the backend already summed exactly.
  const diff = Math.round((dashboard.totals.netSpending - previous.netSpending) * 100) / 100
  if (diff === 0) return <>Same as {label}.</>
  return (
    <>
      That&apos;s{' '}
      <strong className="font-semibold text-stone-950">
        {formatMoney(Math.abs(diff))} {diff > 0 ? 'more' : 'less'}
      </strong>{' '}
      than {label}.
    </>
  )
}

/** Categories ranked by spending, each with a thin bar sized against the biggest. */
function SpendingByCategory({
  data,
  onSelect,
}: {
  data: api.Dashboard['spendingByCategory']
  onSelect: (categoryId: number | null) => void
}) {
  const biggest = Math.max(...data.map((d) => d.amount), 0)
  return (
    <section className="card h-full p-6">
      <h2 className="text-lg font-bold text-stone-950">Where it went</h2>
      {data.length === 0 ? (
        <p className="py-10 text-center text-sm text-stone-500">No spending this month.</p>
      ) : (
        <ul className="mt-3 space-y-0.5">
          {data.map((d) => (
            <li key={d.categoryName}>
              <button
                type="button"
                onClick={() => onSelect(d.categoryId)}
                className="block w-full rounded-md px-2 py-2 text-left transition-colors duration-150 hover:bg-stone-50"
              >
                <span className="flex items-center gap-3">
                  <CategoryIcon name={d.categoryName} size="sm" />
                  <span className="flex-1 truncate text-sm text-stone-800">{d.categoryName}</span>
                  <span className="text-sm font-semibold tabular-nums text-stone-950">{formatMoney(d.amount)}</span>
                </span>
                <span aria-hidden="true" className="mt-1.5 ml-9 block h-1 overflow-hidden rounded-full bg-stone-100">
                  <span
                    className="block h-full rounded-full"
                    style={{
                      width: `${biggest > 0 ? Math.max((d.amount / biggest) * 100, 2) : 0}%`,
                      backgroundColor: categoryStyle(d.categoryName).color,
                    }}
                  />
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function MonthlyTrend({ data, selected }: { data: api.Dashboard['monthlyTrend']; selected: string }) {
  const rows = data.map((d) => ({ ...d, label: formatMonthShort(d.month) }))
  const colors = chartColors(useIsDark())
  return (
    <section className="card h-full p-6">
      <h2 className="text-lg font-bold text-stone-950">The last six months</h2>
      {/* Amounts sit on the bars, so there's no y-axis or grid to read across. */}
      <div className="mt-4 h-60">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 24, right: 0, bottom: 0, left: 0 }} barCategoryGap="28%">
            <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: colors.axis, fontSize: 13 }} />
            <Tooltip
              cursor={{ fill: colors.hover }}
              contentStyle={{ ...colors.tooltip, borderRadius: 6 }}
              itemStyle={{ color: colors.tooltip.color }}
              labelStyle={{ color: colors.tooltip.color }}
              formatter={(value) => [formatMoney(Number(value)), 'Net spending']}
              labelFormatter={(_, payload) =>
                payload?.[0] ? formatMonthShort(String(payload[0].payload.month), { year: true }) : ''
              }
            />
            <Bar dataKey="netSpending" radius={[4, 4, 0, 0]} maxBarSize={56}>
              {rows.map((row) => (
                <Cell key={row.month} fill={row.month === selected ? colors.accent : colors.accentMuted} />
              ))}
              <LabelList
                dataKey="netSpending"
                position="top"
                offset={8}
                fill={colors.label}
                fontSize={12}
                fontWeight={600}
                formatter={(value) => `$${Math.round(Number(value)).toLocaleString('en-US')}`}
              />
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

/** Subscriptions due next, soonest first, like lines on a statement: date, name, amount. */
function UpcomingCharges({ overview }: { overview: api.RecurringOverview }) {
  const upcoming = overview.charges
    .filter((c) => c.active)
    .sort((a, b) => a.nextExpected.localeCompare(b.nextExpected))
    .slice(0, 5)
  return (
    <section className="card h-full p-6">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-lg font-bold text-stone-950">Coming up</h2>
        <Link to="/subscriptions" className="text-sm font-medium text-accent hover:underline">
          All subscriptions
        </Link>
      </div>
      {upcoming.length === 0 ? (
        <div className="mt-6 flex flex-col items-center text-center">
          <CalendarClock size={20} className="text-stone-500" aria-hidden="true" />
          <p className="mt-3 text-sm text-stone-600">No subscriptions found yet.</p>
          <p className="mt-1 text-xs text-stone-500">They show up once a few months of statements are uploaded.</p>
        </div>
      ) : (
        <>
          <p className="mt-1 text-sm text-stone-600">
            Subscriptions cost you{' '}
            <span className="font-semibold tabular-nums text-stone-950">{formatMoney(overview.activeMonthlyTotal)}</span> a
            month.
          </p>
          <ul className="mt-4 divide-y divide-stone-100">
            {upcoming.map((c) => (
              <li key={c.merchantKey} className="flex items-center gap-4 py-2.5">
                <span className="w-14 shrink-0 text-sm font-medium tabular-nums text-stone-500">
                  {formatShortDate(c.nextExpected)}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-stone-900">{c.name}</p>
                  <p className="text-xs text-stone-500">
                    {formatCadence(c.cadence)} · {dueLabel(c.nextExpected)}
                  </p>
                </div>
                <span className="text-sm font-semibold tabular-nums text-stone-950">{formatMoney(c.amount)}</span>
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

/** Each budget as one line: "$82 of $150" and a thin bar. Red only when over. */
function BudgetList({ overview, plan }: { overview: api.BudgetOverview; plan: api.BudgetPlan | null }) {
  const top = overview.budgets.slice(0, 5) // already sorted: most used first
  return (
    <section className="card h-full p-6">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="text-lg font-bold text-stone-950">Budget</h2>
        <Link to={`/budgets?month=${overview.month}`} className="text-sm font-medium text-accent hover:underline">
          {top.length === 0 ? 'Set up' : 'See budget'}
        </Link>
      </div>
      {top.length === 0 ? (
        <div className="mt-4 flex items-start gap-3 rounded-md bg-stone-50 p-4">
          <PiggyBank size={20} className="mt-0.5 shrink-0 text-stone-500" aria-hidden="true" />
          <div className="text-sm text-stone-600">
            {plan?.target ? (
              <p>
                There&apos;s a budget ready for you, built from your statements:{' '}
                <strong className="tabular-nums text-stone-950">{formatMoney(plan.suggestedTotal)}</strong> a month.
              </p>
            ) : (
              <p>Once a full month of statements is uploaded, a budget is suggested for you.</p>
            )}
            {plan?.target && (
              <Link
                to={`/budgets?month=${overview.month}`}
                className="mt-3 inline-block rounded-lg bg-accent px-3 py-1.5 font-semibold text-accent-ink transition-colors duration-150 hover:bg-accent-hover"
              >
                See my budget
              </Link>
            )}
          </div>
        </div>
      ) : (
        <ul className="mt-4 space-y-4">
          {top.map((line) => {
            const over = line.remaining < 0
            return (
              <li key={line.categoryId}>
                <div className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="flex min-w-0 items-center gap-2 font-medium text-stone-900">
                    <CategoryIcon name={line.categoryName} size="sm" />
                    <span className="truncate">{line.categoryName}</span>
                  </span>
                  <span className="shrink-0 tabular-nums text-stone-600">
                    <span className="font-semibold text-stone-950">{formatMoney(Math.max(line.spent, 0))}</span> of{' '}
                    {formatMoney(line.limit)}
                  </span>
                </div>
                <div
                  className="mt-2 h-1.5 overflow-hidden rounded-full bg-stone-100"
                  role="progressbar"
                  aria-valuenow={line.percentUsed}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-label={`${line.categoryName}: ${line.percentUsed}% of budget used`}
                >
                  <div
                    className={`h-full rounded-full ${over ? 'bg-red-600' : 'bg-accent'}`}
                    style={{ width: `${Math.min(Math.max(line.percentUsed, 0), 100)}%` }}
                  />
                </div>
                {/* Status in words too, never by color alone. */}
                <p className={`mt-1 text-xs ${over ? 'font-medium text-red-600' : 'text-stone-500'}`}>
                  {over ? `${formatMoney(-line.remaining)} over` : `${formatMoney(line.remaining)} left`}
                </p>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}

/** First visit: no statements yet. Three steps instead of an empty page. */
function Welcome() {
  const steps = [
    { icon: Upload, title: 'Upload a statement', text: 'Export a CSV from any bank or card. Columns are figured out for you.' },
    { icon: Tags, title: 'Check the categories', text: 'Most transactions are sorted automatically. Fix any in one click.' },
    { icon: PiggyBank, title: 'Use your budget', text: 'One is suggested from your own statements. Accept it in one click.' },
  ]
  return (
    <div className="mx-auto max-w-3xl py-8 text-center">
      <h1 className="text-4xl font-bold text-stone-950 sm:text-5xl">Let&apos;s see where your money goes</h1>
      <p className="mx-auto mt-3 max-w-lg text-stone-600">
        No bank login needed. Everything starts with one statement file.
      </p>
      <ol className="mt-10 grid gap-4 text-left sm:grid-cols-3">
        {steps.map((step, i) => (
          <li key={step.title} className="card p-5">
            <span className="grid size-9 place-items-center rounded-lg bg-accent-soft text-accent">
              <step.icon size={18} aria-hidden="true" />
            </span>
            <p className="mt-4 text-xs font-medium text-stone-500">Step {i + 1}</p>
            <p className="mt-0.5 font-semibold text-stone-950">{step.title}</p>
            <p className="mt-1 text-sm text-stone-600">{step.text}</p>
          </li>
        ))}
      </ol>
      <Link
        to="/import"
        className="mt-8 inline-flex items-center gap-2 rounded-lg bg-accent px-5 py-3 font-semibold text-accent-ink transition-colors duration-150 hover:bg-accent-hover"
      >
        <Upload size={18} aria-hidden="true" />
        Upload your first statement
      </Link>
    </div>
  )
}

/** Gray shapes where the content will be, so the page doesn't jump when data arrives. */
function DashboardSkeleton() {
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-12" aria-busy="true" aria-label="Loading dashboard">
      <div className="card p-6 lg:col-span-7">
        <div className="skeleton h-5 w-40" />
        <div className="skeleton mt-6 h-56 w-full" />
      </div>
      <div className="card space-y-4 p-6 lg:col-span-5">
        <div className="skeleton h-5 w-32" />
        {[0, 1, 2, 3, 4].map((i) => (
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
      <summary className="cursor-pointer text-stone-500">Show as table</summary>
      <table className="mt-2 w-full text-left">
        <thead className="text-xs uppercase text-stone-500">
          <tr>
            {headers.map((h, i) => (
              <th key={h} className={`py-1 font-medium ${i > 0 ? 'text-right' : ''}`}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-stone-100">
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
