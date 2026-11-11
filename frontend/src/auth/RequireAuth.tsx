import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { LandingPage } from '../pages/LandingPage'
import { useAuth } from './authContext'

/**
 * Wraps pages that need login. Logged out, "/" shows the landing page (what Spendr is), and every
 * other page sends you to /login.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const { pathname } = useLocation()

  if (loading) {
    return <p className="p-6 text-stone-500">Loading…</p>
  }
  if (!user) {
    return pathname === '/' ? <LandingPage /> : <Navigate to="/login" replace />
  }
  return children
}
