import { Download } from 'lucide-react'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import * as api from '../api'
import { useAuth } from '../auth/authContext'

/**
 * Your account: bank accounts, the monthly reminder email, password, downloading your data, and
 * deleting everything.
 */
export function AccountPage() {
  const { user } = useAuth()
  return (
    <div className="max-w-2xl space-y-8">
      <div>
        <h1 className="text-3xl font-bold text-stone-950">Account</h1>
        <p className="mt-1 text-sm text-stone-600">Logged in as {user?.email}</p>
      </div>
      <BankAccounts />
      <Reminders />
      <ChangePassword />
      <YourData />
      <DeleteAccount />
    </div>
  )
}

function Section({ title, children, danger = false }: { title: string; children: ReactNode; danger?: boolean }) {
  return (
    <section className={`border-t pt-6 ${danger ? 'border-red-300' : 'border-stone-200'}`}>
      <h2 className="text-lg font-bold text-stone-950">{title}</h2>
      {children}
    </section>
  )
}

/** The bank and card accounts statements are uploaded to: rename or remove them. */
function BankAccounts() {
  const [accounts, setAccounts] = useState<api.Account[] | null>(null)
  const [editing, setEditing] = useState<number | null>(null)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.fetchAccounts()
      .then(setAccounts)
      .catch(() => setError('Could not load your accounts.'))
  }, [])

  async function handleRename(event: FormEvent, account: api.Account) {
    event.preventDefault()
    setError(null)
    try {
      const updated = await api.renameAccount(account.id, name)
      setAccounts((list) => list && list.map((a) => (a.id === updated.id ? updated : a)))
      setEditing(null)
    } catch (err) {
      setError(err instanceof api.ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : 'Could not rename.')
    }
  }

  async function handleRemove(account: api.Account) {
    if (!window.confirm(`Remove "${account.name}" and every transaction uploaded to it? This can't be undone.`)) return
    setError(null)
    try {
      await api.removeAccount(account.id)
      setAccounts((list) => list && list.filter((a) => a.id !== account.id))
    } catch {
      setError('Could not remove the account.')
    }
  }

  return (
    <Section title="Bank accounts">
      <p className="mt-1 text-sm text-stone-600">Where your statements go. Removing one also removes its transactions.</p>
      {error && (
        <p role="alert" className="mt-3 text-sm text-red-700">
          {error}
        </p>
      )}
      {accounts !== null && accounts.length === 0 && (
        <p className="mt-3 text-sm text-stone-500">None yet. One is created when you upload your first statement.</p>
      )}
      <ul className="mt-3 divide-y divide-stone-100">
        {accounts?.map((account) => (
          <li key={account.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
            {editing === account.id ? (
              <form onSubmit={(event) => handleRename(event, account)} className="flex flex-1 flex-wrap items-center gap-2">
                <input
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  maxLength={100}
                  autoFocus
                  aria-label={`New name for ${account.name}`}
                  className="min-h-9 min-w-0 flex-1 rounded-md border border-stone-300 bg-white px-2 py-1.5 text-sm"
                />
                <button type="submit" className="text-sm font-medium text-stone-900">
                  Save
                </button>
                <button type="button" onClick={() => setEditing(null)} className="text-sm text-stone-500">
                  Cancel
                </button>
              </form>
            ) : (
              <>
                <span className="font-medium text-stone-900">{account.name}</span>
                <span className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setName(account.name)
                      setEditing(account.id)
                    }}
                    className="min-h-9 rounded-lg border border-stone-300 px-3 py-1.5 text-sm hover:bg-stone-100"
                  >
                    Rename
                  </button>
                  <button
                    type="button"
                    onClick={() => handleRemove(account)}
                    className="min-h-9 rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50"
                  >
                    Remove
                  </button>
                </span>
              </>
            )}
          </li>
        ))}
      </ul>
    </Section>
  )
}

/** The opt-in monthly "time to upload your statement" email. */
function Reminders() {
  const { user, refreshUser } = useAuth()
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const on = user?.uploadReminders ?? false

  async function toggle() {
    setSaving(true)
    setError(null)
    try {
      await api.setUploadReminders(!on)
      await refreshUser()
    } catch {
      setError('Could not save that. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Section title="Monthly reminder">
      <div className="mt-2 flex items-start justify-between gap-4">
        <p className="text-sm text-stone-600">
          An email early each month when it&apos;s time to upload last month&apos;s statement. Only if you haven&apos;t
          already, and never more than once a month.
        </p>
        <button
          type="button"
          role="switch"
          aria-checked={on}
          aria-label="Monthly reminder email"
          onClick={toggle}
          disabled={saving}
          className={`relative h-7 w-12 shrink-0 rounded-full transition-colors duration-150 disabled:opacity-60 ${
            on ? 'bg-accent' : 'bg-stone-300'
          }`}
        >
          <span
            aria-hidden="true"
            className={`absolute top-1 left-1 size-5 rounded-full bg-white shadow transition-transform duration-150 ${
              on ? 'translate-x-5' : ''
            }`}
          />
        </button>
      </div>
      {on && user && !user.emailVerified && (
        <p className="mt-2 text-sm text-amber-800">
          Verify your email first: reminders only go to confirmed addresses. The link is in your inbox.
        </p>
      )}
      {error && (
        <p role="alert" className="mt-2 text-sm text-red-700">
          {error}
        </p>
      )}
    </Section>
  )
}

function ChangePassword() {
  const [open, setOpen] = useState(false)
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.changePassword(current, next)
      setDone(true)
      setOpen(false)
      setCurrent('')
      setNext('')
    } catch (err) {
      setError(
        err instanceof api.ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : 'Could not change it.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <Section title="Password">
      {done && !open && (
        <p role="status" className="mt-2 text-sm text-stone-700">
          Password changed. Other devices were logged out; this one stays logged in.
        </p>
      )}
      {!open ? (
        <button
          type="button"
          onClick={() => {
            setOpen(true)
            setDone(false)
          }}
          className="mt-3 rounded-lg border border-stone-300 px-3 py-2 text-sm font-medium hover:bg-stone-100"
        >
          Change password
        </button>
      ) : (
        <form onSubmit={handleSubmit} className="mt-3 space-y-3">
          <label className="block">
            <span className="text-sm font-medium text-stone-700">Current password</span>
            <input
              type="password"
              autoComplete="current-password"
              value={current}
              onChange={(event) => setCurrent(event.target.value)}
              autoFocus
              className="mt-1 block w-full rounded-lg border border-stone-300 bg-white px-3 py-2"
            />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-stone-700">New password</span>
            <input
              type="password"
              autoComplete="new-password"
              value={next}
              onChange={(event) => setNext(event.target.value)}
              className="mt-1 block w-full rounded-lg border border-stone-300 bg-white px-3 py-2"
            />
            <span className="mt-1 block text-xs text-stone-500">At least 8 characters.</span>
          </label>
          {error && (
            <p role="alert" className="text-sm text-red-700">
              {error}
            </p>
          )}
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={busy || current === '' || next === ''}
              className="rounded-lg bg-stone-950 px-3 py-2 text-sm font-medium text-white hover:bg-stone-800 disabled:opacity-50"
            >
              {busy ? 'Saving…' : 'Save new password'}
            </button>
            <button
              type="button"
              onClick={() => {
                setOpen(false)
                setError(null)
              }}
              className="rounded-lg border border-stone-300 px-3 py-2 text-sm hover:bg-stone-100"
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </Section>
  )
}

/** Download every transaction as a CSV: your data is yours to take anywhere. */
function YourData() {
  return (
    <Section title="Your data">
      <p className="mt-1 text-sm text-stone-600">
        Every transaction with its category and account, as a CSV that opens in Excel or Google Sheets.
      </p>
      <a
        href={api.EXPORT_URL}
        download
        className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-stone-300 px-3 py-2 text-sm font-medium hover:bg-stone-100"
      >
        <Download size={16} aria-hidden="true" />
        Download my data
      </a>
    </Section>
  )
}

function DeleteAccount() {
  const { deleteAccount } = useAuth()
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
    <Section title="Delete account" danger>
      <p className="mt-1 text-sm text-stone-600">
        Permanently deletes your account and everything in it: statements, transactions, sorting choices, budgets,
        and subscriptions. This can&apos;t be undone.
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
            <span className="text-sm font-medium text-stone-700">Enter your password to confirm</span>
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoFocus
              className="mt-1 block w-full rounded-lg border border-stone-300 bg-white px-3 py-2"
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
              className="rounded-lg border border-stone-300 px-3 py-2 text-sm hover:bg-stone-100"
            >
              Cancel
            </button>
          </div>
        </form>
      )}
    </Section>
  )
}
