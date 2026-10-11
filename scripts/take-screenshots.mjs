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
 * deletes the account. No dependencies: Node's built-in fetch and WebSocket talk to the browser
 * through the Chrome DevTools Protocol.
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const APP_URL = process.env.APP_URL ?? 'http://localhost:5173'
const OUT = join(ROOT, 'docs', 'screenshots')
const DEBUG_PORT = 9333
const DARK = process.env.THEME === 'dark'
const SUFFIX = DARK ? '-dark' : ''
const BROWSERS = [
  process.env.BROWSER,
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
].filter(Boolean)

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// --- a tiny API client that keeps cookies, like a browser would ---

const cookies = new Map()

async function api(path, { method = 'GET', json, form } = {}) {
  const headers = { Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join('; ') }
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = decodeURIComponent(cookies.get('XSRF-TOKEN') ?? '')
  if (json) headers['Content-Type'] = 'application/json'
  const response = await fetch(APP_URL + path, { method, headers, body: json ? JSON.stringify(json) : form })
  for (const line of response.headers.getSetCookie()) {
    const [pair] = line.split(';')
    const [name, ...value] = pair.split('=')
    cookies.set(name.trim(), value.join('='))
  }
  if (!response.ok && response.status !== 401) {
    throw new Error(`${method} ${path} -> ${response.status} ${await response.text()}`)
  }
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

async function createDemoAccount() {
  const email = `screenshots-${Date.now()}@example.test`
  const password = crypto.randomUUID()
  await api('/api/auth/me') // sets the XSRF-TOKEN cookie
  await api('/api/auth/register', { method: 'POST', json: { email, password } })
  await api('/api/auth/me')
  await api('/api/auth/login', { method: 'POST', json: { email, password } })

  const account = await api('/api/accounts', { method: 'POST', json: { name: 'Chase Freedom' } })
  const form = new FormData()
  form.append('accountId', String(account.id))
  form.append('rememberFormat', 'true')
  const csv = readFileSync(join(ROOT, 'samples', 'subscriptions-6-months.csv'))
  form.append('file', new Blob([csv], { type: 'text/csv' }), 'chase-freedom-statement.csv')
  await api('/api/imports', { method: 'POST', form })

  const categories = await api('/api/categories')
  const limits = { Groceries: '300', 'Food & Dining': '120', Entertainment: '40', 'Bills & Utilities': '90' }
  for (const [name, limit] of Object.entries(limits)) {
    const category = categories.find((c) => c.name === name)
    await api(`/api/budgets/${category.id}`, { method: 'PUT', json: { monthlyLimit: limit } })
  }
  return password
}

// --- the browser, driven over the DevTools protocol ---

async function launchBrowser() {
  const executable = BROWSERS.find((path) => existsSync(path))
  if (!executable) throw new Error('No Edge or Chrome found. Set BROWSER=/path/to/chrome')
  const profile = mkdtempSync(join(tmpdir(), 'screenshots-'))
  const child = spawn(executable, [
    '--headless=new',
    `--remote-debugging-port=${DEBUG_PORT}`,
    `--user-data-dir=${profile}`,
    '--no-first-run',
    '--hide-scrollbars',
    'about:blank',
  ])
  for (let i = 0; i < 50; i++) {
    try {
      const targets = await (await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/list`)).json()
      const page = targets.find((t) => t.type === 'page')
      if (page) return { child, profile, wsUrl: page.webSocketDebuggerUrl }
    } catch {
      // not listening yet
    }
    await sleep(200)
  }
  throw new Error('The browser did not start')
}

function connect(wsUrl) {
  const socket = new WebSocket(wsUrl)
  let nextId = 0
  const pending = new Map()
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    if (message.id && pending.has(message.id)) {
      const { ok, fail } = pending.get(message.id)
      pending.delete(message.id)
      if (message.error) fail(new Error(message.error.message))
      else ok(message.result)
    }
  })
  const send = (method, params = {}) =>
    new Promise((ok, fail) => {
      const id = ++nextId
      pending.set(id, { ok, fail })
      socket.send(JSON.stringify({ id, method, params }))
    })
  return new Promise((ok) => socket.addEventListener('open', () => ok({ send, close: () => socket.close() })))
}

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

// --- main ---

mkdirSync(OUT, { recursive: true })
const browser = await launchBrowser()
const cdp = await connect(browser.wsUrl)
try {
  await cdp.send('Page.enable')
  // Final values instead of mid-animation numbers; light or dark as asked (the app follows the system).
  await cdp.send('Emulation.setEmulatedMedia', {
    features: [
      { name: 'prefers-reduced-motion', value: 'reduce' },
      { name: 'prefers-color-scheme', value: DARK ? 'dark' : 'light' },
    ],
  })

  console.log('Logged out:')
  await shoot(cdp, 'login', '/login')

  console.log('Creating a demo account with fake data...')
  const password = await createDemoAccount()
  const host = new URL(APP_URL).hostname
  for (const name of ['SESSION', 'JSESSIONID', 'XSRF-TOKEN']) {
    if (cookies.has(name)) await cdp.send('Network.setCookie', { name, value: cookies.get(name), domain: host, path: '/' })
  }

  console.log('Logged in:')
  await shoot(cdp, 'dashboard', '/', { fullPage: true })
  await shoot(cdp, 'transactions', '/transactions')
  await shoot(cdp, 'subscriptions', '/subscriptions', { fullPage: true })
  await shoot(cdp, 'budgets', '/budgets')
  await shoot(cdp, 'upload', '/import')
  await shoot(cdp, 'dashboard-phone', '/', { width: 390, height: 844, mobile: true })

  console.log('Deleting the demo account...')
  await api('/api/auth/account', { method: 'DELETE', json: { password } })
} finally {
  cdp.close()
  browser.child.kill()
  // The browser lets go of its temporary profile a moment after exiting; don't fail over leftovers.
  try {
    rmSync(browser.profile, { recursive: true, force: true, maxRetries: 10, retryDelay: 300 })
  } catch {
    console.log(`(Could not remove the temporary browser profile ${browser.profile}; it's safe to delete.)`)
  }
}
console.log('Done.')
