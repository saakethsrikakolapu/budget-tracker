import { CircleHelp } from 'lucide-react'
import { useState } from 'react'

/**
 * Where to find the CSV download on common banks' websites. Banks rename their menus now and then,
 * so the steps say what to look for, and the general tips cover any bank not listed.
 */
const BANKS: { name: string; steps: string[] }[] = [
  {
    name: 'Chase',
    steps: [
      'Sign in at chase.com and open the account or card.',
      'Scroll to the transactions and click the download icon (an arrow pointing down) on the right.',
      'Choose "Spreadsheet (Excel, CSV)", pick a date range, and click Download.',
    ],
  },
  {
    name: 'Bank of America',
    steps: [
      'Sign in at bankofamerica.com and open the account or card.',
      'Click "Download" above the list of transactions.',
      'Pick a date range, choose "Microsoft Excel Format" (that file is a CSV), and click Download Transactions.',
    ],
  },
  {
    name: 'Wells Fargo',
    steps: [
      'Sign in at wellsfargo.com and open the account.',
      'Find "Download Account Activity" near the transactions.',
      'Choose the dates and "Comma Delimited", then click Download.',
    ],
  },
  {
    name: 'Capital One',
    steps: [
      'Sign in at capitalone.com on a computer (the app can’t export).',
      'Open the card or 360 account and find "Download Transactions".',
      'Choose CSV and a date range, then click Export.',
    ],
  },
  {
    name: 'Discover',
    steps: [
      'Sign in at discover.com and open your recent activity.',
      'Search the dates you want, then click the download button at the top right of the results.',
      'Choose CSV and click Download.',
    ],
  },
  {
    name: 'American Express',
    steps: [
      'Sign in at americanexpress.com and open "Statements & Activity".',
      'Pick a statement or a custom date range and click Download.',
      'Choose CSV and click Download.',
    ],
  },
  {
    name: 'PNC',
    steps: [
      'Sign in at pnc.com and open the account (Virtual Wallet: open Spend).',
      'Find the download or export option above the transactions.',
      'Choose CSV and a date range, then download.',
    ],
  },
]

/** "Where do I find my CSV?" under the drop zone: pick your bank, see three steps. */
export function CsvHelp() {
  const [open, setOpen] = useState(false)
  const [bank, setBank] = useState<string | null>(null)
  const selected = BANKS.find((b) => b.name === bank)

  return (
    <div className="text-sm">
      <button
        type="button"
        onClick={() => setOpen((shown) => !shown)}
        aria-expanded={open}
        className="inline-flex items-center gap-1.5 font-medium text-accent hover:underline"
      >
        <CircleHelp size={16} aria-hidden="true" />
        Where do I find my CSV?
      </button>

      {open && (
        <div className="mt-3 space-y-4 rounded-lg border border-stone-200 bg-stone-50 p-4">
          <p className="text-stone-700">
            Use your bank&apos;s <strong>website on a computer</strong>; most banking apps can&apos;t download files.
            Pick your bank:
          </p>
          <div className="flex flex-wrap gap-2" role="group" aria-label="Your bank">
            {BANKS.map((b) => (
              <button
                key={b.name}
                type="button"
                onClick={() => setBank(b.name)}
                aria-pressed={bank === b.name}
                className={`rounded-full border px-3 py-1.5 transition-colors duration-150 ${
                  bank === b.name
                    ? 'border-accent bg-accent text-accent-ink'
                    : 'border-stone-300 bg-white text-stone-800 hover:border-stone-400'
                }`}
              >
                {b.name}
              </button>
            ))}
          </div>

          {selected ? (
            <ol className="list-decimal space-y-1.5 pl-5 text-stone-800">
              {selected.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
          ) : (
            <p className="text-stone-600">
              Another bank? Open the account on its website and look for <strong>Download</strong> or{' '}
              <strong>Export</strong> near your transactions. Choose <strong>CSV</strong> (sometimes called
              &ldquo;Spreadsheet&rdquo;, &ldquo;Excel&rdquo; or &ldquo;Comma delimited&rdquo;), not PDF or Quicken.
            </p>
          )}

          <p className="text-xs text-stone-500">
            Tip: pick the last 3 months or more. Banks rename their menus sometimes, so if a step looks different,
            look for &ldquo;Download&rdquo; or &ldquo;Export&rdquo;.
          </p>
        </div>
      )}
    </div>
  )
}
