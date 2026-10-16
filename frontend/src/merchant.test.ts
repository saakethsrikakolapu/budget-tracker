import { describe, expect, it } from 'vitest'
import { merchantName } from './merchant'

describe('merchantName', () => {
  it.each([
    ['Debit Card Purchase - CHICK FIL A 04954 CHAPEL HILL NC', 'Chick Fil A'],
    ['PURCHASE AUTHORIZED ON 10/21 STREAMFLIX CARD 1234', 'Streamflix'],
    ['SQ *JOES COFFEE', 'Joes Coffee'],
    ['PAYPAL *STEAMGAMES 4029357733', 'Steamgames'],
    ['HARRIS TEETER #0318', 'Harris Teeter'],
    ['CVS/PHARMACY #01234', 'CVS Pharmacy'],
    ['Debit Card Purchase - NAYAX LLC HUNT VALLEY MD', 'Nayax'],
    ['AUTOMATIC PAYMENT - THANK YOU', 'Automatic Payment'],
  ])('cleans up %j', (raw, expected) => {
    expect(merchantName(raw)).toBe(expected)
  })

  it('keeps web addresses readable', () => {
    expect(merchantName('NETFLIX.COM')).toBe('Netflix.com')
    expect(merchantName('Debit Card Purchase - APPLE COM BILL 866 712 7753 CA')).toBe('Apple.com')
  })

  it('leaves text that was already written for humans alone', () => {
    expect(merchantName('Zelle money received from Jane Doe')).toBe('Zelle money received from Jane Doe')
  })

  it('falls back to the original when nothing readable is left', () => {
    expect(merchantName('12345 / 678')).toBe('12345 / 678')
    expect(merchantName('CHECK 1234')).toBe('CHECK 1234')
  })
})
