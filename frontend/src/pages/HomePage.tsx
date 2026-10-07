import { Link } from 'react-router'
import { useAuth } from '../auth/authContext'

/** Placeholder home page; the transaction list replaces this in Stage 1, piece 5. */
export function HomePage() {
  const { user } = useAuth()

  return (
    <>
      <h1 className="text-2xl font-semibold text-slate-900">Welcome, {user?.email}</h1>
      <p className="mt-2 text-slate-600">
        Start by{' '}
        <Link to="/import" className="font-medium text-slate-900 underline">
          uploading a statement
        </Link>
        .
      </p>
    </>
  )
}
