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
    <main className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
      <form onSubmit={handleSubmit} noValidate className="w-full max-w-sm rounded-xl bg-white p-6 shadow space-y-4">
        <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>

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
          className="w-full rounded-lg bg-slate-900 py-2 font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {submitting ? 'Please wait…' : submitLabel}
        </button>

        <p className="text-center text-sm text-slate-600">{footer}</p>
      </form>
    </main>
  )
}

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
      <span className="text-sm font-medium text-slate-700">{label}</span>
      <input
        type={type}
        autoComplete={autoComplete}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        className={`mt-1 w-full rounded-lg border px-3 py-2 outline-none focus:ring-2 focus:ring-slate-400 ${
          error ? 'border-red-500' : 'border-slate-300'
        }`}
      />
      {error && <span className="mt-1 block text-sm text-red-600">{error}</span>}
    </label>
  )
}
