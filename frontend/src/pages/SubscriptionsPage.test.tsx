import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api'
import { SubscriptionsPage } from './SubscriptionsPage'

const NETFLIX: api.RecurringCharge = {
  merchantKey: 'NETFLIX COM',
  name: 'Netflix',
  description: 'NETFLIX.COM',
  cadence: 'MONTHLY',
  amount: 17.99,
  monthlyCost: 17.99,
  chargeCount: 6,
  firstDate: '2026-04-13',
  lastDate: '2026-09-13',
  // Past today in any real run, and after the latest statement below.
  nextExpected: '2026-10-13',
  active: true,
  categoryId: 6,
  categoryName: 'Entertainment',
}

function renderPage() {
  return render(
    <MemoryRouter>
      <SubscriptionsPage />
    </MemoryRouter>,
  )
}

describe('SubscriptionsPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: new Date(2026, 10, 11), toFake: ['Date'] })
    vi.spyOn(api, 'fetchRecurring').mockResolvedValue({
      charges: [NETFLIX],
      hidden: [],
      activeMonthlyTotal: 17.99,
      asOf: '2026-09-28',
    })
  })

  it('says a past expected date is after the latest statement instead of "next"', async () => {
    renderPage()
    expect(await screen.findByText(/monthly, expected/i)).toHaveTextContent(
      'Monthly, expected Oct 13 (after your latest statement)',
    )
  })

  it('hides "Not a subscription" in the row menu, with Undo', async () => {
    const user = userEvent.setup()
    const hide = vi.spyOn(api, 'hideRecurring').mockResolvedValue(undefined)
    const unhide = vi.spyOn(api, 'unhideRecurring').mockResolvedValue(undefined)
    renderPage()

    await screen.findByText('Netflix')
    expect(screen.queryByRole('menuitem')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'More for Netflix' }))
    await user.click(screen.getByRole('menuitem', { name: 'Not a subscription' }))
    expect(hide).toHaveBeenCalledWith('NETFLIX COM')

    await user.click(await screen.findByRole('button', { name: 'Undo' }))
    expect(unhide).toHaveBeenCalledWith('NETFLIX COM')
  })
})
