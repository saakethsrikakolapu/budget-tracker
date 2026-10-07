import { useEffect, useState } from 'react'
import { fetchHealth, type HealthResponse } from '../api'

type HealthState =
  | { kind: 'loading' }
  | { kind: 'loaded'; health: HealthResponse }
  | { kind: 'error' }

/** One-line backend/database status, from GET /api/health. */
export function SystemStatus() {
  const [state, setState] = useState<HealthState>({ kind: 'loading' })

  useEffect(() => {
    fetchHealth()
      .then((health) => setState({ kind: 'loaded', health }))
      .catch(() => setState({ kind: 'error' }))
  }, [])

  return (
    <p className="text-xs text-slate-500">
      {state.kind === 'loading' && 'Checking system status…'}
      {state.kind === 'error' && <span className="text-red-600">Backend not reachable</span>}
      {state.kind === 'loaded' && (
        <>
          {/* Any answer at all means the backend is running, even if it reports DOWN overall. */}
          Backend <Dot up /> · Database <Dot up={state.health.database === 'UP'} />
        </>
      )}
    </p>
  )
}

function Dot({ up }: { up: boolean }) {
  return <span className={up ? 'text-green-600' : 'text-red-600'}>{up ? 'UP' : 'DOWN'}</span>
}
