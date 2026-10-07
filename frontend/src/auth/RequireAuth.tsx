import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { useAuth } from './authContext'

/** Wraps pages that need login: logged-out visitors are sent to /login. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()

  if (loading) {
    return <p className="p-6 text-slate-500">Loading…</p>
  }
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return children
}
