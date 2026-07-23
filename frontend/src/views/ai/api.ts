import { http } from '@/shared/api/http'
import { getCsrfHeaders } from '@/shared/api/csrf'

import type {
  AiPlanResponse,
  AiSuggestionBody,
  AiSuggestionResponse,
  GeneratePlanBody,
  ImportPlanBody,
  ImportPlanResponse,
} from './types'

export async function getTaskSuggestion(
  body: AiSuggestionBody,
): Promise<AiSuggestionResponse> {
  const headers = await getCsrfHeaders()
  const response = await http.post<AiSuggestionResponse>(
    '/ai/task-suggestions',
    body,
    { headers },
  )

  return response.data
}

export async function generateTaskPlan(
  projectId: string,
  body: GeneratePlanBody,
): Promise<AiPlanResponse> {
  const headers = await getCsrfHeaders()
  const response = await http.post<AiPlanResponse>(
    `/projects/${projectId}/ai/task-plans`,
    body,
    { headers },
  )

  return response.data
}

export async function importTaskPlan(
  projectId: string,
  body: ImportPlanBody,
): Promise<ImportPlanResponse> {
  const headers = await getCsrfHeaders()
  const response = await http.post<ImportPlanResponse>(
    `/projects/${projectId}/ai/task-plans/imports`,
    body,
    { headers },
  )

  return response.data
}
