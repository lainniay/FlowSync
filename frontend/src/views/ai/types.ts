import type { Priority } from '@/shared/api/types'
import type { Task } from '@/views/tasks/types'

export type AiTaskPlanItem = {
  readonly draftId: string
  readonly parentDraftId: string | null
  readonly title: string
  readonly description: string | null
  readonly priority: Priority
  readonly dueDate: string | null
  readonly assigneeId: string | null
}

export type AiSuggestionResponse = {
  readonly suggestion: string
  readonly generatedAt: string
}

export type AiSuggestionBody = {
  readonly taskId: string
  readonly focus?: string
}

export type GeneratePlanBody = {
  readonly goal: string
  readonly description?: string
  readonly constraints?: {
    readonly maxItems?: number
    readonly targetEndDate?: string | null
  }
}

export type AiPlanResponse = {
  readonly overview: string
  readonly items: readonly AiTaskPlanItem[]
  readonly generatedAt: string
}

export type ImportPlanBody = {
  readonly items: readonly AiTaskPlanItem[]
}

export type ImportPlanResponse = {
  readonly importedCount: number
  readonly tasks: readonly Task[]
}
