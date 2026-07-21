import { http } from '@/shared/api/http'

import type { Project } from './types'
import type {
  ProjectListQuery,
  ProjectPage,
} from './types'

export async function getProject(
  projectId: string,
): Promise<Project> {
  const response = await http.get<Project>(`/projects/${projectId}`)

  return response.data
}

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