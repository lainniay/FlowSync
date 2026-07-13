import { http, HttpResponse } from 'msw'

import {
  canManageProject,
  canViewProject,
  currentUser,
  isMember,
  memberView,
  projectById,
  userById,
} from '../selectors'
import { mockState, replaceRecord } from '../store'
import { conflict, forbidden, invalid, notFound, now, readJson, unauthorized } from '../utils'

type MemberBody = { readonly userIds: readonly string[] }

export const memberHandlers = [
  http.get<{ projectId: string }>('/api/projects/:projectId/members', ({ params, request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const project = projectById(params.projectId)
    if (!project || !canViewProject(project, actor)) return notFound(request)
    return HttpResponse.json(
      mockState.members.filter((member) => member.projectId === project.id).map(memberView),
    )
  }),

  http.post<{ projectId: string }, MemberBody>(
    '/api/projects/:projectId/members',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      if (actor.systemRole !== 'ADMIN') return forbidden(request)
      const project = projectById(params.projectId)
      if (!project) return notFound(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能添加成员')
      const body = await readJson<MemberBody>(request)
      if (body instanceof Response) return body
      if (!Array.isArray(body.userIds) || body.userIds.length === 0) {
        return invalid(request, 'userIds 不能为空')
      }
      const seen = new Set<string>()
      for (const [index, userId] of body.userIds.entries()) {
        const field = `userIds[${index}]`
        if (typeof userId !== 'string' || userId.length === 0) {
          return invalid(request, '用户 ID 不符合要求', [
            { field, code: 'INVALID_USER_ID', message: '用户 ID 必须是非空字符串' },
          ])
        }
        if (seen.has(userId)) {
          return invalid(request, 'userIds 不能重复', [
            { field, code: 'DUPLICATE', message: '用户 ID 不能重复' },
          ])
        }
        seen.add(userId)
        const user = userById(userId)
        if (!user || !user.active || user.systemRole !== 'USER') {
          return invalid(request, `用户 ${userId} 不是有效的 USER`, [
            { field, code: 'INVALID_USER', message: '项目成员必须是有效的 USER' },
          ])
        }
        if (isMember(project.id, userId)) {
          return conflict(request, 'MEMBER_ALREADY_EXISTS', `用户 ${userId} 已是项目成员`, [
            { field, code: 'MEMBER_ALREADY_EXISTS', message: '该用户已经是项目成员' },
          ])
        }
      }
      const joinedAt = now()
      const added = body.userIds.map((userId) => ({ projectId: project.id, userId, joinedAt }))
      mockState.members.push(...added)
      for (const invitation of mockState.invitations) {
        if (
          invitation.projectId === project.id
          && body.userIds.includes(invitation.inviteeId)
          && invitation.status === 'PENDING'
        ) {
          replaceRecord(mockState.invitations, {
            ...invitation,
            status: 'CANCELLED',
            respondedAt: now(),
          })
        }
      }
      return HttpResponse.json(added.map(memberView), { status: 201 })
    },
  ),

  http.delete<{ projectId: string; userId: string }>(
    '/api/projects/:projectId/members/:userId',
    ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const project = projectById(params.projectId)
      if (!project) return notFound(request)
      if (!canManageProject(project, actor)) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能移除成员')
      if (!isMember(project.id, params.userId)) return notFound(request)
      if (project.ownerId === params.userId) {
        return conflict(request, 'RESOURCE_IN_USE', '项目负责人不能被移除')
      }
      const hasTasks = mockState.tasks.some(
        (task) => task.projectId === project.id && task.assigneeId === params.userId
          && !['COMPLETED', 'CANCELLED'].includes(task.status),
      )
      if (hasTasks) {
        return conflict(request, 'MEMBER_HAS_ACTIVE_TASKS', '成员仍负责未完成任务')
      }
      mockState.members = mockState.members.filter(
        (member) => member.projectId !== project.id || member.userId !== params.userId,
      )
      return new HttpResponse(null, { status: 204 })
    },
  ),
]
