import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import { AuthForm } from '../components/AuthForm'

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()

  if (user) {
    return <Navigate to="/" replace />
  }

  return (
    <AuthForm
      title="Create account"
      submitLabel="Create account"
      passwordAutoComplete="new-password"
      onSubmit={async (email, password) => {
        await register(email, password)
        navigate('/', { replace: true })
      }}
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-slate-900 underline">
            Log in
          </Link>
        </>
      }
    />
  )
}
