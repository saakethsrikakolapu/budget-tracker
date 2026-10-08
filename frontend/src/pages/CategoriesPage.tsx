import { useEffect, useState, type FormEvent } from 'react'
import * as api from '../api'
import { RulesSection } from '../components/RulesSection'

/** Add, rename, and delete your spending categories, and choose which ones count as spending. */
export function CategoriesPage() {
  const [categories, setCategories] = useState<api.Category[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [newName, setNewName] = useState('')
  const [newCountsAsSpending, setNewCountsAsSpending] = useState(true)
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    api.fetchCategories()
      .then(setCategories)
      .catch(() => setError('Could not load your categories.'))
  }, [])

  function sortByName(list: api.Category[]) {
    return [...list].sort((a, b) => a.name.localeCompare(b.name))
  }

  function replace(updated: api.Category) {
    setCategories((previous) => sortByName((previous ?? []).map((c) => (c.id === updated.id ? updated : c))))
  }

  async function handleAdd(event: FormEvent) {
    event.preventDefault()
    if (newName.trim() === '') return
    setAdding(true)
    setError(null)
    try {
      const created = await api.createCategory(newName.trim(), newCountsAsSpending)
      setCategories((previous) => sortByName([...(previous ?? []), created]))
      setNewName('')
      setNewCountsAsSpending(true)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setAdding(false)
    }
  }

  async function handleDelete(category: api.Category) {
    const consequence =
      category.transactionCount === 0
        ? 'It has no transactions.'
        : `Its ${category.transactionCount} transaction(s) will become Uncategorized (they won't be deleted).`
    if (!window.confirm(`Delete "${category.name}"? ${consequence}`)) return
    setError(null)
    try {
      await api.deleteCategory(category.id)
      setCategories((previous) => (previous ?? []).filter((c) => c.id !== category.id))
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  if (categories === null && error === null) {
    return <p className="text-slate-500">Loading…</p>
  }

  return (
    <div className="max-w-2xl space-y-5">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Categories</h1>
        <p className="mt-1 text-sm text-slate-600">
          Turn off <span className="font-medium">Counts as spending</span> for money that isn&apos;t really spending,
          like paying your card bill, so it isn&apos;t counted twice.
        </p>
      </div>

      <form onSubmit={handleAdd} className="flex flex-wrap items-end gap-3 rounded-xl bg-white p-4 shadow">
        <label className="block flex-1">
          <span className="text-xs font-medium text-slate-600">New category</span>
          <input
            type="text"
            value={newName}
            onChange={(event) => setNewName(event.target.value)}
            placeholder="e.g. Textbooks"
            maxLength={50}
            className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
        </label>
        <label className="flex items-center gap-2 py-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={newCountsAsSpending}
            onChange={(event) => setNewCountsAsSpending(event.target.checked)}
          />
          Counts as spending
        </label>
        <button
          type="submit"
          disabled={adding || newName.trim() === ''}
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {adding ? 'Adding…' : 'Add'}
        </button>
      </form>

      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-red-800">
          {error}
        </p>
      )}

      <ul className="divide-y divide-slate-100 rounded-xl bg-white shadow">
        {categories?.map((category) => (
          <CategoryRow
            key={category.id}
            category={category}
            onSaved={replace}
            onDelete={() => handleDelete(category)}
            onError={setError}
          />
        ))}
      </ul>
      <p className="text-xs text-slate-500">
        Transactions without a category show as <span className="font-medium">Uncategorized</span>.
      </p>

      {categories && <RulesSection categories={categories} />}
    </div>
  )
}

type RowProps = {
  category: api.Category
  onSaved: (updated: api.Category) => void
  onDelete: () => void
  onError: (message: string | null) => void
}

function CategoryRow({ category, onSaved, onDelete, onError }: RowProps) {
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(category.name)
  const [saving, setSaving] = useState(false)

  async function save(nextName: string, nextCountsAsSpending: boolean) {
    setSaving(true)
    onError(null)
    try {
      onSaved(await api.updateCategory(category.id, nextName.trim(), nextCountsAsSpending))
      setEditing(false)
    } catch (err) {
      onError(errorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  return (
    <li className="flex flex-wrap items-center gap-3 px-4 py-3">
      <div className="min-w-0 flex-1">
        {editing ? (
          <form
            onSubmit={(event) => {
              event.preventDefault()
              void save(name, category.countsAsSpending)
            }}
            className="flex gap-2"
          >
            <input
              type="text"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={50}
              autoFocus
              className="w-full rounded-lg border border-slate-300 px-2 py-1 text-sm"
            />
            <button type="submit" disabled={saving || name.trim() === ''} className="text-sm font-medium text-slate-900">
              Save
            </button>
            <button
              type="button"
              onClick={() => {
                setName(category.name)
                setEditing(false)
              }}
              className="text-sm text-slate-500"
            >
              Cancel
            </button>
          </form>
        ) : (
          <>
            <p className="font-medium text-slate-900">{category.name}</p>
            <p className="text-xs text-slate-500">
              {category.transactionCount} transaction{category.transactionCount === 1 ? '' : 's'}
            </p>
          </>
        )}
      </div>

      <label className="flex items-center gap-2 text-sm text-slate-700">
        <input
          type="checkbox"
          checked={category.countsAsSpending}
          disabled={saving}
          onChange={(event) => void save(category.name, event.target.checked)}
        />
        Counts as spending
      </label>

      {!editing && (
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setEditing(true)}
            className="rounded-lg border border-slate-300 px-3 py-1 text-sm hover:bg-slate-100"
          >
            Rename
          </button>
          <button
            type="button"
            onClick={onDelete}
            className="rounded-lg border border-red-300 px-3 py-1 text-sm text-red-700 hover:bg-red-50"
          >
            Delete
          </button>
        </div>
      )}
    </li>
  )
}

function errorMessage(err: unknown): string {
  if (err instanceof api.ApiError) {
    return err.fieldErrors.name ?? Object.values(err.fieldErrors)[0] ?? err.message
  }
  return 'Could not reach the server. Please try again.'
}
