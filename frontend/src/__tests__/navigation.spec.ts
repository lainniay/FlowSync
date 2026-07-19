import { describe, expect, it } from 'vitest'

import {
  getSafeRedirect,
  hasRequiredRole,
} from '@/router/navigation'

describe('getSafeRedirect', () => {
  it('keeps an internal application path', () => {
    expect(getSafeRedirect('/projects?page=1'))
      .toBe('/projects?page=1')
  })

  it('rejects external redirect addresses', () => {
    expect(getSafeRedirect('https://example.com'))
      .toBe('/overview')

    expect(getSafeRedirect('//example.com'))
      .toBe('/overview')
  })

  it('does not redirect back to login', () => {
    expect(getSafeRedirect('/login?redirect=/login'))
      .toBe('/overview')
  })
})

describe('hasRequiredRole', () => {
  it('allows a route without role restrictions', () => {
    expect(hasRequiredRole('USER')).toBe(true)
  })

  it('distinguishes ADMIN and USER', () => {
    expect(hasRequiredRole('ADMIN', ['ADMIN']))
      .toBe(true)

    expect(hasRequiredRole('USER', ['ADMIN']))
      .toBe(false)

    expect(hasRequiredRole('USER', ['USER']))
      .toBe(true)
  })
})