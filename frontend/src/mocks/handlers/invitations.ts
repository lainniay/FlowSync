import { http, HttpResponse } from 'msw'

import {
  canManageProject,
  currentUser,
  invitationView,
  isMember,
  projectById,
  userById,
} from '../selectors'
import { mockState, replaceRecord } from '../store'
import type { InvitationStatus } from '../types'
import {
  conflict, forbidden, invalid, nextId, notFound, now, problem, readJson, unauthorized,
  validateEnumQuery,
} from '../utils'

type InvitationBody = { readonly userIds: readonly string[] }
type InvitationStatusBody = { readonly status: InvitationStatus }

export const invitationHandlers = [
  http.get<{ projectId: string }>(
    '/api/projects/:projectId/invitations',
    ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const project = projectById(params.projectId)
      if (!project) return notFound(request)
      if (!canManageProject(project, actor)) return forbidden(request)
      return HttpResponse.json(
        mockState.invitations
          .filter((invitation) => invitation.projectId === project.id)
          .map(invitationView),
      )
    },
  ),

  http.post<{ projectId: string }, InvitationBody>(
    '/api/projects/:projectId/invitations',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const project = projectById(params.projectId)
      if (!project) return notFound(request)
      if (project.ownerId !== actor.id) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能邀请用户')
      const body = await readJson<InvitationBody>(request)
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
      }
      for (const [index, userId] of body.userIds.entries()) {
        const field = `userIds[${index}]`
        const user = userById(userId)
        if (!user || !user.active || user.systemRole !== 'USER') {
          return invalid(request, `用户 ${userId} 不是有效的 USER`, [
            { field, code: 'INVALID_USER', message: '被邀请人必须是有效的 USER' },
          ])
        }
        if (isMember(project.id, userId)) {
          return conflict(request, 'MEMBER_ALREADY_EXISTS', `用户 ${userId} 已是项目成员`, [
            { field, code: 'MEMBER_ALREADY_EXISTS', message: '该用户已经是项目成员' },
          ])
        }
        if (mockState.invitations.some(
          (invitation) => invitation.projectId === project.id
            && invitation.inviteeId === userId && invitation.status === 'PENDING',
        )) {
          return conflict(request, 'INVITATION_ALREADY_PENDING', `用户 ${userId} 已有待处理邀请`, [
            { field, code: 'INVITATION_ALREADY_PENDING', message: '该用户已有待处理邀请' },
          ])
        }
      }
      const createdAt = now()
      const invitations = body.userIds.map((userId) => {
        const existing = mockState.invitations.find(
          (invitation) => invitation.projectId === project.id && invitation.inviteeId === userId,
        )
        const invitation = existing
          ? { ...existing, invitedById: actor.id, status: 'PENDING' as const, createdAt, respondedAt: null }
          : {
              id: nextId('invitation'), projectId: project.id, inviteeId: userId,
              invitedById: actor.id, status: 'PENDING' as const, createdAt, respondedAt: null,
            }
        if (existing) replaceRecord(mockState.invitations, invitation)
        else mockState.invitations.push(invitation)
        return invitation
      })
      return HttpResponse.json(invitations.map(invitationView), { status: 201 })
    },
  ),

  http.delete<{ projectId: string; invitationId: string }>(
    '/api/projects/:projectId/invitations/:invitationId',
    ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const project = projectById(params.projectId)
      const invitation = mockState.invitations.find(
        (candidate) => candidate.id === params.invitationId && candidate.projectId === params.projectId,
      )
      if (!project || !invitation) return notFound(request)
      if (!canManageProject(project, actor)) return forbidden(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能取消邀请')
      if (invitation.status !== 'PENDING') {
        return conflict(request, 'INVALID_INVITATION_STATE', '只能取消待处理邀请')
      }
      replaceRecord(mockState.invitations, {
        ...invitation,
        status: 'CANCELLED',
        respondedAt: now(),
      })
      return new HttpResponse(null, { status: 204 })
    },
  ),

  http.get('/api/project-invitations', ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const queryError = validateEnumQuery(
      request,
      'status',
      ['PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'],
    )
    if (queryError) return queryError
    const status = new URL(request.url).searchParams.get('status')
    return HttpResponse.json(
      mockState.invitations
        .filter((invitation) => invitation.inviteeId === actor.id)
        .filter((invitation) => !status || invitation.status === status)
        .map(invitationView),
    )
  }),

  http.put<{ invitationId: string }, InvitationStatusBody>(
    '/api/project-invitations/:invitationId',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      const invitation = mockState.invitations.find((candidate) => candidate.id === params.invitationId)
      if (!invitation) return notFound(request)
      if (invitation.inviteeId !== actor.id) {
        return problem(request, 403, 'INVITATION_NOT_OWNED', '无权处理邀请', '当前用户不是邀请接收人')
      }
      if (actor.systemRole !== 'USER') return forbidden(request)
      const project = projectById(invitation.projectId)
      if (!project) return notFound(request)
      if (project.archivedAt) return conflict(request, 'PROJECT_ARCHIVED', '归档项目不能处理邀请')
      if (invitation.status !== 'PENDING') {
        return conflict(request, 'INVALID_INVITATION_STATE', '邀请已经处理')
      }
      const body = await readJson<InvitationStatusBody>(request)
      if (body instanceof Response) return body
      if (!['ACCEPTED', 'REJECTED'].includes(body.status)) {
        return invalid(request, '邀请状态只能是 ACCEPTED 或 REJECTED')
      }
      const updated = { ...invitation, status: body.status, respondedAt: now() }
      replaceRecord(mockState.invitations, updated)
      if (body.status === 'ACCEPTED' && !isMember(invitation.projectId, actor.id)) {
        mockState.members.push({ projectId: invitation.projectId, userId: actor.id, joinedAt: now() })
      }
      return HttpResponse.json(invitationView(updated))
    },
  ),
]
