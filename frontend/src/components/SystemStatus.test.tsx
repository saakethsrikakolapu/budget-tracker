import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api'
import { SystemStatus } from './SystemStatus'

describe('SystemStatus', () => {
  it('shows nothing while everything works', async () => {
    const health = vi.spyOn(api, 'fetchHealth').mockResolvedValue({ status: 'UP', database: 'UP' })
    const { container } = render(<SystemStatus />)
    await vi.waitFor(() => expect(health).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })

  it('says so when the server is unreachable', async () => {
    vi.spyOn(api, 'fetchHealth').mockRejectedValue(new Error('offline'))
    render(<SystemStatus />)
    expect(await screen.findByRole('alert')).toHaveTextContent(/can't reach its server/i)
  })

  it('says so when the database is down', async () => {
    vi.spyOn(api, 'fetchHealth').mockResolvedValue({ status: 'DOWN', database: 'DOWN' })
    render(<SystemStatus />)
    expect(await screen.findByRole('alert')).toHaveTextContent(/trouble with its database/i)
  })
})
