import { http } from '@/shared/api/http'

import type { Overview, OverviewQuery } from './types'

export async function getOverview(
  query?: OverviewQuery,
): Promise<Overview> {
  const response = await http.get<Overview>(
    '/overview',
    { params: query },
  )

  return response.data
}
