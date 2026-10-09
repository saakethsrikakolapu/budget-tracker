import { useEffect, useRef, useState } from 'react'
import * as api from '../api'
import { ColumnMappingEditor } from '../components/ColumnMappingEditor'
import { ImportHistory } from '../components/ImportHistory'
import { formatDate, formatMoney } from '../format'
import { startingMapping } from '../mapping'

const MAX_FILE_BYTES = 2 * 1024 * 1024
const NEW_ACCOUNT = 'new'

/**
 * Upload any bank's CSV. Choosing a file shows a preview of how it will be read (columns are
 * worked out from the values); adjust the columns if needed, then import.
 */
export function ImportPage() {
  const [accounts, setAccounts] = useState<api.Account[] | null>(null)
  // Either an account id (as a string, since <select> values are strings) or NEW_ACCOUNT.
  const [accountChoice, setAccountChoice] = useState<string>(NEW_ACCOUNT)
  const [newAccountName, setNewAccountName] = useState('')
  const [file, setFile] = useState<File | null>(null)
  // Changing this key resets the <input type="file"> after a successful upload.
  const [fileInputKey, setFileInputKey] = useState(0)

  const [preview, setPreview] = useState<api.ImportPreview | null>(null)
  const [mapping, setMapping] = useState<api.ColumnMapping | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [showColumns, setShowColumns] = useState(false)
  const [rememberFormat, setRememberFormat] = useState(true)
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
        // Default to the first existing account; offer "new" only if there are none.
        if (list.length > 0) {
          setAccountChoice(String(list[0].id))
        }
      })
      .catch(() => setError('Could not load your accounts.'))
    refreshImports()
  }, [])

  function refreshImports() {
    api.fetchImports()
      .then(setImports)
      .catch(() => setError('Could not load your import history.'))
  }

  async function loadPreview(chosen: File, choices?: api.ColumnMapping) {
    const request = ++latestPreview.current
    setPreviewing(true)
    try {
      const p = await api.previewStatement(chosen, choices)
      if (request !== latestPreview.current) return
      setPreview(p)
      if (!choices) {
        setMapping(p.mapping)
        // Open the column choices when something needs attention.
        setShowColumns(p.mapping === null || p.errors.length > 0 || p.warnings.length > 0)
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
    setResult(null)
    setError(null)
    setRowErrors([])
    if (!chosen) return
    // Quick checks for instant feedback. The backend checks all of these again: never trust the browser.
    if (!chosen.name.toLowerCase().endsWith('.csv')) {
      setError('Only .csv files can be imported.')
      return
    }
    if (chosen.size > MAX_FILE_BYTES) {
      setError('The file is larger than 2 MB.')
      return
    }
    void loadPreview(chosen)
  }

  function handleMappingChange(next: api.ColumnMapping) {
    setMapping(next)
    if (file) void loadPreview(file, next)
  }

  async function handleImport() {
    if (!file || !mapping) return
    if (accountChoice === NEW_ACCOUNT && newAccountName.trim() === '') {
      setError('Enter a name for the new account.')
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

      const imported = await api.uploadStatement(account.id, file, { mapping, rememberFormat })
      setResult({ imported, accountName: account.name })
      setFile(null)
      setPreview(null)
      setMapping(null)
      setFileInputKey((key) => key + 1)
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
    return <p className="text-slate-500">Loading…</p>
  }

  const canImport = !!preview && !!mapping && preview.errors.length === 0 && preview.transactionCount > 0

  return (
    <div className="max-w-3xl">
      <h1 className="text-2xl font-semibold text-slate-900">Upload a statement</h1>
      <p className="mt-2 text-sm text-slate-600">
        Any bank or card&apos;s CSV export works: the columns are worked out automatically and you can check them
        before importing. Your file is read once and never stored; only the transactions are saved.
      </p>

      <div className="mt-6 space-y-5 rounded-xl bg-white p-6 shadow">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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
                placeholder="e.g. Chase Freedom"
                maxLength={100}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              />
            </label>
          )}
        </div>

        <label className="block">
          <span className="text-sm font-medium text-slate-700">CSV file</span>
          <input
            key={fileInputKey}
            type="file"
            accept=".csv,text/csv"
            onChange={(event) => handleFileChosen(event.target.files?.[0] ?? null)}
            className="mt-1 block w-full text-sm text-slate-700 file:mr-3 file:rounded-lg file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-sm file:font-medium hover:file:bg-slate-200"
          />
        </label>

        {previewing && !preview && <p className="text-sm text-slate-500">Reading the file…</p>}

        {preview && (
          <PreviewPanel
            preview={preview}
            mapping={mapping}
            showColumns={showColumns}
            onToggleColumns={() => setShowColumns((open) => !open)}
            onMappingChange={handleMappingChange}
            onStartManual={() => {
              setShowColumns(true)
              handleMappingChange(startingMapping(preview.columns.length))
            }}
            updating={previewing}
          />
        )}

        {preview && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={rememberFormat}
                onChange={(event) => setRememberFormat(event.target.checked)}
              />
              Remember these columns for files like this
            </label>
            <button
              type="button"
              onClick={handleImport}
              disabled={!canImport || submitting || previewing}
              className="rounded-lg bg-slate-900 px-5 py-2 font-medium text-white hover:bg-slate-700 disabled:opacity-50"
            >
              {submitting
                ? 'Importing…'
                : canImport
                  ? `Import ${preview.transactionCount} transaction${preview.transactionCount === 1 ? '' : 's'}`
                  : 'Import'}
            </button>
          </div>
        )}
      </div>

      {result && <ImportResultMessage imported={result.imported} accountName={result.accountName} />}

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

      <h2 className="mt-10 mb-3 text-lg font-semibold text-slate-900">Import history</h2>
      <ImportHistory imports={imports} onDelete={handleDeleteImport} />
    </div>
  )
}

const SOURCE_TEXT = {
  DETECTED: 'Columns worked out automatically. Check the preview below.',
  SAVED: 'Using the columns you saved for files like this.',
  CUSTOM: 'Using your column choices.',
}

function PreviewPanel({
  preview,
  mapping,
  showColumns,
  onToggleColumns,
  onMappingChange,
  onStartManual,
  updating,
}: {
  preview: api.ImportPreview
  mapping: api.ColumnMapping | null
  showColumns: boolean
  onToggleColumns: () => void
  onMappingChange: (mapping: api.ColumnMapping) => void
  onStartManual: () => void
  updating: boolean
}) {
  return (
    <section aria-label="Preview" className={`space-y-4 ${updating ? 'opacity-60' : ''}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-slate-700">{preview.source ? SOURCE_TEXT[preview.source] : 'Preview'}</p>
        {mapping && (
          <button type="button" onClick={onToggleColumns} className="text-sm text-slate-600 underline">
            {showColumns ? 'Hide column choices' : 'Adjust columns'}
          </button>
        )}
      </div>

      {preview.warnings.map((warning) => (
        <p key={warning} className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
          {warning}
        </p>
      ))}

      {preview.errors.length > 0 && (
        <div role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">
          <ul className="list-disc space-y-1 pl-5">
            {preview.errors.map((e) => (
              <li key={e}>{e}</li>
            ))}
          </ul>
          {!mapping && preview.columns.length > 0 && (
            <button type="button" onClick={onStartManual} className="mt-2 font-medium underline">
              Choose the columns myself
            </button>
          )}
        </div>
      )}

      {mapping && showColumns && (
        <div className="rounded-lg border border-slate-200 p-4">
          <ColumnMappingEditor columns={preview.columns} mapping={mapping} onChange={onMappingChange} />
        </div>
      )}

      {preview.transactions.length > 0 && (
        <div>
          <p className="mb-2 text-sm text-slate-600">
            {preview.transactionCount} transaction{preview.transactionCount === 1 ? '' : 's'} found
            {preview.skippedRows > 0 &&
              ` (${preview.skippedRows} row${preview.skippedRows === 1 ? '' : 's'} without an amount skipped, like balance lines)`}
            . First {preview.transactions.length}:
          </p>
          <div className="overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                <tr>
                  <th className="px-3 py-2 font-medium">Date</th>
                  <th className="px-3 py-2 font-medium">Description</th>
                  <th className="px-3 py-2 font-medium">Bank&apos;s category</th>
                  <th className="px-3 py-2 text-right font-medium">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {preview.transactions.map((t, i) => (
                  <tr key={i}>
                    <td className="whitespace-nowrap px-3 py-2 text-slate-600">{formatDate(t.transactionDate)}</td>
                    <td className="px-3 py-2 text-slate-900">{t.description}</td>
                    <td className="px-3 py-2 text-slate-500">{t.bankCategory ?? '—'}</td>
                    <td
                      className={`whitespace-nowrap px-3 py-2 text-right tabular-nums ${
                        t.amount < 0 ? 'text-slate-900' : 'text-green-700'
                      }`}
                    >
                      {formatMoney(t.amount, { signed: true })}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="mt-2 text-xs text-slate-500">
            Spending should show as <span className="font-medium">−</span> and refunds/payments as{' '}
            <span className="font-medium text-green-700">+</span>. If they&apos;re reversed, use Adjust columns.
          </p>
        </div>
      )}
    </section>
  )
}

function plural(count: number, word: string) {
  return `${count} ${word}${count === 1 ? '' : 's'}`
}

function ImportResultMessage({ imported, accountName }: { imported: api.ImportResult; accountName: string }) {
  const nothingNew = imported.importedCount === 0
  return (
    <p
      role="status"
      className={`mt-4 rounded-lg px-4 py-3 ${nothingNew ? 'bg-amber-50 text-amber-900' : 'bg-green-50 text-green-800'}`}
    >
      {nothingNew
        ? `Nothing new: all ${plural(imported.skippedCount, 'transaction')} in ${imported.fileName} were already imported into ${accountName}.`
        : `Imported ${plural(imported.importedCount, 'new transaction')} from ${imported.fileName} into ${accountName}.`}
      {!nothingNew && imported.skippedCount > 0 && ` Skipped ${imported.skippedCount} already imported.`}
    </p>
  )
}
