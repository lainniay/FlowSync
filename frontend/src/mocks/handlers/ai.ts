import { http, HttpResponse } from 'msw'

import {
  currentUser,
  isMember,
  isProjectOwner,
  projectById,
  taskById,
  taskView,
  userById,
} from '../selectors'
import { mockState } from '../store'
import type { AiTaskPlanItem, TaskRecord } from '../types'
import {
  conflict, forbidden, hasFields, invalid, isDate, nextId, notFound, now, readJson, unauthorized,
} from '../utils'

type SuggestionBody = { readonly taskId: string; readonly focus?: string }
type GeneratePlanBody = {
  readonly goal: string
  readonly description?: string
  readonly constraints?: {
    readonly maxItems?: number
    readonly targetEndDate?: string | null
  }
}
type ImportPlanBody = { readonly items: readonly AiTaskPlanItem[] }

export const aiHandlers = [
  http.post<never, SuggestionBody>('/api/ai/task-suggestions', async ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const body = await readJson<SuggestionBody>(request)
    if (body instanceof Response) return body
    if (typeof body.taskId !== 'string'
      || (body.focus !== undefined && typeof body.focus !== 'string')) {
      return invalid(request, 'AI 建议参数不符合要求')
    }
    const task = taskById(body.taskId)
    const project = task ? projectById(task.projectId) : undefined
    if (!task || !project) return notFound(request)
    if (!isProjectOwner(project, actor)) return forbidden(request)
    if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能生成建议')
    const focus = body.focus ? `重点关注${body.focus}。` : ''
    return HttpResponse.json({
      suggestion: `${focus}建议先确认“${task.title}”的依赖，再按优先级推进并及时记录进度。`,
      generatedAt: now(),
    })
  }),

  http.post<{ projectId: string }, GeneratePlanBody>(
    '/api/projects/:projectId/ai/task-plans',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const project = projectById(params.projectId)
      if (!project) return notFound(request)
      if (!isProjectOwner(project, actor)) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能生成计划')
      const body = await readJson<GeneratePlanBody>(request)
      if (body instanceof Response) return body
      if (typeof body.goal !== 'string' || !body.goal.trim()
        || (body.description !== undefined && typeof body.description !== 'string')
        || (body.constraints !== undefined
          && (typeof body.constraints !== 'object' || body.constraints === null
            || Array.isArray(body.constraints)))) {
        return invalid(request, 'AI 计划参数不符合要求')
      }
      const maxItems = body.constraints?.maxItems ?? 10
      if (!Number.isInteger(maxItems) || maxItems < 1 || maxItems > 20) {
        return invalid(request, 'maxItems 必须为 1..20 的整数')
      }
      const targetEndDate = body.constraints?.targetEndDate
      if (targetEndDate !== undefined && targetEndDate !== null && !isDate(targetEndDate)) {
        return invalid(request, 'targetEndDate 必须是有效日期')
      }
      const ownerId = project.ownerId
      const templates: readonly Omit<AiTaskPlanItem, 'draftId' | 'parentDraftId'>[] = [
        {
          title: `分析：${body.goal}`.slice(0, 100),
          description: body.description ?? '确认范围、依赖和验收标准',
          priority: 'HIGH', dueDate: null, assigneeId: ownerId,
        },
        {
          title: '实现核心功能', description: '按接口约定完成可演示的核心流程', priority: 'HIGH',
          dueDate: targetEndDate ?? null, assigneeId: ownerId,
        },
        {
          title: '测试与整理演示', description: '完成测试、修复问题并准备演示材料', priority: 'MEDIUM',
          dueDate: targetEndDate ?? null, assigneeId: null,
        },
      ]
      const items = templates.slice(0, maxItems).map((template, index) => ({
        draftId: `d${index + 1}`,
        parentDraftId: index === 0 ? null : 'd1',
        ...template,
      }))
      return HttpResponse.json({
        overview: `已将“${body.goal}”拆分为 ${items.length} 个可编辑任务。`,
        items,
        generatedAt: now(),
      })
    },
  ),

  http.post<{ projectId: string }, ImportPlanBody>(
    '/api/projects/:projectId/ai/task-plans/imports',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const project = projectById(params.projectId)
      if (!project) return notFound(request)
      if (!isProjectOwner(project, actor)) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能导入计划')
      const body = await readJson<ImportPlanBody>(request)
      if (body instanceof Response) return body
      if (!Array.isArray(body.items) || body.items.length === 0 || body.items.length > 20) {
        return invalid(request, 'items 数量必须为 1..20')
      }
      const draftIds = new Set<string>()
      for (const [index, item] of body.items.entries()) {
        if (typeof item !== 'object' || item === null || !hasFields(item, [
          'draftId', 'parentDraftId', 'title', 'description', 'priority', 'dueDate', 'assigneeId',
        ])) return invalidItem(request, index, '', '任务草稿必须包含全部字段')
        if (typeof item.draftId !== 'string' || item.draftId.length === 0) {
          return invalidItem(request, index, 'draftId', 'draftId 必须是非空字符串')
        }
        if (draftIds.has(item.draftId)) {
          return invalidItem(request, index, 'draftId', 'draftId 不能重复')
        }
        draftIds.add(item.draftId)
        if (item.parentDraftId !== null && typeof item.parentDraftId !== 'string') {
          return invalidItem(request, index, 'parentDraftId', 'parentDraftId 类型不符合要求')
        }
        if (typeof item.title !== 'string' || !item.title.trim() || item.title.length > 100) {
          return invalidItem(request, index, 'title', '标题长度必须为 1..100')
        }
        if (item.description !== null && (typeof item.description !== 'string'
          || item.description.length > 5000)) {
          return invalidItem(request, index, 'description', '说明最多为 5000 字符')
        }
        if (!['LOW', 'MEDIUM', 'HIGH'].includes(item.priority)) {
          return invalidItem(request, index, 'priority', '优先级不符合要求')
        }
        if (item.dueDate !== null && !isDate(item.dueDate)) {
          return invalidItem(request, index, 'dueDate', '截止日期不符合要求')
        }
        if (item.assigneeId !== null && typeof item.assigneeId !== 'string') {
          return invalidItem(request, index, 'assigneeId', '负责人 ID 类型不符合要求')
        }
      }
      for (const [index, item] of body.items.entries()) {
        if (item.parentDraftId && !draftIds.has(item.parentDraftId)) {
          return invalidItem(request, index, 'parentDraftId', 'parentDraftId 必须引用本次计划')
        }
        if (item.assigneeId && (!isMember(project.id, item.assigneeId)
          || !userById(item.assigneeId)?.active)) {
          return invalidItem(request, index, 'assigneeId', '推荐负责人必须是有效的项目成员')
        }
        if (item.dueDate && ((project.startDate && item.dueDate < project.startDate)
          || (project.endDate && item.dueDate > project.endDate))) {
          return invalidItem(request, index, 'dueDate', '任务截止日期必须在项目日期范围内')
        }
      }
      if (hasDraftCycle(body.items)) {
        return invalid(request, '任务草稿不能形成循环', [
          { field: 'items', code: 'INVALID_RELATION', message: '任务草稿不能形成循环' },
        ])
      }
      const taskIds = new Map(body.items.map((item) => [item.draftId, nextId('task')]))
      const timestamp = now()
      const tasks: TaskRecord[] = []
      for (const item of body.items) {
        const parentId = item.parentDraftId ? taskIds.get(item.parentDraftId) : null
        if (parentId === undefined) return invalid(request, '父任务不存在')
        const id = taskIds.get(item.draftId)
        if (!id) return invalid(request, '任务草稿 ID 无效')
        tasks.push({
          id, projectId: project.id, parentId, assigneeId: item.assigneeId, creatorId: actor.id,
          title: item.title, description: item.description, status: 'NOT_STARTED',
          priority: item.priority, dueDate: item.dueDate, createdAt: timestamp, updatedAt: timestamp,
        })
      }
      mockState.tasks.push(...tasks)
      return HttpResponse.json(
        { importedCount: tasks.length, tasks: tasks.map(taskView) },
        { status: 201 },
      )
    },
  ),
]

function invalidItem(request: Request, index: number, field: string, message: string): Response {
  const path = field ? `items[${index}].${field}` : `items[${index}]`
  return invalid(request, message, [{ field: path, code: 'INVALID_VALUE', message }])
}

function hasDraftCycle(items: readonly AiTaskPlanItem[]): boolean {
  const parentById = new Map(items.map((item) => [item.draftId, item.parentDraftId]))
  for (const item of items) {
    const visited = new Set<string>()
    let current: string | null | undefined = item.draftId
    while (current) {
      if (visited.has(current)) return true
      visited.add(current)
      current = parentById.get(current)
    }
  }
  return false
}
