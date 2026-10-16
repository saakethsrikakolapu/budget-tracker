import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { CsvHelp } from './CsvHelp'

describe('CsvHelp', () => {
  it('stays folded until asked for', () => {
    render(<CsvHelp />)
    const toggle = screen.getByRole('button', { name: /where do i find my csv/i })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('group', { name: 'Your bank' })).not.toBeInTheDocument()
  })

  it('shows general advice, then the steps for the bank you pick', async () => {
    const user = userEvent.setup()
    render(<CsvHelp />)

    await user.click(screen.getByRole('button', { name: /where do i find my csv/i }))
    expect(screen.getByText(/another bank\?/i)).toBeInTheDocument()

    const chase = screen.getByRole('button', { name: 'Chase' })
    await user.click(chase)
    expect(chase).toHaveAttribute('aria-pressed', 'true')
    const steps = screen.getAllByRole('listitem')
    expect(steps).toHaveLength(3)
    expect(steps[0]).toHaveTextContent('chase.com')

    await user.click(screen.getByRole('button', { name: 'Capital One' }))
    expect(screen.getAllByRole('listitem')[0]).toHaveTextContent('capitalone.com')
    expect(chase).toHaveAttribute('aria-pressed', 'false')
  })
})
