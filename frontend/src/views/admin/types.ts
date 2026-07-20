import type {
  Page,
  PaginationQuery,
  SystemRole,
  User,
} from '@/shared/api/types'

export type UserPage = Page<User>

export type UserListQuery = PaginationQuery & {
  readonly q?: string
  readonly systemRole?: SystemRole
  readonly active?: boolean
}

export type UserListFilters = {
  q: string
  systemRole: SystemRole | ''
  active: boolean
}

export type CreateUserRequest = {
  readonly username: string
  readonly initialPassword: string
  readonly displayName: string
  readonly systemRole: SystemRole
  readonly phone: string | null
  readonly email: string | null
}

export type UpdateUserRequest = {
  readonly displayName: string
  readonly phone: string | null
  readonly email: string | null
  readonly systemRole: SystemRole
  readonly active: boolean
}

export type ResetUserPasswordRequest = {
  readonly newPassword: string
}
