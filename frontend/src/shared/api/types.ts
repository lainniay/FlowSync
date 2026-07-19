export type SystemRole = 'ADMIN' | 'USER'

export type ProjectStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'COMPLETED'

export type TaskStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'BLOCKED'
  | 'COMPLETED'
  | 'CANCELLED'

export type Priority =
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'

export type InvitationStatus =
  | 'PENDING'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'CANCELLED'

export type SummaryType =
  | 'STAGE'
  | 'FINAL'

export type Page<T> = {
  readonly items: readonly T[]
  readonly page: number
  readonly size: number
  readonly totalElements: number
  readonly totalPages: number
}

export type PaginationQuery = {
  readonly page?: number
  readonly size?: number
  readonly sort?: string
}

export type UserBrief = {
  readonly id: string
  readonly displayName: string
}

export type User = {
  readonly id: string
  readonly username: string
  readonly displayName: string
  readonly phone: string | null
  readonly email: string | null
  readonly systemRole: SystemRole
  readonly active: boolean
  readonly createdAt: string
  readonly updatedAt: string
}

export type LoginRequest = {
  readonly username: string
  readonly password: string
}

export type CsrfTokenResponse = {
  readonly token: string
  readonly headerName: string
}

export type ProblemFieldError = {
  readonly field: string
  readonly code: string
  readonly message: string
}

export type ProblemDetails = {
  readonly type: string
  readonly title: string
  readonly status: number
  readonly detail: string
  readonly instance: string
  readonly code: string
  readonly errors: readonly ProblemFieldError[]
}