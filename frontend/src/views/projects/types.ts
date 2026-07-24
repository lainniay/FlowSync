import type {
  InvitationStatus,
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
  readonly userId?: string
  readonly myRole?: 'OWNER' | 'MEMBER'
  readonly archived?: boolean
}

export type ProjectListFilters = {
  q: string
  status: ProjectStatus | ''
  userId: string
  myRole: '' | 'OWNER' | 'MEMBER'
  archived: boolean
}

export type UserOption = {
  readonly id: string
  readonly username: string
}

export type CreateProjectRequest = {
  readonly name: string
  readonly description: string | null
  readonly status: ProjectStatus
  readonly priority: Priority
  readonly startDate: string | null
  readonly endDate: string | null
  readonly ownerId?: string | null
}

export type UpdateProjectRequest = {
  readonly name: string
  readonly description: string | null
  readonly status: ProjectStatus
  readonly priority: Priority
  readonly startDate: string | null
  readonly endDate: string | null
}

export type TransferProjectOwnerRequest = {
  readonly ownerId: string
}

export type ProjectMember = {
  readonly user: UserBrief
  readonly joinedAt: string
}

export type ProjectInvitation = {
  readonly id: string
  readonly project: {
    readonly id: string
    readonly name: string
  }
  readonly invitee: UserBrief
  readonly invitedBy: UserBrief
  readonly status: InvitationStatus
  readonly createdAt: string
  readonly respondedAt: string | null
}

export type BatchUserIdsRequest = {
  readonly userIds: readonly string[]
}

export type InvitationCandidate = {
  readonly id: string
  readonly displayName: string
  readonly username: string
}
