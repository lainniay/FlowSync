import { getCsrfHeaders } from './csrf'
import { http } from './http'
import type { LoginRequest, User } from './types'

export async function getCurrentUser(): Promise<User> {
  const response = await http.get<User>('/users/me')
  return response.data
}

export async function login(
  credentials: LoginRequest,
): Promise<User> {
  const headers = await getCsrfHeaders()

  const response = await http.post<User>(
    '/auth/login',
    credentials,
    { headers },
  )

  return response.data
}

export async function logout(): Promise<void> {
  const headers = await getCsrfHeaders()

  await http.post<void>('/auth/logout', undefined, {
    headers,
  })
}