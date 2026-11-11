import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AuthForm } from './AuthForm'

function renderForm(onSubmit: () => Promise<void>) {
  return render(
    <AuthForm
      title="Log in"
      submitLabel="Log in"
      passwordAutoComplete="current-password"
      onSubmit={onSubmit}
      footer={null}
    />,
  )
}

describe('AuthForm', () => {
  it('shows and hides the password', async () => {
    const user = userEvent.setup()
    renderForm(() => Promise.resolve())
    const password = screen.getByLabelText('Password', { selector: 'input' })
    expect(password).toHaveAttribute('type', 'password')
    expect(password).toHaveAttribute('autocomplete', 'current-password')

    await user.click(screen.getByRole('button', { name: 'Show password' }))
    expect(password).toHaveAttribute('type', 'text')
    await user.click(screen.getByRole('button', { name: 'Hide password' }))
    expect(password).toHaveAttribute('type', 'password')
  })

  it('says it is logging in while the request runs', async () => {
    const user = userEvent.setup()
    let finish: () => void = () => {}
    renderForm(() => new Promise<void>((resolve) => (finish = resolve)))

    await user.click(screen.getByRole('button', { name: 'Log in' }))
    expect(screen.getByRole('button', { name: /logging in/i })).toBeDisabled()
    finish()
  })
})
