<script setup lang="ts">
import {
  computed,
  nextTick,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/pagination/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'
import type { FormInstance, FormRules } from 'element-plus'

import MaterialIcon from '@/components/MaterialIcon.vue'
import ProjectLink from '@/components/ProjectLink.vue'
import TaskLink from '@/components/TaskLink.vue'
import UserLink from '@/components/UserLink.vue'
import { getApiErrorMessage } from '@/shared/api/errors'
import { fetchAllPages, PAGE_SIZE } from '@/shared/api/pagination'
import type {
  Priority,
  TaskStatus,
} from '@/shared/api/types'
import { useAuthStore } from '@/stores/auth'
import {
  getProject,
  getProjectMembers,
  getProjects,
} from '@/views/projects/api'
import type {
  Project,
  ProjectMember,
} from '@/views/projects/types'

import {
  createTask,
  getTasks,
} from './api'
import type {
  CreateTaskBody,
  Task,
  TaskListFilters,
  TaskListQuery,
} from './types'

type PageState =
  | 'initialLoading'
  | 'refreshing'
  | 'success'
  | 'empty'
  | 'error'

type TagType =
  | 'primary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'

const props = withDefaults(defineProps<{
  embedded?: boolean
  project?: Project | null
}>(), {
  embedded: false,
  project: null,
})

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

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

function getContextProjectId(): string {
  if (props.project) return props.project.id

  return typeof route?.query?.projectId === 'string'
    ? route.query.projectId
    : ''
}

function createInitialFilters(): TaskListFilters {
  const routeStatus = typeof route?.query?.status === 'string'
    && Object.prototype.hasOwnProperty.call(statusLabels, route.query.status)
    ? route.query.status as TaskStatus
    : ''
  return {
    q: '',
    projectId: getContextProjectId(),
    status: routeStatus,
    priority: '',
    dueBefore: typeof route?.query?.dueBefore === 'string' ? route.query.dueBefore : '',
    dueAfter: typeof route?.query?.dueAfter === 'string' ? route.query.dueAfter : '',
    incomplete: route?.query?.incomplete === 'true',
  }
}

const filters = reactive<TaskListFilters>(
  createInitialFilters(),
)

const appliedFilters = ref<TaskListFilters>(
  createInitialFilters(),
)

const tasks = ref<Task[]>([])
const summaryTasks = ref<Task[]>([])
const taskListRef = ref<HTMLElement | null>(null)
const projectOptions = ref<Project[]>([])
const writableProjectOptions = computed(() => projectOptions.value
  .filter((project) => project.archivedAt === null))
const projectNames = computed(() => new Map(
  projectOptions.value.map((project) => [project.id, project.name]),
))
const page = ref(0)
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined
let taskRequestId = 0

const isAdmin = computed(() => authStore.currentUser?.systemRole === 'ADMIN')

const hasActiveFilters = computed(() => {
  const current = appliedFilters.value

  return (
    current.q !== ''
    || (!props.embedded && current.projectId !== '')
    || current.status !== ''
    || current.priority !== ''
    || current.dueBefore !== ''
    || current.dueAfter !== ''
    || current.incomplete
  )
})

const pageState = computed<PageState>(() => {
  if (loading.value && !loaded.value) {
    return 'initialLoading'
  }

  if (errorMessage.value) {
    return 'error'
  }

  if (loading.value) {
    return 'refreshing'
  }

  return tasks.value.length > 0
    ? 'success'
    : 'empty'
})

const taskSummaryCards = computed(() => [
  {
    key: 'NOT_STARTED',
    label: '未开始',
    icon: 'checklist',
    count: summaryTasks.value.filter((task) => task.status === 'NOT_STARTED').length,
  },
  {
    key: 'IN_PROGRESS',
    label: '进行中',
    icon: 'schedule',
    count: summaryTasks.value.filter((task) => task.status === 'IN_PROGRESS').length,
  },
  {
    key: 'COMPLETED',
    label: '已完成',
    icon: 'task_alt',
    count: summaryTasks.value.filter((task) => task.status === 'COMPLETED').length,
  },
  {
    key: 'BLOCKED',
    label: '阻塞中',
    icon: 'warning',
    count: summaryTasks.value.filter((task) => task.status === 'BLOCKED').length,
  },
] as const)

const dueSoonTasks = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const deadline = new Date(today)
  deadline.setDate(deadline.getDate() + 7)

  return summaryTasks.value.filter((task) => {
    if (!task.dueDate || task.status === 'COMPLETED' || task.status === 'CANCELLED') return false
    const dueDate = new Date(`${task.dueDate}T00:00:00`)
    return dueDate > today && dueDate <= deadline
  })
})

const dueSoonTitle = computed(() => {
  const titles = dueSoonTasks.value.slice(0, 3).map((task) => task.title)
  const suffix = dueSoonTasks.value.length > 3 ? ' 等' : ''
  return `7 天内即将到期：${titles.join('、')}${suffix}（共 ${dueSoonTasks.value.length} 个）`
})

function buildQuery(): TaskListQuery {
  const current = appliedFilters.value

  return {
    q: current.q || undefined,
    projectId: current.projectId || undefined,
    assigneeId: props.embedded ? undefined : authStore.currentUser?.id,
    status: current.status || undefined,
    priority: current.priority || undefined,
    dueBefore: current.dueBefore || undefined,
    dueAfter: current.dueAfter || undefined,
    incomplete: current.incomplete || undefined,
    page: page.value,
    size: PAGE_SIZE,
    sort: 'createdAt,desc',
  }
}

async function loadTasks(): Promise<void> {
  const requestId = ++taskRequestId
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getTasks(buildQuery())
    if (requestId !== taskRequestId) return

    tasks.value = [...result.items]
    page.value = result.page
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error) {
    if (requestId !== taskRequestId) return
    errorMessage.value = getApiErrorMessage(
      error,
      '任务列表加载失败，请稍后重试',
    )
  } finally {
    if (requestId === taskRequestId) {
      loading.value = false
      loaded.value = true
    }
  }
}

async function loadTaskSummary(): Promise<void> {
  if (props.embedded || isAdmin.value || !authStore.currentUser?.id) {
    summaryTasks.value = []
    return
  }

  try {
    summaryTasks.value = [...await fetchAllPages(getTasks, {
      assigneeId: authStore.currentUser.id,
      sort: 'dueDate,asc',
    })]
  } catch {
    summaryTasks.value = []
  }
}

function isSummaryCardActive(status: TaskStatus): boolean {
  return appliedFilters.value.status === status
}

async function applySummaryFilter(status: TaskStatus): Promise<void> {
  clearTimeout(searchTimer)
  searchTimer = undefined
  const initial: TaskListFilters = {
    q: '',
    projectId: getContextProjectId(),
    status,
    priority: '',
    dueBefore: '',
    dueAfter: '',
    incomplete: false,
  }
  Object.assign(filters, initial)
  appliedFilters.value = initial
  page.value = 0
  await Promise.all([loadTasks(), checkCreatePermission('')])
  await nextTick()
  taskListRef.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}

async function handleSearch(): Promise<void> {
  clearTimeout(searchTimer)
  searchTimer = undefined
  appliedFilters.value = {
    q: filters.q.trim(),
    projectId: filters.projectId.trim(),
    status: filters.status,
    priority: filters.priority,
    dueBefore: filters.dueBefore,
    dueAfter: filters.dueAfter,
    incomplete: filters.incomplete,
  }

  page.value = 0
  await Promise.all([
    loadTasks(),
    checkCreatePermission(appliedFilters.value.projectId),
  ])
}

function scheduleSearch(): void {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void handleSearch(), 300)
}

async function handleReset(): Promise<void> {
  clearTimeout(searchTimer)
  searchTimer = undefined
  const initial: TaskListFilters = {
    q: '',
    projectId: getContextProjectId(),
    status: '',
    priority: '',
    dueBefore: '',
    dueAfter: '',
    incomplete: false,
  }

  Object.assign(filters, initial)
  appliedFilters.value = initial
  page.value = 0

  await Promise.all([
    loadTasks(),
    checkCreatePermission(initial.projectId),
  ])
}

async function handlePageChange(
  displayedPage: number,
): Promise<void> {
  page.value = displayedPage - 1
  await loadTasks()
}

// --- Create dialog ---

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createSubmitting = ref(false)
const createRelationsLoading = ref(false)
const createMemberOptions = ref<ProjectMember[]>([])
const createParentOptions = ref<Task[]>([])

const defaultCreateForm: {
  projectId: string
  parentId: string
  title: string
  description: string
  assigneeId: string
  status: TaskStatus
  priority: Priority
  dueDate: string
} = {
  projectId: '',
  parentId: '',
  title: '',
  description: '',
  assigneeId: '',
  status: 'NOT_STARTED',
  priority: 'MEDIUM',
  dueDate: '',
}

const createForm = reactive({ ...defaultCreateForm })

const createRules: FormRules = {
  projectId: [
    { required: true, message: '请选择项目', trigger: 'change' },
  ],
  title: [
    { required: true, message: '请输入任务标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过 100 个字符', trigger: 'blur' },
  ],
  description: [
    { max: 5000, message: '描述不能超过 5000 个字符', trigger: 'blur' },
  ],
}

// --- Create permission ---
const createProjectOwnerId = ref<string | null>(null)
const createProjectArchived = ref(false)

async function checkCreatePermission(projectId: string): Promise<void> {
  if (!projectId) {
    createProjectOwnerId.value = null
    createProjectArchived.value = false
    return
  }
  if (props.project?.id === projectId) {
    createProjectOwnerId.value = props.project.owner.id
    createProjectArchived.value = Boolean(props.project.archivedAt)
    return
  }
  try {
    const project = await getProject(projectId)
    createProjectOwnerId.value = project.owner.id
    createProjectArchived.value = Boolean(project.archivedAt)
  } catch {
    createProjectOwnerId.value = null
    createProjectArchived.value = false
  }
}

const canCreateTask = computed(() => {
  if (authStore.currentUser?.systemRole !== 'USER') return false
  const pid = appliedFilters.value.projectId
  if (!pid) return false
  if (createProjectArchived.value) return false
  return createProjectOwnerId.value === authStore.currentUser?.id
})

async function loadProjectOptions(): Promise<void> {
  if (props.project) {
    projectOptions.value = [props.project]
    return
  }

  try {
    const [active, archived] = await Promise.all([
      fetchAllPages(getProjects, { archived: false, sort: 'name,asc' }),
      fetchAllPages(getProjects, { archived: true, sort: 'name,asc' }),
    ])
    projectOptions.value = [...active, ...archived]
  } catch (error) {
    projectOptions.value = []
    ElMessage.error(getApiErrorMessage(error, '项目选项加载失败，请稍后重试'))
  }
}

async function loadCreateRelations(projectId: string): Promise<void> {
  createMemberOptions.value = []
  createParentOptions.value = []
  createForm.parentId = ''
  createForm.assigneeId = ''
  if (!projectId) return

  createRelationsLoading.value = true
  try {
    const [members, parents] = await Promise.all([
      getProjectMembers(projectId),
      fetchAllPages(getTasks, { projectId, sort: 'title,asc' }),
    ])
    createMemberOptions.value = [...members]
    createParentOptions.value = [...parents]
  } catch (error) {
    createMemberOptions.value = []
    createParentOptions.value = []
    ElMessage.error(getApiErrorMessage(error, '任务关联选项加载失败，请稍后重试'))
  } finally {
    createRelationsLoading.value = false
  }
}

async function openCreateDialog(): Promise<void> {
  const prefilled = {
    ...defaultCreateForm,
    projectId: appliedFilters.value.projectId || '',
  }
  Object.assign(createForm, prefilled)
  createDialogVisible.value = true
  await loadCreateRelations(createForm.projectId)
}

async function handleCreate(): Promise<void> {
  const form = createFormRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  const title = createForm.title.trim()
  if (!title) {
    ElMessage.warning('请输入任务标题')
    return
  }

  createSubmitting.value = true

  try {
    const body: CreateTaskBody = {
      projectId: createForm.projectId.trim(),
      parentId: createForm.parentId.trim() || null,
      title,
      description: createForm.description.trim() || null,
      assigneeId: createForm.assigneeId.trim() || null,
      status: createForm.status,
      priority: createForm.priority,
      dueDate: createForm.dueDate || null,
    }

    await createTask(body)
    ElMessage.success('任务创建成功')
    createDialogVisible.value = false
    await Promise.all([loadTasks(), loadTaskSummary()])
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '创建任务失败，请稍后重试'),
    )
  } finally {
    createSubmitting.value = false
  }
}

function goToProject(): void {
  const routeProjectId = getContextProjectId()
  if (!routeProjectId) return

  void router.push({
    name: 'project-detail',
    params: { projectId: routeProjectId },
  })
}

onMounted(async () => {
  const pid = appliedFilters.value.projectId
  await Promise.all([
    loadTasks(),
    loadProjectOptions(),
    loadTaskSummary(),
    pid ? checkCreatePermission(pid) : Promise.resolve(),
  ])
})

onUnmounted(() => clearTimeout(searchTimer))

watch(() => props.project?.id, (projectId) => {
  if (!props.embedded || !projectId) return

  clearTimeout(searchTimer)
  searchTimer = undefined

  const initial = createInitialFilters()
  Object.assign(filters, initial)
  appliedFilters.value = initial
  page.value = 0
  void Promise.all([
    loadTasks(),
    loadProjectOptions(),
    checkCreatePermission(projectId),
  ])
})

function projectName(projectId: string): string {
  return projectNames.value.get(projectId) ?? '项目不可用'
}

defineExpose({ reload: loadTasks })
</script>

<template>
  <section class="task-page" :class="{ 'task-page--embedded': embedded }">
    <header class="page-header">
      <h1>{{ embedded ? '任务委派' : '我的任务' }}</h1>

      <div class="header-actions">
        <el-button
          v-if="!embedded && getContextProjectId()"
          data-testid="back-to-project"
          @click="goToProject"
        >
          <MaterialIcon name="arrow_back" />
          返回项目
        </el-button>

        <el-button
          v-if="canCreateTask"
          type="primary"
          @click="openCreateDialog"
        >
          <MaterialIcon name="add_task" />
          创建任务
        </el-button>

      </div>
    </header>

    <template v-if="!embedded && !isAdmin">
      <div
        v-if="dueSoonTasks.length > 0"
        class="deadline-alert"
        data-testid="task-deadline-alert"
        role="alert"
      >
        <MaterialIcon name="warning" :size="24" />
        <strong>{{ dueSoonTitle }}</strong>
      </div>

      <section class="task-summary-grid" aria-label="我的任务汇总">
        <button
          v-for="card in taskSummaryCards"
          :key="card.key"
          class="task-summary-card"
          :class="{ active: isSummaryCardActive(card.key) }"
          :data-testid="`task-summary-${card.key}`"
          type="button"
          @click="applySummaryFilter(card.key)"
        >
          <span class="summary-icon">
            <MaterialIcon :name="card.icon" :size="24" />
          </span>
          <span>
            <strong>{{ card.count }}</strong>
            <small>{{ card.label }}</small>
          </span>
        </button>
      </section>
    </template>

    <section class="filter-panel">
      <el-form
        class="filter-layout"
        label-position="top"
        :model="filters"
        @submit.prevent="handleSearch"
      >
        <div class="filter-fields">
          <el-form-item label="标题搜索">
          <el-input
            v-model="filters.q"
            clearable
            placeholder="按标题搜索"
            @clear="handleSearch"
            @input="scheduleSearch"
          />
          </el-form-item>

          <el-form-item v-if="!embedded" label="项目">
          <el-select
            v-model="filters.projectId"
            clearable
            filterable
            placeholder="全部项目"
            @change="handleSearch"
          >
            <el-option
              v-for="project in projectOptions"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
          </el-form-item>

          <el-form-item label="状态">
          <el-select
            v-model="filters.status"
            class="enum-select"
            placeholder="全部状态"
            @change="handleSearch"
          >
            <el-option label="全部状态" value="" />
            <el-option label="未开始" value="NOT_STARTED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="阻塞中" value="BLOCKED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
          </el-form-item>

          <el-form-item label="优先级">
          <el-select
            v-model="filters.priority"
            class="enum-select"
            placeholder="全部优先级"
            @change="handleSearch"
          >
            <el-option label="全部优先级" value="" />
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
          </el-select>
          </el-form-item>

        </div>

        <div class="filter-actions" role="group" aria-label="筛选操作">
          <el-button @click="handleReset">
            <MaterialIcon name="filter_list_off" />
            重置
          </el-button>
        </div>
      </el-form>
      <div
        v-if="appliedFilters.dueBefore || appliedFilters.dueAfter"
        class="due-filter-hint"
      >
        <MaterialIcon name="schedule" :size="18" />
        <span v-if="appliedFilters.incomplete">仅未完成 ·</span>
        截止日期：{{ appliedFilters.dueAfter || '最早' }} 至
        {{ appliedFilters.dueBefore || '最晚' }}
      </div>
    </section>

    <section
      ref="taskListRef"
      class="content-panel"
      data-testid="task-content"
      :data-state="pageState"
    >
      <div
        v-if="pageState === 'initialLoading'"
        aria-label="加载中"
        class="initial-loading-space"
      />

      <div
        v-else-if="pageState === 'error'"
        class="feedback-state"
      >
        <el-alert
          :closable="false"
          :title="errorMessage"
          type="error"
          show-icon
        />

        <el-button
          type="primary"
          @click="loadTasks"
        >
          重新加载
        </el-button>
      </div>

      <template v-else>
        <el-table
          :data="tasks"
          row-key="id"
        >
          <el-table-column
            label="标题"
            min-width="160"
          >
            <template #default="{ row }">
              <TaskLink
                :task-id="(row as Task).id"
                :project-id="getContextProjectId() || undefined"
              >
                {{ row.title }}
              </TaskLink>
            </template>
          </el-table-column>

          <el-table-column
            v-if="!embedded"
            label="项目"
            min-width="140"
          >
            <template #default="{ row }">
              <ProjectLink
                v-if="projectNames.has((row as Task).projectId)"
                :project-id="(row as Task).projectId"
              >
                {{ projectName((row as Task).projectId) }}
              </ProjectLink>
              <span v-else>项目不可用</span>
            </template>
          </el-table-column>

          <el-table-column
            label="负责人"
            min-width="100"
          >
            <template #default="{ row }">
              <UserLink v-if="row.assignee" :user-id="row.assignee.id">
                {{ row.assignee.displayName }}
              </UserLink>
              <span v-else>--</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag
                :type="statusTagTypes[(row as Task).status]"
                effect="plain"
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
              >
                {{ priorityLabels[(row as Task).priority] }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            label="进度"
            width="80"
          >
            <template #default="{ row }">
              {{ row.progressPercent }}%
            </template>
          </el-table-column>

          <el-table-column
            label="截止日期"
            width="120"
          >
            <template #default="{ row }">
              {{ row.dueDate ?? '--' }}
            </template>
          </el-table-column>

          <template #empty>
            <el-empty
              :description="
                hasActiveFilters
                  ? '没有符合条件的任务'
                  : embedded
                    ? '当前项目没有任务'
                    : '当前没有负责的任务'
              "
            >
              <el-button
                v-if="hasActiveFilters"
                @click="handleReset"
              >
                重置筛选
              </el-button>
            </el-empty>
          </template>
        </el-table>

        <el-pagination
          v-if="totalElements > 0"
          class="task-pagination"
          :current-page="page + 1"
          layout="total, prev, pager, next"
          :page-size="PAGE_SIZE"
          :total="totalElements"
          @current-change="handlePageChange"
        />
      </template>
    </section>

    <!-- Create Task Dialog -->
    <el-dialog
      v-model="createDialogVisible"
      title="创建任务"
      width="560px"
      @closed="createFormRef?.resetFields()"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
      >
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="createForm.projectId"
            filterable
            placeholder="选择项目"
            @change="loadCreateRelations"
          >
            <el-option
              v-for="project in writableProjectOptions"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="父任务">
          <el-select
            v-model="createForm.parentId"
            clearable
            filterable
            :loading="createRelationsLoading"
            placeholder="可选"
          >
            <el-option
              v-for="parent in createParentOptions"
              :key="parent.id"
              :label="parent.title"
              :value="parent.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标题" prop="title">
          <el-input
            v-model="createForm.title"
            maxlength="100"
            placeholder="1 到 100 个字符"
          />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="createForm.description"
            maxlength="5000"
            placeholder="可选，最长 5000 个字符"
            type="textarea"
            :rows="3"
          />
        </el-form-item>

        <el-form-item label="负责人">
          <el-select
            v-model="createForm.assigneeId"
            clearable
            filterable
            :loading="createRelationsLoading"
            placeholder="可选"
          >
            <el-option
              v-for="member in createMemberOptions"
              :key="member.user.id"
              :label="member.user.displayName"
              :value="member.user.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-select
            v-model="createForm.status"
            class="enum-select"
          >
            <el-option label="未开始" value="NOT_STARTED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="阻塞中" value="BLOCKED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级" prop="priority">
          <el-select
            v-model="createForm.priority"
            class="enum-select"
          >
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
          </el-select>
        </el-form-item>

        <el-form-item label="截止日期" prop="dueDate">
          <el-date-picker
            v-model="createForm.dueDate"
            placeholder="可选"
            type="date"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">
          取消
        </el-button>

        <el-button
          type="primary"
          :loading="createSubmitting"
          @click="handleCreate"
        >
          创建
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.task-page {
  display: grid;
  min-width: 0;
  gap: 16px;
  grid-template-columns: minmax(0, 1fr);
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

.page-header p {
  margin: 4px 0 0;
  color: var(--fs-color-text-secondary, #64748b);
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.deadline-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border: 1px solid #f59e0b;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
}

.task-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.task-summary-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
  color: var(--fs-color-text, #1f2937);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.task-summary-card:hover,
.task-summary-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 14px rgb(37 99 235 / 12%);
}

.task-summary-card.active {
  background: var(--el-color-primary-light-9);
}

.task-summary-card .summary-icon {
  display: inline-flex;
  padding: 10px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.task-summary-card strong,
.task-summary-card small {
  display: block;
}

.task-summary-card strong {
  font-size: 24px;
  line-height: 1.1;
}

.task-summary-card small {
  margin-top: 4px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
}

.filter-panel,
.content-panel {
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
}

.filter-panel {
  padding: 16px;
}

.due-filter-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
}

.content-panel {
  min-height: 320px;
  padding: 20px;
  overflow-x: auto;
}

.enum-select {
  width: 100%;
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

.muted {
  color: var(--fs-color-text-secondary, #64748b);
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .content-panel {
    padding: 16px;
    overflow-x: auto;
  }
}
</style>
