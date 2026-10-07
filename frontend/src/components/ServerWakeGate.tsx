import { useEffect, useState, type ReactNode } from 'react'
import { fetchHealth } from '../api'

const RETRY_MS = 3000
const SHOW_MESSAGE_AFTER_MS = 1500
const STILL_WAITING_AFTER_MS = 90_000

/**
 * The free backend host sleeps after 15 minutes without traffic and takes up to about a minute
 * to wake. Until /api/health answers, show a friendly message instead of a broken-looking app.
 * When the server is already awake (or in local dev), this passes straight through.
 */
export function ServerWakeGate({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false)
  const [elapsedMs, setElapsedMs] = useState(0)

  useEffect(() => {
    let cancelled = false
    const startedAt = Date.now()
    const ticker = setInterval(() => setElapsedMs(Date.now() - startedAt), 500)

    async function waitForServer() {
      while (!cancelled) {
        try {
          await fetchHealth() // resolves once the backend answers at all (UP or DOWN)
          if (!cancelled) setReady(true)
          return
        } catch {
          // Not up yet (e.g. 502/504 from the proxy while it boots). Try again shortly.
          await new Promise((resolve) => setTimeout(resolve, RETRY_MS))
        }
      }
    }
    waitForServer().finally(() => clearInterval(ticker))

    return () => {
      cancelled = true
      clearInterval(ticker)
    }
  }, [])

  if (ready) return children
  // Don't flash a message when the server answers quickly.
  if (elapsedMs < SHOW_MESSAGE_AFTER_MS) return null

  return (
    <main className="min-h-screen bg-slate-50 flex items-center justify-center p-6">
      <div role="status" className="w-full max-w-sm rounded-xl bg-white p-6 text-center shadow">
        <div className="mx-auto h-8 w-8 animate-spin rounded-full border-4 border-slate-200 border-t-slate-900" />
        <h1 className="mt-4 text-lg font-semibold text-slate-900">Starting the server…</h1>
        <p className="mt-2 text-sm text-slate-600">
          {elapsedMs < STILL_WAITING_AFTER_MS
            ? 'This app runs on free hosting that sleeps when nobody is using it. Waking up can take up to a minute.'
            : 'This is taking longer than usual. You can keep waiting or try refreshing the page.'}
        </p>
      </div>
    </main>
  )
}
