import { http } from '@/shared/api/http'

import type {
  ProjectListQuery,
  ProjectPage,
} from './types'

export async function getProjects(
  query: ProjectListQuery,
): Promise<ProjectPage> {
  const response = await http.get<ProjectPage>(
    '/projects',
    {
      params: query,
    },
  )

  return response.data
}