import { expect, test } from '@playwright/test'
import { fileURLToPath } from 'node:url'

/** Six months of a made-up student's credit card (April to September 2026). */
const SAMPLE = fileURLToPath(new URL('../../samples/subscriptions-6-months.csv', import.meta.url))
const PASSWORD = 'correct-horse-battery'

/**
 * The path every new user takes: sign up, upload a statement, see the dashboard, use the
 * suggested budget. Ends by deleting the account, which also checks that deleting works.
 */
test('sign up, upload a statement, see where the money went, use the suggested budget', async ({ page }, testInfo) => {
  const email = `e2e-${testInfo.project.name}-${Date.now()}@example.test`
  const phone = testInfo.project.name === 'phone'

  // Sign up: lands on the welcome screen.
  await page.goto('/register')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: /let's see where your money goes/i })).toBeVisible()

  // Upload the sample statement into a new account.
  await page.getByRole('link', { name: /upload your first statement/i }).click()
  await page.getByLabel('Name this account').fill('Chase Freedom')
  await page.locator('input[type="file"]').setInputFiles(SAMPLE)
  await page.getByRole('button', { name: /^Import \d+ transactions$/ }).click()
  await expect(page.getByText(/transactions added to Chase Freedom/)).toBeVisible()

  // The dashboard tells September's story in a sentence.
  await page.getByRole('link', { name: 'Dashboard', exact: true }).click()
  // The accessible name is what a screen reader announces (the count-up animation is hidden from it).
  await expect(page.getByRole('heading', { level: 1 })).toHaveAccessibleName('You spent $293.75 in September.')
  await expect(page.getByText('Where it went')).toBeVisible()

  // Transactions: readable names on top of the bank's text.
  await page.getByRole('link', { name: 'Transactions', exact: true }).click()
  await expect(page.getByText('Harris Teeter', { exact: true }).first()).toBeVisible()
  if (phone) {
    // On phones the dropdowns are folded away behind "Filters".
    // includeHidden: find it even while hidden, so this fails if the dropdown is missing altogether.
    const category = page.getByRole('combobox', { name: 'Category', exact: true, includeHidden: true })
    await expect(category).toHaveCount(1)
    await expect(category).toBeHidden()
    await page.getByRole('button', { name: /filters/i }).click()
    await expect(category).toBeVisible()
  }

  // Budget: suggested from the statements, saved in one click.
  await page.getByRole('link', { name: 'Budget', exact: true }).click()
  await expect(page.getByText(/you usually spend about/i)).toBeVisible()
  await page.getByRole('button', { name: 'Use this budget' }).click()
  await expect(page.getByText(/(left of|over your) \$[\d,.]+ (budget )?this month/)).toBeVisible()
  await expect(page.getByRole('progressbar').first()).toBeVisible()

  // Clean up: delete the account (and check that works too).
  await page.getByRole('link', { name: 'Your account' }).click()
  await page.getByRole('button', { name: /delete my account/i }).click()
  await page.getByLabel('Enter your password to confirm').fill(PASSWORD)
  await page.getByRole('button', { name: /permanently delete everything/i }).click()
  await expect(page).toHaveURL(/\/login/)
})
