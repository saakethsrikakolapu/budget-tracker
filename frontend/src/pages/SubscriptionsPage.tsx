import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { MerchantAvatar } from '../components/MerchantAvatar'
import * as api from '../api'
import { formatCadence, formatDate, formatMoney } from '../format'

/**
 * Subscriptions and bills found automatically in your statements: same merchant, similar amount,
 * regular schedule. Nothing to set up; anything that isn't really a subscription can be hidden.
 */
export function SubscriptionsPage() {
  const [overview, setOverview] = useState<api.RecurringOverview | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    api.fetchRecurring()
      .then((result) => {
        if (!ignore) setOverview(result)
      })
      .catch(() => {
        if (!ignore) setError('Could not load subscriptions.')
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
    } catch {
      setError('Could not save that change.')
    }
  }

  if (overview === null) {
    return error ? <ErrorMessage text={error} /> : <div className="skeleton h-40 max-w-3xl" aria-label="Loading subscriptions" />
  }

  const active = overview.charges.filter((c) => c.active)
  const stopped = overview.charges.filter((c) => !c.active)

  return (
    <div className="max-w-3xl space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-stone-950">Subscriptions</h1>
        <p className="mt-1 text-sm text-stone-600">
          Found automatically: charges from the same place, for about the same amount, on a regular schedule.
        </p>
      </div>

      {error && <ErrorMessage text={error} />}

      {overview.asOf === null ? (
        <p className="text-stone-600">
          <Link to="/import" className="font-medium text-accent hover:underline">
            Upload a statement
          </Link>{' '}
          and your subscriptions will show up here. A few months of history works best.
        </p>
      ) : (
        <>
          {/* The yearly number is the eye-opener, so it gets the color. */}
          <p className="font-display text-2xl font-semibold leading-snug tracking-tight text-stone-950">
            {active.length} {active.length === 1 ? 'subscription costs' : 'subscriptions cost'} you{' '}
            <span className="tabular-nums">{formatMoney(overview.activeMonthlyTotal)}</span> a month. That&apos;s about{' '}
            <span className="text-accent tabular-nums">{formatMoney(overview.activeMonthlyTotal * 12)}</span> a year.
          </p>

          <ChargeList
            title="Active"
            charges={active}
            empty="No active subscriptions found. Upload more months of statements to find them."
            onHide={(c) => change(() => api.hideRecurring(c.merchantKey))}
          />
          {stopped.length > 0 && (
            <ChargeList
              title="Looks cancelled"
              note={`No charge since the expected date (your statements go up to ${formatDate(overview.asOf)}).`}
              charges={stopped}
              onHide={(c) => change(() => api.hideRecurring(c.merchantKey))}
            />
          )}
          {overview.hidden.length > 0 && (
            <section className="rounded-lg border border-stone-200 bg-white p-5">
              <h2 className="text-lg font-bold text-stone-950">Not subscriptions</h2>
              <p className="mt-1 text-sm text-stone-500">You hid these. They don't count in the total.</p>
              <ul className="mt-3 divide-y divide-stone-100">
                {overview.hidden.map((c) => (
                  <li key={c.merchantKey} className="flex items-center justify-between py-2 text-sm">
                    <span className="text-stone-700">{c.name}</span>
                    <button
                      type="button"
                      onClick={() => change(() => api.unhideRecurring(c.merchantKey))}
                      className="text-stone-600 underline hover:text-stone-900"
                    >
                      Show again
                    </button>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
    </div>
  )
}

function ChargeList(props: {
  title: string
  note?: string
  charges: api.RecurringCharge[]
  empty?: string
  onHide: (charge: api.RecurringCharge) => void
}) {
  return (
    <section className="rounded-lg border border-stone-200 bg-white p-5">
      <h2 className="text-lg font-bold text-stone-950">{props.title}</h2>
      {props.note && <p className="mt-1 text-sm text-stone-500">{props.note}</p>}
      {props.charges.length === 0 ? (
        <p className="mt-2 text-sm text-stone-500">{props.empty}</p>
      ) : (
        <ul className="mt-2 divide-y divide-stone-100">
          {props.charges.map((c) => (
            <li key={c.merchantKey} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="flex min-w-0 items-center gap-3">
                <MerchantAvatar description={c.description} />
                <div className="min-w-0">
                  <p className="font-medium text-stone-900">{c.name}</p>
                  <p className="text-sm text-stone-500">
                    {formatCadence(c.cadence)}
                    {c.categoryName && ` · ${c.categoryName}`}
                    {' · '}
                    {c.active ? `next around ${formatDate(c.nextExpected)}` : `last charged ${formatDate(c.lastDate)}`}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-4">
                <div className="text-right">
                  <p className="font-semibold tabular-nums text-stone-950">{formatMoney(c.amount)}</p>
                  {c.cadence !== 'MONTHLY' && (
                    <p className="text-xs tabular-nums text-stone-500">≈ {formatMoney(c.monthlyCost)}/mo</p>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => props.onHide(c)}
                  title={`Hide ${c.name}: it isn't a subscription`}
                  className="min-h-9 rounded-lg border border-stone-300 px-3 py-1.5 text-xs text-stone-700 transition-colors duration-150 hover:bg-stone-100"
                >
                  Not a subscription
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function ErrorMessage({ text }: { text: string }) {
  return (
    <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-red-800">
      {text}
    </p>
  )
}
