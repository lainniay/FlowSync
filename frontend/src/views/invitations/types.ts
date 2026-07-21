import type { InvitationStatus } from '@/shared/api/types'

import type { ProjectInvitation } from '@/views/projects/types'

export type InvitationListQuery = {
  readonly status?: InvitationStatus
}

export type RespondInvitationRequest = {
  readonly status: 'ACCEPTED' | 'REJECTED'
}

export type ReceivedInvitation = ProjectInvitation
