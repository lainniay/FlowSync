import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  http,
  isSessionExpiryResponse,
  setUnauthorizedHandler,
} from '@/shared/api/http'

function axiosError(
  status: number,
  url: string,
): unknown {
  return {
    isAxiosError: true,
    config: {
      url,
    },
    response: {
      status,
    },
  }
}

function rejectedRequest(
  url: string,
  status: number,
): Promise<unknown> {
  return http.request({
    url,
    method: 'get',
    adapter: async (config) => Promise.reject({
      isAxiosError: true,
      config,
      response: {
        status,
      },
    }),
  })
}

afterEach(() => {
  setUnauthorizedHandler(null)
})

describe('isSessionExpiryResponse', () => {
  it('recognizes a business API 401', () => {
    expect(
      isSessionExpiryResponse(
        axiosError(401, '/projects'),
      ),
    ).toBe(true)
  })

  it('does not treat invalid login as session expiry', () => {
    expect(
      isSessionExpiryResponse(
        axiosError(401, '/auth/login'),
      ),
    ).toBe(false)
  })

  it('does not treat initial current-user 401 as expiry', () => {
    expect(
      isSessionExpiryResponse(
        axiosError(401, '/users/me'),
      ),
    ).toBe(false)
  })

  it('ignores non-401 responses', () => {
    expect(
      isSessionExpiryResponse(
        axiosError(403, '/projects'),
      ),
    ).toBe(false)
  })
})

describe('HTTP unauthorized handler', () => {
  it('calls the handler for a business API 401', async () => {
    const handler = vi.fn<() => void>()
    setUnauthorizedHandler(handler)

    await expect(
      rejectedRequest('/projects', 401),
    ).rejects.toBeDefined()

    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('does not call the handler for invalid login', async () => {
    const handler = vi.fn<() => void>()
    setUnauthorizedHandler(handler)

    await expect(
      rejectedRequest('/auth/login', 401),
    ).rejects.toBeDefined()

    expect(handler).not.toHaveBeenCalled()
  })

  it('does not call the handler for a forbidden response', async () => {
    const handler = vi.fn<() => void>()
    setUnauthorizedHandler(handler)

    await expect(
      rejectedRequest('/projects', 403),
    ).rejects.toBeDefined()

    expect(handler).not.toHaveBeenCalled()
  })
})