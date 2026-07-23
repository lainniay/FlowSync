import { getCsrfHeaders } from '@/shared/api/csrf'
import { http } from '@/shared/api/http'
import type { User } from '@/shared/api/types'

import type {
  CreateUserRequest,
  ResetUserPasswordRequest,
  UpdateUserRequest,
  UserListQuery,
  UserPage,
} from './types'

export async function getUsers(
  query: UserListQuery,
): Promise<UserPage> {
  const response = await http.get<UserPage>(
    '/users',
    { params: query },
  )

  return response.data
}

export async function getUser(
  userId: string,
): Promise<User> {
  const response = await http.get<User>(
    `/users/${userId}`,
  )

  return response.data
}

export async function createUser(
  payload: CreateUserRequest,
): Promise<User> {
  const headers = await getCsrfHeaders()

  const response = await http.post<User>(
    '/users',
    payload,
    { headers },
  )

  return response.data
}

export async function updateUser(
  userId: string,
  payload: UpdateUserRequest,
): Promise<User> {
  const headers = await getCsrfHeaders()

  const response = await http.put<User>(
    `/users/${userId}`,
    payload,
    { headers },
  )

  return response.data
}

export async function resetUserPassword(
  userId: string,
  payload: ResetUserPasswordRequest,
): Promise<void> {
  const headers = await getCsrfHeaders()

  await http.put<void>(
    `/users/${userId}/password`,
    payload,
    { headers },
  )
}
