import { http } from '@/shared/api/http'
import { getCsrfHeaders } from '@/shared/api/csrf'

import type {
  CreateTaskBody,
  CreateTaskLogBody,
  Task,
  TaskListQuery,
  TaskLog,
  TaskLogPage,
  TaskPage,
  TaskStatusBody,
  UpdateTaskBody,
} from './types'

export async function getTasks(
  query: TaskListQuery,
): Promise<TaskPage> {
  const response = await http.get<TaskPage>('/tasks', {
    params: query,
  })

  return response.data
}

export async function getTask(taskId: string): Promise<Task> {
  const response = await http.get<Task>(`/tasks/${taskId}`)

  return response.data
}

export async function createTask(
  body: CreateTaskBody,
): Promise<Task> {
  const headers = await getCsrfHeaders()
  const response = await http.post<Task>('/tasks', body, { headers })

  return response.data
}

export async function updateTask(
  taskId: string,
  body: UpdateTaskBody,
): Promise<Task> {
  const headers = await getCsrfHeaders()
  const response = await http.put<Task>(`/tasks/${taskId}`, body, { headers })

  return response.data
}

export async function updateTaskStatus(
  taskId: string,
  body: TaskStatusBody,
): Promise<Task> {
  const headers = await getCsrfHeaders()
  const response = await http.put<Task>(`/tasks/${taskId}/status`, body, { headers })

  return response.data
}

export async function deleteTask(taskId: string): Promise<void> {
  const headers = await getCsrfHeaders()
  await http.delete(`/tasks/${taskId}`, { headers })
}

export async function getTaskLogs(
  taskId: string,
  query: { readonly page?: number; readonly size?: number; readonly sort?: string },
): Promise<TaskLogPage> {
  const response = await http.get<TaskLogPage>(`/tasks/${taskId}/logs`, {
    params: query,
  })

  return response.data
}

export async function createTaskLog(
  taskId: string,
  body: CreateTaskLogBody,
): Promise<TaskLog> {
  const headers = await getCsrfHeaders()
  const response = await http.post<TaskLog>(`/tasks/${taskId}/logs`, body, { headers })

  return response.data
}

export async function deleteTaskLog(
  taskId: string,
  logId: string,
): Promise<void> {
  const headers = await getCsrfHeaders()
  await http.delete(`/tasks/${taskId}/logs/${logId}`, { headers })
}
