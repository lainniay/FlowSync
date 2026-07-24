import type { ProjectStatus, TaskStatus } from '@/shared/api/types'

export const taskStatusColors: Record<TaskStatus, string> = {
  NOT_STARTED: '#94a3b8',
  IN_PROGRESS: '#2563eb',
  BLOCKED: '#f59e0b',
  COMPLETED: '#16a34a',
  CANCELLED: '#cbd5e1',
}

export const projectStatusColors: Record<ProjectStatus, string> = {
  NOT_STARTED: '#94a3b8',
  IN_PROGRESS: '#2563eb',
  COMPLETED: '#16a34a',
}

export const taskStatusLabels: Record<TaskStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  BLOCKED: '阻塞中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

export const projectStatusLabels: Record<ProjectStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
}
