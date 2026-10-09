import type { ColumnMapping, ImportPreview } from '../api'

/** Date patterns the backend understands, shown as examples people recognize. */
const DATE_FORMATS: Record<string, string> = {
  'M/d/uuuu': '7/30/2026',
  'M/d/uu': '7/30/26',
  'uuuu-MM-dd': '2026-07-30',
  'uuuu/M/d': '2026/7/30',
  'M-d-uuuu': '7-30-2026',
  'M-d-uu': '7-30-26',
  'MMM d, uuuu': 'Jul 30, 2026',
  'd MMM uuuu': '30 Jul 2026',
}

type Props = {
  columns: ImportPreview['columns']
  mapping: ColumnMapping
  onChange: (mapping: ColumnMapping) => void
}

/** Dropdowns for choosing which column is the date, description, amount, and so on. */
export function ColumnMappingEditor({ columns, mapping, onChange }: Props) {
  const set = (changes: Partial<ColumnMapping>) => onChange({ ...mapping, ...changes })

  function setStyle(amountStyle: ColumnMapping['amountStyle']) {
    // Fill in sensible column choices for the new style so the preview can update right away.
    const fallback = mapping.amountColumn ?? mapping.debitColumn ?? 0
    set({
      amountStyle,
      amountColumn: amountStyle === 'DEBIT_CREDIT' ? null : (mapping.amountColumn ?? fallback),
      debitColumn: amountStyle === 'DEBIT_CREDIT' ? (mapping.debitColumn ?? fallback) : null,
      creditColumn: amountStyle === 'DEBIT_CREDIT' ? (mapping.creditColumn ?? Math.min(fallback + 1, columns.length - 1)) : null,
      typeColumn: amountStyle === 'UNSIGNED_WITH_TYPE' ? (mapping.typeColumn ?? 0) : null,
    })
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <ColumnSelect label="Date" columns={columns} value={mapping.dateColumn} onChange={(v) => set({ dateColumn: v! })} />
      <label className="block">
        <span className="text-xs font-medium text-slate-600">Date format</span>
        <select
          value={mapping.dateFormat}
          onChange={(event) => set({ dateFormat: event.target.value })}
          className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
        >
          {Object.entries(DATE_FORMATS).map(([pattern, example]) => (
            <option key={pattern} value={pattern}>
              {example}
            </option>
          ))}
        </select>
      </label>

      <ColumnSelect
        label="Description (merchant)"
        columns={columns}
        value={mapping.descriptionColumn}
        onChange={(v) => set({ descriptionColumn: v! })}
      />
      <ColumnSelect
        label="Bank's category (optional)"
        columns={columns}
        value={mapping.categoryColumn}
        optional
        onChange={(v) => set({ categoryColumn: v })}
      />

      <fieldset className="sm:col-span-2">
        <legend className="text-xs font-medium text-slate-600">How amounts are shown</legend>
        <div className="mt-1 flex flex-wrap gap-4 text-sm text-slate-700">
          {(
            [
              ['SIGNED', 'One amount column (+/−)'],
              ['DEBIT_CREDIT', 'Separate money-out and money-in columns'],
              ['UNSIGNED_WITH_TYPE', 'Amount plus a Debit/Credit column'],
            ] as const
          ).map(([style, label]) => (
            <label key={style} className="flex items-center gap-2">
              <input type="radio" name="amountStyle" checked={mapping.amountStyle === style} onChange={() => setStyle(style)} />
              {label}
            </label>
          ))}
        </div>
      </fieldset>

      {mapping.amountStyle === 'DEBIT_CREDIT' ? (
        <>
          <ColumnSelect label="Money out (spending)" columns={columns} value={mapping.debitColumn} onChange={(v) => set({ debitColumn: v })} />
          <ColumnSelect label="Money in (refunds, payments)" columns={columns} value={mapping.creditColumn} onChange={(v) => set({ creditColumn: v })} />
        </>
      ) : (
        <ColumnSelect label="Amount" columns={columns} value={mapping.amountColumn} onChange={(v) => set({ amountColumn: v })} />
      )}
      {mapping.amountStyle === 'UNSIGNED_WITH_TYPE' && (
        <ColumnSelect label="Debit/Credit column" columns={columns} value={mapping.typeColumn} onChange={(v) => set({ typeColumn: v })} />
      )}
      {mapping.amountStyle === 'SIGNED' && (
        <label className="flex items-center gap-2 self-end pb-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={mapping.positiveIsSpending}
            onChange={(event) => set({ positiveIsSpending: event.target.checked })}
          />
          Purchases are shown as positive numbers
        </label>
      )}
    </div>
  )
}

function ColumnSelect({
  label,
  columns,
  value,
  optional = false,
  onChange,
}: {
  label: string
  columns: ImportPreview['columns']
  value: number | null
  optional?: boolean
  onChange: (value: number | null) => void
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-slate-600">{label}</span>
      <select
        value={value === null ? '' : String(value)}
        onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))}
        className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
      >
        {optional && <option value="">None</option>}
        {columns.map((column) => (
          <option key={column.index} value={String(column.index)}>
            {column.name}
            {column.samples[0] ? ` — e.g. ${column.samples[0].slice(0, 30)}` : ''}
          </option>
        ))}
      </select>
    </label>
  )
}
