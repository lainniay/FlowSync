import type { MockState } from './types'

function initialState(): MockState {
  return {
    users: [
      {
        id: '1', username: 'admin', password: 'admin1234', displayName: '系统管理员',
        phone: null, email: 'admin@flowsync.local', systemRole: 'ADMIN', active: true,
        createdAt: '2026-07-13T08:00:00Z', updatedAt: '2026-07-13T08:00:00Z',
      },
      {
        id: '2', username: 'zhangsan', password: 'user1234', displayName: '张三',
        phone: '13800000000', email: 'zhangsan@example.com', systemRole: 'USER', active: true,
        createdAt: '2026-07-13T08:01:00Z', updatedAt: '2026-07-13T08:01:00Z',
      },
      {
        id: '3', username: 'lisi', password: 'user1234', displayName: '李四', phone: null,
        email: 'lisi@example.com', systemRole: 'USER', active: true,
        createdAt: '2026-07-13T08:02:00Z', updatedAt: '2026-07-13T08:02:00Z',
      },
      {
        id: '4', username: 'wangwu', password: 'user1234', displayName: '王五', phone: null,
        email: 'wangwu@example.com', systemRole: 'USER', active: true,
        createdAt: '2026-07-13T08:03:00Z', updatedAt: '2026-07-13T08:03:00Z',
      },
      {
        id: '5', username: 'inactive', password: 'user1234', displayName: '停用用户', phone: null,
        email: null, systemRole: 'USER', active: false,
        createdAt: '2026-07-13T08:04:00Z', updatedAt: '2026-07-13T08:04:00Z',
      },
    ],
    projects: [
      {
        id: '101', ownerId: '2', name: 'FlowSync', description: '小组任务协同系统',
        status: 'IN_PROGRESS', priority: 'HIGH', startDate: '2026-07-13', endDate: '2026-08-01',
        archivedAt: null, createdAt: '2026-07-13T08:10:00Z', updatedAt: '2026-07-13T09:30:00Z',
      },
      {
        id: '102', ownerId: '2', name: '演示空项目', description: null, status: 'NOT_STARTED',
        priority: 'LOW', startDate: null, endDate: null, archivedAt: null,
        createdAt: '2026-07-13T08:15:00Z', updatedAt: '2026-07-13T08:15:00Z',
      },
      {
        id: '103', ownerId: '4', name: '已归档项目', description: '用于测试恢复操作',
        status: 'COMPLETED', priority: 'MEDIUM', startDate: null, endDate: null,
        archivedAt: '2026-07-13T11:30:00Z', createdAt: '2026-07-13T08:20:00Z',
        updatedAt: '2026-07-13T11:30:00Z',
      },
    ],
    members: [
      { projectId: '101', userId: '2', joinedAt: '2026-07-13T08:10:00Z' },
      { projectId: '101', userId: '3', joinedAt: '2026-07-13T08:20:00Z' },
      { projectId: '102', userId: '2', joinedAt: '2026-07-13T08:15:00Z' },
      { projectId: '102', userId: '4', joinedAt: '2026-07-13T08:16:00Z' },
      { projectId: '103', userId: '4', joinedAt: '2026-07-13T08:20:00Z' },
    ],
    invitations: [
      {
        id: '1001', projectId: '101', inviteeId: '4', invitedById: '2', status: 'PENDING',
        createdAt: '2026-07-13T09:00:00Z', respondedAt: null,
      },
    ],
    tasks: [
      {
        id: '501', projectId: '101', parentId: null, assigneeId: '3', creatorId: '2',
        title: '完成登录页面', description: '实现登录、退出和错误提示', status: 'IN_PROGRESS',
        priority: 'HIGH', dueDate: '2026-07-20', createdAt: '2026-07-13T09:00:00Z',
        updatedAt: '2026-07-13T10:00:00Z',
      },
      {
        id: '502', projectId: '101', parentId: null, assigneeId: null, creatorId: '2',
        title: '整理演示材料', description: null, status: 'NOT_STARTED', priority: 'LOW',
        dueDate: null, createdAt: '2026-07-13T09:10:00Z', updatedAt: '2026-07-13T09:10:00Z',
      },
    ],
    taskLogs: [
      {
        id: '801', taskId: '501', operatorId: '3', progressPercent: 20,
        content: '完成页面结构', createdAt: '2026-07-13T09:30:00Z',
      },
      {
        id: '802', taskId: '501', operatorId: '2', progressPercent: 40,
        content: '登录表单已经完成，正在联调接口', createdAt: '2026-07-13T10:00:00Z',
      },
    ],
    summaries: [
      {
        id: '901', projectId: '101', taskId: null, createdById: '2', type: 'STAGE',
        content: '第一阶段已经完成需求分析和接口设计。', createdAt: '2026-07-13T11:00:00Z',
        updatedAt: '2026-07-13T11:00:00Z',
      },
      {
        id: '902', projectId: '101', taskId: '501', createdById: '2', type: 'STAGE',
        content: '登录任务正在按计划推进。', createdAt: '2026-07-13T11:10:00Z',
        updatedAt: '2026-07-13T11:10:00Z',
      },
    ],
    currentUserId: '1',
    clock: Date.parse('2026-07-13T12:00:00Z'),
    sequences: { user: 6, project: 104, invitation: 1002, task: 503, taskLog: 803, summary: 903 },
  }
}

export const mockState = initialState()

export function resetMockState(): void {
  Object.assign(mockState, initialState())
}

export function setMockCurrentUser(userId: string | null): void {
  mockState.currentUserId = userId
}

export function replaceRecord<T extends { readonly id: string }>(records: T[], record: T): void {
  const index = records.findIndex((candidate) => candidate.id === record.id)
  if (index >= 0) records.splice(index, 1, record)
}

export function removeRecord<T extends { readonly id: string }>(records: T[], id: string): void {
  const index = records.findIndex((record) => record.id === id)
  if (index >= 0) records.splice(index, 1)
}
