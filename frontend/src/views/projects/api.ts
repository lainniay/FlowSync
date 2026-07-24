import { getCsrfHeaders } from '@/shared/api/csrf'
import { http } from '@/shared/api/http'

import type {
  BatchUserIdsRequest,
  CreateProjectRequest,
  InvitationCandidate,
  Project,
  ProjectInvitation,
  ProjectListQuery,
  ProjectMember,
  ProjectPage,
  TransferProjectOwnerRequest,
  UpdateProjectRequest,
  UserOption,
} from './types'

export async function getUserOptions(query?: string): Promise<readonly UserOption[]> {
  const response = await http.get<readonly UserOption[]>('/user-options', {
    params: { q: query || undefined },
  })

  return response.data
}

export async function getProjects(
  query: ProjectListQuery,
): Promise<ProjectPage> {
  const response = await http.get<ProjectPage>(
    '/projects',
    { params: query },
  )

  return response.data
}

export async function getProject(
  projectId: string,
): Promise<Project> {
  const response = await http.get<Project>(
    `/projects/${projectId}`,
  )

  return response.data
}

export async function createProject(
  payload: CreateProjectRequest,
): Promise<Project> {
  const headers = await getCsrfHeaders()

  const response = await http.post<Project>(
    '/projects',
    payload,
    { headers },
  )

  return response.data
}

export async function updateProject(
  projectId: string,
  payload: UpdateProjectRequest,
): Promise<Project> {
  const headers = await getCsrfHeaders()

  const response = await http.put<Project>(
    `/projects/${projectId}`,
    payload,
    { headers },
  )

  return response.data
}

export async function transferProjectOwner(
  projectId: string,
  payload: TransferProjectOwnerRequest,
): Promise<Project> {
  const headers = await getCsrfHeaders()

  const response = await http.put<Project>(
    `/projects/${projectId}/owner`,
    payload,
    { headers },
  )

  return response.data
}

export async function archiveProject(
  projectId: string,
): Promise<Project> {
  const headers = await getCsrfHeaders()

  const response = await http.put<Project>(
    `/projects/${projectId}/archive`,
    undefined,
    { headers },
  )

  return response.data
}

export async function restoreProject(
  projectId: string,
): Promise<Project> {
  const headers = await getCsrfHeaders()

  const response = await http.delete<Project>(
    `/projects/${projectId}/archive`,
    { headers },
  )

  return response.data
}

export async function deleteProject(
  projectId: string,
): Promise<void> {
  const headers = await getCsrfHeaders()

  await http.delete<void>(
    `/projects/${projectId}`,
    { headers },
  )
}

export async function getProjectMembers(
  projectId: string,
): Promise<readonly ProjectMember[]> {
  const response = await http.get<readonly ProjectMember[]>(
    `/projects/${projectId}/members`,
  )

  return response.data
}

export async function addProjectMembers(
  projectId: string,
  payload: BatchUserIdsRequest,
): Promise<readonly ProjectMember[]> {
  const headers = await getCsrfHeaders()

  const response = await http.post<readonly ProjectMember[]>(
    `/projects/${projectId}/members`,
    payload,
    { headers },
  )

  return response.data
}

export async function removeProjectMember(
  projectId: string,
  userId: string,
): Promise<void> {
  const headers = await getCsrfHeaders()

  await http.delete<void>(
    `/projects/${projectId}/members/${userId}`,
    { headers },
  )
}

export async function getProjectInvitations(
  projectId: string,
): Promise<readonly ProjectInvitation[]> {
  const response = await http.get<readonly ProjectInvitation[]>(
    `/projects/${projectId}/invitations`,
  )

  return response.data
}

export async function getInvitationCandidates(
  projectId: string,
  query: string,
): Promise<readonly InvitationCandidate[]> {
  const response = await http.get<readonly InvitationCandidate[]>(
    `/projects/${projectId}/invitation-candidates`,
    { params: { q: query } },
  )

  return response.data
}

export async function createProjectInvitations(
  projectId: string,
  payload: BatchUserIdsRequest,
): Promise<readonly ProjectInvitation[]> {
  const headers = await getCsrfHeaders()

  const response = await http.post<readonly ProjectInvitation[]>(
    `/projects/${projectId}/invitations`,
    payload,
    { headers },
  )

  return response.data
}

export async function cancelProjectInvitation(
  projectId: string,
  invitationId: string,
): Promise<void> {
  const headers = await getCsrfHeaders()

  await http.delete<void>(
    `/projects/${projectId}/invitations/${invitationId}`,
    { headers },
  )
}
