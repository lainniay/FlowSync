export type SystemRole = 'ADMIN' | 'USER'

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