import { getCsrfHeaders } from '@/shared/api/csrf'
import { http } from '@/shared/api/http'
import type { User } from '@/shared/api/types'

import type {
  ChangePasswordRequest,
  UpdateProfileRequest,
} from './types'

export async function updateProfile(
  payload: UpdateProfileRequest,
): Promise<User> {
  const headers = await getCsrfHeaders()

  const response = await http.put<User>(
    '/users/me',
    payload,
    { headers },
  )

  return response.data
}

export async function changePassword(
  payload: ChangePasswordRequest,
): Promise<void> {
  const headers = await getCsrfHeaders()

  await http.put<void>(
    '/users/me/password',
    payload,
    { headers },
  )
}
