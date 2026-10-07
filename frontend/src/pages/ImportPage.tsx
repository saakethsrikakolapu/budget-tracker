import { useEffect, useState, type FormEvent } from 'react'
import * as api from '../api'

const MAX_FILE_BYTES = 2 * 1024 * 1024
const NEW_ACCOUNT = 'new'

/** Upload a Capital One credit card CSV into one of your accounts. */
export function ImportPage() {
  const [accounts, setAccounts] = useState<api.Account[] | null>(null)
  // Either an account id (as a string, since <select> values are strings) or NEW_ACCOUNT.
  const [accountChoice, setAccountChoice] = useState<string>(NEW_ACCOUNT)
  const [newAccountName, setNewAccountName] = useState('')
  const [file, setFile] = useState<File | null>(null)
  // Changing this key resets the <input type="file"> after a successful upload.
  const [fileInputKey, setFileInputKey] = useState(0)

  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<{ count: number; fileName: string; accountName: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [rowErrors, setRowErrors] = useState<string[]>([])

  useEffect(() => {
    api.fetchAccounts()
      .then((list) => {
        setAccounts(list)
        // Default to the first existing account; offer "new" only if there are none.
        if (list.length > 0) {
          setAccountChoice(String(list[0].id))
        }
      })
      .catch(() => setError('Could not load your accounts.'))
  }, [])

  // Quick checks for instant feedback. The backend checks all of these again: never trust the browser.
  function checkFile(): string | null {
    if (!file) return 'Choose a CSV file to upload.'
    if (!file.name.toLowerCase().endsWith('.csv')) return 'Only .csv files can be imported.'
    if (file.size > MAX_FILE_BYTES) return 'The file is larger than 2 MB.'
    if (accountChoice === NEW_ACCOUNT && newAccountName.trim() === '') return 'Enter a name for the new account.'
    return null
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setResult(null)
    setError(null)
    setRowErrors([])

    const problem = checkFile()
    if (problem) {
      setError(problem)
      return
    }

    setSubmitting(true)
    try {
      let account: api.Account
      if (accountChoice === NEW_ACCOUNT) {
        account = await api.createAccount(newAccountName.trim())
        setAccounts((previous) => [...(previous ?? []), account].sort((a, b) => a.name.localeCompare(b.name)))
        setAccountChoice(String(account.id))
        setNewAccountName('')
      } else {
        account = accounts!.find((a) => String(a.id) === accountChoice)!
      }

      const imported = await api.uploadStatement(account.id, file!)
      setResult({ count: imported.importedCount, fileName: imported.fileName, accountName: account.name })
      setFile(null)
      setFileInputKey((key) => key + 1)
    } catch (err) {
      if (err instanceof api.ApiError) {
        setError(err.fieldErrors.name ?? err.message)
        setRowErrors(err.rowErrors)
      } else {
        setError('Could not reach the server. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (accounts === null && error === null) {
    return <p className="text-slate-500">Loading…</p>
  }

  return (
    <div className="max-w-xl">
      <h1 className="text-2xl font-semibold text-slate-900">Upload a statement</h1>
      <p className="mt-2 text-sm text-slate-600">
        Supported: Capital One credit card CSV exports. Your file is read once and never stored; only the
        transactions are saved.
      </p>

      <form onSubmit={handleSubmit} className="mt-6 space-y-5 rounded-xl bg-white p-6 shadow">
        <label className="block">
          <span className="text-sm font-medium text-slate-700">Account</span>
          <select
            value={accountChoice}
            onChange={(event) => setAccountChoice(event.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2"
          >
            {accounts?.map((account) => (
              <option key={account.id} value={String(account.id)}>
                {account.name}
              </option>
            ))}
            <option value={NEW_ACCOUNT}>+ New account…</option>
          </select>
        </label>

        {accountChoice === NEW_ACCOUNT && (
          <label className="block">
            <span className="text-sm font-medium text-slate-700">New account name</span>
            <input
              type="text"
              value={newAccountName}
              onChange={(event) => setNewAccountName(event.target.value)}
              placeholder="e.g. Capital One Quicksilver"
              maxLength={100}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            />
          </label>
        )}

        <label className="block">
          <span className="text-sm font-medium text-slate-700">CSV file</span>
          <input
            key={fileInputKey}
            type="file"
            accept=".csv,text/csv"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            className="mt-1 block w-full text-sm text-slate-700 file:mr-3 file:rounded-lg file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-sm file:font-medium hover:file:bg-slate-200"
          />
        </label>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-lg bg-slate-900 py-2 font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {submitting ? 'Importing…' : 'Import'}
        </button>
      </form>

      {result && (
        <p role="status" className="mt-4 rounded-lg bg-green-50 px-4 py-3 text-green-800">
          Imported {result.count} transaction{result.count === 1 ? '' : 's'} from {result.fileName} into{' '}
          {result.accountName}.
        </p>
      )}

      {error && (
        <div role="alert" className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-red-800">
          <p className="font-medium">{error}</p>
          {rowErrors.length > 0 && (
            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm">
              {rowErrors.map((rowError) => (
                <li key={rowError}>{rowError}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
