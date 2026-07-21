<script setup lang="ts">
import {
  computed,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElSkeleton,
  ElTable,
  ElTableColumn,
  ElTag,
  ElDatePicker,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/input-number/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/pagination/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import { getApiErrorMessage, hasApiStatus } from '@/shared/api/errors'
import type {
  Priority,
  TaskStatus,
} from '@/shared/api/types'
import { useAuthStore } from '@/stores/auth'

import {
  createTaskLog,
  deleteTask,
  deleteTaskLog,
  getTask,
  getTaskLogs,
  getTasks,
  updateTask,
  updateTaskStatus,
} from './api'
import type {
  CreateTaskLogBody,
  Task,
  TaskLog,
  UpdateTaskBody,
} from './types'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const taskId = computed(() => route.params.taskId as string)

type TagType =
  | 'primary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'

const statusLabels: Record<TaskStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  BLOCKED: '阻塞',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const statusTagTypes: Record<TaskStatus, TagType> = {
  NOT_STARTED: 'info',
  IN_PROGRESS: 'primary',
  BLOCKED: 'danger',
  COMPLETED: 'success',
  CANCELLED: 'info',
}

const priorityLabels: Record<Priority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

const priorityTagTypes: Record<Priority, TagType> = {
  LOW: 'info',
  MEDIUM: 'warning',
  HIGH: 'danger',
}

// --- Task data ---
const task = ref<Task | null>(null)
const parentTask = ref<Task | null>(null)
const childTasks = ref<Task[]>([])
const taskLoading = ref(false)
const taskLoaded = ref(false)
const taskError = ref('')
const notFound = ref(false)

// --- Logs ---
const logs = ref<TaskLog[]>([])
const logPage = ref(0)
const logSize = ref(20)
const logTotalElements = ref(0)
const logsLoading = ref(false)

// --- Permissions ---
const isAdmin = computed(() => authStore.currentUser?.systemRole === 'ADMIN')
const isAssignee = computed(() =>
  task.value?.assignee?.id === authStore.currentUser?.id,
)
const canEdit = computed(() => !isAdmin.value)
const canChangeStatus = computed(() => !isAdmin.value)
const canCreateLog = computed(() => !isAdmin.value)
const canUseAi = computed(() => !isAdmin.value && isAssignee.value)
const canDeleteLog = (log: TaskLog): boolean =>
  !isAdmin.value && (log.operator.id === authStore.currentUser?.id)

// --- Fetch task ---
async function fetchTask(): Promise<void> {
  taskLoading.value = true
  taskError.value = ''
  notFound.value = false

  try {
    const result = await getTask(taskId.value)
    task.value = result

    if (result.parentId) {
      try {
        parentTask.value = await getTask(result.parentId)
      } catch {
        parentTask.value = null
      }
    }

    await fetchChildren()
  } catch (error) {
    if (hasApiStatus(error, 404)) {
      notFound.value = true
    }
    taskError.value = getApiErrorMessage(
      error,
      '任务加载失败，请稍后重试',
    )
  } finally {
    taskLoading.value = false
    taskLoaded.value = true
  }
}

async function fetchChildren(): Promise<void> {
  try {
    const result = await getTasks({
      parentId: taskId.value,
      size: 50,
    })
    childTasks.value = [...result.items]
  } catch {
    childTasks.value = []
  }
}

// --- Logs ---
async function fetchLogs(): Promise<void> {
  logsLoading.value = true

  try {
    const result = await getTaskLogs(taskId.value, {
      page: logPage.value,
      size: logSize.value,
      sort: 'createdAt,desc',
    })
    logs.value = [...result.items]
    logTotalElements.value = result.totalElements
  } catch {
    // logs errors are non-critical
  } finally {
    logsLoading.value = false
  }
}

async function handleLogPageChange(displayedPage: number): Promise<void> {
  logPage.value = displayedPage - 1
  await fetchLogs()
}

// --- Edit dialog ---
const editDialogVisible = ref(false)
const editSubmitting = ref(false)

const editForm = reactive({
  parentId: '',
  title: '',
  description: '',
  assigneeId: '',
  status: 'NOT_STARTED' as TaskStatus,
  priority: 'MEDIUM' as Priority,
  dueDate: '',
})

function openEditDialog(): void {
  if (!task.value) return

  editForm.parentId = task.value.parentId ?? ''
  editForm.title = task.value.title
  editForm.description = task.value.description ?? ''
  editForm.assigneeId = task.value.assignee?.id ?? ''
  editForm.status = task.value.status
  editForm.priority = task.value.priority
  editForm.dueDate = task.value.dueDate ?? ''

  editDialogVisible.value = true
}

async function handleEdit(): Promise<void> {
  editSubmitting.value = true

  try {
    const body: UpdateTaskBody = {
      parentId: editForm.parentId.trim() || null,
      title: editForm.title.trim(),
      description: editForm.description.trim() || null,
      assigneeId: editForm.assigneeId.trim() || null,
      status: editForm.status,
      priority: editForm.priority,
      dueDate: editForm.dueDate || null,
    }

    task.value = await updateTask(taskId.value, body)
    ElMessage.success('任务更新成功')
    editDialogVisible.value = false
    await fetchChildren()
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '更新任务失败，请稍后重试'),
    )
  } finally {
    editSubmitting.value = false
  }
}

// --- Status dialog ---
const statusDialogVisible = ref(false)
const selectedStatus = ref<TaskStatus>('NOT_STARTED')
const statusSubmitting = ref(false)

function openStatusDialog(): void {
  if (!task.value) return
  selectedStatus.value = task.value.status
  statusDialogVisible.value = true
}

async function handleStatusChange(): Promise<void> {
  statusSubmitting.value = true

  try {
    task.value = await updateTaskStatus(taskId.value, {
      status: selectedStatus.value,
    })
    ElMessage.success('任务状态已更新')
    statusDialogVisible.value = false
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '更新状态失败，请稍后重试'),
    )
  } finally {
    statusSubmitting.value = false
  }
}

// --- Delete confirmation ---
const deleteDialogVisible = ref(false)
const deleteSubmitting = ref(false)

async function handleDelete(): Promise<void> {
  deleteSubmitting.value = true

  try {
    await deleteTask(taskId.value)
    ElMessage.success('任务已删除')
    await router.push({ name: 'tasks' })
  } catch (error) {
    if (hasApiStatus(error, 409)) {
      ElMessage.error(
        getApiErrorMessage(error, '任务仍有关联记录，无法删除'),
      )
    } else {
      ElMessage.error(
        getApiErrorMessage(error, '删除任务失败，请稍后重试'),
      )
    }
    deleteDialogVisible.value = false
  } finally {
    deleteSubmitting.value = false
  }
}

// --- Add log dialog ---
const addLogDialogVisible = ref(false)
const addLogSubmitting = ref(false)
const newLogProgress = ref(0)
const newLogContent = ref('')

function openAddLogDialog(): void {
  newLogProgress.value = 0
  newLogContent.value = ''
  addLogDialogVisible.value = true
}

async function handleCreateLog(): Promise<void> {
  if (!newLogContent.value.trim()) return

  addLogSubmitting.value = true

  try {
    const body: CreateTaskLogBody = {
      progressPercent: newLogProgress.value,
      content: newLogContent.value.trim(),
    }

    await createTaskLog(taskId.value, body)
    ElMessage.success('进度记录已添加')

    // Re-fetch task (progressPercent updates) and logs
    task.value = await getTask(taskId.value)
    addLogDialogVisible.value = false
    await fetchLogs()
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '添加进度记录失败，请稍后重试'),
    )
  } finally {
    addLogSubmitting.value = false
  }
}

// --- Delete log ---
async function handleDeleteLog(log: TaskLog): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定要删除这条进度记录吗？`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await deleteTaskLog(taskId.value, log.id)
    ElMessage.success('进度记录已删除')
    task.value = await getTask(taskId.value)
    await fetchLogs()
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '删除进度记录失败，请稍后重试'),
    )
  }
}

// --- AI suggestion ---
import {
  getTaskSuggestion,
} from '@/views/ai/api'

const aiGenerating = ref(false)
const aiSuggestion = ref('')
const aiProgressPercent = ref(0)
const aiSubmitting = ref(false)

async function handleAiSuggestion(): Promise<void> {
  aiGenerating.value = true

  try {
    const result = await getTaskSuggestion({
      taskId: taskId.value,
    })
    aiSuggestion.value = result.suggestion
    aiProgressPercent.value = task.value?.progressPercent ?? 0
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '获取 AI 建议失败，请稍后重试'),
    )
  } finally {
    aiGenerating.value = false
  }
}

async function handleSubmitAiAsLog(): Promise<void> {
  if (!aiSuggestion.value.trim()) return

  aiSubmitting.value = true

  try {
    const body: CreateTaskLogBody = {
      progressPercent: aiProgressPercent.value,
      content: aiSuggestion.value.trim(),
    }

    await createTaskLog(taskId.value, body)
    ElMessage.success('AI 建议已提交为进度记录')
    aiSuggestion.value = ''
    task.value = await getTask(taskId.value)
    await fetchLogs()
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '提交失败，请稍后重试'),
    )
  } finally {
    aiSubmitting.value = false
  }
}

function dismissAiSuggestion(): void {
  aiSuggestion.value = ''
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

onMounted(() => {
  void fetchTask()
  void fetchLogs()
})
</script>

<template>
  <section class="task-detail-page">
    <template v-if="taskLoading && !taskLoaded">
      <div class="content-panel">
        <el-skeleton animated :rows="8" />
      </div>
    </template>

    <template v-else-if="notFound">
      <div class="content-panel">
        <div class="feedback-state">
          <el-alert
            :closable="false"
            title="任务不存在或当前用户不可见"
            type="error"
            show-icon
          />
          <router-link :to="{ name: 'tasks' }">
            返回任务列表
          </router-link>
        </div>
      </div>
    </template>

    <template v-else-if="taskError">
      <div class="content-panel">
        <div class="feedback-state">
          <el-alert
            :closable="false"
            :title="taskError"
            type="error"
            show-icon
          />
          <el-button
            type="primary"
            @click="fetchTask"
          >
            重新加载
          </el-button>
        </div>
      </div>
    </template>

    <template v-else-if="task">
      <!-- Breadcrumb -->
      <nav class="breadcrumb">
        <router-link :to="{ name: 'tasks' }">任务列表</router-link>
        <span> &gt; </span>
        <span>任务详情</span>
      </nav>

      <!-- Header -->
      <header class="page-header">
        <div>
          <h1>{{ task.title }}</h1>
          <div class="task-meta">
            <span>项目 {{ task.projectId }}</span>
            <span>创建者 {{ task.creator.displayName }}</span>
            <span v-if="task.assignee">负责人 {{ task.assignee.displayName }}</span>
            <span class="muted">更新于 {{ formatDateTime(task.updatedAt) }}</span>
          </div>
        </div>

        <div class="header-actions">
          <el-button
            v-if="canEdit"
            @click="openEditDialog"
          >
            编辑
          </el-button>

          <el-button
            v-if="canChangeStatus"
            @click="openStatusDialog"
          >
            修改状态
          </el-button>

          <el-button
            v-if="canEdit"
            type="danger"
            @click="deleteDialogVisible = true"
          >
            删除
          </el-button>
        </div>
      </header>

      <!-- Task info -->
      <section class="content-panel">
        <div class="tag-row">
          <el-tag
            :type="statusTagTypes[task.status]"
            effect="plain"
          >
            {{ statusLabels[task.status] }}
          </el-tag>

          <el-tag
            :type="priorityTagTypes[task.priority]"
            effect="plain"
          >
            {{ priorityLabels[task.priority] }}
          </el-tag>

          <el-tag effect="plain" type="info">
            进度 {{ task.progressPercent }}%
          </el-tag>
        </div>

        <div
          v-if="task.dueDate"
          class="info-row"
        >
          <span class="label">截止日期</span>
          <span>{{ task.dueDate }}</span>
        </div>

        <div class="info-section">
          <h3>描述</h3>
          <div class="description-text">
            {{ task.description || '暂无描述' }}
          </div>
        </div>

        <div
          v-if="task.parentId"
          class="info-row"
        >
          <span class="label">父任务</span>
          <router-link
            v-if="parentTask"
            :to="`/tasks/${task.parentId}`"
            class="task-link"
          >
            {{ parentTask.title }}
          </router-link>
          <span v-else>{{ task.parentId }}</span>
        </div>

        <div
          v-if="childTasks.length > 0"
          class="info-section"
        >
          <h3>子任务</h3>
          <el-table
            :data="childTasks"
            row-key="id"
            size="small"
          >
            <el-table-column label="标题" min-width="140">
              <template #default="{ row }">
                <router-link
                  :to="`/tasks/${row.id}`"
                  class="task-link"
                >
                  {{ row.title }}
                </router-link>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag
                  :type="statusTagTypes[(row as Task).status]"
                  effect="plain"
                  size="small"
                >
                  {{ statusLabels[(row as Task).status] }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="优先级" width="80">
              <template #default="{ row }">
                <el-tag
                  :type="priorityTagTypes[(row as Task).priority]"
                  effect="plain"
                  size="small"
                >
                  {{ priorityLabels[(row as Task).priority] }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="负责人" width="100">
              <template #default="{ row }">
                {{ row.assignee?.displayName ?? '--' }}
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>

      <!-- Task logs -->
      <section class="content-panel">
        <div class="section-header">
          <h3>进度记录</h3>

          <el-button
            v-if="canCreateLog"
            type="primary"
            size="small"
            @click="openAddLogDialog"
          >
            新增日志
          </el-button>
        </div>

        <el-table
          :data="logs"
          row-key="id"
          v-loading="logsLoading"
        >
          <el-table-column
            label="操作人"
            width="120"
          >
            <template #default="{ row }">
              {{ row.operator.displayName }}
            </template>
          </el-table-column>

          <el-table-column label="进度" width="100">
            <template #default="{ row }">
              {{ row.progressPercent }}%
            </template>
          </el-table-column>

          <el-table-column label="内容" min-width="200">
            <template #default="{ row }">
              {{ row.content }}
            </template>
          </el-table-column>

          <el-table-column label="时间" min-width="160">
            <template #default="{ row }">
              <span class="muted">{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button
                v-if="canDeleteLog(row as TaskLog)"
                size="small"
                type="danger"
                text
                @click="handleDeleteLog(row as TaskLog)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂无进度记录" />
          </template>
        </el-table>

        <el-pagination
          v-if="logTotalElements > 0"
          class="task-pagination"
          :current-page="logPage + 1"
          layout="total, prev, pager, next"
          :page-size="logSize"
          :total="logTotalElements"
          @current-change="handleLogPageChange"
        />
      </section>

      <!-- AI suggestion -->
      <section
        v-if="canUseAi"
        class="content-panel"
      >
        <h3>AI 任务建议</h3>

        <el-button
          type="primary"
          :loading="aiGenerating"
          @click="handleAiSuggestion"
        >
          获取 AI 建议
        </el-button>

        <div
          v-if="aiSuggestion"
          class="ai-result"
        >
          <el-alert
            class="ai-hint"
            :closable="false"
            title="AI 建议为临时内容，请人工审阅修改后再提交为进度记录。"
            type="warning"
            show-icon
          />

          <div class="ai-form">
            <label class="ai-label">建议内容（可编辑）</label>
            <el-input
              v-model="aiSuggestion"
              maxlength="1000"
              placeholder="编辑 AI 建议内容..."
              type="textarea"
              :rows="4"
            />

            <label class="ai-label">实际进度 (%)</label>
            <el-input-number
              v-model="aiProgressPercent"
              :max="100"
              :min="0"
            />

            <div class="ai-actions">
              <el-button
                type="primary"
                :loading="aiSubmitting"
                @click="handleSubmitAiAsLog"
              >
                提交为进度记录
              </el-button>

              <el-button @click="dismissAiSuggestion">
                放弃
              </el-button>
            </div>
          </div>
        </div>
      </section>

      <!-- Edit dialog -->
      <el-dialog
        v-model="editDialogVisible"
        title="编辑任务"
        width="560px"
      >
        <el-form
          label-position="top"
          :model="editForm"
        >
          <el-form-item label="父任务 ID">
            <el-input
              v-model="editForm.parentId"
              placeholder="可选"
            />
          </el-form-item>

          <el-form-item label="标题" required>
            <el-input
              v-model="editForm.title"
              maxlength="100"
              placeholder="1 到 100 个字符"
            />
          </el-form-item>

          <el-form-item label="描述">
            <el-input
              v-model="editForm.description"
              maxlength="5000"
              placeholder="可选，最长 5000 个字符"
              type="textarea"
              :rows="3"
            />
          </el-form-item>

          <el-form-item label="负责人 ID">
            <el-input
              v-model="editForm.assigneeId"
              placeholder="可选"
            />
          </el-form-item>

          <el-form-item label="状态">
            <el-select
              v-model="editForm.status"
              class="enum-select"
            >
              <el-option label="未开始" value="NOT_STARTED" />
              <el-option label="进行中" value="IN_PROGRESS" />
              <el-option label="阻塞" value="BLOCKED" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
          </el-form-item>

          <el-form-item label="优先级">
            <el-select
              v-model="editForm.priority"
              class="enum-select"
            >
              <el-option label="低" value="LOW" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="高" value="HIGH" />
            </el-select>
          </el-form-item>

          <el-form-item label="截止日期">
            <el-date-picker
              v-model="editForm.dueDate"
              placeholder="可选"
              type="date"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="editDialogVisible = false">
            取消
          </el-button>

          <el-button
            type="primary"
            :loading="editSubmitting"
            @click="handleEdit"
          >
            保存
          </el-button>
        </template>
      </el-dialog>

      <!-- Status dialog -->
      <el-dialog
        v-model="statusDialogVisible"
        title="修改任务状态"
        width="400px"
      >
        <el-form label-position="top">
          <el-form-item label="状态">
            <el-select
              v-model="selectedStatus"
              class="enum-select"
            >
              <el-option label="未开始" value="NOT_STARTED" />
              <el-option label="进行中" value="IN_PROGRESS" />
              <el-option label="阻塞" value="BLOCKED" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="statusDialogVisible = false">
            取消
          </el-button>

          <el-button
            type="primary"
            :loading="statusSubmitting"
            @click="handleStatusChange"
          >
            保存
          </el-button>
        </template>
      </el-dialog>

      <!-- Delete confirmation -->
      <el-dialog
        v-model="deleteDialogVisible"
        title="删除任务"
        width="400px"
      >
        <p>确定要删除任务「{{ task.title }}」吗？该操作不可撤销。</p>

        <template #footer>
          <el-button @click="deleteDialogVisible = false">
            取消
          </el-button>

          <el-button
            type="danger"
            :loading="deleteSubmitting"
            @click="handleDelete"
          >
            确定删除
          </el-button>
        </template>
      </el-dialog>

      <!-- Add log dialog -->
      <el-dialog
        v-model="addLogDialogVisible"
        title="新增进度记录"
        width="480px"
      >
        <el-form label-position="top">
          <el-form-item label="进度 (%)" required>
            <el-input-number
              v-model="newLogProgress"
              :max="100"
              :min="0"
              :step="5"
            />
          </el-form-item>

          <el-form-item label="内容" required>
            <el-input
              v-model="newLogContent"
              maxlength="1000"
              placeholder="1 到 1000 个字符"
              type="textarea"
              :rows="3"
            />
          </el-form-item>
        </el-form>

        <template #footer>
          <el-button @click="addLogDialogVisible = false">
            取消
          </el-button>

          <el-button
            type="primary"
            :loading="addLogSubmitting"
            @click="handleCreateLog"
          >
            提交
          </el-button>
        </template>
      </el-dialog>
    </template>
  </section>
</template>

<style scoped>
.task-detail-page {
  display: grid;
  gap: 16px;
}

.breadcrumb {
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 14px;
}

.breadcrumb a {
  color: var(--fs-color-primary, #2563eb);
  text-decoration: none;
}

.breadcrumb a:hover {
  text-decoration: underline;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-header h1 {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
  font-size: 24px;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-top: 8px;
  color: var(--fs-color-text-secondary, #64748b);
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.content-panel {
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
  padding: 20px;
}

.tag-row {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.info-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.label {
  color: var(--fs-color-text-secondary, #64748b);
}

.info-section {
  margin-top: 16px;
}

.info-section h3,
.section-header h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: var(--fs-color-text, #1f2937);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
}

.description-text {
  color: var(--fs-color-text, #1f2937);
  white-space: pre-wrap;
  line-height: 1.6;
}

.task-link {
  color: var(--fs-color-primary, #2563eb);
  text-decoration: none;
}

.task-link:hover {
  text-decoration: underline;
}

.feedback-state {
  display: grid;
  min-height: 240px;
  align-content: center;
  gap: 16px;
  justify-items: center;
}

.task-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.enum-select {
  width: 160px;
}

.muted {
  color: var(--fs-color-text-secondary, #64748b);
}

/* AI section */
.ai-result {
  margin-top: 16px;
}

.ai-hint {
  margin-bottom: 12px;
}

.ai-form {
  display: grid;
  gap: 12px;
}

.ai-label {
  font-weight: 500;
  color: var(--fs-color-text, #1f2937);
}

.ai-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .content-panel {
    padding: 16px;
  }

  .task-meta {
    flex-direction: column;
    gap: 4px;
  }

  .header-actions {
    width: 100%;
  }
}
</style>
