import axios from 'axios'

export type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

export const http = axios.create({
  baseURL: '/api',
  timeout: 10_000,
  withCredentials: true,
})

export function setUnauthorizedHandler(
  handler: UnauthorizedHandler | null,
): void {
  unauthorizedHandler = handler
}

export function isSessionExpiryResponse(
  error: unknown,
): boolean {
  if (
    !axios.isAxiosError(error)
    || error.response?.status !== 401
  ) {
    return false
  }

  const url = error.config?.url ?? ''
  const path = url.split('?')[0]

  return (
    path !== '/auth/login'
    && path !== '/users/me'
  )
}

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (isSessionExpiryResponse(error)) {
      unauthorizedHandler?.()
    }

    return Promise.reject(error)
  },
)