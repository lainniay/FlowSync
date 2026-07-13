import { http, HttpResponse } from 'msw'

import { aiHandlers } from './handlers/ai'
import { authHandlers } from './handlers/auth'
import { invitationHandlers } from './handlers/invitations'
import { memberHandlers } from './handlers/members'
import { overviewHandlers } from './handlers/overview'
import { projectHandlers } from './handlers/projects'
import { summaryHandlers } from './handlers/summaries'
import { taskLogHandlers } from './handlers/taskLogs'
import { taskHandlers } from './handlers/tasks'
import { userHandlers } from './handlers/users'
import { problem } from './utils'

export const apiHandlers = [
  ...authHandlers,
  ...userHandlers,
  ...projectHandlers,
  ...memberHandlers,
  ...invitationHandlers,
  ...taskHandlers,
  ...taskLogHandlers,
  ...summaryHandlers,
  ...overviewHandlers,
  ...aiHandlers,
]

const requireCsrf = ({ request }: { request: Request }) => {
  if (request.headers.get('X-CSRF-TOKEN') === 'mock-csrf-token') return
  return problem(request, 403, 'CSRF_INVALID', 'CSRF 校验失败', '请先获取并提交有效的 CSRF Token')
}

const csrfHandlers = [
  http.post('/api/*', requireCsrf),
  http.put('/api/*', requireCsrf),
  http.patch('/api/*', requireCsrf),
  http.delete('/api/*', requireCsrf),
]

const unhandledApiHandler = http.all('/api/*', () => HttpResponse.error())

export const handlers = [...csrfHandlers, ...apiHandlers, unhandledApiHandler]
