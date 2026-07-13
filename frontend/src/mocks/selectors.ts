import { mockState } from './store'
import type {
  InvitationRecord,
  MemberRecord,
  ProjectRecord,
  SummaryRecord,
  TaskLogRecord,
  TaskRecord,
  UserBrief,
  UserRecord,
} from './types'

export function currentUser(): UserRecord | undefined {
  return mockState.users.find((user) => user.id === mockState.currentUserId && user.active)
}

export function userById(userId: string): UserRecord | undefined {
  return mockState.users.find((user) => user.id === userId)
}

export function projectById(projectId: string): ProjectRecord | undefined {
  return mockState.projects.find((project) => project.id === projectId)
}

export function taskById(taskId: string): TaskRecord | undefined {
  return mockState.tasks.find((task) => task.id === taskId)
}

export function brief(userId: string): UserBrief {
  const user = userById(userId)
  return { id: userId, displayName: user?.displayName ?? '未知用户' }
}

export function publicUser(user: UserRecord): Omit<UserRecord, 'password'> {
  return {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    phone: user.phone,
    email: user.email,
    systemRole: user.systemRole,
    active: user.active,
    createdAt: user.createdAt,
    updatedAt: user.updatedAt,
  }
}

export function isMember(projectId: string, userId: string): boolean {
  return mockState.members.some((member) => member.projectId === projectId && member.userId === userId)
}

export function isProjectOwner(project: ProjectRecord, user: UserRecord): boolean {
  return project.ownerId === user.id
}

export function canViewProject(project: ProjectRecord, user: UserRecord): boolean {
  return user.systemRole === 'ADMIN' || isProjectOwner(project, user) || isMember(project.id, user.id)
}

export function canManageProject(project: ProjectRecord, user: UserRecord): boolean {
  return user.systemRole === 'ADMIN' || isProjectOwner(project, user)
}

export function projectView(project: ProjectRecord) {
  const tasks = mockState.tasks.filter((task) => task.projectId === project.id)
  return {
    id: project.id,
    owner: brief(project.ownerId),
    name: project.name,
    description: project.description,
    status: project.status,
    priority: project.priority,
    startDate: project.startDate,
    endDate: project.endDate,
    archivedAt: project.archivedAt,
    memberCount: mockState.members.filter((member) => member.projectId === project.id).length,
    taskStats: { total: tasks.length, completed: tasks.filter((task) => task.status === 'COMPLETED').length },
    createdAt: project.createdAt,
    updatedAt: project.updatedAt,
  }
}

export function memberView(member: MemberRecord) {
  return { user: brief(member.userId), joinedAt: member.joinedAt }
}

export function invitationView(invitation: InvitationRecord) {
  const project = projectById(invitation.projectId)
  return {
    id: invitation.id,
    project: { id: invitation.projectId, name: project?.name ?? '未知项目' },
    invitee: brief(invitation.inviteeId),
    invitedBy: brief(invitation.invitedById),
    status: invitation.status,
    createdAt: invitation.createdAt,
    respondedAt: invitation.respondedAt,
  }
}

export function taskView(task: TaskRecord) {
  const latestLog = mockState.taskLogs
    .filter((log) => log.taskId === task.id)
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0]
  return {
    id: task.id,
    projectId: task.projectId,
    parentId: task.parentId,
    assignee: task.assigneeId === null ? null : brief(task.assigneeId),
    creator: brief(task.creatorId),
    title: task.title,
    description: task.description,
    status: task.status,
    priority: task.priority,
    progressPercent: latestLog?.progressPercent ?? 0,
    dueDate: task.dueDate,
    createdAt: task.createdAt,
    updatedAt: task.updatedAt,
  }
}

export function taskLogView(log: TaskLogRecord) {
  return {
    id: log.id,
    taskId: log.taskId,
    operator: brief(log.operatorId),
    progressPercent: log.progressPercent,
    content: log.content,
    createdAt: log.createdAt,
  }
}

export function summaryView(summary: SummaryRecord) {
  return {
    id: summary.id,
    projectId: summary.projectId,
    taskId: summary.taskId,
    createdBy: brief(summary.createdById),
    type: summary.type,
    content: summary.content,
    createdAt: summary.createdAt,
    updatedAt: summary.updatedAt,
  }
}
