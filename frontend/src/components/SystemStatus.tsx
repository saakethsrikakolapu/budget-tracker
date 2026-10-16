import { useEffect, useState } from 'react'
import { fetchHealth } from '../api'

/**
 * Stays out of the way while everything works. If the backend can't be reached, or it answers but
 * its database is down (GET /api/health), a banner says so instead of pages just failing.
 */
export function SystemStatus() {
  const [problem, setProblem] = useState<'server' | 'database' | null>(null)

  useEffect(() => {
    fetchHealth()
      .then((health) => setProblem(health.database === 'UP' ? null : 'database'))
      .catch(() => setProblem('server'))
  }, [])

  if (problem === null) return null
  return (
    <div role="alert" className="border-b border-red-200 bg-red-50">
      <p className="mx-auto max-w-6xl px-4 py-2.5 text-sm text-red-800 sm:px-6">
        {problem === 'server'
          ? "Spendr can't reach its server right now. Your data is safe; try again in a minute."
          : 'Spendr is having trouble with its database right now. Your data is safe; try again in a minute.'}
      </p>
    </div>
  )
}
