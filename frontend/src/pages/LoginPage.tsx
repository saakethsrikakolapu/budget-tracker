import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import { AuthForm } from '../components/AuthForm'

export function LoginPage() {
  const { user, notice, login } = useAuth()
  const navigate = useNavigate()

  if (user) {
    return <Navigate to="/" replace />
  }

  return (
    <AuthForm
      title="Log in"
      submitLabel="Log in"
      passwordAutoComplete="current-password"
      notice={notice}
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
