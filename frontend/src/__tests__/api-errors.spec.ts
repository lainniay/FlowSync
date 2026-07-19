import { describe, expect, it } from 'vitest'

import {
  getApiErrorKind,
  getApiErrorMessage,
  getApiFieldErrors,
  getProblemDetails,
} from '@/shared/api/errors'
import type { ProblemDetails } from '@/shared/api/types'

const validationProblem: ProblemDetails = {
  type: 'about:blank',
  title: 'Validation failed',
  status: 422,
  detail: '项目名称不能为空',
  instance: '/api/projects',
  code: 'VALIDATION_ERROR',
  errors: [
    {
      field: 'name',
      code: 'NotBlank',
      message: 'name must not be blank',
    },
  ],
}

function axiosError(
  status?: number,
  data?: unknown,
): unknown {
  return {
    isAxiosError: true,
    response: status === undefined
      ? undefined
      : {
          status,
          data,
        },
  }
}

describe('Problem Details helpers', () => {
  it('reads a valid Problem Details response', () => {
    const error = axiosError(422, validationProblem)

    expect(getProblemDetails(error))
      .toEqual(validationProblem)

    expect(getApiErrorMessage(error, 'fallback'))
      .toBe('项目名称不能为空')

    expect(getApiFieldErrors(error))
      .toEqual(validationProblem.errors)
  })

  it('uses the fallback for a non-problem response', () => {
    const error = axiosError(500, 'plain text')

    expect(getProblemDetails(error)).toBeNull()
    expect(getApiErrorMessage(error, '请求失败'))
      .toBe('请求失败')
  })
})

describe('getApiErrorKind', () => {
  it('classifies HTTP status codes', () => {
    expect(getApiErrorKind(axiosError(401)))
      .toBe('unauthorized')

    expect(getApiErrorKind(axiosError(403)))
      .toBe('forbidden')

    expect(getApiErrorKind(axiosError(404)))
      .toBe('notFound')

    expect(getApiErrorKind(axiosError(409)))
      .toBe('conflict')

    expect(getApiErrorKind(axiosError(422)))
      .toBe('validation')

    expect(getApiErrorKind(axiosError(503)))
      .toBe('server')
  })

  it('classifies missing responses as network errors', () => {
    expect(getApiErrorKind(axiosError()))
      .toBe('network')
  })

  it('classifies non-Axios errors as unknown', () => {
    expect(getApiErrorKind(new Error('unexpected')))
      .toBe('unknown')
  })
})