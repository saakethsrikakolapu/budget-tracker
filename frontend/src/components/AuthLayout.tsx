import { useEffect, type ReactNode } from 'react'
import { Logo } from './Logo'

/**
 * The logged-out screens (log in, sign up, forgot/reset password, verify email): the product pitch
 * on the left on wide screens, and the page's own content on the right.
 */
export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  useEffect(() => {
    document.title = `${title} · Budget Tracker`
  }, [title])

  return (
    <main className="grid min-h-screen lg:grid-cols-2">
      {/* The pitch: only on wide screens, so phones go straight to the form. Fixed colors (not palette
          classes) so it stays a dark panel in dark mode too instead of flipping light. */}
      <section className="hidden flex-col justify-between bg-[#09090b] p-12 text-[#fafafa] lg:flex">
        <div className="flex items-center gap-2">
          <Logo inverted />
          <span className="text-[15px] font-semibold tracking-tight">Budget Tracker</span>
        </div>
        <div>
          <h2 className="max-w-md text-4xl font-semibold leading-tight tracking-tight">
            Know where your money goes. No bank login.
          </h2>
          <ul className="mt-8 space-y-4 text-sm text-[#d4d4d8]">
            {PITCH.map((line) => (
              <li key={line} className="flex gap-3">
                <span aria-hidden="true" className="mt-1.5 size-1.5 shrink-0 rounded-full bg-[#818cf8]" />
                {line}
              </li>
            ))}
          </ul>
        </div>
        <p className="text-xs text-[#a1a1aa]">Built for students. Your statements, your data.</p>
      </section>

      <div className="flex items-center justify-center bg-white p-6">
        <div className="w-full max-w-sm space-y-5">
          <div>
            <div className="mb-6 flex items-center gap-2 lg:hidden">
              <Logo />
              <span className="font-semibold tracking-tight text-zinc-950">Budget Tracker</span>
            </div>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight text-zinc-950">{title}</h1>
          </div>
          {children}
        </div>
      </div>
    </main>
  )
}

const PITCH = [
  'Upload a CSV from any bank or card. Columns are figured out for you.',
  'Transactions are categorized automatically; budgets show what is left.',
  'Subscriptions are found on their own, including ones you forgot.',
  'Delete your account and every byte of data whenever you want.',
]

type FieldProps = {
  label: string
  type: string
  autoComplete: string
  value: string
  onChange: (value: string) => void
  error?: string
  hint?: string
}

/** A labeled input with its error underneath. */
export function Field({ label, type, autoComplete, value, onChange, error, hint }: FieldProps) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-zinc-700">{label}</span>
      <input
        type={type}
        autoComplete={autoComplete}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        className={`mt-1.5 w-full rounded-lg border px-3 py-2.5 outline-none transition-colors duration-150 focus:border-accent focus:ring-2 focus:ring-accent-soft ${
          error ? 'border-red-500' : 'border-zinc-300'
        }`}
      />
      {error ? (
        <span className="mt-1 block text-sm text-red-600">{error}</span>
      ) : (
        hint && <span className="mt-1 block text-xs text-zinc-500">{hint}</span>
      )}
    </label>
  )
}

/** The big primary button at the bottom of these forms. */
export function SubmitButton({ busy, children }: { busy: boolean; children: ReactNode }) {
  return (
    <button
      type="submit"
      disabled={busy}
      className="w-full rounded-lg bg-zinc-950 py-2.5 font-medium text-white transition-colors duration-150 hover:bg-zinc-800 disabled:opacity-50"
    >
      {busy ? 'Please wait…' : children}
    </button>
  )
}
