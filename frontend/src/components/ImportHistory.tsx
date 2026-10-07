import { useState } from 'react'
import type { ImportSummary } from '../api'

type Props = {
  imports: ImportSummary[]
  onDelete: (id: number) => Promise<void>
}

/** Past uploads, newest first, each with an undo (Delete) button. */
export function ImportHistory({ imports, onDelete }: Props) {
  const [deletingId, setDeletingId] = useState<number | null>(null)

  async function handleDelete(item: ImportSummary) {
    const confirmed = window.confirm(
      `Delete the import of "${item.fileName}"? This removes the ${item.importedCount} transaction(s) it added.`,
    )
    if (!confirmed) return
    setDeletingId(item.id)
    try {
      await onDelete(item.id)
    } finally {
      setDeletingId(null)
    }
  }

  if (imports.length === 0) {
    return <p className="text-sm text-slate-500">No imports yet.</p>
  }

  return (
    <ul className="divide-y divide-slate-200 rounded-xl bg-white shadow">
      {imports.map((item) => (
        <li key={item.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="min-w-0">
            <p className="truncate font-medium text-slate-900">{item.fileName}</p>
            <p className="text-sm text-slate-500">
              {item.accountName} · {new Date(item.createdAt).toLocaleString()} · {item.importedCount} added
              {item.skippedCount > 0 && `, ${item.skippedCount} skipped as duplicates`}
            </p>
          </div>
          <button
            type="button"
            onClick={() => handleDelete(item)}
            disabled={deletingId === item.id}
            className="rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50"
          >
            {deletingId === item.id ? 'Deleting…' : 'Delete'}
          </button>
        </li>
      ))}
    </ul>
  )
}
