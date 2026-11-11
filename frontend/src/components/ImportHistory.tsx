import { useState } from 'react'
import type { ImportSummary } from '../api'
import { RowMenu } from './RowMenu'

type Props = {
  imports: ImportSummary[]
  onDelete: (id: number) => Promise<void>
}

/**
 * Past uploads, newest first. Removing one (it takes its transactions with it) sits in the row's ⋯
 * menu and asks first, instead of a red button on every row.
 */
export function ImportHistory({ imports, onDelete }: Props) {
  const [deletingId, setDeletingId] = useState<number | null>(null)

  async function handleDelete(item: ImportSummary) {
    const confirmed = window.confirm(
      `Remove the upload "${item.fileName}"? This deletes the ${item.importedCount} transaction(s) it added. You can upload the file again later.`,
    )
    if (!confirmed) return
    setDeletingId(item.id)
    try {
      await onDelete(item.id)
    } finally {
      setDeletingId(null)
    }
  }

  if (imports.length === 0) return null

  return (
    <ul className="divide-y divide-stone-200 rounded-lg border border-stone-200 bg-white">
      {imports.map((item) => (
        <li key={item.id} className="flex items-center justify-between gap-3 px-4 py-3">
          <div className="min-w-0">
            <p className="truncate font-medium text-stone-900">{item.fileName}</p>
            <p className="text-sm text-stone-500">
              {item.importedCount} added to {item.accountName} on{' '}
              {new Date(item.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
              {item.skippedCount > 0 && `, ${item.skippedCount} skipped as duplicates`}
            </p>
          </div>
          {deletingId === item.id ? (
            <span className="text-sm text-stone-500">Removing…</span>
          ) : (
            <RowMenu
              label={`More for ${item.fileName}`}
              items={[{ label: 'Remove this upload…', onSelect: () => handleDelete(item) }]}
            />
          )}
        </li>
      ))}
    </ul>
  )
}
