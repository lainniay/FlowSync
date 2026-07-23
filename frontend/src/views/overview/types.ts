import type { ProjectStatus, TaskStatus } from '@/shared/api/types'

export type OverviewCounts = {
  readonly projects: number
  readonly inProgressProjects: number
  readonly tasks: number
  readonly completedTasks: number
  readonly overdueTasks: number
  readonly blockedTasks: number
  readonly dueSoonTasks: number
  readonly myOverdueTasks: number
  readonly myBlockedTasks: number
  readonly myTodayDueTasks: number
  readonly staleBlockedTasks: number
  readonly summaries: number
  readonly members: number
}

export type TaskStatusCount = {
  readonly status: TaskStatus
  readonly count: number
}

export type RecentActivity = {
  readonly type: string
  readonly resourceId: string
  readonly summary: string
  readonly occurredAt: string
}

export type ProjectHealth = {
  readonly id: string
  readonly name: string
  readonly isOwner: boolean
  readonly status: ProjectStatus
  readonly endDate: string | null
  readonly tasks: number
  readonly completedTasks: number
  readonly overdueTasks: number
  readonly blockedTasks: number
}

export type Overview = {
  readonly counts: OverviewCounts
  readonly tasksByStatus: readonly TaskStatusCount[]
  readonly projectHealth: readonly ProjectHealth[]
  readonly recentActivities: readonly RecentActivity[]
}

export type AdminOverviewCounts = {
  readonly activeUsers: number
  readonly inactiveUsers: number
  readonly users: number
  readonly admins: number
  readonly projects: number
  readonly inProgressProjects: number
  readonly tasks: number
  readonly completedTasks: number
  readonly overdueTasks: number
  readonly overdueProjects: number
}

export type AdminFocusProject = {
  readonly id: string
  readonly name: string
  readonly ownerId: string
  readonly ownerName: string
  readonly endDate: string | null
  readonly tasks: number
  readonly completedTasks: number
  readonly overdueTasks: number
  readonly blockedTasks: number
}

export type AdminOverview = {
  readonly counts: AdminOverviewCounts
  readonly projectsByStatus: readonly {
    readonly status: ProjectStatus
    readonly count: number
  }[]
  readonly tasksByStatus: readonly TaskStatusCount[]
  readonly focusProjects: readonly AdminFocusProject[]
  readonly recentActivities: readonly RecentActivity[]
}

export type OverviewQuery = {
  readonly projectId?: string
}
