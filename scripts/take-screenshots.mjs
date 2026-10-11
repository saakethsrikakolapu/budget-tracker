#!/usr/bin/env node
/**
 * Takes the README screenshots (docs/screenshots/*.png) from a running local copy of the app.
 *
 *   1. Start the app as usual (docker compose up -d, backend, frontend).
 *   2. node scripts/take-screenshots.mjs            (APP_URL defaults to http://localhost:5173)
 *      THEME=dark node scripts/take-screenshots.mjs (dark mode; files end in -dark.png)
 *
 * It signs up a throwaway demo account, imports samples/subscriptions-6-months.csv (fake data),
 * sets a few budgets, photographs each page in headless Edge or Chrome at 2x resolution, then
 * deletes the account.
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { APP_URL, ROOT, createDemoAccount, deleteDemoAccount, hideVerifyBanner, openBrowser, setTheme, shareLogin, sleep } from './demo-browser.mjs'

const OUT = join(ROOT, 'docs', 'screenshots')
const SUFFIX = process.env.THEME === 'dark' ? '-dark' : ''

async function shoot(cdp, name, path, { width = 1440, height = 900, mobile = false, fullPage = false } = {}) {
  await cdp.send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 2, mobile })
  await cdp.send('Page.navigate', { url: APP_URL + path })
  await sleep(2500) // data loads, charts draw
  let clip
  if (fullPage) {
    const { cssContentSize } = await cdp.send('Page.getLayoutMetrics')
    clip = { x: 0, y: 0, width, height: Math.ceil(cssContentSize.height), scale: 1 }
  }
  const { data } = await cdp.send('Page.captureScreenshot', { format: 'png', clip, captureBeyondViewport: fullPage })
  writeFileSync(join(OUT, `${name}${SUFFIX}.png`), Buffer.from(data, 'base64'))
  console.log(`  docs/screenshots/${name}${SUFFIX}.png`)
}

mkdirSync(OUT, { recursive: true })
const browser = await openBrowser()
const { cdp } = browser
try {
  await setTheme(cdp, SUFFIX === '-dark')

  console.log('Logged out:')
  await shoot(cdp, 'login', '/login')

  console.log('Creating a demo account with fake data...')
  const password = await createDemoAccount()
  await shareLogin(cdp)
  await hideVerifyBanner(cdp)

  console.log('Logged in:')
  await shoot(cdp, 'dashboard', '/', { fullPage: true })
  await shoot(cdp, 'transactions', '/transactions')
  await shoot(cdp, 'subscriptions', '/subscriptions', { fullPage: true })
  await shoot(cdp, 'budgets', '/budgets')
  await shoot(cdp, 'upload', '/import')
  await shoot(cdp, 'dashboard-phone', '/', { width: 390, height: 844, mobile: true })

  console.log('Deleting the demo account...')
  await deleteDemoAccount(password)
} finally {
  await browser.close()
}
console.log('Done.')
