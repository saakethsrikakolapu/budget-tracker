import { useState, type FormEvent } from 'react'
import * as api from '../api'
import { useAuth } from '../auth/authContext'

/** Your account: who you're logged in as, and deleting the account with all its data. */
export function AccountPage() {
  const { user, deleteAccount } = useAuth()
  const [confirming, setConfirming] = useState(false)
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)

  async function handleDelete(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setDeleting(true)
    try {
      // Logged out afterwards, so RequireAuth takes us to the login page, which shows a confirmation.
      await deleteAccount(password)
    } catch (err) {
      setError(err instanceof api.ApiError ? (err.fieldErrors.password ?? err.message) : 'Could not delete the account.')
      setDeleting(false)
    }
  }

  return (
    <div className="max-w-xl space-y-5">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight text-zinc-950">Account</h1>
        <p className="mt-1 text-sm text-zinc-600">Logged in as {user?.email}</p>
      </div>

      <section className="rounded-xl border border-red-200 bg-white p-5">
        <h2 className="font-semibold text-zinc-900">Delete account</h2>
        <p className="mt-1 text-sm text-zinc-600">
          Permanently deletes your account and everything in it: statements, transactions, categories, rules,
          budgets, and subscriptions. This can't be undone.
        </p>

        {!confirming ? (
          <button
            type="button"
            onClick={() => setConfirming(true)}
            className="mt-4 rounded-lg border border-red-300 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-50"
          >
            Delete my account…
          </button>
        ) : (
          <form onSubmit={handleDelete} className="mt-4 space-y-3">
            <label className="block">
              <span className="text-sm font-medium text-zinc-700">Enter your password to confirm</span>
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoFocus
                className="mt-1 block w-full rounded-lg border border-zinc-300 px-3 py-2"
              />
            </label>
            {error && (
              <p role="alert" className="text-sm text-red-700">
                {error}
              </p>
            )}
            <div className="flex gap-3">
              <button
                type="submit"
                disabled={deleting || password === ''}
                className="rounded-lg bg-red-600 px-3 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
              >
                {deleting ? 'Deleting…' : 'Permanently delete everything'}
              </button>
              <button
                type="button"
                onClick={() => {
                  setConfirming(false)
                  setPassword('')
                  setError(null)
                }}
                className="rounded-lg border border-zinc-300 px-3 py-2 text-sm hover:bg-zinc-100"
              >
                Cancel
              </button>
            </div>
          </form>
        )}
      </section>
    </div>
  )
}
