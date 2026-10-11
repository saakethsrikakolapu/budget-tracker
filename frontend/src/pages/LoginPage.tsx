import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import { AuthForm } from '../components/AuthForm'

export function LoginPage() {
  const { user, notice, login } = useAuth()
  const navigate = useNavigate()
  // Sent here after a password reset (see ResetPasswordPage).
  const passwordReset = (useLocation().state as { passwordReset?: boolean } | null)?.passwordReset

  if (user) {
    return <Navigate to="/" replace />
  }

  return (
    <AuthForm
      title="Log in"
      submitLabel="Log in"
      passwordAutoComplete="current-password"
      notice={passwordReset ? 'Password changed. Log in with your new password.' : notice}
      afterPassword={
        <Link to="/forgot-password" className="font-medium text-accent hover:underline">
          Forgot password?
        </Link>
      }
      onSubmit={async (email, password) => {
        await login(email, password)
        navigate('/', { replace: true })
      }}
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="font-medium text-accent hover:underline">
            Create an account
          </Link>
        </>
      }
    />
  )
}
