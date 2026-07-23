import type {
  Page,
  PaginationQuery,
  Priority,
  TaskStatus,
  UserBrief,
} from '@/shared/api/types'

export type Task = {
  readonly id: string
  readonly projectId: string
  readonly parentId: string | null
  readonly assignee: UserBrief | null
  readonly creator: UserBrief
  readonly title: string
  readonly description: string | null
  readonly status: TaskStatus
  readonly priority: Priority
  readonly progressPercent: number
  readonly dueDate: string | null
  readonly createdAt: string
  readonly updatedAt: string
}

export type TaskPage = Page<Task>

export type TaskListQuery = PaginationQuery & {
  readonly projectId?: string
  readonly assigneeId?: string
  readonly status?: TaskStatus
  readonly priority?: Priority
  readonly parentId?: string
  readonly dueBefore?: string
  readonly dueAfter?: string
  readonly incomplete?: boolean
  readonly q?: string
}

export type TaskListFilters = {
  q: string
  projectId: string
  status: TaskStatus | ''
  priority: Priority | ''
  dueBefore: string
  dueAfter: string
  incomplete: boolean
}

export type CreateTaskBody = {
  readonly projectId: string
  readonly parentId: string | null
  readonly title: string
  readonly description: string | null
  readonly assigneeId: string | null
  readonly status: TaskStatus
  readonly priority: Priority
  readonly dueDate: string | null
}

export type UpdateTaskBody = {
  readonly parentId: string | null
  readonly title: string
  readonly description: string | null
  readonly assigneeId: string | null
  readonly status: TaskStatus
  readonly priority: Priority
  readonly dueDate: string | null
}

export type TaskStatusBody = {
  readonly status: TaskStatus
}

export type TaskLog = {
  readonly id: string
  readonly taskId: string
  readonly operator: UserBrief
  readonly progressPercent: number
  readonly content: string
  readonly createdAt: string
}

export type TaskLogPage = Page<TaskLog>

export type CreateTaskLogBody = {
  readonly progressPercent: number
  readonly content: string
}
