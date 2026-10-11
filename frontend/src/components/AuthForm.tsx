import { useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../api'
import { AuthLayout, Field, SubmitButton } from './AuthLayout'

type Props = {
  title: string
  submitLabel: string
  passwordAutoComplete: 'current-password' | 'new-password'
  onSubmit: (email: string, password: string) => Promise<void>
  footer: ReactNode
  /** Optional note shown above the submit button. */
  notice?: ReactNode
  /** Optional link under the password field (e.g. "Forgot password?"). */
  afterPassword?: ReactNode
}

/** Email + password form shared by the login and register pages. */
export function AuthForm({ title, submitLabel, passwordAutoComplete, onSubmit, footer, notice, afterPassword }: Props) {
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
    <AuthLayout title={title}>
      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        {formError && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {formError}
          </p>
        )}

        <Field label="Email" type="email" autoComplete="email" value={email} onChange={setEmail} error={fieldErrors.email} />
        <div>
          <Field
            label="Password"
            type="password"
            autoComplete={passwordAutoComplete}
            value={password}
            onChange={setPassword}
            error={fieldErrors.password}
          />
          {afterPassword && <div className="mt-2 text-right text-sm">{afterPassword}</div>}
        </div>

        {notice && <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900">{notice}</p>}

        <SubmitButton busy={submitting}>{submitLabel}</SubmitButton>

        <p className="text-center text-sm text-zinc-600">{footer}</p>
      </form>
    </AuthLayout>
  )
}
