import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import * as api from '../api'
import { useAuth } from '../auth/authContext'
import { AuthLayout, Field, SubmitButton } from '../components/AuthLayout'

/** The page the reset email links to (/reset-password?token=...): choose a new password. */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const navigate = useNavigate()
  const { user, refreshUser } = useAuth()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [errors, setErrors] = useState<{ password?: string; confirm?: string; form?: string }>({})

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (password.length < 8) {
      setErrors({ password: 'Password must be 8 to 72 characters' })
      return
    }
    if (password !== confirm) {
      setErrors({ confirm: "The two passwords don't match" })
      return
    }
    setBusy(true)
    setErrors({})
    try {
      await api.resetPassword(token, password)
      // Every session ended, including this browser's if it was logged in: forget the old user.
      if (user) await refreshUser().catch(() => undefined)
      navigate('/login', { replace: true, state: { passwordReset: true } })
    } catch (err) {
      if (err instanceof api.ApiError) {
        setErrors({ password: err.fieldErrors.password, form: err.fieldErrors.password ? undefined : err.message })
      } else {
        setErrors({ form: 'Could not reach the server. Please try again.' })
      }
      setBusy(false)
    }
  }

  if (!token) {
    return (
      <AuthLayout title="Link missing">
        <p className="text-zinc-700">This page needs the link from your reset email.</p>
        <Link to="/forgot-password" className="text-sm font-medium text-accent hover:underline">
          Send a new link
        </Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Choose a new password">
      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        {errors.form && (
          <div role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            <p>{errors.form}</p>
            <Link to="/forgot-password" className="mt-1 inline-block font-medium underline">
              Send a new link
            </Link>
          </div>
        )}
        <Field
          label="New password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={setPassword}
          error={errors.password}
          hint="At least 8 characters."
        />
        <Field
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={setConfirm}
          error={errors.confirm}
        />
        <SubmitButton busy={busy}>Save new password</SubmitButton>
        <p className="text-center text-xs text-zinc-500">You&apos;ll be logged out everywhere and can log in with the new one.</p>
      </form>
    </AuthLayout>
  )
}
