import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getCsrfHeaders } from '@/shared/api/csrf'
import { http } from '@/shared/api/http'
import {
  generateTaskPlan,
  getTaskSuggestion,
} from '@/views/ai/api'

vi.mock('@/shared/api/csrf', () => ({
  getCsrfHeaders: vi.fn<typeof getCsrfHeaders>(),
}))

vi.mock('@/shared/api/http', () => ({
  http: { post: vi.fn<typeof http.post>() },
}))

beforeEach(() => {
  vi.mocked(getCsrfHeaders).mockResolvedValue({ 'X-CSRF-TOKEN': 'token' })
  vi.mocked(http.post).mockResolvedValue({ data: {} })
})

describe('AI API', () => {
  it('allows provider-backed requests to outlive the backend timeout', async () => {
    await getTaskSuggestion({ taskId: '501' })
    await generateTaskPlan('103', { goal: '发布项目' })

    expect(http.post).toHaveBeenNthCalledWith(
      1,
      '/ai/task-suggestions',
      { taskId: '501' },
      { headers: { 'X-CSRF-TOKEN': 'token' }, timeout: 120_000 },
    )
    expect(http.post).toHaveBeenNthCalledWith(
      2,
      '/projects/103/ai/task-plans',
      { goal: '发布项目' },
      { headers: { 'X-CSRF-TOKEN': 'token' }, timeout: 120_000 },
    )
  })
})
