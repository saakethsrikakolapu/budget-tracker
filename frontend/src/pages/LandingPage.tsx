import { useEffect } from 'react'
import { Link } from 'react-router'
import { Brand } from '../components/Logo'

/**
 * What a logged-out visitor sees at "/": one column that reads like a statement. The promise, a
 * receipt that prints out into a slice of the real dashboard (sample numbers, labeled as such), the
 * three steps as statement lines, and the fine print, which is short and true.
 */
export function LandingPage() {
  useEffect(() => {
    document.title = 'Spendr: see where your money went'
  }, [])

  return (
    <div className="min-h-dvh bg-stone-50 text-stone-900">
      <header className="mx-auto flex max-w-3xl items-center justify-between gap-4 px-4 py-4 sm:px-6">
        <Brand />
        <nav aria-label="Account" className="flex items-center gap-2">
          <Link to="/login" className="rounded-lg px-3 py-2 text-sm font-medium text-stone-700 hover:text-stone-950">
            Log in
          </Link>
          <Link
            to="/register"
            className="rounded-lg bg-button px-3 py-2 text-sm font-semibold text-button-ink transition-colors duration-150 hover:bg-button-hover"
          >
            Create account
          </Link>
        </nav>
      </header>

      <main className="mx-auto max-w-3xl px-4 pb-20 sm:px-6">
        <section aria-labelledby="promise" className="pt-10 sm:pt-16">
          <h1
            id="promise"
            className="max-w-[16ch] text-5xl leading-[1.02] font-bold text-stone-950 sm:text-7xl"
          >
            <span className="block">See where your money went.</span>
            <span className="block">No bank login.</span>
          </h1>
          <p className="mt-6 max-w-[52ch] text-lg text-stone-600">
            Download a statement from your bank or card as a CSV and drop it in. Spendr sorts every purchase, builds a
            budget from how you actually spend, and finds the subscriptions that keep charging you.
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Link
              to="/register"
              className="rounded-lg bg-button px-5 py-3 font-semibold text-button-ink transition-colors duration-150 hover:bg-button-hover"
            >
              Create an account
            </Link>
            <Link to="/login" className="px-2 py-3 font-medium text-accent underline-offset-4 hover:underline">
              I have one, log in
            </Link>
          </div>
        </section>

        <StatementSlice />

        <section aria-labelledby="how" className="mt-20">
          <h2 id="how" className="text-2xl font-bold text-stone-950">
            How it works
          </h2>
          {/* A real sequence, so numbered; laid out like lines on a statement, with dotted leaders. */}
          <ol className="mt-6 divide-y divide-stone-200 border-y border-stone-200">
            {STEPS.map((step, i) => (
              <li key={step.title} className="grid grid-cols-[2rem_minmax(0,1fr)] gap-x-3 py-5 sm:grid-cols-[2.5rem_minmax(0,1fr)_auto]">
                <span className="font-display text-xl font-bold text-accent tabular-nums">{i + 1}</span>
                <div className="min-w-0">
                  <p className="flex items-baseline gap-2 font-semibold text-stone-950">
                    {step.title}
                    <span aria-hidden="true" className="hidden flex-1 translate-y-[-3px] border-b border-dotted border-stone-300 sm:block" />
                  </p>
                  <p className="mt-1 max-w-[56ch] text-sm text-stone-600">{step.text}</p>
                </div>
                <span className="col-start-2 mt-2 text-sm font-medium text-stone-500 sm:col-start-3 sm:mt-0">{step.tag}</span>
              </li>
            ))}
          </ol>
        </section>

        <section aria-labelledby="fine-print" className="mt-16 border-t border-dashed border-stone-300 pt-8">
          <h2 id="fine-print" className="text-lg font-bold text-stone-950">
            The fine print, which is short
          </h2>
          <ul className="mt-3 max-w-[60ch] space-y-2 text-stone-700">
            <li>No bank login, ever. Spendr only sees the statements you choose to upload.</li>
            <li>The file itself is never stored. Spendr keeps the transactions it reads from it.</li>
            <li>Download all of it as a CSV, or delete your account and everything in it, from the Account page.</li>
          </ul>
        </section>

        <section className="mt-16 flex flex-wrap items-center justify-between gap-4 rounded-lg bg-accent-soft px-6 py-6">
          <p className="font-display text-xl font-semibold text-stone-950">Your last statement is a good place to start.</p>
          <Link
            to="/register"
            className="rounded-lg bg-button px-5 py-3 font-semibold text-button-ink transition-colors duration-150 hover:bg-button-hover"
          >
            Create an account
          </Link>
        </section>
      </main>

      <footer className="border-t border-stone-200">
        <div className="mx-auto flex max-w-3xl flex-wrap items-center justify-between gap-3 px-4 py-6 text-sm text-stone-500 sm:px-6">
          <Brand size={22} />
          <span>Made for students. Your statements stay yours.</span>
        </div>
      </footer>
    </div>
  )
}

const STEPS = [
  {
    title: 'Download a CSV',
    text: "On your bank or card's website, look for Download or Export and pick CSV. Most banking apps can't, so use a computer.",
    tag: 'Any bank or card',
  },
  {
    title: 'Drop it in',
    text: 'Spendr figures out the columns, skips anything already uploaded, and sorts each purchase into a category.',
    tag: 'Sorted for you',
  },
  {
    title: 'See where it went',
    text: 'Your month in one sentence, a budget built from your own spending, and the subscriptions you forgot about.',
    tag: 'In plain English',
  },
]

/** Sample categories for the slice (a made-up student's September, same as the demo data). */
const SAMPLE = [
  { name: 'Groceries', amount: 134.03, color: 'var(--color-cat-groceries)' },
  { name: 'Bills & Utilities', amount: 64.31, color: 'var(--color-cat-bills)' },
  { name: 'Food & Dining', amount: 40.44, color: 'var(--color-cat-food)' },
  { name: 'Entertainment', amount: 29.98, color: 'var(--color-cat-entertainment)' },
  { name: 'Health', amount: 24.99, color: 'var(--color-cat-health)' },
]

/**
 * The receipt: it prints out of a slot (the one authored motion on the page; skipped with reduced
 * motion) and on it is a slice of the real dashboard. Decorative for screen readers except the
 * caption, which says it's sample data.
 */
function StatementSlice() {
  const total = SAMPLE.reduce((sum, c) => sum + c.amount, 0)
  return (
    <figure className="mt-16 sm:mt-20">
      {/* The slot the receipt comes out of. */}
      <div aria-hidden="true" className="mx-auto h-2 max-w-md rounded-full bg-stone-300 sm:max-w-lg" />
      <div className="overflow-hidden">
        <div
          aria-hidden="true"
          className="receipt-print mx-auto -mt-1 max-w-md bg-white px-6 pt-7 pb-10 text-stone-900 shadow-[0_24px_48px_-24px_rgb(23_48_42/0.35)] sm:max-w-lg sm:px-8 [clip-path:polygon(0_0,100%_0,100%_100%,95%_98%,90%_100%,85%_98%,80%_100%,75%_98%,70%_100%,65%_98%,60%_100%,55%_98%,50%_100%,45%_98%,40%_100%,35%_98%,30%_100%,25%_98%,20%_100%,15%_98%,10%_100%,5%_98%,0_100%)]"
        >
          <p className="font-display text-2xl leading-tight font-bold text-stone-950">You spent $293.75 in September.</p>
          <p className="mt-1 text-sm text-stone-600">
            <mark className="font-medium">Groceries</mark> is $69.14 higher than usual.
          </p>

          <div className="mt-5 flex h-8 gap-0.5 overflow-hidden rounded">
            {SAMPLE.map((c) => (
              <span key={c.name} style={{ flexBasis: `${(c.amount / total) * 100}%`, backgroundColor: c.color }} />
            ))}
          </div>
          <ul className="mt-3 flex flex-wrap gap-x-5 gap-y-1.5 text-sm">
            {SAMPLE.slice(0, 3).map((c) => (
              <li key={c.name} className="flex items-center gap-2">
                <span className="size-2.5 shrink-0 rounded-sm" style={{ backgroundColor: c.color }} />
                <span className="text-stone-700">{c.name}</span>
                <span className="tabular-nums text-stone-950">${c.amount.toFixed(2)}</span>
              </li>
            ))}
          </ul>

          <div className="mt-6 space-y-4 border-t border-dashed border-stone-300 pt-5 text-sm">
            <SampleBudget name="Groceries" spent={134.03} limit={64} />
            <SampleBudget name="Entertainment" spent={29.98} limit={35} />
          </div>

          <div className="mt-6 flex items-baseline justify-between border-t border-dashed border-stone-300 pt-4 text-sm">
            <span className="text-stone-700">4 subscriptions</span>
            <span className="tabular-nums">
              <strong className="font-semibold text-stone-950">$119.28</strong> a month
            </span>
          </div>
        </div>
      </div>
      <figcaption className="mt-4 text-center text-xs text-stone-500">
        Sample data from a made-up student&apos;s card statement.
      </figcaption>
    </figure>
  )
}

/** A budget line on the receipt: same look as the real one (budget mark and overflow when over). */
function SampleBudget({ name, spent, limit }: { name: string; spent: number; limit: number }) {
  const over = spent > limit
  const scale = over ? Math.min(spent, limit * 2) : limit
  const mark = (limit / scale) * 100
  return (
    <div>
      <div className="flex items-baseline justify-between gap-3">
        <span className="font-medium text-stone-900">{name}</span>
        <span className="tabular-nums text-stone-600">
          <strong className="font-semibold text-stone-950">${spent.toFixed(2)}</strong> of ${limit.toFixed(2)}
        </span>
      </div>
      <div className="relative mt-2 h-1.5 rounded-full bg-stone-200">
        {over ? (
          <>
            <div className="absolute inset-y-0 left-0 rounded-l-full bg-stone-300" style={{ width: `${mark}%` }} />
            <div className="absolute inset-y-0 right-0 rounded-r-full bg-over" style={{ left: `${mark}%` }} />
            <div className="absolute -inset-y-1 w-0.5 rounded-full bg-stone-950" style={{ left: `calc(${mark}% - 1px)` }} />
          </>
        ) : (
          <div className="h-full rounded-full bg-good" style={{ width: `${(spent / limit) * 100}%` }} />
        )}
      </div>
      <p className={`mt-1 text-xs tabular-nums ${over ? 'font-medium text-over' : 'text-stone-500'}`}>
        {over ? `$${(spent - limit).toFixed(2)} over` : `$${(limit - spent).toFixed(2)} left`}
      </p>
    </div>
  )
}
