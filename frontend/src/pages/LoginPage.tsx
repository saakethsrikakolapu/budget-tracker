import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import { AuthForm } from '../components/AuthForm'

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()

  if (user) {
    return <Navigate to="/" replace />
  }

  return (
    <AuthForm
      title="Log in"
      submitLabel="Log in"
      passwordAutoComplete="current-password"
      onSubmit={async (email, password) => {
        await login(email, password)
        navigate('/', { replace: true })
      }}
      footer={
        <>
          New here?{' '}
          <Link to="/register" className="font-medium text-slate-900 underline">
            Create an account
          </Link>
        </>
      }
    />
  )
}
