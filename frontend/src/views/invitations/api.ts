import { getCsrfHeaders } from '@/shared/api/csrf'
import { http } from '@/shared/api/http'

import type {
  InvitationListQuery,
  ReceivedInvitation,
  RespondInvitationRequest,
} from './types'

export async function getReceivedInvitations(
  query?: InvitationListQuery,
): Promise<readonly ReceivedInvitation[]> {
  const response = await http.get<readonly ReceivedInvitation[]>(
    '/project-invitations',
    { params: query },
  )

  return response.data
}

export async function respondToInvitation(
  invitationId: string,
  payload: RespondInvitationRequest,
): Promise<ReceivedInvitation> {
  const headers = await getCsrfHeaders()

  const response = await http.put<ReceivedInvitation>(
    `/project-invitations/${invitationId}`,
    payload,
    { headers },
  )

  return response.data
}
