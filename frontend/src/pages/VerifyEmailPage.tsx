import { CircleCheck, TriangleAlert } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import * as api from '../api'
import { useAuth } from '../auth/authContext'
import { AuthLayout } from '../components/AuthLayout'

/** The page the verification email links to (/verify-email?token=...). Works logged in or out. */
export function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const { user, refreshUser } = useAuth()
  const [state, setState] = useState<'working' | 'done' | 'failed'>(token ? 'working' : 'failed')
  // React runs effects twice in development; a link only works once, so only send it once.
  const sent = useRef(false)

  useEffect(() => {
    if (!token || sent.current) return
    sent.current = true
    api.verifyEmail(token)
      .then(async () => {
        setState('done')
        await refreshUser().catch(() => undefined) // hides the "verify your email" banner
      })
      .catch(() => setState('failed'))
  }, [token, refreshUser])

  return (
    <AuthLayout title={state === 'done' ? 'Email verified' : state === 'failed' ? 'Link not valid' : 'Verifying…'}>
      {state === 'working' && <div className="skeleton h-5 w-48" aria-busy="true" />}
      {state === 'done' && (
        <div role="status" className="space-y-4">
          <span className="grid size-12 place-items-center rounded-full bg-emerald-50 text-emerald-700">
            <CircleCheck size={22} aria-hidden="true" />
          </span>
          <p className="text-zinc-700">Thanks! If you ever forget your password, you can reset it by email.</p>
          <Link
            to={user ? '/' : '/login'}
            className="inline-block rounded-lg bg-zinc-950 px-4 py-2 text-sm font-medium text-white transition-colors duration-150 hover:bg-zinc-800"
          >
            {user ? 'Go to dashboard' : 'Log in'}
          </Link>
        </div>
      )}
      {state === 'failed' && (
        <div role="alert" className="space-y-4">
          <span className="grid size-12 place-items-center rounded-full bg-amber-50 text-amber-700">
            <TriangleAlert size={22} aria-hidden="true" />
          </span>
          <p className="text-zinc-700">
            This link has expired or was already used. {user ? 'Send a new one from the banner at the top of the app.' : 'Log in to send a new one.'}
          </p>
          <Link to={user ? '/' : '/login'} className="text-sm font-medium text-accent hover:underline">
            {user ? 'Go to dashboard' : 'Log in'}
          </Link>
        </div>
      )}
    </AuthLayout>
  )
}
