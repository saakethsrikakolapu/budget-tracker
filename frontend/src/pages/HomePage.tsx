import { useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import { SystemStatus } from '../components/SystemStatus'

/** Placeholder home page; transactions replace this in Stage 1, piece 5. */
export function HomePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
        <span className="text-lg font-semibold text-slate-900">Budget Tracker</span>
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
        <h1 className="text-2xl font-semibold text-slate-900">Welcome, {user?.email}</h1>
        <p className="mt-2 text-slate-600">Uploading statements is coming next.</p>
      </main>

      <footer className="border-t border-slate-200 bg-white px-6 py-3">
        <SystemStatus />
      </footer>
    </div>
  )
}
