import { useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../api'

type Props = {
  title: string
  submitLabel: string
  passwordAutoComplete: 'current-password' | 'new-password'
  onSubmit: (email: string, password: string) => Promise<void>
  footer: ReactNode
  /** Optional note shown above the submit button. */
  notice?: ReactNode
}

/** Email + password form shared by the login and register pages. */
export function AuthForm({ title, submitLabel, passwordAutoComplete, onSubmit, footer, notice }: Props) {
  // "Controlled inputs": React state is the single source of truth for what's typed.
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  async function handleSubmit(event: FormEvent) {
    event.preventDefault() // stop the browser's default full-page form submission
    setSubmitting(true)
    setFormError(null)
    setFieldErrors({})
    try {
      await onSubmit(email, password)
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors)
        // Field errors are shown under each input; show the general message only otherwise.
        if (Object.keys(error.fieldErrors).length === 0) {
          setFormError(error.message)
        }
      } else {
        setFormError('Could not reach the server. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="grid min-h-screen lg:grid-cols-2">
      {/* The pitch: only on wide screens, so phones go straight to the form. */}
      <section className="hidden flex-col justify-between bg-zinc-950 p-12 text-white lg:flex">
        <div className="flex items-center gap-2">
          <span aria-hidden="true" className="grid size-7 place-items-center rounded-md bg-white text-xs font-bold text-zinc-950">
            B
          </span>
          <span className="text-[15px] font-semibold tracking-tight">Budget Tracker</span>
        </div>
        <div>
          <h2 className="max-w-md text-4xl font-semibold leading-tight tracking-tight">
            Know where your money goes. No bank login.
          </h2>
          <ul className="mt-8 space-y-4 text-sm text-zinc-300">
            {PITCH.map((line) => (
              <li key={line} className="flex gap-3">
                <span aria-hidden="true" className="mt-1.5 size-1.5 shrink-0 rounded-full bg-accent" />
                {line}
              </li>
            ))}
          </ul>
        </div>
        <p className="text-xs text-zinc-500">Built for students. Your statements, your data.</p>
      </section>

      <div className="flex items-center justify-center bg-white p-6">
        <form onSubmit={handleSubmit} noValidate className="w-full max-w-sm space-y-5">
          <div>
            <p className="kicker lg:hidden">Budget Tracker</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight text-zinc-950">{title}</h1>
          </div>

          {formError && (
            <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
              {formError}
            </p>
          )}

          <Field
            label="Email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={setEmail}
            error={fieldErrors.email}
          />
          <Field
            label="Password"
            type="password"
            autoComplete={passwordAutoComplete}
            value={password}
            onChange={setPassword}
            error={fieldErrors.password}
          />

          {notice && <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900">{notice}</p>}

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-lg bg-zinc-950 py-2.5 font-medium text-white transition-colors duration-150 hover:bg-zinc-800 disabled:opacity-50"
          >
            {submitting ? 'Please wait…' : submitLabel}
          </button>

          <p className="text-center text-sm text-zinc-600">{footer}</p>
        </form>
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
}

function Field({ label, type, autoComplete, value, onChange, error }: FieldProps) {
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
      {error && <span className="mt-1 block text-sm text-red-600">{error}</span>}
    </label>
  )
}
