import { useEffect, useState } from 'react'
import * as api from '../api'
import { categoryStyle } from '../categoryStyle'
import { FirstRun } from '../components/FirstRun'
import { LoadError } from '../components/LoadError'
import { RowMenu } from '../components/RowMenu'
import { expectedLabel, formatCadence, formatDate, formatMoney, formatShortDate } from '../format'

/**
 * Subscriptions and bills found automatically in your statements: same merchant, similar amount,
 * regular schedule. Nothing to set up; anything that isn't really a subscription can be hidden
 * from the row's ⋯ menu.
 */
export function SubscriptionsPage() {
  const [overview, setOverview] = useState<api.RecurringOverview | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [loadFailed, setLoadFailed] = useState(false)
  /** After hiding one: its name and how to bring it back. */
  const [hidden, setHidden] = useState<api.RecurringCharge | null>(null)

  useEffect(() => {
    let ignore = false
    api.fetchRecurring()
      .then((result) => {
        if (!ignore) {
          setOverview(result)
          setLoadFailed(false)
        }
      })
      .catch(() => {
        if (!ignore) setLoadFailed(true)
      })
    return () => {
      ignore = true
    }
  }, [reloadKey])

  async function change(action: () => Promise<void>) {
    setError(null)
    try {
      await action()
      setReloadKey((key) => key + 1)
      return true
    } catch {
      setError('Could not save that change.')
      return false
    }
  }

  async function hide(charge: api.RecurringCharge) {
    if (await change(() => api.hideRecurring(charge.merchantKey))) setHidden(charge)
  }

  async function undoHide() {
    if (hidden && (await change(() => api.unhideRecurring(hidden.merchantKey)))) setHidden(null)
  }

  if (overview === null) {
    return loadFailed ? (
      <LoadError
        what="your subscriptions"
        onRetry={() => {
          setLoadFailed(false)
          setReloadKey((key) => key + 1)
        }}
      />
    ) : (
      <SubscriptionsSkeleton />
    )
  }

  if (overview.asOf === null) {
    return (
      <FirstRun title="Subscriptions">
        <p>
          Spendr finds subscriptions and bills on its own: charges from the same place, for about the same amount,
          on a regular schedule. A few months of statements works best.
        </p>
      </FirstRun>
    )
  }

  const active = overview.charges.filter((c) => c.active)
  const stopped = overview.charges.filter((c) => !c.active)

  return (
    <div className="space-y-6">
      <div className="max-w-3xl">
        <h1 className="text-3xl font-bold text-stone-950">Subscriptions</h1>
        <p className="mt-1 text-sm text-stone-600">
          Found automatically: charges from the same place, for about the same amount, on a regular schedule.
        </p>
        {/* Costs stay plain ink: green means "good" in this app, and this is money going out. */}
        <p className="mt-5 max-w-[40ch] font-display text-2xl font-semibold leading-snug tracking-tight text-balance text-stone-950">
          {active.length} {active.length === 1 ? 'subscription costs' : 'subscriptions cost'} you{' '}
          <span className="tabular-nums">{formatMoney(overview.activeMonthlyTotal)}</span> a month. That&apos;s about{' '}
          <span className="tabular-nums">{formatMoney(overview.activeMonthlyTotal * 12)}</span> a year.
          {active.length > 1 && ' Still using all of them?'}
        </p>
      </div>

      {error && <ErrorMessage text={error} />}
      {hidden && (
        <p role="status" className="flex flex-wrap items-center gap-3 text-sm text-stone-700">
          {hidden.name} is off the list.
          <button type="button" onClick={undoHide} className="font-medium text-accent underline underline-offset-4">
            Undo
          </button>
        </p>
      )}

      {/* Desktop: the active list on the left, the cancelled and hidden ones beside it. */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:gap-12">
        <ChargeList
          title="Active"
          charges={active}
          asOf={overview.asOf}
          empty="None found. Either you have no subscriptions (impressive) or they'll show up after a few more months of statements."
          onHide={hide}
        />
        {(stopped.length > 0 || overview.hidden.length > 0) && (
          <div className="space-y-6 lg:border-l lg:border-stone-200 lg:pl-10">
            {stopped.length > 0 && (
              <ChargeList
                title="Looks cancelled"
                note={`No charge since the expected date (your statements go up to ${formatDate(overview.asOf)}).`}
                charges={stopped}
                asOf={overview.asOf}
                onHide={hide}
              />
            )}
            {overview.hidden.length > 0 && (
              <section className="border-t border-stone-200 pt-6">
                <h2 className="text-lg font-bold text-stone-950">Not subscriptions</h2>
                <p className="mt-1 text-sm text-stone-500">You hid these. They don&apos;t count in the total.</p>
                <ul className="mt-3 divide-y divide-stone-100">
                  {overview.hidden.map((c) => (
                    <li key={c.merchantKey} className="flex items-center justify-between gap-3 py-2 text-sm">
                      <span className="min-w-0 truncate text-stone-700">{c.name}</span>
                      <button
                        type="button"
                        onClick={() => change(() => api.unhideRecurring(c.merchantKey))}
                        className="shrink-0 text-stone-600 underline hover:text-stone-900"
                      >
                        Show again
                      </button>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function ChargeList(props: {
  title: string
  note?: string
  charges: api.RecurringCharge[]
  asOf: string
  empty?: string
  onHide: (charge: api.RecurringCharge) => void
}) {
  return (
    <section className="border-t border-stone-200 pt-6">
      <h2 className="text-lg font-bold text-stone-950">{props.title}</h2>
      {props.note && <p className="mt-1 text-sm text-stone-500">{props.note}</p>}
      {props.charges.length === 0 ? (
        <p className="mt-2 text-sm text-stone-500">{props.empty}</p>
      ) : (
        <ul className="mt-2 divide-y divide-stone-100">
          {props.charges.map((c) => (
            <li key={c.merchantKey} className="flex items-center gap-3 py-3">
              <CategoryTile name={c.categoryName} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-stone-900">{c.name}</p>
                <p className="text-sm text-stone-500">
                  {formatCadence(c.cadence)},{' '}
                  {c.active ? lowerFirst(expectedLabel(c.nextExpected, props.asOf)) : `last charged ${formatShortDate(c.lastDate)}`}
                </p>
              </div>
              {/* Prices line up on the right, in tabular figures. */}
              <div className="shrink-0 text-right">
                <p className="font-semibold tabular-nums text-stone-950">{formatMoney(c.amount)}</p>
                {c.cadence !== 'MONTHLY' && (
                  <p className="text-xs tabular-nums text-stone-500">about {formatMoney(c.monthlyCost)} a month</p>
                )}
              </div>
              <RowMenu
                label={`More for ${c.name}`}
                items={[{ label: 'Not a subscription', onSelect: () => props.onHide(c) }]}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** The category's icon on a soft tint of its color: says "streaming" or "phone bill" at a glance. */
function CategoryTile({ name }: { name: string | null }) {
  const { icon: Icon, color } = categoryStyle(name)
  return (
    <span
      aria-hidden="true"
      className="flex size-9 shrink-0 items-center justify-center rounded-lg"
      style={{ color, backgroundColor: `color-mix(in oklab, ${color} 14%, transparent)` }}
    >
      <Icon size={18} strokeWidth={1.75} />
    </span>
  )
}

function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1)
}

function SubscriptionsSkeleton() {
  return (
    <div className="max-w-3xl space-y-4" aria-busy="true" aria-label="Loading subscriptions">
      <div className="skeleton h-9 w-56" />
      <div className="skeleton h-16 w-full max-w-xl" />
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="flex items-center gap-3">
          <div className="skeleton size-9" />
          <div className="skeleton h-10 flex-1" />
        </div>
      ))}
    </div>
  )
}

function ErrorMessage({ text }: { text: string }) {
  return (
    <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-red-800">
      {text}
    </p>
  )
}
