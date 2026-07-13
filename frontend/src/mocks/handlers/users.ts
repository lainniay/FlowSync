import { http, HttpResponse } from 'msw'

import { currentUser, publicUser, userById } from '../selectors'
import { mockState, replaceRecord } from '../store'
import type { SystemRole } from '../types'
import {
  conflict, forbidden, hasFields, invalid, isStringOrNull, nextId, notFound, now, paginated,
  readJson, unauthorized, validateBooleanQuery, validateEnumQuery,
} from '../utils'

type CreateUserBody = {
  readonly username: string
  readonly initialPassword: string
  readonly displayName: string
  readonly systemRole: SystemRole
  readonly phone?: string | null
  readonly email?: string | null
}
type UpdateUserBody = {
  readonly displayName: string
  readonly phone: string | null
  readonly email: string | null
  readonly systemRole: SystemRole
  readonly active: boolean
}
type ResetPasswordBody = { readonly newPassword: string }

export const userHandlers = [
  http.get('/api/users', ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const queryError = validateBooleanQuery(request, 'active')
      ?? validateEnumQuery(request, 'systemRole', ['ADMIN', 'USER'])
    if (queryError) return queryError
    const url = new URL(request.url)
    const query = url.searchParams.get('q')?.toLowerCase()
    const role = url.searchParams.get('systemRole')
    const activeValue = url.searchParams.get('active')
    const active = activeValue === null ? true : activeValue === 'true'
    const users = mockState.users
      .filter((user) => user.active === active)
      .filter((user) => !role || user.systemRole === role)
      .filter((user) => !query || `${user.username} ${user.displayName}`.toLowerCase().includes(query))
      .map(publicUser)
    return paginated(users, request, {
        createdAt: (user) => user.createdAt,
        username: (user) => user.username,
        displayName: (user) => user.displayName,
      })
  }),

  http.post<never, CreateUserBody>('/api/users', async ({ request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    if (actor.systemRole !== 'ADMIN') return forbidden(request)
    const body = await readJson<CreateUserBody>(request)
    if (body instanceof Response) return body
    if (typeof body.username !== 'string' || !/^[A-Za-z0-9._-]{3,50}$/.test(body.username)
      || typeof body.displayName !== 'string' || !body.displayName.trim()
      || body.displayName.length > 50 || !validPassword(body.initialPassword)
      || !['ADMIN', 'USER'].includes(body.systemRole)
      || (body.phone !== undefined && !isStringOrNull(body.phone, 20))
      || (body.email !== undefined && !isStringOrNull(body.email, 100))) {
      return invalid(request, '用户名、显示名称和初始密码不符合要求')
    }
    if (mockState.users.some((user) => user.username === body.username)) {
      return conflict(request, 'USERNAME_ALREADY_EXISTS', '用户名已经存在')
    }
    const createdAt = now()
    const created = {
      id: nextId('user'),
      username: body.username,
      password: body.initialPassword,
      displayName: body.displayName,
      phone: body.phone ?? null,
      email: body.email ?? null,
      systemRole: body.systemRole,
      active: true,
      createdAt,
      updatedAt: createdAt,
    }
    mockState.users.push(created)
    return HttpResponse.json(publicUser(created), { status: 201 })
  }),

  http.get<{ userId: string }>('/api/users/:userId', ({ params, request }) => {
    const actor = currentUser()
    if (!actor) return unauthorized(request)
    const user = userById(params.userId)
    return user ? HttpResponse.json(publicUser(user)) : notFound(request)
  }),

  http.put<{ userId: string }, UpdateUserBody>(
    '/api/users/:userId',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      if (actor.systemRole !== 'ADMIN') return forbidden(request)
      const user = userById(params.userId)
      if (!user) return notFound(request)
      const body = await readJson<UpdateUserBody>(request)
      if (body instanceof Response) return body
      if (!hasFields(body, ['displayName', 'phone', 'email', 'systemRole', 'active'])) {
        return invalid(request, 'PUT 必须包含全部可编辑字段')
      }
      if (typeof body.displayName !== 'string' || !body.displayName.trim()
        || body.displayName.length > 50 || !['ADMIN', 'USER'].includes(body.systemRole)
        || typeof body.active !== 'boolean' || !isStringOrNull(body.phone, 20)
        || !isStringOrNull(body.email, 100)) {
        return invalid(request, '用户字段不完整或不符合要求')
      }
      const removesAdmin = user.active && user.systemRole === 'ADMIN'
        && (!body.active || body.systemRole !== 'ADMIN')
      const activeAdmins = mockState.users.filter(
        (candidate) => candidate.active && candidate.systemRole === 'ADMIN',
      ).length
      if (removesAdmin && activeAdmins === 1) {
        return conflict(request, 'LAST_ADMIN_REQUIRED', '系统必须保留至少一个有效管理员')
      }
      const promotesAdmin = user.systemRole === 'USER' && body.systemRole === 'ADMIN'
      if (promotesAdmin && (mockState.members.some((member) => member.userId === user.id)
        || mockState.invitations.some(
          (invitation) => invitation.inviteeId === user.id && invitation.status === 'PENDING',
        ))) {
        return conflict(
          request,
          'USER_HAS_PROJECT_MEMBERSHIP',
          '用户仍有项目成员关系或待处理邀请',
        )
      }
      if (!body.active && mockState.projects.some((project) => project.ownerId === user.id)) {
        return conflict(request, 'USER_OWNS_PROJECT', '用户仍是项目负责人')
      }
      if (!body.active && mockState.tasks.some(
        (task) => task.assigneeId === user.id && !['COMPLETED', 'CANCELLED'].includes(task.status),
      )) {
        return conflict(request, 'USER_HAS_ACTIVE_TASKS', '用户仍负责未完成任务')
      }
      const updated = {
        ...user, displayName: body.displayName, phone: body.phone, email: body.email,
        systemRole: body.systemRole, active: body.active, updatedAt: now(),
      }
      replaceRecord(mockState.users, updated)
      if (!updated.active) mockState.currentUserId = actor.id === updated.id ? null : actor.id
      return HttpResponse.json(publicUser(updated))
    },
  ),

  http.put<{ userId: string }, ResetPasswordBody>(
    '/api/users/:userId/password',
    async ({ params, request }) => {
      const actor = currentUser()
      if (!actor) return unauthorized(request)
      if (actor.systemRole !== 'ADMIN') return forbidden(request)
      const user = userById(params.userId)
      if (!user) return notFound(request)
      const body = await readJson<ResetPasswordBody>(request)
      if (body instanceof Response) return body
      if (!validPassword(body.newPassword)) return invalid(request, '密码长度必须为 8..72')
      replaceRecord(mockState.users, { ...user, password: body.newPassword, updatedAt: now() })
      if (mockState.currentUserId === user.id) mockState.currentUserId = null
      return new HttpResponse(null, { status: 204 })
    },
  ),
]

function validPassword(value: unknown): value is string {
  return typeof value === 'string' && value.length >= 8 && value.length <= 72
}
