import { http } from './http'
import type { CsrfTokenResponse } from './types'

export async function getCsrfHeaders(): Promise<Record<string, string>> {
  const response = await http.get<CsrfTokenResponse>('/auth/csrf')

  return {
    [response.data.headerName]: response.data.token,
  }
}