import { HttpResponse } from 'msw'

import { mockState } from './store'
import type { Page } from './types'

type Sequence = keyof MockStateSequences
type MockStateSequences = typeof mockState.sequences
type SortValue = string | number | null
type Sorters<T> = Readonly<Record<string, (item: T) => SortValue>>
type ProblemFieldError = {
  readonly field: string
  readonly code: string
  readonly message: string
}

export function hasFields(value: object, fields: readonly string[]): boolean {
  return fields.every((field) => Object.prototype.hasOwnProperty.call(value, field))
}

export function nextId(sequence: Sequence): string {
  const id = mockState.sequences[sequence]
  mockState.sequences[sequence] += 1
  return String(id)
}

export function now(): string {
  mockState.clock += 60_000
  return new Date(mockState.clock).toISOString()
}

export function problem(
  request: Request,
  status: number,
  code: string,
  title: string,
  detail: string,
  errors: readonly ProblemFieldError[] = [],
): Response {
  return HttpResponse.json(
    {
      type: `urn:flowsync:problem:${code.toLowerCase().replace(/_/g, '-')}`,
      title,
      status,
      detail,
      instance: new URL(request.url).pathname,
      code,
      errors,
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

export async function readJson<T extends object>(request: Request): Promise<T | Response> {
  try {
    const body: T = await request.json()
    if (body === null || typeof body !== 'object' || Array.isArray(body)) {
      return problem(request, 400, 'BAD_REQUEST', '请求格式错误', 'JSON 请求体必须是对象')
    }
    return body
  } catch (error) {
    if (error instanceof SyntaxError) {
      return problem(request, 400, 'BAD_REQUEST', '请求格式错误', '请求体不是有效的 JSON')
    }
    throw error
  }
}

export function isDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
}

export function isStringOrNull(value: unknown, maxLength: number): value is string | null {
  return value === null || (typeof value === 'string' && value.length <= maxLength)
}

export function validateEnumQuery(
  request: Request,
  name: string,
  allowed: readonly string[],
): Response | null {
  const value = new URL(request.url).searchParams.get(name)
  return value === null || allowed.includes(value)
    ? null
    : invalid(request, `${name} 参数不符合要求`, [
        { field: name, code: 'INVALID_VALUE', message: `${name} 参数不符合要求` },
      ])
}

export function validateBooleanQuery(request: Request, name: string): Response | null {
  return validateEnumQuery(request, name, ['true', 'false'])
}

export function validateDateQuery(request: Request, name: string): Response | null {
  const value = new URL(request.url).searchParams.get(name)
  return value === null || isDate(value)
    ? null
    : invalid(request, `${name} 必须是有效日期`, [
        { field: name, code: 'INVALID_DATE', message: `${name} 必须是有效日期` },
      ])
}

export function unauthorized(request: Request): Response {
  return problem(request, 401, 'UNAUTHORIZED', '未登录', '请先登录后再操作')
}

export function forbidden(request: Request): Response {
  return problem(request, 403, 'FORBIDDEN', '权限不足', '当前用户不能执行此操作')
}

export function notFound(request: Request): Response {
  return problem(request, 404, 'NOT_FOUND', '资源不存在', '资源不存在或当前用户不可见')
}

export function conflict(
  request: Request,
  code: string,
  detail: string,
  errors: readonly ProblemFieldError[] = [],
): Response {
  return problem(request, 409, code, '操作冲突', detail, errors)
}

export function invalid(
  request: Request,
  detail: string,
  errors: readonly ProblemFieldError[] = [],
): Response {
  return problem(request, 422, 'VALIDATION_ERROR', '请求参数校验失败', detail, errors)
}

export function paginated<T>(
  values: readonly T[],
  request: Request,
  sorters: Sorters<T>,
): Response {
  const url = new URL(request.url)
  const page = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? 20)
  const [field = 'createdAt', direction = 'desc'] =
    (url.searchParams.get('sort') ?? 'createdAt,desc').split(',')
  const selector = sorters[field]
  if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) {
    return invalid(request, 'page 必须大于等于 0，size 必须为 1..100')
  }
  if (!selector || !['asc', 'desc'].includes(direction)) {
    return invalid(request, 'sort 字段或方向不受支持')
  }
  const items = [...values].sort((left, right) => compare(selector(left), selector(right), direction))
  const totalElements = items.length

  const result: Page<T> = {
    items: items.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements,
    totalPages: Math.ceil(totalElements / size),
  }
  return HttpResponse.json(result)
}

function compare(left: SortValue, right: SortValue, direction: string): number {
  const multiplier = direction === 'asc' ? 1 : -1
  if (left === right) return 0
  if (left === null) return multiplier
  if (right === null) return -multiplier
  return (left < right ? -1 : 1) * multiplier
}
