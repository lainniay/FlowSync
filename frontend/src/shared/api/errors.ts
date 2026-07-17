import axios from 'axios'

import type { ProblemDetails } from './types'

export function getApiErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (!axios.isAxiosError<ProblemDetails>(error)) {
    return fallback
  }

  const detail = error.response?.data?.detail

  return typeof detail === 'string' && detail
    ? detail
    : fallback
}

export function hasApiStatus(
  error: unknown,
  status: number,
): boolean {
  return (
    axios.isAxiosError(error)
    && error.response?.status === status
  )
}