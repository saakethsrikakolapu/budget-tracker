import { MailCheck } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { AuthLayout, Field, SubmitButton } from '../components/AuthLayout'

/**
 * "Forgot password": enter an email, get a reset link. The confirmation reads the same whether or
 * not the email has an account, so this page can't be used to find out who uses the app.
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (email.trim() === '') {
      setError('Enter your email')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await api.forgotPassword(email.trim())
      setSent(true)
    } catch (err) {
      setError(err instanceof api.ApiError ? (err.fieldErrors.email ?? err.message) : 'Could not reach the server.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title={sent ? 'Check your email' : 'Reset your password'}>
      {sent ? (
        <div className="space-y-4">
          <span className="grid size-12 place-items-center rounded-full bg-accent-soft text-accent">
            <MailCheck size={22} aria-hidden="true" />
          </span>
          <p role="status" className="text-stone-700">
            If <span className="font-medium text-stone-950">{email.trim()}</span> has an account, a link to reset the
            password is on its way. It works for 30 minutes.
          </p>
          <p className="text-sm text-stone-500">Nothing after a few minutes? Check your spam folder.</p>
          <Link to="/login" className="inline-block text-sm font-medium text-accent hover:underline">
            Back to log in
          </Link>
        </div>
      ) : (
        <form onSubmit={handleSubmit} noValidate className="space-y-5">
          <p className="text-sm text-stone-600">Enter the email you signed up with and we&apos;ll send you a link.</p>
          <Field label="Email" type="email" autoComplete="email" value={email} onChange={setEmail} error={error ?? undefined} />
          <SubmitButton busy={busy}>Send reset link</SubmitButton>
          <p className="text-center text-sm text-stone-600">
            Remembered it?{' '}
            <Link to="/login" className="font-medium text-accent hover:underline">
              Log in
            </Link>
          </p>
        </form>
      )}
    </AuthLayout>
  )
}
