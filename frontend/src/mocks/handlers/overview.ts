import { http, HttpResponse } from 'msw'

import { brief, canViewProject, currentUser, taskById } from '../selectors'
import { mockState } from '../store'
import type { TaskStatus } from '../types'
import { notFound, unauthorized } from '../utils'

const taskStatuses: readonly TaskStatus[] = [
  'NOT_STARTED',
  'IN_PROGRESS',
  'BLOCKED',
  'COMPLETED',
  'CANCELLED',
]

export const overviewHandlers = [
  http.get('/api/overview', ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const projectId = new URL(request.url).searchParams.get('projectId')
    const visibleProjects = mockState.projects
      .filter((project) => canViewProject(project, actor))
      .filter((project) => !projectId || project.id === projectId)
    if (projectId && visibleProjects.length === 0) return notFound(request)
    const projectIds = new Set(visibleProjects.map((project) => project.id))
    const tasks = mockState.tasks.filter((task) => projectIds.has(task.projectId))
    const summaries = mockState.summaries.filter((summary) => projectIds.has(summary.projectId))
    const members = new Set(
      mockState.members.filter((member) => projectIds.has(member.projectId)).map((member) => member.userId),
    )
    const today = new Date(mockState.clock).toISOString().slice(0, 10)
    const recentActivities = [
      ...visibleProjects.map((project) => ({
        type: 'PROJECT_CREATED', resourceId: project.id, summary: `创建项目“${project.name}”`,
        occurredAt: project.createdAt,
      })),
      ...tasks.map((task) => ({
        type: 'TASK_CREATED', resourceId: task.id, summary: `创建任务“${task.title}”`,
        occurredAt: task.createdAt,
      })),
      ...mockState.taskLogs.filter((log) => {
        const task = taskById(log.taskId)
        return task ? projectIds.has(task.projectId) : false
      }).map((log) => {
        const task = taskById(log.taskId)
        return {
          type: 'TASK_PROGRESS_ADDED', resourceId: log.taskId,
          summary: `${brief(log.operatorId).displayName}将“${task?.title ?? '任务'}”的进度更新为 ${log.progressPercent}%`,
          occurredAt: log.createdAt,
        }
      }),
      ...summaries.map((summary) => ({
        type: 'SUMMARY_CREATED', resourceId: summary.id, summary: '创建项目总结',
        occurredAt: summary.createdAt,
      })),
    ].sort((left, right) => right.occurredAt.localeCompare(left.occurredAt)).slice(0, 10)

    return HttpResponse.json({
      counts: {
        projects: visibleProjects.length,
        tasks: tasks.length,
        completedTasks: tasks.filter((task) => task.status === 'COMPLETED').length,
        overdueTasks: tasks.filter(
          (task) => task.dueDate !== null && task.dueDate < today
            && !['COMPLETED', 'CANCELLED'].includes(task.status),
        ).length,
        summaries: summaries.length,
        members: members.size,
      },
      tasksByStatus: taskStatuses.map((status) => ({
        status,
        count: tasks.filter((task) => task.status === status).length,
      })),
      recentActivities,
    })
  }),
]
