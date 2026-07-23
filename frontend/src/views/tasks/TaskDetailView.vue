<script setup lang="ts">
import {
  computed,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElLoading,
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
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/input-number/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/pagination/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

const vLoading = ElLoading.directive

import {
  getApiErrorMessage,
  getProblemDetails,
  hasApiStatus,
} from '@/shared/api/errors'
import MaterialIcon from '@/components/MaterialIcon.vue'
import ProjectLink from '@/components/ProjectLink.vue'
import TaskLink from '@/components/TaskLink.vue'
import UserLink from '@/components/UserLink.vue'
import { formatDateTime } from '@/shared/format'
import { fetchAllPages, PAGE_SIZE } from '@/shared/api/pagination'
import type {
  Priority,
  TaskStatus,
} from '@/shared/api/types'
import { useAuthStore } from '@/stores/auth'
import { getProject, getProjectMembers } from '@/views/projects/api'
import type { Project, ProjectMember } from '@/views/projects/types'

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

function getTaskListLocation() {
  const projectId = typeof route.query?.projectId === 'string'
    ? route.query.projectId
    : ''

  return {
    name: 'tasks',
    ...(projectId ? { query: { projectId } } : {}),
  }
}

type TagType =
  | 'primary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'

const statusLabels: Record<TaskStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  BLOCKED: '阻塞中',
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
const project = ref<Project | null>(null)
const parentTask = ref<Task | null>(null)
const childTasks = ref<Task[]>([])
const taskLoading = ref(false)
const taskLoaded = ref(false)
const taskError = ref('')
const notFound = ref(false)

// --- Project owner ---
const projectOwner = ref<{ id: string; displayName: string } | null>(null)
const projectArchived = ref<boolean | null>(null)

// --- Logs ---
const logs = ref<TaskLog[]>([])
const latestLog = ref<TaskLog | null>(null)
const logPage = ref(0)
const logTotalElements = ref(0)
const logsLoading = ref(false)
const logsError = ref('')

const latestProgress = computed(() => (
  latestLog.value?.progressPercent ?? task.value?.progressPercent ?? 0
))

function progressColor(progressPercent: number): string {
  const progress = Math.min(100, Math.max(0, progressPercent))
  return `hsl(${Math.round(215 - progress * 0.7)} 72% 42%)`
}

// --- Permissions ---
const isAdmin = computed(() => authStore.currentUser?.systemRole === 'ADMIN')
const isAssignee = computed(() =>
  task.value?.assignee?.id === authStore.currentUser?.id,
)
const isProjectOwner = computed(() =>
  projectOwner.value?.id === authStore.currentUser?.id,
)
const canWriteProjectContent = computed(
  () => !isAdmin.value && projectArchived.value === false,
)
const canEdit = computed(
  () => canWriteProjectContent.value && isProjectOwner.value,
)
const canChangeStatus = computed(
  () => canWriteProjectContent.value
    && (isProjectOwner.value || isAssignee.value),
)
const canCreateLog = computed(
  () => canWriteProjectContent.value
    && (isProjectOwner.value || isAssignee.value),
)
const canUseAi = computed(
  () => canWriteProjectContent.value
    && (isProjectOwner.value || isAssignee.value),
)
const canDeleteLog = (log: TaskLog): boolean =>
  canWriteProjectContent.value
  && (isProjectOwner.value || log.operator.id === authStore.currentUser?.id)

// --- Fetch task ---
async function fetchTask(): Promise<void> {
  taskLoading.value = true
  taskError.value = ''
  notFound.value = false

  try {
    const result = await getTask(taskId.value)
    task.value = result

    // Fetch project owner and archive state for permission checks.
    // Keep projectArchived as null when this context cannot be loaded,
    // so project-content write actions remain unavailable.
    try {
      project.value = await getProject(result.projectId)
      projectOwner.value = project.value.owner
      projectArchived.value = Boolean(project.value.archivedAt)
    } catch {
      project.value = null
      projectOwner.value = null
      projectArchived.value = null
    }

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
    childTasks.value = [...await fetchAllPages(getTasks, {
      parentId: taskId.value,
    })]
  } catch {
    childTasks.value = []
  }
}

// --- Logs ---
async function fetchLogs(): Promise<void> {
  logsLoading.value = true
  logsError.value = ''

  try {
    const result = await getTaskLogs(taskId.value, {
      page: logPage.value,
      size: PAGE_SIZE,
      sort: 'createdAt,desc',
    })
    logs.value = [...result.items]
    if (logPage.value === 0) latestLog.value = result.items[0] ?? null
    logTotalElements.value = result.totalElements
  } catch (error) {
    logs.value = []
    logTotalElements.value = 0
    logsError.value = getApiErrorMessage(
      error,
      '进度记录加载失败，请稍后重试',
    )
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
const editOptionsLoading = ref(false)
const editMemberOptions = ref<ProjectMember[]>([])
const editParentOptions = ref<Task[]>([])

const editForm = reactive({
  parentId: '',
  title: '',
  description: '',
  assigneeId: '',
  status: 'NOT_STARTED' as TaskStatus,
  priority: 'MEDIUM' as Priority,
  dueDate: '',
})

async function openEditDialog(): Promise<void> {
  if (!task.value) return

  editForm.parentId = task.value.parentId ?? ''
  editForm.title = task.value.title
  editForm.description = task.value.description ?? ''
  editForm.assigneeId = task.value.assignee?.id ?? ''
  editForm.status = task.value.status
  editForm.priority = task.value.priority
  editForm.dueDate = task.value.dueDate ?? ''

  editDialogVisible.value = true
  editOptionsLoading.value = true
  try {
    const [members, parents] = await Promise.all([
      getProjectMembers(task.value.projectId),
      fetchAllPages(getTasks, {
        projectId: task.value.projectId,
        sort: 'title,asc',
      }),
    ])
    editMemberOptions.value = [...members]
    editParentOptions.value = parents.filter((candidate) => candidate.id !== taskId.value)
  } catch {
    editMemberOptions.value = []
    editParentOptions.value = []
  } finally {
    editOptionsLoading.value = false
  }
}

async function handleEdit(): Promise<void> {
  const title = editForm.title.trim()

  if (!title) {
    ElMessage.warning('请输入任务标题')
    return
  }

  editSubmitting.value = true

  try {
    const body: UpdateTaskBody = {
      parentId: editForm.parentId.trim() || null,
      title,
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
    if (getProblemDetails(error)?.code === 'TASK_ASSIGNEE_CHANGED') {
      await fetchTask()
      if (task.value) {
        selectedStatus.value = task.value.status
      }
      ElMessage.error('任务负责人已变更，请确认后重试')
    } else {
      ElMessage.error(
        getApiErrorMessage(error, '更新状态失败，请稍后重试'),
      )
    }
  } finally {
    statusSubmitting.value = false
  }
}

// --- Delete confirmation ---
const deleteSubmitting = ref(false)

async function handleDelete(): Promise<void> {
  if (!task.value || deleteSubmitting.value) return

  try {
    await ElMessageBox.confirm(
      `确定要删除任务「${task.value.title}」吗？该操作不可撤销`,
      '删除任务',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error',
      },
    )
  } catch {
    return
  }

  deleteSubmitting.value = true
  try {
    await deleteTask(taskId.value)
    ElMessage.success('任务已删除')
    await router.push(getTaskListLocation())
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
    logPage.value = 0
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
const aiDialogVisible = ref(false)
const aiSuggestion = ref('')
const aiErrorMessage = ref('')
const aiProgressPercent = ref(0)
const aiSubmitting = ref(false)
let aiRequestId = 0

function openAiSuggestionDialog(): void {
  if (aiGenerating.value) return
  aiDialogVisible.value = true
  aiSuggestion.value = ''
  aiErrorMessage.value = ''
  aiProgressPercent.value = task.value?.progressPercent ?? 0
  void handleAiSuggestion()
}

async function handleAiSuggestion(): Promise<void> {
  const requestId = ++aiRequestId
  aiGenerating.value = true
  aiErrorMessage.value = ''

  try {
    const result = await getTaskSuggestion({
      taskId: taskId.value,
    })
    if (requestId !== aiRequestId) return
    aiSuggestion.value = result.suggestion
    aiProgressPercent.value = task.value?.progressPercent ?? 0
  } catch (error) {
    if (requestId !== aiRequestId) return
    aiErrorMessage.value = getApiErrorMessage(error, '获取 AI 建议失败，请稍后重试')
  } finally {
    if (requestId === aiRequestId) aiGenerating.value = false
  }
}

async function handleSubmitAiAsLog(): Promise<void> {
  if (!aiSuggestion.value.trim()) {
    ElMessage.warning('建议内容不能为空')
    return
  }

  if (aiSuggestion.value.trim().length > 1000) {
    ElMessage.warning('进度内容不能超过 1000 个字符，请精简后再提交')
    return
  }

  aiSubmitting.value = true

  try {
    const body: CreateTaskLogBody = {
      progressPercent: aiProgressPercent.value,
      content: aiSuggestion.value.trim(),
    }

    await createTaskLog(taskId.value, body)
    ElMessage.success('AI 建议已提交为进度记录')
    aiDialogVisible.value = false
    task.value = await getTask(taskId.value)
    logPage.value = 0
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
  aiDialogVisible.value = false
}

function resetAiSuggestionDialog(): void {
  aiRequestId++
  aiGenerating.value = false
  aiSuggestion.value = ''
  aiErrorMessage.value = ''
}

// --- Reset & reload on route change ---
function resetDetailState(): void {
  editDialogVisible.value = false
  statusDialogVisible.value = false
  addLogDialogVisible.value = false
  aiDialogVisible.value = false
  resetAiSuggestionDialog()
  task.value = null
  parentTask.value = null
  childTasks.value = []
  taskError.value = ''
  notFound.value = false
  taskLoaded.value = false
  projectOwner.value = null
  projectArchived.value = null
  logs.value = []
  latestLog.value = null
  logsError.value = ''
  logPage.value = 0
  logTotalElements.value = 0
}

async function loadDetailData(): Promise<void> {
  resetDetailState()
  await fetchTask()
  await fetchLogs()
}

onMounted(() => {
  void loadDetailData()
})

watch(
  () => route.params.taskId,
  () => {
    void loadDetailData()
  },
)
</script>

<template>
  <section class="task-detail-page">
    <template v-if="taskLoading && !taskLoaded">
      <div class="content-panel">
        <div aria-label="加载中" class="initial-loading-space" role="status" />
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
          <router-link :to="getTaskListLocation()">
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
      <header class="page-header">
        <div>
          <p class="breadcrumb">
            <RouterLink :to="getTaskListLocation()">
              任务
            </RouterLink>
            <span>/</span>
            <span>{{ task.title }}</span>
          </p>
          <h1>{{ task.title }}</h1>
          <div class="task-meta">
            <div class="task-meta-tags">
              <el-tag :type="statusTagTypes[task.status]" effect="plain">
                状态：{{ statusLabels[task.status] }}
              </el-tag>
              <el-tag :type="priorityTagTypes[task.priority]" effect="plain">
                优先级：{{ priorityLabels[task.priority] }}
              </el-tag>
            </div>
            <div class="task-meta-context">
              <span>
                所属项目：<ProjectLink v-if="project" :project-id="project.id">{{ project.name }}</ProjectLink>
                <template v-else>不可用</template>
              </span>
              <span>
                负责人：<UserLink v-if="task.assignee" :user-id="task.assignee.id">
                  {{ task.assignee.displayName }}
                </UserLink><template v-else>未委派</template>
              </span>
            </div>
          </div>
        </div>

        <div class="header-actions">
          <el-button
            v-if="canUseAi"
            data-testid="task-ai-suggestion-action"
            type="primary"
            @click="openAiSuggestionDialog"
          >
            <MaterialIcon name="auto_awesome" />
            生成 AI 建议
          </el-button>

          <el-button
            v-if="canEdit"
            @click="openEditDialog"
          >
            <MaterialIcon name="edit" />
            编辑
          </el-button>

          <el-button
            v-if="canChangeStatus"
            @click="openStatusDialog"
          >
            <MaterialIcon name="sync_alt" />
            修改状态
          </el-button>

          <el-button
            v-if="canEdit"
            :loading="deleteSubmitting"
            type="danger"
            @click="handleDelete"
          >
            <MaterialIcon name="delete" />
            删除
          </el-button>
        </div>
      </header>

      <section class="task-overview-grid">
        <div class="task-overview-main">
          <article class="task-overview-section latest-progress">
            <div class="section-header">
              <h3>最后记录进度</h3>
              <strong>{{ latestProgress }}%</strong>
            </div>
            <div
              class="progress-track"
              role="progressbar"
              aria-label="最后记录进度"
              aria-valuemin="0"
              aria-valuemax="100"
              :aria-valuenow="latestProgress"
            >
              <span :style="{ width: `${latestProgress}%` }" />
            </div>
            <p class="latest-progress-content">
              {{ latestLog?.content ?? '尚未记录进度' }}
            </p>
            <small v-if="latestLog" class="muted">
              <UserLink :user-id="latestLog.operator.id">{{ latestLog.operator.displayName }}</UserLink>
              · {{ formatDateTime(latestLog.createdAt) }}
            </small>
          </article>

          <article class="task-overview-section">
            <h3>任务描述</h3>
            <div class="description-text">
              {{ task.description || '暂无描述' }}
            </div>
          </article>

          <article class="task-overview-section child-task-section">
            <h3>子任务</h3>
            <el-table
              v-if="childTasks.length > 0"
              :data="childTasks"
              row-key="id"
              size="small"
            >
              <el-table-column label="标题" min-width="140">
                <template #default="{ row }">
                  <TaskLink :task-id="row.id" :project-id="task.projectId">
                    {{ row.title }}
                  </TaskLink>
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
                  {{ priorityLabels[(row as Task).priority] }}
                </template>
              </el-table-column>
              <el-table-column label="负责人" width="100">
                <template #default="{ row }">
                  <UserLink v-if="row.assignee" :user-id="row.assignee.id">
                    {{ row.assignee.displayName }}
                  </UserLink>
                  <span v-else>未委派</span>
                </template>
              </el-table-column>
            </el-table>
            <p v-else class="muted">暂无子任务</p>
          </article>
        </div>

        <aside class="task-information">
          <h3>任务信息</h3>
          <dl>
            <div>
              <dt>状态</dt>
              <dd>{{ statusLabels[task.status] }}</dd>
            </div>
            <div>
              <dt>优先级</dt>
              <dd>{{ priorityLabels[task.priority] }}</dd>
            </div>
            <div>
              <dt>负责人</dt>
              <dd>
                <UserLink v-if="task.assignee" :user-id="task.assignee.id">
                  {{ task.assignee.displayName }}
                </UserLink>
                <span v-else>未委派</span>
              </dd>
            </div>
            <div>
              <dt>创建者</dt>
              <dd><UserLink :user-id="task.creator.id">{{ task.creator.displayName }}</UserLink></dd>
            </div>
            <div>
              <dt>原截止日期</dt>
              <dd>{{ task.dueDate ?? '未设置' }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatDateTime(task.updatedAt) }}</dd>
            </div>
            <div v-if="task.parentId">
              <dt>父任务</dt>
              <dd>
                <TaskLink
                  v-if="parentTask"
                  :task-id="task.parentId"
                  :project-id="task.projectId"
                >
                  {{ parentTask.title }}
                </TaskLink>
                <span v-else>关联任务不可用</span>
              </dd>
            </div>
          </dl>
        </aside>
      </section>

      <!-- Task logs -->
      <section class="content-panel">
        <div class="section-header">
          <h3>进度记录</h3>

          <el-button
            v-if="canCreateLog"
            type="primary"
            @click="openAddLogDialog"
          >
            <MaterialIcon name="add" :size="18" />
            新增日志
          </el-button>
        </div>

        <el-alert
          v-if="logsError"
          class="logs-error"
          :closable="false"
          :title="logsError"
          type="error"
          show-icon
        >
          <template #default>
            <el-button
              size="small"
              @click="fetchLogs"
            >
              重新加载
            </el-button>
          </template>
        </el-alert>

        <div v-else v-loading="logsLoading" class="progress-timeline">
          <el-empty v-if="logs.length === 0" description="暂无进度记录" />
          <template v-else>
            <article
              v-for="log in logs"
              :key="log.id"
              class="progress-timeline-item"
              :style="{ '--timeline-color': progressColor(log.progressPercent) }"
            >
              <div class="timeline-marker">
                <span :class="{ complete: log.progressPercent === 100 }" />
              </div>
              <div class="timeline-content">
                <header>
                  <strong>{{ log.progressPercent }}%</strong>
                  <UserLink :user-id="log.operator.id">{{ log.operator.displayName }}</UserLink>
                </header>
                <p>{{ log.content }}</p>
                <footer>
                  <time>{{ formatDateTime(log.createdAt) }}</time>
                  <el-button
                    v-if="canDeleteLog(log)"
                    :aria-label="`删除 ${log.operator.displayName} 的进度记录`"
                    size="small"
                    type="danger"
                    text
                    @click="handleDeleteLog(log)"
                  >
                    <MaterialIcon name="delete" :size="16" />
                    删除记录
                  </el-button>
                </footer>
              </div>
            </article>
          </template>
        </div>

        <el-pagination
          v-if="logTotalElements > 0"
          class="task-pagination"
          :current-page="logPage + 1"
          layout="total, prev, pager, next"
              :page-size="PAGE_SIZE"
          :total="logTotalElements"
          @current-change="handleLogPageChange"
        />
      </section>

      <el-dialog
        v-model="aiDialogVisible"
        align-center
        destroy-on-close
        :teleported="false"
        title="AI 任务建议"
        width="min(760px, 92vw)"
        @closed="resetAiSuggestionDialog"
      >
        <div v-if="aiGenerating" class="ai-loading" aria-label="AI 建议生成中">
          <el-skeleton animated :rows="6" />
        </div>
        <div v-else-if="aiErrorMessage" class="feedback-state">
          <el-alert
            :closable="false"
            :title="aiErrorMessage"
            type="error"
            show-icon
          />
          <el-button type="primary" @click="handleAiSuggestion">重新生成</el-button>
        </div>
        <div v-else-if="aiSuggestion" class="ai-result">
          <el-alert
            class="ai-hint"
            :closable="false"
            title="AI 建议为临时内容，请人工审阅修改后再提交为进度记录"
            type="warning"
            show-icon
          />

          <div class="ai-form">
            <label class="ai-label">建议内容（可编辑）</label>
            <el-input
              v-model="aiSuggestion"
              maxlength="1000"
              placeholder="编辑 AI 建议内容"
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
      </el-dialog>

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
          <el-form-item label="父任务">
            <el-select
              v-model="editForm.parentId"
              clearable
              filterable
              :loading="editOptionsLoading"
              placeholder="可选"
            >
              <el-option
                v-for="candidate in editParentOptions"
                :key="candidate.id"
                :label="candidate.title"
                :value="candidate.id"
              />
            </el-select>
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

          <el-form-item label="负责人">
            <el-select
              v-model="editForm.assigneeId"
              clearable
              filterable
              :loading="editOptionsLoading"
              placeholder="可选"
            >
              <el-option
                v-for="member in editMemberOptions"
                :key="member.user.id"
                :label="member.user.displayName"
                :value="member.user.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="状态">
            <el-select
              v-model="editForm.status"
              class="enum-select"
            >
              <el-option label="未开始" value="NOT_STARTED" />
              <el-option label="进行中" value="IN_PROGRESS" />
              <el-option label="阻塞中" value="BLOCKED" />
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
              <el-option label="阻塞中" value="BLOCKED" />
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
  min-width: 0;
  gap: 16px;
  container-type: inline-size;
  grid-template-columns: minmax(0, 1fr);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
}

.breadcrumb a {
  color: var(--fs-color-primary, #2563eb);
  text-decoration: none;
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
  overflow-wrap: anywhere;
}

.page-header > div:first-child {
  min-width: 0;
}

.task-meta {
  display: grid;
  gap: 8px;
  margin-top: 8px;
  color: var(--fs-color-text-secondary, #64748b);
}

.task-meta-tags,
.task-meta-context {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.content-panel {
  min-width: 0;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
  padding: 20px;
  overflow-x: auto;
}

.logs-error {
  margin-bottom: 12px;
}

.task-overview-section h3,
.task-information h3,
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

.task-overview-grid {
  display: grid;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
  grid-template-columns: minmax(0, 1.7fr) minmax(260px, 0.8fr);
}

.task-overview-main {
  min-width: 0;
}

.task-overview-section,
.task-information {
  padding: 20px;
}

.task-overview-section:not(:last-child) {
  border-bottom: 1px solid var(--fs-color-border, #dbe3ee);
}

.task-information {
  border-left: 1px solid var(--fs-color-border, #dbe3ee);
}

.task-information dl {
  display: grid;
  gap: 16px;
  margin: 20px 0 0;
}

.task-information dl div {
  display: grid;
  gap: 4px;
}

.task-information dt {
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
}

.task-information dt::after {
  content: '：';
}

.task-information dd {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
  overflow-wrap: anywhere;
}

.latest-progress .section-header strong {
  color: var(--el-color-primary);
  font-size: 20px;
}

.progress-track {
  height: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--el-color-primary-light-8);
}

.progress-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--el-color-primary);
}

.latest-progress-content {
  margin: 16px 0 6px;
  color: var(--fs-color-text, #1f2937);
  line-height: 1.6;
}

.child-task-section > .muted {
  display: block;
  margin: 12px 0 0;
}

.description-text {
  max-width: 70ch;
  color: var(--fs-color-text, #1f2937);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  line-height: 1.6;
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

.progress-timeline {
  min-height: 120px;
}

.progress-timeline-item {
  display: grid;
  min-width: 0;
  gap: 12px;
  padding-bottom: 24px;
  grid-template-columns: 18px minmax(0, 1fr);
}

.progress-timeline-item:last-child {
  padding-bottom: 0;
}

.timeline-marker {
  position: relative;
  display: flex;
  justify-content: center;
  padding-top: 5px;
}

.timeline-marker span {
  z-index: 1;
  width: 12px;
  height: 12px;
  border: 3px solid var(--fs-color-surface, #fff);
  border-radius: 50%;
  background: var(--timeline-color);
  box-shadow: 0 0 0 2px var(--timeline-color);
}

.timeline-marker span.complete {
  background: var(--el-color-success);
  box-shadow: 0 0 0 2px var(--el-color-success);
}

.progress-timeline-item:not(:last-child) .timeline-marker::after {
  position: absolute;
  top: 17px;
  bottom: -19px;
  width: 2px;
  background: var(--fs-color-border-strong, #c7d2e0);
  content: '';
}

.timeline-content {
  min-width: 0;
  padding: 12px 14px;
  border-left: 3px solid var(--timeline-color);
  border-radius: 6px;
  background: linear-gradient(
    100deg,
    color-mix(in srgb, var(--timeline-color) 12%, transparent),
    transparent 72%
  );
}

.timeline-content header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.timeline-content header strong {
  color: var(--timeline-color);
  font-size: 18px;
}

.timeline-content header span,
.timeline-content time {
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
}

.timeline-content p {
  margin: 8px 0;
  color: var(--fs-color-text, #1f2937);
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.timeline-content footer {
  display: flex;
  min-height: 28px;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
}

@container (max-width: 700px) {
  .task-overview-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .task-information {
    border-top: 1px solid var(--fs-color-border, #dbe3ee);
    border-left: 0;
  }
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

.ai-loading {
  min-height: 240px;
  padding: 12px 0;
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
    overflow-x: auto;
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
