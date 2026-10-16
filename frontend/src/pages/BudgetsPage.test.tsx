import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api'
import { BudgetsPage } from './BudgetsPage'

/** Fake server answers: the page is tested on its own, without a backend. */
const EMPTY_OVERVIEW: api.BudgetOverview = {
  month: '2026-09',
  budgets: [],
  totalBudgeted: 0,
  totalSpent: 0,
  unbudgetedSpending: 0,
}

function line(overrides: Partial<api.PlanLine>): api.PlanLine {
  return {
    categoryId: 1,
    categoryName: 'Groceries',
    usual: 64.89,
    fixed: 0,
    suggested: 64,
    spent: 0,
    usualSoFar: 0,
    pace: 'NORMAL',
    hasBudget: false,
    ...overrides,
  }
}

const PLAN: api.BudgetPlan = {
  month: '2026-09',
  fullMonths: 5,
  throughDay: 28,
  monthlyIncome: null,
  target: 250,
  basis: 'USUAL',
  suggestedTotal: 129,
  usualTotal: 129.8,
  spent: 0,
  usualSoFar: 0,
  pace: 'NORMAL',
  lines: [
    line({}),
    line({ categoryId: 2, categoryName: 'Bills & Utilities', usual: 64.91, fixed: 64.91, suggested: 65 }),
  ],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/budgets?month=2026-09']}>
      <BudgetsPage />
    </MemoryRouter>,
  )
}

describe('BudgetsPage', () => {
  beforeEach(() => {
    vi.spyOn(api, 'fetchTransactionMonths').mockResolvedValue(['2026-09', '2026-08'])
  })

  it('suggests a budget and saves it in one click', async () => {
    const user = userEvent.setup()
    const fetchBudgets = vi.spyOn(api, 'fetchBudgets').mockResolvedValue(EMPTY_OVERVIEW)
    vi.spyOn(api, 'fetchBudgetPlan').mockResolvedValue(PLAN)
    const apply = vi.spyOn(api, 'applyBudgetPlan').mockResolvedValue(EMPTY_OVERVIEW)

    renderPage()

    expect(await screen.findByText(/you usually spend about/i)).toHaveTextContent('$250.00')
    const list = screen.getAllByRole('list')[0]
    expect(within(list).getByText('Groceries')).toBeInTheDocument()
    expect(within(list).getByText('Regular charges · usually $64.91')).toBeInTheDocument() // the phone bill

    // After saving, the page reloads and shows the budget being tracked.
    fetchBudgets.mockResolvedValue({
      ...EMPTY_OVERVIEW,
      budgets: [{ categoryId: 1, categoryName: 'Groceries', limit: 64, spent: 20, remaining: 44, percentUsed: 31 }],
      totalBudgeted: 64,
      totalSpent: 20,
    })
    await user.click(screen.getByRole('button', { name: 'Use this budget' }))

    expect(apply).toHaveBeenCalledWith('2026-09', undefined)
    expect(await screen.findByText(/left of/)).toHaveTextContent('$44.00 left of $64.00 this month.')
    expect(screen.queryByRole('button', { name: 'Use this budget' })).not.toBeInTheDocument()
  })

  it('rebuilds the suggestion around your own monthly total', async () => {
    const user = userEvent.setup()
    vi.spyOn(api, 'fetchBudgets').mockResolvedValue(EMPTY_OVERVIEW)
    const fetchPlan = vi.spyOn(api, 'fetchBudgetPlan').mockResolvedValue(PLAN)
    const apply = vi.spyOn(api, 'applyBudgetPlan').mockResolvedValue(EMPTY_OVERVIEW)

    renderPage()
    await user.click(await screen.findByRole('button', { name: 'Change' }))
    const input = screen.getByLabelText('Monthly total')

    await user.clear(input)
    await user.type(input, 'lots')
    await user.click(screen.getByRole('button', { name: 'Update' }))
    expect(screen.getByText(/enter an amount like/i)).toBeInTheDocument()
    expect(fetchPlan).not.toHaveBeenCalledWith('2026-09', 'lots')

    fetchPlan.mockResolvedValue({ ...PLAN, basis: 'CUSTOM', target: 300 })
    await user.clear(input)
    await user.type(input, '300')
    await user.click(screen.getByRole('button', { name: 'Update' }))

    expect(fetchPlan).toHaveBeenLastCalledWith('2026-09', '300')
    expect(await screen.findByText(/here's a budget for/i)).toHaveTextContent('$300.00')
    await user.click(screen.getByRole('button', { name: 'Use this budget' }))
    expect(apply).toHaveBeenCalledWith('2026-09', '300')
  })

  it('asks for a full month of statements before suggesting anything', async () => {
    vi.spyOn(api, 'fetchBudgets').mockResolvedValue(EMPTY_OVERVIEW)
    vi.spyOn(api, 'fetchBudgetPlan').mockResolvedValue({ ...PLAN, fullMonths: 0, target: null, basis: null, lines: [] })

    renderPage()

    expect(await screen.findByText(/needs at least one full month/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Use this budget' })).not.toBeInTheDocument()
  })

  it('flags categories that are higher than usual', async () => {
    vi.spyOn(api, 'fetchBudgets').mockResolvedValue({
      ...EMPTY_OVERVIEW,
      budgets: [{ categoryId: 1, categoryName: 'Groceries', limit: 64, spent: 134.03, remaining: -70.03, percentUsed: 209 }],
      totalBudgeted: 64,
      totalSpent: 134.03,
    })
    vi.spyOn(api, 'fetchBudgetPlan').mockResolvedValue({
      ...PLAN,
      lines: [line({ spent: 134.03, usualSoFar: 64.89, pace: 'HIGHER', hasBudget: true })],
    })

    renderPage()

    expect(await screen.findByText(/over your/)).toHaveTextContent('$70.03 over your $64.00 budget this month.')
    expect(screen.getByText(/higher than usual:/i)).toHaveTextContent('Higher than usual: Groceries.')
    expect(screen.getByRole('progressbar', { name: /groceries/i })).toHaveAttribute('aria-valuenow', '209')
  })
})
