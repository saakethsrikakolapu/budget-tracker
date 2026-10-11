/**
 * Shared by take-screenshots.mjs and record-demo.mjs: a throwaway demo account with fake data,
 * and a headless Edge/Chrome driven over the Chrome DevTools Protocol. No dependencies.
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
export const APP_URL = process.env.APP_URL ?? 'http://localhost:5173'
export const SAMPLE_CSV = join(ROOT, 'samples', 'subscriptions-6-months.csv')
const DEBUG_PORT = 9333
const BROWSERS = [
  process.env.BROWSER,
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
].filter(Boolean)

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

// --- a tiny API client that keeps cookies, like a browser would ---

export const cookies = new Map()

export async function api(path, { method = 'GET', json, form } = {}) {
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

/**
 * Signs up a throwaway account and logs in (cookies kept in `cookies`).
 * With importSample, also imports the fake 6-month statement and sets a few budgets.
 * @returns the password, needed to delete the account afterwards
 */
export async function createDemoAccount({ importSample = true } = {}) {
  const email = `demo-${Date.now()}@example.test`
  const password = crypto.randomUUID()
  await api('/api/auth/me') // sets the XSRF-TOKEN cookie
  await api('/api/auth/register', { method: 'POST', json: { email, password } })
  await api('/api/auth/me')
  await api('/api/auth/login', { method: 'POST', json: { email, password } })
  const account = await api('/api/accounts', { method: 'POST', json: { name: 'Chase Freedom' } })
  if (importSample) {
    const form = new FormData()
    form.append('accountId', String(account.id))
    form.append('rememberFormat', 'true')
    form.append('file', new Blob([readFileSync(SAMPLE_CSV)], { type: 'text/csv' }), 'chase-freedom-statement.csv')
    await api('/api/imports', { method: 'POST', form })
  }
  await setBudgets()
  return password
}

export async function setBudgets() {
  const categories = await api('/api/categories')
  const limits = { Groceries: '300', 'Food & Dining': '120', Entertainment: '40', 'Bills & Utilities': '90' }
  for (const [name, limit] of Object.entries(limits)) {
    const category = categories.find((c) => c.name === name)
    await api(`/api/budgets/${category.id}`, { method: 'PUT', json: { monthlyLimit: limit } })
  }
}

export async function deleteDemoAccount(password) {
  await api('/api/auth/account', { method: 'DELETE', json: { password } })
}

// --- the browser, driven over the DevTools protocol ---

export async function openBrowser() {
  const executable = BROWSERS.find((path) => existsSync(path))
  if (!executable) throw new Error('No Edge or Chrome found. Set BROWSER=/path/to/chrome')
  const profile = mkdtempSync(join(tmpdir(), 'demo-browser-'))
  const child = spawn(executable, [
    '--headless=new',
    `--remote-debugging-port=${DEBUG_PORT}`,
    `--user-data-dir=${profile}`,
    '--no-first-run',
    '--hide-scrollbars',
    'about:blank',
  ])
  let wsUrl
  for (let i = 0; i < 50 && !wsUrl; i++) {
    try {
      const targets = await (await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/list`)).json()
      wsUrl = targets.find((t) => t.type === 'page')?.webSocketDebuggerUrl
    } catch {
      // not listening yet
    }
    if (!wsUrl) await sleep(200)
  }
  if (!wsUrl) throw new Error('The browser did not start')
  const cdp = await connect(wsUrl)
  await cdp.send('Page.enable')
  return {
    cdp,
    async close() {
      cdp.close()
      child.kill()
      // The browser lets go of its temporary profile a moment after exiting; don't fail over leftovers.
      try {
        rmSync(profile, { recursive: true, force: true, maxRetries: 10, retryDelay: 300 })
      } catch {
        console.log(`(Could not remove the temporary browser profile ${profile}; it's safe to delete.)`)
      }
    },
  }
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

/** Light or dark, without animations (so captures show final values). */
export async function setTheme(cdp, dark) {
  await cdp.send('Emulation.setEmulatedMedia', {
    features: [
      { name: 'prefers-reduced-motion', value: 'reduce' },
      { name: 'prefers-color-scheme', value: dark ? 'dark' : 'light' },
    ],
  })
}

/** Gives the browser the API client's login cookies. */
export async function shareLogin(cdp) {
  const domain = new URL(APP_URL).hostname
  for (const name of ['SESSION', 'JSESSIONID', 'XSRF-TOKEN']) {
    if (cookies.has(name)) await cdp.send('Network.setCookie', { name, value: cookies.get(name), domain, path: '/' })
  }
}

/**
 * Dismisses the "verify your email" banner for this tab, as a user would (the demo account's
 * email is made up, so it can never be verified).
 */
export async function hideVerifyBanner(cdp) {
  await cdp.send('Page.navigate', { url: APP_URL + '/login' })
  await sleep(500)
  await evaluate(cdp, "sessionStorage.setItem('verify-banner-hidden', '1')")
}

/** Runs JavaScript in the page and returns its (JSON) result. */
export async function evaluate(cdp, expression) {
  const { result, exceptionDetails } = await cdp.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  })
  if (exceptionDetails) throw new Error(exceptionDetails.exception?.description ?? exceptionDetails.text)
  return result.value
}
