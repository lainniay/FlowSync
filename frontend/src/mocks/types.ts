export type SystemRole = 'ADMIN' | 'USER'
export type ProjectStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED'
export type TaskStatus =
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'BLOCKED'
  | 'COMPLETED'
  | 'CANCELLED'
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH'
export type SummaryType = 'STAGE' | 'FINAL'
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED'

export type UserRecord = {
  readonly id: string
  readonly username: string
  readonly password: string
  readonly displayName: string
  readonly phone: string | null
  readonly email: string | null
  readonly systemRole: SystemRole
  readonly active: boolean
  readonly createdAt: string
  readonly updatedAt: string
}

export type ProjectRecord = {
  readonly id: string
  readonly ownerId: string
  readonly name: string
  readonly description: string | null
  readonly status: ProjectStatus
  readonly priority: Priority
  readonly startDate: string | null
  readonly endDate: string | null
  readonly archivedAt: string | null
  readonly createdAt: string
  readonly updatedAt: string
}

export type MemberRecord = {
  readonly projectId: string
  readonly userId: string
  readonly joinedAt: string
}

export type InvitationRecord = {
  readonly id: string
  readonly projectId: string
  readonly inviteeId: string
  readonly invitedById: string
  readonly status: InvitationStatus
  readonly createdAt: string
  readonly respondedAt: string | null
}

export type TaskRecord = {
  readonly id: string
  readonly projectId: string
  readonly parentId: string | null
  readonly assigneeId: string | null
  readonly creatorId: string
  readonly title: string
  readonly description: string | null
  readonly status: TaskStatus
  readonly priority: Priority
  readonly dueDate: string | null
  readonly createdAt: string
  readonly updatedAt: string
}

export type TaskLogRecord = {
  readonly id: string
  readonly taskId: string
  readonly operatorId: string
  readonly progressPercent: number
  readonly content: string
  readonly createdAt: string
}

export type SummaryRecord = {
  readonly id: string
  readonly projectId: string
  readonly taskId: string | null
  readonly createdById: string
  readonly type: SummaryType
  readonly content: string
  readonly createdAt: string
  readonly updatedAt: string
}

export type MockState = {
  users: UserRecord[]
  projects: ProjectRecord[]
  members: MemberRecord[]
  invitations: InvitationRecord[]
  tasks: TaskRecord[]
  taskLogs: TaskLogRecord[]
  summaries: SummaryRecord[]
  currentUserId: string | null
  clock: number
  sequences: {
    user: number
    project: number
    invitation: number
    task: number
    taskLog: number
    summary: number
  }
}

export type Page<T> = {
  readonly items: readonly T[]
  readonly page: number
  readonly size: number
  readonly totalElements: number
  readonly totalPages: number
}

export type UserBrief = {
  readonly id: string
  readonly displayName: string
}

export type AiTaskPlanItem = {
  readonly draftId: string
  readonly parentDraftId: string | null
  readonly title: string
  readonly description: string | null
  readonly priority: Priority
  readonly dueDate: string | null
  readonly assigneeId: string | null
}
