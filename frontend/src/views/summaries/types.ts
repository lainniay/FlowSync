import type {
  Page,
  PaginationQuery,
  SummaryType,
  UserBrief,
} from '@/shared/api/types'

export type Summary = {
  readonly id: string
  readonly projectId: string
  readonly taskId: string | null
  readonly createdBy: UserBrief
  readonly type: SummaryType
  readonly content: string
  readonly createdAt: string
  readonly updatedAt: string
}

export type SummaryPage = Page<Summary>

export type SummaryListQuery = PaginationQuery & {
  readonly projectId?: string
  readonly taskId?: string
  readonly type?: SummaryType
  readonly createdBy?: string
}

export type SummaryListFilters = {
  projectId: string
  type: SummaryType | ''
}

export type CreateSummaryBody = {
  readonly projectId: string
  readonly taskId: string | null
  readonly type: SummaryType
  readonly content: string
}

export type UpdateSummaryBody = {
  readonly type: SummaryType
  readonly content: string
}
