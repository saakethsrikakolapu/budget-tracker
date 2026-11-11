import { Upload } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { CsvHelp } from './CsvHelp'

/**
 * A page before the first upload: what will show up here, one action, and the "where do I find my
 * CSV?" help right next to it (the usual reason people stall at this step).
 */
export function FirstRun({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="max-w-xl">
      <h1 className="text-3xl font-bold text-stone-950">{title}</h1>
      <div className="mt-3 text-stone-600">{children}</div>
      <Link
        to="/import"
        className="mt-6 inline-flex items-center gap-2 rounded-md bg-button px-5 py-3 font-semibold text-button-ink transition-colors duration-150 hover:bg-button-hover"
      >
        <Upload size={18} aria-hidden="true" />
        Upload your first CSV
      </Link>
      <div className="mt-6 border-t border-stone-200 pt-5">
        <CsvHelp />
      </div>
    </div>
  )
}
