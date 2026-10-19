import { History, PiggyBank, Tags, Upload } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Bar, BarChart, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis } from 'recharts'
import * as api from '../api'
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
 * Colors set in JS because Recharts and the month strip draw with them. One ink-green ramp, darkest
 * for the biggest share: categories are told apart by order and label, not by a rainbow, so the
 * colors that do stand out on the page (red, the highlighter) keep their meaning.
 */
function palette(dark: boolean) {
  return dark
    ? {
        strip: ['#6fae88', '#5a9475', '#4a7d62', '#3d6852', '#335745'],
        rest: '#2a3530',
        bar: '#8fc7a3',
        barMuted: '#34433c',
        axis: '#a9b4ac',
        label: '#eef1ea',
        hover: '#232e29',
        tooltip: { background: '#1b2420', border: '1px solid #2b3631', color: '#eef1ea' },
      }
    : {
        strip: ['#2f6b4f', '#4c8467', '#729f86', '#9bbaa7', '#c2d4c6'],
        rest: '#dde5da',
        bar: '#2f6b4f',
        barMuted: '#c2d4c6',
        axis: '#5b6b60',
        label: '#17302a',
        hover: '#e3e9e0',
        tooltip: { background: '#ffffff', border: '1px solid #d3dcd0', color: '#17302a' },
      }
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
    // Subscriptions don't depend on the month: load once. The section just stays hidden if this fails.
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
    <div className="space-y-10">
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
              className="block rounded-md border border-stone-300 bg-white px-3 py-2 text-sm"
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
        <p role="alert" className="rounded-md bg-red-50 px-4 py-3 text-red-800">
          {error}
        </p>
      )}

      {!dashboard && !error && <DashboardSkeleton />}

      {dashboard && (
        <>
          <MonthStrip
            data={dashboard.spendingByCategory}
            onSelect={(categoryId) =>
              navigate(transactionsLink({ category: categoryId === null ? 'uncategorized' : String(categoryId) }))
            }
          />

          {/* Two quieter sections side by side, split by a rule instead of boxed in cards. */}
          <div className="grid grid-cols-1 gap-10 border-t border-stone-200 pt-8 lg:grid-cols-2 lg:gap-0">
            <MonthlyTrend data={dashboard.monthlyTrend} selected={dashboard.month} />
            <div className="lg:border-l lg:border-stone-200 lg:pl-10">
              {recurring && <UpcomingCharges overview={recurring} />}
            </div>
          </div>

          {budgets && <BudgetList overview={budgets} plan={plan} />}
        </>
      )}
    </div>
  )
}

/**
 * The month in one sentence, in one color: "You spent $293.75 in September." The type carries it.
 * Below: how it compares, what's higher than usual, and how fresh the statements are.
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
    <>
      {/* Screen readers get the exact value at once, not the animation. */}
      <span aria-hidden="true">{formatMoney(shown)}</span>
      <span className="sr-only">{formatMoney(Math.abs(net))}</span>
    </>
  )
  return (
    <div className="max-w-3xl">
      <h1 className="text-4xl leading-[1.05] font-bold text-balance text-stone-950 tabular-nums sm:text-6xl">
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
      <p className="mt-4 max-w-[65ch] text-lg text-stone-600">
        <Comparison dashboard={dashboard} />
        {dashboard.totals.refunds > 0 && <> {formatMoney(dashboard.totals.refunds)} came back in refunds.</>}{' '}
        <Link to={seeAllLink} className="font-medium text-accent underline-offset-4 hover:underline">
          See all transactions
        </Link>
      </p>
      {plan && <HigherThanUsual plan={plan} />}
      {dashboard.latestTransactionDate && <StaleData latest={dashboard.latestTransactionDate} />}
    </div>
  )
}

/** "Groceries is $69.14 higher than usual so far", the category marked with the highlighter. */
function HigherThanUsual({ plan }: { plan: api.BudgetPlan }) {
  const higher = plan.lines
    .filter((l) => l.pace === 'HIGHER')
    .sort((a, b) => b.spent - b.usualSoFar - (a.spent - a.usualSoFar))
    .slice(0, 2)
  if (higher.length === 0) return null
  const soFar = plan.throughDay < 28 ? ' so far' : ''
  return (
    <p className="mt-2 max-w-[65ch] text-lg text-stone-600">
      {higher.map((l, i) => (
        <span key={l.categoryName}>
          {i > 0 && ' '}
          <mark className="font-medium">{l.categoryName}</mark> is{' '}
          <span className="tabular-nums">{formatMoney(l.spent - l.usualSoFar)}</span> higher than usual{soFar}.
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
      That&apos;s {formatMoney(Math.abs(diff))} {diff > 0 ? 'more' : 'less'} than {label}.
    </>
  )
}

/**
 * There's no bank connection, so the app only knows what's been uploaded. Once the newest
 * transaction is more than {@link STALE_AFTER_DAYS} days old, say so (one line, one link: the
 * header already has the upload button).
 */
const STALE_AFTER_DAYS = 10

function StaleData({ latest }: { latest: string }) {
  if (daysFromToday(latest) > -STALE_AFTER_DAYS) return null
  const thisMonth = formatMonth(currentMonth()).split(' ')[0]
  const newMonth = latest.slice(0, 7) < currentMonth()
  return (
    <p className="mt-4 flex items-center gap-2 text-sm text-stone-600">
      <History size={16} className="shrink-0 text-stone-500" aria-hidden="true" />
      <span>
        Your statements end {formatShortDate(latest)}.{' '}
        <Link to="/import" className="font-medium text-accent underline underline-offset-4">
          {newMonth ? `Upload a newer one to see ${thisMonth}` : 'Upload a newer one'}
        </Link>
      </span>
    </p>
  )
}

const STRIP_CATEGORIES = 5

/**
 * Where the month went, as one strip: each category's share of the spending, biggest first, in one
 * ink-green ramp. The labels underneath are the buttons (the strip itself is a picture of them).
 */
function MonthStrip({
  data,
  onSelect,
}: {
  data: api.Dashboard['spendingByCategory']
  onSelect: (categoryId: number | null) => void
}) {
  const colors = palette(useIsDark())
  const total = data.reduce((sum, d) => sum + d.amount, 0)
  if (data.length === 0 || total <= 0) {
    return (
      <section aria-labelledby="where-it-went">
        <h2 id="where-it-went" className="text-lg font-bold text-stone-950">
          Where it went
        </h2>
        <p className="mt-2 text-stone-600">No spending this month.</p>
      </section>
    )
  }
  const top = data.slice(0, STRIP_CATEGORIES)
  const rest = data.slice(STRIP_CATEGORIES)
  const restTotal = rest.reduce((sum, d) => sum + d.amount, 0)
  const pieces = [
    ...top.map((d, i) => ({ key: d.categoryName, label: d.categoryName, amount: d.amount, color: colors.strip[i], categoryId: d.categoryId as number | null | undefined })),
    ...(restTotal > 0
      ? [{ key: 'rest', label: `${rest.length} more`, amount: restTotal, color: colors.rest, categoryId: undefined }]
      : []),
  ]
  const share = (amount: number) => Math.max((amount / total) * 100, 1.5)

  return (
    <section aria-labelledby="where-it-went">
      <h2 id="where-it-went" className="text-lg font-bold text-stone-950">
        Where it went
      </h2>
      <div aria-hidden="true" className="mt-4 flex h-14 gap-0.5 overflow-hidden rounded-md">
        {pieces.map((p) => (
          <div key={p.key} style={{ flexBasis: `${share(p.amount)}%`, backgroundColor: p.color }} className="min-w-1" />
        ))}
      </div>
      <ul className="mt-4 flex flex-wrap gap-x-10 gap-y-3">
        {pieces.map((p) => {
          const content = (
            <>
              <span className="flex items-center gap-2 text-sm text-stone-700">
                <span aria-hidden="true" className="size-2.5 shrink-0 rounded-sm" style={{ backgroundColor: p.color }} />
                <span className="truncate">{p.label}</span>
              </span>
              <span className="mt-0.5 block font-display text-xl font-semibold tabular-nums text-stone-950">
                {formatMoney(p.amount)}
              </span>
            </>
          )
          return (
            <li key={p.key}>
              {p.categoryId === undefined ? (
                <div className="px-1">{content}</div>
              ) : (
                <button
                  type="button"
                  onClick={() => onSelect(p.categoryId ?? null)}
                  className="w-full rounded-md px-1 py-0.5 text-left transition-colors duration-150 hover:bg-stone-100"
                >
                  {content}
                  <span className="sr-only">: show these transactions</span>
                </button>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}

function MonthlyTrend({ data, selected }: { data: api.Dashboard['monthlyTrend']; selected: string }) {
  const rows = data.map((d) => ({ ...d, label: formatMonthShort(d.month) }))
  const colors = palette(useIsDark())
  return (
    <section aria-labelledby="six-months" className="lg:pr-10">
      <h2 id="six-months" className="text-lg font-bold text-stone-950">
        The last six months
      </h2>
      {/* Amounts sit on the bars, so there's no y-axis or grid to read across. */}
      <div className="mt-4 h-48">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 22, right: 0, bottom: 0, left: 0 }} barCategoryGap="30%">
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
            <Bar dataKey="netSpending" radius={[3, 3, 0, 0]} maxBarSize={44}>
              {rows.map((row) => (
                <Cell key={row.month} fill={row.month === selected ? colors.bar : colors.barMuted} />
              ))}
              <LabelList
                dataKey="netSpending"
                position="top"
                offset={6}
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
    <section aria-labelledby="coming-up">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="coming-up" className="text-lg font-bold text-stone-950">
          Coming up
        </h2>
        <Link to="/subscriptions" className="text-sm font-medium text-accent hover:underline">
          All subscriptions
        </Link>
      </div>
      {upcoming.length === 0 ? (
        <p className="mt-3 text-sm text-stone-600">
          Nothing regular found yet. Subscriptions show up once a few months of statements are uploaded.
        </p>
      ) : (
        <>
          <p className="mt-1 text-sm text-stone-600">
            Subscriptions cost you {formatMoney(overview.activeMonthlyTotal)} a month.
          </p>
          <ul className="mt-3 divide-y divide-stone-200">
            {upcoming.map((c) => (
              <li key={c.merchantKey} className="flex items-baseline gap-4 py-2.5">
                <span className="w-14 shrink-0 text-sm tabular-nums text-stone-500">{formatShortDate(c.nextExpected)}</span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-stone-900">{c.name}</p>
                  <p className="text-xs text-stone-500">
                    {formatCadence(c.cadence)}, {dueLabel(c.nextExpected)}
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

/**
 * The budget, one line per category: "$82 of $150" and a thin bar. Red only when over; the
 * highlighter marks a category that's running higher than usual.
 */
function BudgetList({ overview, plan }: { overview: api.BudgetOverview; plan: api.BudgetPlan | null }) {
  const top = overview.budgets.slice(0, 6) // already sorted: most used first
  const higher = new Set(plan?.lines.filter((l) => l.pace === 'HIGHER').map((l) => l.categoryId))
  return (
    <section aria-labelledby="budget" className="border-t border-stone-200 pt-8">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="budget" className="text-lg font-bold text-stone-950">
          Budget
        </h2>
        {top.length > 0 && (
          <Link to={`/budgets?month=${overview.month}`} className="text-sm font-medium text-accent hover:underline">
            See budget
          </Link>
        )}
      </div>
      {top.length === 0 ? (
        <div className="mt-3 flex max-w-xl items-start gap-3">
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
                className="mt-3 inline-block rounded-md bg-button px-3 py-1.5 font-semibold text-button-ink transition-colors duration-150 hover:bg-button-hover"
              >
                See my budget
              </Link>
            )}
          </div>
        </div>
      ) : (
        <ul className="mt-4 grid grid-cols-1 gap-x-12 gap-y-5 lg:grid-cols-2">
          {top.map((line) => {
            const over = line.remaining < 0
            return (
              <li key={line.categoryId}>
                <div className="flex items-baseline justify-between gap-3 text-sm">
                  <span className="min-w-0 font-medium text-stone-900">
                    {higher.has(line.categoryId) ? (
                      <mark className="truncate">{line.categoryName}</mark>
                    ) : (
                      <span className="truncate">{line.categoryName}</span>
                    )}
                  </span>
                  <span className="shrink-0 tabular-nums text-stone-600">
                    <span className="font-semibold text-stone-950">{formatMoney(Math.max(line.spent, 0))}</span> of{' '}
                    {formatMoney(line.limit)}
                  </span>
                </div>
                <div
                  className="mt-2 h-1.5 overflow-hidden rounded-full bg-stone-200"
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
    <div className="mx-auto max-w-3xl py-8">
      <h1 className="text-4xl leading-[1.05] font-bold text-stone-950 sm:text-6xl">Let&apos;s see where your money goes</h1>
      <p className="mt-4 max-w-lg text-lg text-stone-600">
        No bank login needed. Everything starts with one statement file.
      </p>
      {/* A real sequence, so the steps are numbered. */}
      <ol className="mt-10 grid gap-8 border-t border-stone-200 pt-8 sm:grid-cols-3">
        {steps.map((step, i) => (
          <li key={step.title}>
            <step.icon size={20} className="text-accent" aria-hidden="true" />
            <p className="mt-3 font-semibold text-stone-950">
              {i + 1}. {step.title}
            </p>
            <p className="mt-1 text-sm text-stone-600">{step.text}</p>
          </li>
        ))}
      </ol>
      <Link
        to="/import"
        className="mt-10 inline-flex items-center gap-2 rounded-md bg-button px-5 py-3 font-semibold text-button-ink transition-colors duration-150 hover:bg-button-hover"
      >
        <Upload size={18} aria-hidden="true" />
        Upload your first statement
      </Link>
    </div>
  )
}

/** Shapes where the content will be, so the page doesn't jump when data arrives. */
function DashboardSkeleton() {
  return (
    <div className="space-y-8" aria-busy="true" aria-label="Loading dashboard">
      <div className="skeleton h-14 w-full" />
      <div className="grid grid-cols-2 gap-6 sm:grid-cols-6">
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <div key={i} className="skeleton h-10" />
        ))}
      </div>
      <div className="skeleton h-48 w-full" />
    </div>
  )
}

/** The same numbers as a table, for screen readers and anyone who prefers exact values. */
function TableView({ headers, rows }: { headers: string[]; rows: string[][] }) {
  return (
    <details className="mt-3 text-sm">
      <summary className="cursor-pointer text-stone-500">Show as table</summary>
      <table className="mt-2 w-full text-left">
        <thead className="text-xs text-stone-500">
          <tr>
            {headers.map((h, i) => (
              <th key={h} className={`py-1 font-medium ${i > 0 ? 'text-right' : ''}`}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-stone-200">
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
