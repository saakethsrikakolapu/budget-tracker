import { useEffect, useState, type FormEvent } from 'react'
import * as api from '../api'
import { changedText } from '../rules'

/** Lists your rules (in the order they're applied) and lets you add or delete them. */
export function RulesSection({ categories }: { categories: api.Category[] }) {
  const [rules, setRules] = useState<api.Rule[] | null>(null)
  const [pattern, setPattern] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.fetchRules()
      .then(setRules)
      .catch(() => setError('Could not load your rules.'))
  }, [])

  function refresh() {
    api.fetchRules().then(setRules).catch(() => setError('Could not load your rules.'))
  }

  async function handleAdd(event: FormEvent) {
    event.preventDefault()
    if (pattern.trim() === '' || categoryId === '') return
    setSaving(true)
    setError(null)
    setMessage(null)
    try {
      const change = await api.createRule(pattern, Number(categoryId))
      setMessage(`Rule added. ${changedText(change.recategorizedCount)}`)
      setPattern('')
      refresh()
    } catch (err) {
      setError(err instanceof api.ApiError ? (Object.values(err.fieldErrors)[0] ?? err.message) : 'Could not reach the server.')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(rule: api.Rule) {
    if (!window.confirm(`Delete the rule "${rule.pattern}" → ${rule.categoryName}?`)) return
    setError(null)
    setMessage(null)
    try {
      const result = await api.deleteRule(rule.id)
      setMessage(`Rule deleted. ${changedText(result.recategorizedCount)}`)
      refresh()
    } catch {
      setError('Could not delete the rule. Please try again.')
    }
  }

  return (
    <section className="space-y-3">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">Rules</h2>
        <p className="mt-1 text-sm text-slate-600">
          Automatically categorize transactions whose description contains some text. Rules beat the bank&apos;s
          categories, the most specific (longest) rule wins, and anything you set by hand is never changed.
        </p>
      </div>

      <form onSubmit={handleAdd} className="flex flex-wrap items-end gap-3 rounded-xl bg-white p-4 shadow">
        <label className="block flex-1">
          <span className="text-xs font-medium text-slate-600">If the description contains</span>
          <input
            type="text"
            value={pattern}
            onChange={(event) => setPattern(event.target.value)}
            placeholder="e.g. POSHMARK"
            maxLength={100}
            className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
        </label>
        <label className="block">
          <span className="text-xs font-medium text-slate-600">use category</span>
          <select
            value={categoryId}
            onChange={(event) => setCategoryId(event.target.value)}
            className="mt-1 block rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
          >
            <option value="">Choose…</option>
            {categories.map((category) => (
              <option key={category.id} value={String(category.id)}>
                {category.name}
              </option>
            ))}
          </select>
        </label>
        <button
          type="submit"
          disabled={saving || pattern.trim() === '' || categoryId === ''}
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
        >
          {saving ? 'Adding…' : 'Add rule'}
        </button>
      </form>

      {message && (
        <p role="status" className="rounded-lg bg-green-50 px-4 py-3 text-sm text-green-800">
          {message}
        </p>
      )}
      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-4 py-3 text-sm text-red-800">
          {error}
        </p>
      )}

      {rules !== null && rules.length === 0 && <p className="text-sm text-slate-500">No rules yet.</p>}
      {rules !== null && rules.length > 0 && (
        <ul className="divide-y divide-slate-100 rounded-xl bg-white shadow">
          {rules.map((rule) => (
            <li key={rule.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
              <div>
                <p className="text-sm text-slate-900">
                  Contains <span className="font-mono font-medium">{rule.pattern}</span> →{' '}
                  <span className="font-medium">{rule.categoryName}</span>
                </p>
                <p className="text-xs text-slate-500">
                  Matches {rule.matchCount} transaction{rule.matchCount === 1 ? '' : 's'}
                </p>
              </div>
              <button
                type="button"
                onClick={() => handleDelete(rule)}
                className="rounded-lg border border-red-300 px-3 py-1 text-sm text-red-700 hover:bg-red-50"
              >
                Delete
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
