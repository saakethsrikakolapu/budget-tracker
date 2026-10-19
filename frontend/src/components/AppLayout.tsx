import { LayoutDashboard, ListOrdered, LogOut, Mail, PiggyBank, Repeat, Upload, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import * as api from '../api'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import { Brand } from './Logo'
import { SystemStatus } from './SystemStatus'
import { ThemeToggle } from './ThemeToggle'

/** Browser-tab title per page; screen readers announce it when the page changes. */
const TITLES: Record<string, string> = {
  '/': 'Dashboard',
  '/transactions': 'Transactions',
  '/budgets': 'Budget',
  '/subscriptions': 'Subscriptions',
  '/import': 'Upload a statement',
  '/account': 'Account',
}

// Icons only show in the phone tab bar, where the labels are small.
const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/transactions', label: 'Transactions', icon: ListOrdered },
  { to: '/budgets', label: 'Budget', icon: PiggyBank },
  { to: '/subscriptions', label: 'Subscriptions', icon: Repeat },
]

/**
 * Header around every logged-in page (plus a tab bar at the bottom on phones). The current page
 * renders at <Outlet />.
 */
export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  useEffect(() => {
    const page = TITLES[pathname]
    document.title = page ? `${page} · Spendr` : 'Spendr'
  }, [pathname])

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  // Active page: dark text with an accent bar underneath; others are muted until hovered.
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `relative flex items-center whitespace-nowrap py-4 text-sm transition-colors duration-150 ${
      isActive
        ? 'font-medium text-stone-950 after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:bg-accent'
        : 'text-stone-500 hover:text-stone-900'
    }`

  return (
    <div className="flex min-h-screen flex-col">
      {/* First thing a keyboard user reaches: jump past the navigation. Visible only when focused. */}
      <a
        href="#main"
        className="sr-only z-20 rounded-lg bg-stone-950 px-4 py-2 text-sm font-medium text-white focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-10 border-b border-stone-200 bg-stone-50/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-x-6 px-4 sm:px-6">
          <NavLink to="/" className="py-3" aria-label="Spendr home">
            <Brand />
          </NavLink>

          {/* Wide screens: inline in the header. Phones use the tab bar at the bottom instead. */}
          <nav aria-label="Main" className="-mb-px hidden flex-1 gap-5 md:flex">
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-1 sm:gap-2">
            <ThemeToggle />
            <NavLink
              to="/import"
              className="inline-flex items-center gap-1.5 rounded-lg bg-button px-3 py-2 text-sm font-semibold text-button-ink transition-colors duration-150 hover:bg-button-hover"
            >
              <Upload size={16} aria-hidden="true" />
              Upload<span className="hidden md:inline"> statement</span>
            </NavLink>
            <NavLink
              to="/account"
              title={user?.email}
              aria-label="Your account"
              className="grid size-9 place-items-center rounded-full border border-stone-200 text-sm font-medium uppercase text-stone-700 transition-colors duration-150 hover:bg-stone-100"
            >
              {user?.email.charAt(0)}
            </NavLink>
            <button
              type="button"
              onClick={handleLogout}
              aria-label="Log out"
              title="Log out"
              className="grid size-9 place-items-center rounded-full text-stone-600 transition-colors duration-150 hover:bg-stone-100 hover:text-stone-900"
            >
              <LogOut size={18} aria-hidden="true" />
            </button>
          </div>
        </div>
      </header>

      {/* Says something only when the server or its database is down. */}
      <SystemStatus />
      {user && !user.emailVerified && <VerifyBanner />}

      {/* Extra bottom padding on phones so the tab bar never covers the end of the page. */}
      <main
        id="main"
        tabIndex={-1}
        className="mx-auto w-full max-w-6xl flex-1 px-4 pt-8 pb-24 outline-none sm:px-6 md:pb-12"
      >
        <Outlet />
      </main>

      {/* Phones: the pages as a tab bar at the bottom, where thumbs reach. */}
      <nav
        aria-label="Main"
        className="fixed inset-x-0 bottom-0 z-10 grid grid-cols-4 border-t border-stone-200 bg-stone-50/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden"
      >
        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `flex flex-col items-center gap-1 py-2.5 text-[11px] transition-colors duration-150 ${
                isActive ? 'font-semibold text-accent' : 'text-stone-600'
              }`
            }
          >
            <item.icon size={20} aria-hidden="true" />
            {item.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

/**
 * Shown until the email is verified. Soft on purpose: everything works without verifying, but a
 * verified email is how a forgotten password gets reset. Dismissing hides it until the next visit.
 */
function VerifyBanner() {
  const [state, setState] = useState<'idle' | 'sending' | 'sent' | 'hidden'>(() => {
    try {
      return sessionStorage.getItem('verify-banner-hidden') ? 'hidden' : 'idle'
    } catch {
      return 'idle'
    }
  })

  if (state === 'hidden') return null

  async function resend() {
    setState('sending')
    try {
      await api.resendVerification()
      setState('sent')
    } catch {
      setState('idle')
    }
  }

  function hide() {
    try {
      sessionStorage.setItem('verify-banner-hidden', '1')
    } catch {
      // can't remember; hides for now anyway
    }
    setState('hidden')
  }

  return (
    <div className="border-b border-stone-200 bg-accent-soft">
      <div className="mx-auto flex max-w-6xl items-center gap-3 px-6 py-2.5 text-sm text-stone-900">
        <Mail size={16} className="shrink-0" aria-hidden="true" />
        <p className="flex-1">
          {state === 'sent'
            ? 'Sent! Check your inbox (and spam) for the link.'
            : 'Verify your email so you can reset your password if you forget it. The link is in your inbox.'}
        </p>
        {state !== 'sent' && (
          <button
            type="button"
            onClick={resend}
            disabled={state === 'sending'}
            className="shrink-0 font-medium underline disabled:opacity-60"
          >
            {state === 'sending' ? 'Sending…' : 'Resend email'}
          </button>
        )}
        <button
          type="button"
          onClick={hide}
          aria-label="Hide this message"
          className="grid size-7 shrink-0 place-items-center rounded-full hover:bg-white/60"
        >
          <X size={14} aria-hidden="true" />
        </button>
      </div>
    </div>
  )
}
