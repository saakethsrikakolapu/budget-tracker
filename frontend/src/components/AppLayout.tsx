import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import { SystemStatus } from './SystemStatus'

const NAV = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/transactions', label: 'Transactions' },
  { to: '/budgets', label: 'Budgets' },
  { to: '/subscriptions', label: 'Subscriptions' },
  { to: '/categories', label: 'Categories' },
]

/** Header + footer around every logged-in page. The current page renders at <Outlet />. */
export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  // Active page: dark text with an accent bar underneath; others are muted until hovered.
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `relative whitespace-nowrap py-4 text-sm transition-colors duration-150 ${
      isActive
        ? 'font-medium text-zinc-950 after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:bg-accent'
        : 'text-zinc-500 hover:text-zinc-900'
    }`

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-10 border-b border-zinc-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-8 px-6">
          <NavLink to="/" className="flex items-center gap-2 py-3" aria-label="Budget Tracker home">
            <span aria-hidden="true" className="grid size-7 place-items-center rounded-md bg-zinc-950 text-xs font-bold text-white">
              B
            </span>
            <span className="hidden text-[15px] font-semibold tracking-tight text-zinc-950 sm:inline">Budget Tracker</span>
          </NavLink>

          {/* Phones: its own full-width row under the logo (scrolls sideways if needed). Wider: inline. */}
          <nav
            aria-label="Main"
            className="-mb-px order-last flex w-full gap-6 overflow-x-auto [scrollbar-width:none] md:order-none md:w-auto md:flex-1 [&::-webkit-scrollbar]:hidden"
          >
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-3">
            <NavLink
              to="/import"
              className="hidden rounded-lg bg-zinc-950 px-3 py-2 text-sm font-medium text-white transition-colors duration-150 hover:bg-zinc-800 md:inline-block"
            >
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
              className="text-sm text-zinc-500 transition-colors duration-150 hover:text-zinc-900"
            >
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-8">
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
