import type { TaskStatus } from '@/shared/api/types'

export type OverviewCounts = {
  readonly projects: number
  readonly tasks: number
  readonly completedTasks: number
  readonly overdueTasks: number
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

export type Overview = {
  readonly counts: OverviewCounts
  readonly tasksByStatus: readonly TaskStatusCount[]
  readonly recentActivities: readonly RecentActivity[]
}

export type OverviewQuery = {
  readonly projectId?: string
}
