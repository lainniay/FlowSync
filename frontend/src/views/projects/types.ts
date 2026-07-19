import type {
  Page,
  PaginationQuery,
  Priority,
  ProjectStatus,
  UserBrief,
} from '@/shared/api/types'

export type ProjectTaskStats = {
  readonly total: number
  readonly completed: number
}

export type Project = {
  readonly id: string
  readonly owner: UserBrief
  readonly name: string
  readonly description: string | null
  readonly status: ProjectStatus
  readonly priority: Priority
  readonly startDate: string | null
  readonly endDate: string | null
  readonly archivedAt: string | null
  readonly memberCount: number
  readonly taskStats: ProjectTaskStats
  readonly createdAt: string
  readonly updatedAt: string
}

export type ProjectPage = Page<Project>

export type ProjectListQuery = PaginationQuery & {
  readonly q?: string
  readonly status?: ProjectStatus
  readonly ownerId?: string
  readonly archived?: boolean
}

export type ProjectListFilters = {
  q: string
  status: ProjectStatus | ''
  ownerId: string
  archived: boolean
}