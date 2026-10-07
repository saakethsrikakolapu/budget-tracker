import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import { SystemStatus } from './SystemStatus'

/** Header + footer around every logged-in page. The current page renders at <Outlet />. */
export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `text-sm ${isActive ? 'font-semibold text-slate-900' : 'text-slate-600 hover:text-slate-900'}`

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 bg-white px-6 py-4">
        <div className="flex items-center gap-6">
          <span className="text-lg font-semibold text-slate-900">Budget Tracker</span>
          <nav className="flex gap-4">
            <NavLink to="/" end className={linkClass}>
              Transactions
            </NavLink>
            <NavLink to="/import" className={linkClass}>
              Upload statement
            </NavLink>
          </nav>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-sm text-slate-600">{user?.email}</span>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100"
          >
            Log out
          </button>
        </div>
      </header>

      <main className="flex-1 p-6">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white px-6 py-3">
        <SystemStatus />
      </footer>
    </div>
  )
}
