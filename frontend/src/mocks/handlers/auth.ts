import { http, HttpResponse } from 'msw'

import { currentUser, publicUser } from '../selectors'
import { mockState, replaceRecord } from '../store'
import {
  hasFields, invalid, isStringOrNull, now, problem, readJson, unauthorized,
} from '../utils'

type LoginBody = { readonly username: string; readonly password: string }
type ProfileBody = {
  readonly displayName: string
  readonly phone: string | null
  readonly email: string | null
}
type PasswordBody = { readonly currentPassword: string; readonly newPassword: string }

export const authHandlers = [
  http.get('/api/auth/csrf', () =>
    HttpResponse.json({ token: 'mock-csrf-token', headerName: 'X-CSRF-TOKEN' }),
  ),

  http.post<never, LoginBody>('/api/auth/login', async ({ request }) => {
    const body = await readJson<LoginBody>(request)
    if (body instanceof Response) return body
    const user = mockState.users.find((candidate) => candidate.username === body.username)
    if (!user || !user.active || user.password !== body.password) {
      return problem(request, 401, 'INVALID_CREDENTIALS', '登录失败', '用户名或密码错误')
    }
    mockState.currentUserId = user.id
    return HttpResponse.json(publicUser(user))
  }),

  http.post('/api/auth/logout', () => {
    mockState.currentUserId = null
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('/api/users/me', ({ request }) => {
    const user = currentUser()
    return user ? HttpResponse.json(publicUser(user)) : unauthorized(request)
  }),

  http.put<never, ProfileBody>('/api/users/me', async ({ request }) => {
    const user = currentUser()
    if (!user) return unauthorized(request)
    const body = await readJson<ProfileBody>(request)
    if (body instanceof Response) return body
    if (!hasFields(body, ['displayName', 'phone', 'email'])
      || typeof body.displayName !== 'string' || !body.displayName.trim()
      || body.displayName.length > 50
      || !isStringOrNull(body.phone, 20) || !isStringOrNull(body.email, 100)) {
      return invalid(request, '个人资料字段不符合要求')
    }
    const updated = {
      ...user, displayName: body.displayName, phone: body.phone, email: body.email, updatedAt: now(),
    }
    replaceRecord(mockState.users, updated)
    return HttpResponse.json(publicUser(updated))
  }),

  http.put<never, PasswordBody>('/api/users/me/password', async ({ request }) => {
    const user = currentUser()
    if (!user) return unauthorized(request)
    const body = await readJson<PasswordBody>(request)
    if (body instanceof Response) return body
    if (body.currentPassword !== user.password) {
      return problem(
        request,
        422,
        'CURRENT_PASSWORD_INCORRECT',
        '当前密码错误',
        '请输入正确的当前密码',
      )
    }
    if (typeof body.newPassword !== 'string' || body.newPassword.length < 8
      || body.newPassword.length > 72) return invalid(request, '密码长度必须为 8..72')
    replaceRecord(mockState.users, { ...user, password: body.newPassword, updatedAt: now() })
    mockState.currentUserId = null
    return new HttpResponse(null, { status: 204 })
  }),
]
