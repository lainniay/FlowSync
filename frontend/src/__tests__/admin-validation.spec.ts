import { describe, expect, it } from 'vitest'

import { getUsernameValidationError } from '@/views/admin/validation'

describe('getUsernameValidationError', () => {
  it('accepts boundary lengths 1, 2, and 50 characters', () => {
    expect(getUsernameValidationError('a')).toBeUndefined()
    expect(getUsernameValidationError('ab')).toBeUndefined()
    expect(getUsernameValidationError('a'.repeat(50))).toBeUndefined()
  })

  it('rejects empty and 51-character usernames', () => {
    expect(getUsernameValidationError('')).toBe(
      '用户名长度为 1 到 50 个字符',
    )
    expect(getUsernameValidationError('a'.repeat(51))).toBe(
      '用户名长度为 1 到 50 个字符',
    )
  })
})
