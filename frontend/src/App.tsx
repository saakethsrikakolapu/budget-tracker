import { useEffect, useState } from 'react'
import { fetchHealth, type HealthResponse } from './api'

type HealthState =
  | { kind: 'loading' }
  | { kind: 'loaded'; health: HealthResponse }
  | { kind: 'error'; message: string }

function App() {
  const [state, setState] = useState<HealthState>({ kind: 'loading' })

  // Runs once after the page first renders: ask the backend if it's healthy.
  useEffect(() => {
    fetchHealth()
      .then((health) => setState({ kind: 'loaded', health }))
      .catch(() => setState({ kind: 'error', message: 'Backend is not reachable. Is it running on port 8080?' }))
  }, [])

  return (
    <main className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
      <div className="w-full max-w-sm rounded-xl bg-white p-6 shadow">
        <h1 className="text-2xl font-semibold text-slate-900">Budget Tracker</h1>
        <p className="mt-1 text-sm text-slate-500">System status</p>

        <div className="mt-6 space-y-3">
          {state.kind === 'loading' && <p className="text-slate-500">Checking…</p>}
          {state.kind === 'error' && <p className="text-red-600">{state.message}</p>}
          {state.kind === 'loaded' && (
            <>
              {/* Any answer at all means the backend is running, even if it reports DOWN overall. */}
              <StatusRow label="Backend" up={true} />
              <StatusRow label="Database" up={state.health.database === 'UP'} />
            </>
          )}
        </div>
      </div>
    </main>
  )
}

function StatusRow({ label, up }: { label: string; up: boolean }) {
  return (
    <div className="flex items-center justify-between rounded-lg border border-slate-200 px-4 py-3">
      <span className="text-slate-700">{label}</span>
      <span className={up ? 'font-medium text-green-600' : 'font-medium text-red-600'}>
        {up ? 'UP' : 'DOWN'}
      </span>
    </div>
  )
}

export default App
