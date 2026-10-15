import { CircleCheck, FileText, FileUp, TriangleAlert } from 'lucide-react'
import { useEffect, useRef, useState, type DragEvent } from 'react'
import { Link } from 'react-router'
import * as api from '../api'
import { ColumnMappingEditor } from '../components/ColumnMappingEditor'
import { ImportHistory } from '../components/ImportHistory'
import { formatDate, formatMoney, formatShortDate } from '../format'
import { startingMapping } from '../mapping'

const MAX_FILE_BYTES = 2 * 1024 * 1024
const NEW_ACCOUNT = 'new'

/**
 * Upload any bank's CSV. Most of the time it's: drop the file, read a one-line summary, click
 * Import. The preview table and column choices are still there, behind "Something look wrong?",
 * and open on their own when the file can't be read cleanly.
 */
export function ImportPage() {
  const [accounts, setAccounts] = useState<api.Account[] | null>(null)
  // Either an account id (as a string, since <select> values are strings) or NEW_ACCOUNT.
  const [accountChoice, setAccountChoice] = useState<string>(NEW_ACCOUNT)
  const [newAccountName, setNewAccountName] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [dragging, setDragging] = useState(false)

  const [preview, setPreview] = useState<api.ImportPreview | null>(null)
  const [mapping, setMapping] = useState<api.ColumnMapping | null>(null)
  // True once the user changes a column: later previews must keep their choices.
  const [customized, setCustomized] = useState(false)
  const [previewing, setPreviewing] = useState(false)
  const [showDetails, setShowDetails] = useState(false)
  // Only the latest preview request may update the page (choices can change faster than responses).
  const latestPreview = useRef(0)

  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<{ imported: api.ImportResult; accountName: string } | null>(null)
  const [imports, setImports] = useState<api.ImportSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [rowErrors, setRowErrors] = useState<string[]>([])

  useEffect(() => {
    api.fetchAccounts()
      .then((list) => {
        setAccounts(list)
        // Default to the first existing account; a brand-new user names one instead.
        if (list.length > 0) setAccountChoice(String(list[0].id))
      })
      .catch(() => setError('Could not load your accounts.'))
    refreshImports()
  }, [])

  function refreshImports() {
    api.fetchImports()
      .then(setImports)
      .catch(() => setError('Could not load your import history.'))
  }

  const accountId = accountChoice === NEW_ACCOUNT ? undefined : Number(accountChoice)

  async function loadPreview(chosen: File, choices: api.ColumnMapping | undefined, forAccount: number | undefined) {
    const request = ++latestPreview.current
    setPreviewing(true)
    try {
      const p = await api.previewStatement(chosen, choices, forAccount)
      if (request !== latestPreview.current) return
      setPreview(p)
      if (!choices) {
        setMapping(p.mapping)
        // Open the details when something needs attention; otherwise keep the page simple.
        setShowDetails(p.mapping === null || p.errors.length > 0 || p.warnings.length > 0)
      }
    } catch (err) {
      if (request !== latestPreview.current) return
      setPreview(null)
      setError(err instanceof api.ApiError ? err.message : 'Could not reach the server. Please try again.')
    } finally {
      if (request === latestPreview.current) setPreviewing(false)
    }
  }

  function handleFileChosen(chosen: File | null) {
    setFile(chosen)
    setPreview(null)
    setMapping(null)
    setCustomized(false)
    setResult(null)
    setError(null)
    setRowErrors([])
    if (!chosen) return
    // Quick checks for instant feedback. The backend checks all of these again: never trust the browser.
    if (!chosen.name.toLowerCase().endsWith('.csv')) {
      setError('That isn’t a CSV file. In your bank’s website, look for “Download” or “Export” and choose CSV.')
      setFile(null)
      return
    }
    if (chosen.size > MAX_FILE_BYTES) {
      setError('That file is larger than 2 MB. Try downloading a shorter date range.')
      setFile(null)
      return
    }
    void loadPreview(chosen, undefined, accountId)
  }

  function handleDrop(event: DragEvent) {
    event.preventDefault()
    setDragging(false)
    handleFileChosen(event.dataTransfer.files?.[0] ?? null)
  }

  function handleAccountChange(value: string) {
    setAccountChoice(value)
    // "Already imported" depends on the account, so ask again.
    if (file) void loadPreview(file, customized ? (mapping ?? undefined) : undefined, value === NEW_ACCOUNT ? undefined : Number(value))
  }

  function handleMappingChange(next: api.ColumnMapping) {
    setMapping(next)
    setCustomized(true)
    if (file) void loadPreview(file, next, accountId)
  }

  async function handleImport() {
    if (!file || !mapping) return
    if (accountChoice === NEW_ACCOUNT && newAccountName.trim() === '') {
      setError('Give this account a name first, like “Chase Freedom”.')
      return
    }
    setSubmitting(true)
    setError(null)
    setRowErrors([])
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
      // Always remember the columns: the next file from this bank is read the same way, automatically.
      const imported = await api.uploadStatement(account.id, file, { mapping, rememberFormat: true })
      setResult({ imported, accountName: account.name })
      setFile(null)
      setPreview(null)
      setMapping(null)
      refreshImports()
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

  async function handleDeleteImport(id: number) {
    setResult(null)
    setError(null)
    setRowErrors([])
    try {
      await api.deleteImport(id)
      setImports((previous) => previous.filter((item) => item.id !== id))
    } catch {
      setError('Could not delete that import. Please try again.')
    }
  }

  if (accounts === null && error === null) {
    return (
      <div className="max-w-2xl space-y-4" aria-busy="true">
        <div className="skeleton h-9 w-64" />
        <div className="skeleton h-56 w-full rounded-lg" />
      </div>
    )
  }

  const readable = !!preview && !!mapping && preview.errors.length === 0 && preview.transactionCount > 0
  const newCount = preview ? preview.transactionCount - (preview.alreadyImported ?? 0) : 0
  const canImport = readable && newCount > 0

  return (
    <div className="max-w-2xl">
      <h1 className="text-3xl font-bold text-stone-950">Upload a statement</h1>
      <p className="mt-2 text-sm text-stone-600">
        Download a CSV from your bank or card&apos;s website and drop it here. The file itself is never stored.
      </p>

      {result ? (
        <ImportDone
          imported={result.imported}
          accountName={result.accountName}
          onAnother={() => setResult(null)}
        />
      ) : (
        <div className="card mt-6 space-y-5 p-6">
          <AccountPicker
            accounts={accounts ?? []}
            choice={accountChoice}
            onChoice={handleAccountChange}
            newName={newAccountName}
            onNewName={setNewAccountName}
          />

          {!file ? (
            // The whole box is the file picker: click it or drop a file on it.
            <label
              onDragOver={(event) => {
                event.preventDefault()
                setDragging(true)
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={handleDrop}
              className={`flex cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed px-6 py-12 text-center transition-colors duration-150 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-accent ${
                dragging ? 'border-accent bg-accent-soft/40' : 'border-stone-300 hover:border-stone-400 hover:bg-stone-50'
              }`}
            >
              <span className="grid size-12 place-items-center rounded-full bg-accent-soft text-accent">
                <FileUp size={22} aria-hidden="true" />
              </span>
              <span className="mt-4 font-medium text-stone-900">
                Drop your CSV here, or <span className="text-accent underline">browse</span>
              </span>
              <span className="mt-1 text-xs text-stone-500">Any bank or card · up to 2 MB</span>
              <input
                type="file"
                accept=".csv,text/csv"
                onChange={(event) => handleFileChosen(event.target.files?.[0] ?? null)}
                className="sr-only"
              />
            </label>
          ) : (
            <div className="space-y-4">
              <div className="flex items-center gap-3 rounded-lg bg-stone-50 px-4 py-3">
                <FileText size={18} className="shrink-0 text-stone-500" aria-hidden="true" />
                <span className="min-w-0 flex-1 truncate text-sm font-medium text-stone-900">{file.name}</span>
                <button
                  type="button"
                  onClick={() => handleFileChosen(null)}
                  className="text-sm text-stone-600 underline hover:text-stone-900"
                >
                  Change file
                </button>
              </div>

              {previewing && !preview && (
                <div className="space-y-2" aria-busy="true" aria-label="Reading the file">
                  <div className="skeleton h-6 w-56" />
                  <div className="skeleton h-4 w-40" />
                </div>
              )}

              {preview && readable && <Summary preview={preview} newCount={newCount} />}

              {preview && !readable && (
                <Problem
                  preview={preview}
                  onStartManual={() => {
                    setShowDetails(true)
                    handleMappingChange(startingMapping(preview.columns.length))
                  }}
                />
              )}

              {preview?.warnings.map((warning) => (
                <p key={warning} className="flex gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
                  <TriangleAlert size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
                  {warning}
                </p>
              ))}

              {preview && (
                <div className="flex flex-wrap items-center gap-4">
                  <button
                    type="button"
                    onClick={handleImport}
                    disabled={!canImport || submitting || previewing}
                    className="rounded-lg bg-accent px-5 py-2.5 text-accent-ink font-semibold transition-colors duration-150 hover:bg-accent-hover disabled:opacity-40"
                  >
                    {submitting ? 'Importing…' : canImport ? `Import ${plural(newCount, 'transaction')}` : 'Import'}
                  </button>
                  {mapping && (
                    <button
                      type="button"
                      onClick={() => setShowDetails((open) => !open)}
                      aria-expanded={showDetails}
                      className="text-sm text-stone-600 underline hover:text-stone-900"
                    >
                      {showDetails ? 'Hide details' : 'Something look wrong?'}
                    </button>
                  )}
                </div>
              )}

              {preview && showDetails && (
                <Details
                  preview={preview}
                  mapping={mapping}
                  onMappingChange={handleMappingChange}
                  updating={previewing}
                />
              )}
            </div>
          )}
        </div>
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

      <h2 className="mt-12 mb-3 text-lg font-bold text-stone-950">Past uploads</h2>
      <ImportHistory imports={imports} onDelete={handleDeleteImport} />
    </div>
  )
}

/** One account: just its name. Several: a dropdown. None yet: a name field. */
function AccountPicker(props: {
  accounts: api.Account[]
  choice: string
  onChoice: (value: string) => void
  newName: string
  onNewName: (value: string) => void
}) {
  if (props.accounts.length === 0) {
    return (
      <label className="block">
        <span className="text-sm font-medium text-stone-700">Name this account</span>
        <input
          type="text"
          value={props.newName}
          onChange={(event) => props.onNewName(event.target.value)}
          placeholder="Chase Freedom"
          maxLength={100}
          className="mt-1.5 w-full rounded-lg border border-stone-300 px-3 py-2.5"
        />
        <span className="mt-1 block text-xs text-stone-500">The card or bank account this statement is from.</span>
      </label>
    )
  }
  return (
    <div className="flex flex-wrap items-center gap-3">
      <label className="flex items-center gap-2 text-sm text-stone-600">
        Importing into
        <select
          value={props.choice}
          onChange={(event) => props.onChoice(event.target.value)}
          className="rounded-lg border border-stone-300 bg-white px-3 py-1.5 text-sm font-medium text-stone-900"
        >
          {props.accounts.map((account) => (
            <option key={account.id} value={String(account.id)}>
              {account.name}
            </option>
          ))}
          <option value={NEW_ACCOUNT}>+ New account…</option>
        </select>
      </label>
      {props.choice === NEW_ACCOUNT && (
        <input
          type="text"
          value={props.newName}
          onChange={(event) => props.onNewName(event.target.value)}
          placeholder="Account name, like Chase Freedom"
          aria-label="New account name"
          maxLength={100}
          autoFocus
          className="min-w-56 flex-1 rounded-lg border border-stone-300 px-3 py-1.5 text-sm"
        />
      )}
    </div>
  )
}

/** The one-line answer to "did it read my file right?". */
function Summary({ preview, newCount }: { preview: api.ImportPreview; newCount: number }) {
  const already = preview.alreadyImported ?? 0
  if (newCount === 0) {
    return (
      <p className="flex gap-2 rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-900">
        <TriangleAlert size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
        All {plural(preview.transactionCount, 'transaction')} in this file are already in this account. Nothing new
        to add.
      </p>
    )
  }
  return (
    <div>
      <p className="font-display text-xl font-bold tracking-tight text-stone-950">
        Found {plural(preview.transactionCount, 'transaction')}
        {preview.firstDate && preview.lastDate && (
          <span className="font-normal text-stone-500"> · {dateRange(preview.firstDate, preview.lastDate)}</span>
        )}
      </p>
      {already > 0 && (
        <p className="mt-1 text-sm text-stone-600">
          {plural(already, 'transaction')} already imported will be skipped, so there are no duplicates.
        </p>
      )}
    </div>
  )
}

/** The file couldn't be read automatically: say why, and offer to pick the columns. */
function Problem({ preview, onStartManual }: { preview: api.ImportPreview; onStartManual: () => void }) {
  const messages = preview.errors.length > 0 ? preview.errors : ['No transactions were found in this file.']
  return (
    <div role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-800">
      <p className="font-medium">We couldn&apos;t read this file automatically.</p>
      <ul className="mt-1 list-disc space-y-1 pl-5">
        {messages.map((m) => (
          <li key={m}>{m}</li>
        ))}
      </ul>
      {preview.columns.length > 0 && (
        <button type="button" onClick={onStartManual} className="mt-2 font-medium underline">
          Show me the columns and I&apos;ll pick
        </button>
      )}
    </div>
  )
}

/** The first rows as they'd be saved, and the column choices. */
function Details({
  preview,
  mapping,
  onMappingChange,
  updating,
}: {
  preview: api.ImportPreview
  mapping: api.ColumnMapping | null
  onMappingChange: (mapping: api.ColumnMapping) => void
  updating: boolean
}) {
  return (
    <section aria-label="Details" className={`space-y-4 border-t border-stone-100 pt-4 ${updating ? 'opacity-60' : ''}`}>
      {preview.transactions.length > 0 && (
        <div>
          <p className="mb-2 text-sm text-stone-600">
            {preview.transactions.length === 1 ? 'The first row' : `The first ${preview.transactions.length} rows`} as
            they&apos;ll be saved. Spending should show as{' '}
            <span className="font-medium">−</span>, refunds and payments as{' '}
            <span className="font-medium text-accent">+</span>.
            {preview.skippedRows > 0 &&
              ` ${plural(preview.skippedRows, 'row')} without an amount (like balance lines) will be left out.`}
          </p>
          <div className="overflow-x-auto rounded-lg border border-stone-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-stone-50 text-xs uppercase text-stone-500">
                <tr>
                  <th className="px-3 py-2 font-medium">Date</th>
                  <th className="px-3 py-2 font-medium">Description</th>
                  <th className="px-3 py-2 text-right font-medium">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {preview.transactions.map((t, i) => (
                  <tr key={i}>
                    <td className="whitespace-nowrap px-3 py-2 text-stone-600">{formatDate(t.transactionDate)}</td>
                    <td className="px-3 py-2 text-stone-900">{t.description}</td>
                    <td
                      className={`whitespace-nowrap px-3 py-2 text-right tabular-nums ${
                        t.amount < 0 ? 'text-stone-900' : 'text-accent'
                      }`}
                    >
                      {formatMoney(t.amount, { signed: true })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {mapping && (
        <div className="rounded-lg border border-stone-200 p-4">
          <p className="mb-3 text-sm font-medium text-stone-900">Which column is which</p>
          <ColumnMappingEditor columns={preview.columns} mapping={mapping} onChange={onMappingChange} />
        </div>
      )}
    </section>
  )
}

/** After importing: what happened, and the obvious next step. */
function ImportDone({
  imported,
  accountName,
  onAnother,
}: {
  imported: api.ImportResult
  accountName: string
  onAnother: () => void
}) {
  const nothingNew = imported.importedCount === 0
  return (
    <div role="status" className="card mt-6 p-6">
      <div className="flex gap-3">
        <span
          className={`grid size-10 shrink-0 place-items-center rounded-full ${
            nothingNew ? 'bg-amber-50 text-amber-700' : 'bg-accent-soft text-accent'
          }`}
        >
          {nothingNew ? <TriangleAlert size={20} aria-hidden="true" /> : <CircleCheck size={20} aria-hidden="true" />}
        </span>
        <div>
          <p className="font-display text-xl font-bold tracking-tight text-stone-950">
            {nothingNew
              ? 'Nothing new in that file'
              : `${plural(imported.importedCount, 'transaction')} added to ${accountName}`}
          </p>
          <p className="mt-1 text-sm text-stone-600">
            {nothingNew
              ? `All ${plural(imported.skippedCount, 'transaction')} were already imported.`
              : imported.uncategorizedCount > 0
                ? `${plural(imported.uncategorizedCount, 'transaction')} from merchants we don't recognize ${
                    imported.uncategorizedCount === 1 ? 'needs' : 'need'
                  } a category. Everything else was sorted automatically.`
                : 'Everything was sorted into categories automatically.'}
            {!nothingNew && imported.skippedCount > 0 && ` ${imported.skippedCount} already imported were skipped.`}
          </p>
        </div>
      </div>
      <div className="mt-5 flex flex-wrap gap-3">
        {!nothingNew && imported.uncategorizedCount > 0 ? (
          <Link
            to="/transactions?category=uncategorized&month=all"
            className="rounded-lg bg-stone-950 px-4 py-2 text-sm font-medium text-white transition-colors duration-150 hover:bg-stone-800"
          >
            Review {imported.uncategorizedCount}
          </Link>
        ) : (
          <Link
            to="/"
            className="rounded-lg bg-stone-950 px-4 py-2 text-sm font-medium text-white transition-colors duration-150 hover:bg-stone-800"
          >
            View dashboard
          </Link>
        )}
        <button
          type="button"
          onClick={onAnother}
          className="rounded-lg border border-stone-300 px-4 py-2 text-sm text-stone-700 transition-colors duration-150 hover:bg-stone-50"
        >
          Upload another
        </button>
      </div>
    </div>
  )
}

/** "2026-04-01", "2026-09-28" -> "Apr 1 – Sep 28, 2026" */
function dateRange(first: string, last: string): string {
  if (first === last) return formatDate(first)
  const sameYear = first.slice(0, 4) === last.slice(0, 4)
  return `${sameYear ? formatShortDate(first) : formatDate(first)} – ${formatDate(last)}`
}

function plural(count: number, word: string) {
  return `${count} ${word}${count === 1 ? '' : 's'}`
}
