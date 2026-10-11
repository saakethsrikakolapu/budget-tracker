import { useEffect, useState } from 'react'
import { Link } from 'react-router'
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
    return error ? <ErrorMessage text={error} /> : <p className="text-slate-500">Loading…</p>
  }

  const active = overview.charges.filter((c) => c.active)
  const stopped = overview.charges.filter((c) => !c.active)

  return (
    <div className="max-w-3xl space-y-5">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Subscriptions</h1>
        <p className="mt-1 text-sm text-slate-600">
          Found automatically: charges from the same place, for about the same amount, on a regular schedule.
        </p>
      </div>

      {error && <ErrorMessage text={error} />}

      {overview.asOf === null ? (
        <p className="text-slate-600">
          <Link to="/import" className="font-medium text-slate-900 underline">
            Upload a statement
          </Link>{' '}
          and your subscriptions will show up here. A few months of history works best.
        </p>
      ) : (
        <>
          <div className="rounded-xl bg-white px-5 py-4 shadow">
            <p className="text-xs font-medium uppercase text-slate-500">Subscriptions cost you</p>
            <p className="mt-1 text-3xl font-semibold tabular-nums text-slate-900">
              {formatMoney(overview.activeMonthlyTotal)}
              <span className="text-base font-normal text-slate-500"> / month</span>
            </p>
            <p className="mt-1 text-sm text-slate-500">
              {active.length} active · about {formatMoney(overview.activeMonthlyTotal * 12)} a year
            </p>
          </div>

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
            <section className="rounded-xl bg-white p-5 shadow">
              <h2 className="font-semibold text-slate-900">Not subscriptions</h2>
              <p className="mt-1 text-sm text-slate-500">You hid these. They don't count in the total.</p>
              <ul className="mt-3 divide-y divide-slate-100">
                {overview.hidden.map((c) => (
                  <li key={c.merchantKey} className="flex items-center justify-between py-2 text-sm">
                    <span className="text-slate-700">{c.name}</span>
                    <button
                      type="button"
                      onClick={() => change(() => api.unhideRecurring(c.merchantKey))}
                      className="text-slate-600 underline hover:text-slate-900"
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
    <section className="rounded-xl bg-white p-5 shadow">
      <h2 className="font-semibold text-slate-900">{props.title}</h2>
      {props.note && <p className="mt-1 text-sm text-slate-500">{props.note}</p>}
      {props.charges.length === 0 ? (
        <p className="mt-2 text-sm text-slate-500">{props.empty}</p>
      ) : (
        <ul className="mt-2 divide-y divide-slate-100">
          {props.charges.map((c) => (
            <li key={c.merchantKey} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="font-medium text-slate-900">{c.name}</p>
                <p className="text-sm text-slate-500">
                  {formatCadence(c.cadence)}
                  {c.categoryName && ` · ${c.categoryName}`}
                  {' · '}
                  {c.active ? `next around ${formatDate(c.nextExpected)}` : `last charged ${formatDate(c.lastDate)}`}
                </p>
              </div>
              <div className="flex items-center gap-4">
                <div className="text-right">
                  <p className="tabular-nums text-slate-900">{formatMoney(c.amount)}</p>
                  {c.cadence !== 'MONTHLY' && (
                    <p className="text-xs tabular-nums text-slate-500">≈ {formatMoney(c.monthlyCost)}/mo</p>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => props.onHide(c)}
                  title={`Hide ${c.name}: it isn't a subscription`}
                  className="rounded-lg border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-100"
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
