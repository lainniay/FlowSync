import { http } from '@/shared/api/http'
import { getCsrfHeaders } from '@/shared/api/csrf'

import type {
  CreateSummaryBody,
  Summary,
  SummaryListQuery,
  SummaryPage,
  UpdateSummaryBody,
} from './types'

export async function getSummaries(
  query: SummaryListQuery,
): Promise<SummaryPage> {
  const response = await http.get<SummaryPage>('/summaries', {
    params: query,
  })

  return response.data
}

export async function getSummary(
  summaryId: string,
): Promise<Summary> {
  const response = await http.get<Summary>(`/summaries/${summaryId}`)

  return response.data
}

export async function createSummary(
  body: CreateSummaryBody,
): Promise<Summary> {
  const headers = await getCsrfHeaders()
  const response = await http.post<Summary>('/summaries', body, { headers })

  return response.data
}

export async function updateSummary(
  summaryId: string,
  body: UpdateSummaryBody,
): Promise<Summary> {
  const headers = await getCsrfHeaders()
  const response = await http.put<Summary>(
    `/summaries/${summaryId}`,
    body,
    { headers },
  )

  return response.data
}

export async function deleteSummary(
  summaryId: string,
): Promise<void> {
  const headers = await getCsrfHeaders()
  await http.delete(`/summaries/${summaryId}`, { headers })
}
