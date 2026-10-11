#!/usr/bin/env node
/**
 * Records the README demo animation (docs/demo.png, an animated PNG) from a running local copy.
 *
 *   node scripts/record-demo.mjs        (APP_URL defaults to http://localhost:5173)
 *
 * Walks through the real app in headless Edge/Chrome with a throwaway account: log in, upload
 * the fake sample statement, see the summary, import, then the dashboard, transactions,
 * subscriptions, and dark mode. Each step is one frame; the frames are joined into an APNG
 * (animated PNG): like a GIF but full color, and it plays in every modern browser, GitHub included.
 * No dependencies: frames come from the DevTools protocol, and the APNG is assembled by hand below.
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { crc32 } from 'node:zlib'
import {
  APP_URL,
  ROOT,
  SAMPLE_CSV,
  createDemoAccount,
  deleteDemoAccount,
  evaluate,
  hideVerifyBanner,
  openBrowser,
  setTheme,
  shareLogin,
  sleep,
} from './demo-browser.mjs'

const WIDTH = 1280
const HEIGHT = 800
const OUT = join(ROOT, 'docs', 'demo.png')

const frames = [] // { png: Buffer, ms: how long it stays on screen }

async function frame(cdp, ms, settleMs = 1500) {
  await sleep(settleMs)
  const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' })
  frames.push({ png: Buffer.from(data, 'base64'), ms })
  process.stdout.write('.')
}

async function go(cdp, path) {
  await cdp.send('Page.navigate', { url: APP_URL + path })
}

const browser = await openBrowser()
const { cdp } = browser
let password
try {
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: WIDTH, height: HEIGHT, deviceScaleFactor: 1, mobile: false })
  await setTheme(cdp, false)

  await go(cdp, '/login')
  await frame(cdp, 1800)

  // An account with no data yet, so the demo starts from the upload.
  password = await createDemoAccount({ importSample: false })
  await shareLogin(cdp)
  await hideVerifyBanner(cdp)

  await go(cdp, '/import')
  await frame(cdp, 1500)

  // "Drop" the sample statement on the upload area, as a user would drag it there.
  const csv = JSON.stringify(readFileSync(SAMPLE_CSV, 'utf-8'))
  await evaluate(cdp, `(() => {
    const dt = new DataTransfer()
    dt.items.add(new File([${csv}], 'chase-freedom-statement.csv', { type: 'text/csv' }))
    document.querySelector('label.border-dashed').dispatchEvent(new DragEvent('drop', { dataTransfer: dt, bubbles: true, cancelable: true }))
  })()`)
  await frame(cdp, 2200, 2000)

  await evaluate(cdp, `[...document.querySelectorAll('button')].find(b => b.innerText.startsWith('Import ')).click()`)
  await frame(cdp, 2000, 2500)

  await go(cdp, '/')
  await frame(cdp, 3200, 2500)
  await go(cdp, '/transactions')
  await frame(cdp, 2200)
  await go(cdp, '/subscriptions')
  await frame(cdp, 2200)

  await setTheme(cdp, true)
  await go(cdp, '/')
  await frame(cdp, 3000, 2500)
} finally {
  if (password) await deleteDemoAccount(password).catch(() => undefined)
  await browser.close()
}

writeFileSync(OUT, toApng(frames))
console.log(`\ndocs/demo.png: ${frames.length} frames, ${Math.round(readFileSync(OUT).length / 1024)} KB`)

/**
 * Joins same-size PNGs into one animated PNG. An APNG is an ordinary PNG (so viewers that don't
 * animate still show the first frame) plus three chunk types: acTL (how many frames), fcTL (each
 * frame's size and delay), and fdAT (frames after the first; same image data as IDAT, numbered).
 */
function toApng(images) {
  const chunks = (png) => {
    const out = []
    for (let at = 8; at < png.length; ) {
      const length = png.readUInt32BE(at)
      out.push({ type: png.toString('latin1', at + 4, at + 8), data: png.subarray(at + 8, at + 8 + length) })
      at += 12 + length
    }
    return out
  }
  const chunk = (type, data) => {
    const head = Buffer.alloc(8)
    head.writeUInt32BE(data.length, 0)
    head.write(type, 4, 'latin1')
    const crc = Buffer.alloc(4)
    crc.writeUInt32BE(crc32(Buffer.concat([head.subarray(4), data])) >>> 0, 0)
    return Buffer.concat([head, data, crc])
  }
  const u32 = (n) => {
    const b = Buffer.alloc(4)
    b.writeUInt32BE(n, 0)
    return b
  }

  const parsed = images.map(({ png, ms }) => ({ ms, chunks: chunks(png) }))
  const ihdr = parsed[0].chunks.find((c) => c.type === 'IHDR').data
  for (const p of parsed) {
    if (!p.chunks.find((c) => c.type === 'IHDR').data.equals(ihdr)) throw new Error('Frames differ in size or format')
  }

  const out = [Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr)]
  out.push(chunk('acTL', Buffer.concat([u32(parsed.length), u32(0)]))) // 0 = loop forever
  let sequence = 0
  parsed.forEach((p, i) => {
    const fctl = Buffer.alloc(26)
    fctl.writeUInt32BE(sequence++, 0)
    fctl.writeUInt32BE(ihdr.readUInt32BE(0), 4) // width
    fctl.writeUInt32BE(ihdr.readUInt32BE(4), 8) // height
    // x, y offsets stay 0; delay = ms / 1000; dispose and blend ops stay 0 (replace whole frame)
    fctl.writeUInt16BE(p.ms, 20)
    fctl.writeUInt16BE(1000, 22)
    out.push(chunk('fcTL', fctl))
    for (const c of p.chunks.filter((c) => c.type === 'IDAT')) {
      out.push(i === 0 ? chunk('IDAT', c.data) : chunk('fdAT', Buffer.concat([u32(sequence++), c.data])))
    }
  })
  out.push(chunk('IEND', Buffer.alloc(0)))
  return Buffer.concat(out)
}
