import { http } from '@/shared/api/http'

import type { AdminOverview, Overview, OverviewQuery } from './types'

export async function getAdminOverview(): Promise<AdminOverview> {
  const response = await http.get<AdminOverview>('/admin/overview')
  return response.data
}

export async function getOverview(
  query?: OverviewQuery,
): Promise<Overview> {
  const response = await http.get<Overview>(
    '/overview',
    { params: query },
  )

  return response.data
}
