import { describe, expect, it } from 'vitest'
import { formatDate, formatMoney, isMoneyInput, monthRange } from './format'

describe('formatMoney', () => {
  it('shows dollars with cents', () => {
    expect(formatMoney(4.5)).toBe('$4.50')
    expect(formatMoney(1234.5)).toBe('$1,234.50')
  })

  it('adds a sign only when asked', () => {
    expect(formatMoney(-4.75)).toBe('$4.75')
    expect(formatMoney(-4.75, { signed: true })).toBe('−$4.75')
    expect(formatMoney(250, { signed: true })).toBe('+$250.00')
    expect(formatMoney(0, { signed: true })).toBe('$0.00')
  })
})

describe('formatDate', () => {
  it('keeps the calendar day (no slipping to the day before in US time zones)', () => {
    // new Date("2026-09-28") would be midnight UTC, which is still Sep 27 in North Carolina.
    expect(formatDate('2026-09-28')).toBe('Sep 28, 2026')
    expect(formatDate('2026-01-01')).toBe('Jan 1, 2026')
  })
})

describe('monthRange', () => {
  it('covers the whole month, including leap years', () => {
    expect(monthRange('2026-09')).toEqual({ from: '2026-09-01', to: '2026-09-30' })
    expect(monthRange('2028-02')).toEqual({ from: '2028-02-01', to: '2028-02-29' })
    expect(monthRange('2026-02')).toEqual({ from: '2026-02-01', to: '2026-02-28' })
  })
})

describe('isMoneyInput', () => {
  it('accepts plain amounts with up to two decimals', () => {
    expect(isMoneyInput('200')).toBe(true)
    expect(isMoneyInput('64.90')).toBe(true)
    expect(isMoneyInput(' 15 ')).toBe(true)
  })

  it('rejects anything else', () => {
    for (const text of ['', 'abc', '10.005', '-5', '$20', '1,000', '1e3']) {
      expect(isMoneyInput(text), text).toBe(false)
    }
  })
})
