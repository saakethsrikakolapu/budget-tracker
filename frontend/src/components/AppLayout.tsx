import { LayoutDashboard, ListOrdered, LogOut, Mail, PiggyBank, Repeat, Upload, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import * as api from '../api'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import { Logo } from './Logo'
import { SystemStatus } from './SystemStatus'
import { ThemeToggle } from './ThemeToggle'

/** Browser-tab title per page; screen readers announce it when the page changes. */
const TITLES: Record<string, string> = {
  '/': 'Dashboard',
  '/transactions': 'Transactions',
  '/budgets': 'Budgets',
  '/subscriptions': 'Subscriptions',
  '/import': 'Upload a statement',
  '/account': 'Account',
}

const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/transactions', label: 'Transactions', icon: ListOrdered },
  { to: '/budgets', label: 'Budgets', icon: PiggyBank },
  { to: '/subscriptions', label: 'Subscriptions', icon: Repeat },
]

/** Header + footer around every logged-in page. The current page renders at <Outlet />. */
export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  useEffect(() => {
    const page = TITLES[pathname]
    document.title = page ? `${page} · Budget Tracker` : 'Budget Tracker'
  }, [pathname])

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  // Active page: dark text with an accent bar underneath; others are muted until hovered.
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `relative flex items-center gap-1.5 whitespace-nowrap py-4 text-sm transition-colors duration-150 ${
      isActive
        ? 'font-medium text-zinc-950 after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:bg-accent'
        : 'text-zinc-500 hover:text-zinc-900'
    }`

  return (
    <div className="flex min-h-screen flex-col">
      {/* First thing a keyboard user reaches: jump past the navigation. Visible only when focused. */}
      <a
        href="#main"
        className="sr-only z-20 rounded-lg bg-zinc-950 px-4 py-2 text-sm font-medium text-white focus:not-sr-only focus:fixed focus:left-4 focus:top-4"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-10 border-b border-zinc-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-6 px-6">
          <NavLink to="/" className="flex items-center gap-2 py-3" aria-label="Budget Tracker home">
            <Logo />
            <span className="hidden text-[15px] font-semibold tracking-tight text-zinc-950 sm:inline">Budget Tracker</span>
          </NavLink>

          {/* Phones: its own full-width row under the logo (scrolls sideways if needed). Wider: inline. */}
          <nav
            aria-label="Main"
            className="-mb-px order-last flex w-full gap-5 overflow-x-auto [scrollbar-width:none] md:order-none md:w-auto md:flex-1 [&::-webkit-scrollbar]:hidden"
          >
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
                <item.icon size={16} aria-hidden="true" />
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-2">
            <ThemeToggle />
            <NavLink
              to="/import"
              className="hidden items-center gap-1.5 rounded-lg bg-zinc-950 px-3 py-2 text-sm font-medium text-white transition-colors duration-150 hover:bg-zinc-800 md:inline-flex"
            >
              <Upload size={16} aria-hidden="true" />
              Upload statement
            </NavLink>
            <NavLink
              to="/account"
              title={user?.email}
              aria-label="Your account"
              className="grid size-9 place-items-center rounded-full border border-zinc-200 text-sm font-medium uppercase text-zinc-700 transition-colors duration-150 hover:bg-zinc-100"
            >
              {user?.email.charAt(0)}
            </NavLink>
            <button
              type="button"
              onClick={handleLogout}
              aria-label="Log out"
              title="Log out"
              className="grid size-9 place-items-center rounded-full text-zinc-600 transition-colors duration-150 hover:bg-zinc-100 hover:text-zinc-900"
            >
              <LogOut size={18} aria-hidden="true" />
            </button>
          </div>
        </div>
      </header>

      {user && !user.emailVerified && <VerifyBanner />}

      <main id="main" tabIndex={-1} className="mx-auto w-full max-w-6xl flex-1 px-6 py-8 outline-none">
        {/* Upload is a button on wide screens; on small ones it lives here, at the top of every page. */}
        <NavLink to="/import" className="mb-6 block text-sm font-medium text-accent underline md:hidden">
          Upload statement
        </NavLink>
        <Outlet />
      </main>

      <footer className="border-t border-zinc-200">
        <div className="mx-auto max-w-6xl px-6 py-3">
          <SystemStatus />
        </div>
      </footer>
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
    <div className="border-b border-indigo-100 bg-indigo-50">
      <div className="mx-auto flex max-w-6xl items-center gap-3 px-6 py-2.5 text-sm text-indigo-900">
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
          className="grid size-7 shrink-0 place-items-center rounded-full hover:bg-indigo-100"
        >
          <X size={14} aria-hidden="true" />
        </button>
      </div>
    </div>
  )
}
