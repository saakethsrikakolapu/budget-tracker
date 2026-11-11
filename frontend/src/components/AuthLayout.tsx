import { Eye, EyeOff, LoaderCircle } from 'lucide-react'
import { useEffect, useId, useState, type ReactNode } from 'react'
import { Brand, Logo } from './Logo'

/**
 * The logged-out screens (log in, sign up, forgot/reset password, verify email): the pitch on a
 * green panel on wide screens, and the page's own content on the right. The panel uses fixed colors
 * (not palette classes) so it looks the same in light and dark mode.
 */
export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  useEffect(() => {
    document.title = `${title} · Spendr`
  }, [title])

  return (
    <main className="grid min-h-screen lg:grid-cols-[1.1fr_1fr]">
      <section className="hidden flex-col justify-between overflow-hidden bg-[#2f6b4f] p-12 text-[#ffffff] lg:flex">
        <div className="flex items-center gap-2">
          <span className="rounded-lg bg-[#ffffff1a] p-0.5">
            <Logo />
          </span>
          <span className="font-display text-xl font-bold tracking-tight">spendr</span>
        </div>
        <div>
          <h2 className="max-w-lg font-display text-5xl font-bold leading-[1.05] tracking-tight">
            No bank login.
            <br />
            Just your statement.
          </h2>
          <p className="mt-5 max-w-md text-lg text-[#ecf8f1]">
            Drop in a CSV from any bank or card and see where your money went, what's left in your budget, and which
            subscriptions you forgot about.
          </p>
          <ReceiptPreview />
        </div>
        <p className="text-sm text-[#ecf8f1]">Made for students. Your statements stay yours.</p>
      </section>

      <div className="flex items-center justify-center bg-stone-50 p-6">
        <div className="w-full max-w-sm space-y-5">
          <div>
            <div className="mb-8 lg:hidden">
              <Brand />
              <p className="mt-2 text-sm text-stone-600">No bank login. Just your statement.</p>
            </div>
            <h1 className="text-3xl font-bold text-stone-950">{title}</h1>
          </div>
          {children}
        </div>
      </div>
    </main>
  )
}

/** A made-up statement slip: what Spendr does, shown rather than told. Decorative. */
function ReceiptPreview() {
  const lines = [
    ['Chipotle', 'Food & Dining', '$11.85'],
    ['Spotify', 'Subscription', '$5.99'],
    ['Harris Teeter', 'Groceries', '$42.10'],
    ['Insomnia Cookies', 'Food & Dining', '$3.00'],
  ]
  return (
    <div
      aria-hidden="true"
      className="mt-10 w-80 -rotate-2 rounded-t-md bg-[#f4f6f1] px-5 pb-6 pt-4 text-[#17302a] shadow-[0_24px_60px_-20px_rgba(0,0,0,0.45)] [clip-path:polygon(0_0,100%_0,100%_100%,95%_97%,90%_100%,85%_97%,80%_100%,75%_97%,70%_100%,65%_97%,60%_100%,55%_97%,50%_100%,45%_97%,40%_100%,35%_97%,30%_100%,25%_97%,20%_100%,15%_97%,10%_100%,5%_97%,0_100%)]"
    >
      <p className="font-display text-sm font-bold">October so far</p>
      <ul className="mt-3 space-y-2 text-sm">
        {lines.map(([merchant, category, amount]) => (
          <li key={merchant} className="flex items-baseline gap-2">
            <span className="font-medium">{merchant}</span>
            <span className="flex-1 truncate text-xs text-[#5b6b60]">{category}</span>
            <span className="tabular-nums">{amount}</span>
          </li>
        ))}
      </ul>
      <div className="mt-3 flex items-baseline justify-between border-t border-dashed border-[#c2d0c4] pt-3">
        <span className="text-sm">Left in budget</span>
        <span className="font-display text-lg font-bold text-[#2f6b4f] tabular-nums">$186.44</span>
      </div>
    </div>
  )
}

type FieldProps = {
  label: string
  type: string
  autoComplete: string
  value: string
  onChange: (value: string) => void
  error?: string
  hint?: string
}

/**
 * A labeled input with its error underneath (tied to the input with aria-describedby, so screen
 * readers read it). Password fields get a Show/Hide button: typing a password on a phone is error-prone.
 */
export function Field({ label, type, autoComplete, value, onChange, error, hint }: FieldProps) {
  const id = useId()
  const [revealed, setRevealed] = useState(false)
  const isPassword = type === 'password'
  const noteId = `${id}-note`
  return (
    <div>
      <label htmlFor={id} className="text-sm font-medium text-stone-700">
        {label}
      </label>
      <div className="relative mt-1.5">
        <input
          id={id}
          type={isPassword && revealed ? 'text' : type}
          autoComplete={autoComplete}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={error || hint ? noteId : undefined}
          className={`w-full rounded-lg border bg-white px-3 py-2.5 outline-none transition-colors duration-150 focus:border-accent focus:ring-2 focus:ring-accent-soft ${
            error ? 'border-red-500' : 'border-stone-300'
          } ${isPassword ? 'pr-20' : ''}`}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setRevealed((shown) => !shown)}
            aria-pressed={revealed}
            aria-label={revealed ? 'Hide password' : 'Show password'}
            className="absolute inset-y-1 right-1 inline-flex items-center gap-1.5 rounded-md px-2.5 text-sm text-stone-600 transition-colors duration-150 hover:bg-stone-100 hover:text-stone-900"
          >
            {revealed ? <EyeOff size={16} aria-hidden="true" /> : <Eye size={16} aria-hidden="true" />}
            <span aria-hidden="true">{revealed ? 'Hide' : 'Show'}</span>
          </button>
        )}
      </div>
      {error ? (
        <span id={noteId} className="mt-1 block text-sm text-red-600">
          {error}
        </span>
      ) : (
        hint && (
          <span id={noteId} className="mt-1 block text-xs text-stone-500">
            {hint}
          </span>
        )
      )}
    </div>
  )
}

/** The big primary button at the bottom of these forms. While busy it says what's happening. */
export function SubmitButton({ busy, busyLabel = 'One moment', children }: { busy: boolean; busyLabel?: string; children: ReactNode }) {
  return (
    <button
      type="submit"
      disabled={busy}
      aria-busy={busy || undefined}
      className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-button py-2.5 font-semibold text-button-ink transition-colors duration-150 hover:bg-button-hover disabled:cursor-wait disabled:opacity-70"
    >
      {busy && <LoaderCircle size={16} aria-hidden="true" className="animate-spin" />}
      {busy ? `${busyLabel}…` : children}
    </button>
  )
}
