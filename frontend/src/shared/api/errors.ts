import axios from 'axios'

import type {
  ProblemDetails,
  ProblemFieldError,
} from './types'

export type ApiErrorKind =
  | 'unauthorized'
  | 'forbidden'
  | 'notFound'
  | 'conflict'
  | 'validation'
  | 'server'
  | 'network'
  | 'unknown'

export function getProblemDetails(
  error: unknown,
): ProblemDetails | null {
  if (!axios.isAxiosError(error)) {
    return null
  }

  const data = error.response?.data

  if (!data || typeof data !== 'object') {
    return null
  }

  const problem = data as Partial<ProblemDetails>

  if (
    typeof problem.status !== 'number'
    || typeof problem.detail !== 'string'
    || typeof problem.code !== 'string'
    || !Array.isArray(problem.errors)
  ) {
    return null
  }

  return data as ProblemDetails
}

export function getApiErrorMessage(
  error: unknown,
  fallback: string,
): string {
  return getProblemDetails(error)?.detail || fallback
}

export function getApiFieldErrors(
  error: unknown,
): readonly ProblemFieldError[] {
  return getProblemDetails(error)?.errors ?? []
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

export function getApiErrorKind(
  error: unknown,
): ApiErrorKind {
  if (!axios.isAxiosError(error)) {
    return 'unknown'
  }

  if (!error.response) {
    return 'network'
  }

  switch (error.response.status) {
    case 401:
      return 'unauthorized'
    case 403:
      return 'forbidden'
    case 404:
      return 'notFound'
    case 409:
      return 'conflict'
    case 422:
      return 'validation'
    default:
      return error.response.status >= 500
        ? 'server'
        : 'unknown'
  }
}