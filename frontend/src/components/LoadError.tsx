import { RotateCw } from 'lucide-react'

/**
 * When a page's data didn't load: say so plainly and offer the one thing that usually fixes it.
 * (On the free server tier a request can fail while the server wakes up; a second try works.)
 */
export function LoadError({ what, onRetry }: { what: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg bg-red-50 px-4 py-3 text-red-800">
      <p className="flex-1">
        Couldn&apos;t load {what}. It usually works on a second try.
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="inline-flex shrink-0 items-center gap-1.5 rounded-lg bg-button px-3 py-1.5 text-sm font-semibold text-button-ink transition-colors duration-150 hover:bg-button-hover"
      >
        <RotateCw size={14} aria-hidden="true" />
        Try again
      </button>
    </div>
  )
}
