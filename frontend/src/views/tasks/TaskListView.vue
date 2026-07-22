<script setup lang="ts">
import {
  computed,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { useRoute } from 'vue-router'
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
  ElSkeleton,
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
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'
import type { FormInstance, FormRules } from 'element-plus'

import { getApiErrorMessage } from '@/shared/api/errors'
import type {
  Priority,
  TaskStatus,
} from '@/shared/api/types'
import { useAuthStore } from '@/stores/auth'
import { getProject } from '@/views/projects/api'

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

const route = useRoute()
const authStore = useAuthStore()

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

function createInitialFilters(): TaskListFilters {
  const queryProjectId =
    typeof route?.query?.projectId === 'string'
      ? route.query.projectId
      : ''

  return {
    q: '',
    projectId: queryProjectId,
    assigneeId: '',
    status: '',
    priority: '',
    parentId: '',
    dueBefore: '',
    dueAfter: '',
  }
}

const filters = reactive<TaskListFilters>(
  createInitialFilters(),
)

const appliedFilters = ref<TaskListFilters>(
  createInitialFilters(),
)

const tasks = ref<Task[]>([])
const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')

const hasActiveFilters = computed(() => {
  const current = appliedFilters.value

  return (
    current.q !== ''
    || current.projectId !== ''
    || current.assigneeId !== ''
    || current.status !== ''
    || current.priority !== ''
    || current.parentId !== ''
    || current.dueBefore !== ''
    || current.dueAfter !== ''
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

function buildQuery(): TaskListQuery {
  const current = appliedFilters.value

  return {
    q: current.q || undefined,
    projectId: current.projectId || undefined,
    assigneeId: current.assigneeId || undefined,
    status: current.status || undefined,
    priority: current.priority || undefined,
    parentId: current.parentId || undefined,
    dueBefore: current.dueBefore || undefined,
    dueAfter: current.dueAfter || undefined,
    page: page.value,
    size: size.value,
    sort: 'createdAt,desc',
  }
}

async function loadTasks(): Promise<void> {
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getTasks(buildQuery())

    tasks.value = [...result.items]
    page.value = result.page
    size.value = result.size
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error) {
    errorMessage.value = getApiErrorMessage(
      error,
      '任务列表加载失败，请稍后重试',
    )
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function handleSearch(): Promise<void> {
  appliedFilters.value = {
    q: filters.q.trim(),
    projectId: filters.projectId.trim(),
    assigneeId: filters.assigneeId.trim(),
    status: filters.status,
    priority: filters.priority,
    parentId: filters.parentId.trim(),
    dueBefore: filters.dueBefore,
    dueAfter: filters.dueAfter,
  }

  page.value = 0
  await Promise.all([
    loadTasks(),
    checkCreatePermission(appliedFilters.value.projectId),
  ])
}

async function handleReset(): Promise<void> {
  const initial = createInitialFilters()

  Object.assign(filters, initial)
  appliedFilters.value = initial
  page.value = 0

  await loadTasks()
}

async function handlePageChange(
  displayedPage: number,
): Promise<void> {
  page.value = displayedPage - 1
  await loadTasks()
}

async function handleSizeChange(
  nextSize: number,
): Promise<void> {
  size.value = nextSize
  page.value = 0
  await loadTasks()
}

// --- Create dialog ---

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createSubmitting = ref(false)

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
    { required: true, message: '请输入项目 ID', trigger: 'blur' },
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

function openCreateDialog(): void {
  const prefilled = {
    ...defaultCreateForm,
    projectId: appliedFilters.value.projectId || '',
  }
  Object.assign(createForm, prefilled)
  createDialogVisible.value = true
}

async function handleCreate(): Promise<void> {
  const form = createFormRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  createSubmitting.value = true

  try {
    const body: CreateTaskBody = {
      projectId: createForm.projectId.trim(),
      parentId: createForm.parentId.trim() || null,
      title: createForm.title.trim(),
      description: createForm.description.trim() || null,
      assigneeId: createForm.assigneeId.trim() || null,
      status: createForm.status,
      priority: createForm.priority,
      dueDate: createForm.dueDate || null,
    }

    await createTask(body)
    ElMessage.success('任务创建成功')
    createDialogVisible.value = false
    await loadTasks()
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '创建任务失败，请稍后重试'),
    )
  } finally {
    createSubmitting.value = false
  }
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

onMounted(async () => {
  const pid = appliedFilters.value.projectId
  await Promise.all([
    loadTasks(),
    pid ? checkCreatePermission(pid) : Promise.resolve(),
  ])
})
</script>

<template>
  <section class="task-page">
    <header class="page-header">
      <div>
        <h1>任务</h1>
        <p>
          ADMIN 查看全部任务，USER 只查看自己参与项目的任务。
        </p>
      </div>

      <div class="header-actions">
        <el-button
          v-if="canCreateTask"
          type="primary"
          @click="openCreateDialog"
        >
          创建任务
        </el-button>

        <el-button
          :loading="loading"
          @click="loadTasks"
        >
          刷新
        </el-button>
      </div>
    </header>

    <section class="filter-panel">
      <el-form
        :inline="true"
        :model="filters"
        @submit.prevent="handleSearch"
      >
        <el-form-item label="标题搜索">
          <el-input
            v-model="filters.q"
            clearable
            placeholder="按标题搜索"
          />
        </el-form-item>

        <el-form-item label="项目 ID">
          <el-input
            v-model="filters.projectId"
            clearable
            placeholder="可选"
          />
        </el-form-item>

        <el-form-item label="负责人 ID">
          <el-input
            v-model="filters.assigneeId"
            clearable
            placeholder="可选"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="filters.status"
            class="enum-select"
            placeholder="全部状态"
          >
            <el-option label="全部状态" value="" />
            <el-option label="未开始" value="NOT_STARTED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="阻塞" value="BLOCKED" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级">
          <el-select
            v-model="filters.priority"
            class="enum-select"
            placeholder="全部优先级"
          >
            <el-option label="全部优先级" value="" />
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
          </el-select>
        </el-form-item>

        <el-form-item label="父任务 ID">
          <el-input
            v-model="filters.parentId"
            clearable
            placeholder="可选"
          />
        </el-form-item>

        <el-form-item label="截止日期">
          <el-date-picker
            v-model="filters.dueBefore"
            placeholder="不晚于"
            type="date"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>

        <el-form-item label="起始日期">
          <el-date-picker
            v-model="filters.dueAfter"
            placeholder="不早于"
            type="date"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            native-type="submit"
            type="primary"
          >
            查询
          </el-button>

          <el-button @click="handleReset">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <section
      class="content-panel"
      data-testid="task-content"
      :data-state="pageState"
    >
      <el-skeleton
        v-if="pageState === 'initialLoading'"
        animated
        :rows="6"
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
        <el-alert
          v-if="pageState === 'refreshing'"
          class="refresh-alert"
          :closable="false"
          title="正在刷新任务数据"
          type="info"
          show-icon
        />

        <el-table
          :data="tasks"
          row-key="id"
        >
          <el-table-column
            label="标题"
            min-width="160"
          >
            <template #default="{ row }">
              <router-link
                :to="`/tasks/${row.id}`"
                class="task-link"
              >
                {{ row.title }}
              </router-link>
            </template>
          </el-table-column>

          <el-table-column
            label="项目"
            prop="projectId"
            width="100"
          />

          <el-table-column
            label="负责人"
            min-width="100"
          >
            <template #default="{ row }">
              {{ row.assignee?.displayName ?? '--' }}
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

          <el-table-column
            label="更新时间"
            min-width="160"
          >
            <template #default="{ row }">
              <span class="muted">{{ formatDateTime(row.updatedAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <router-link
                :to="`/tasks/${row.id}`"
              >
                查看
              </router-link>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty
              :description="
                hasActiveFilters
                  ? '没有符合条件的任务'
                  : '当前没有可见任务'
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
          layout="total, sizes, prev, pager, next"
          :page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalElements"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
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
        <el-form-item label="项目 ID" prop="projectId">
          <el-input
            v-model="createForm.projectId"
            placeholder="输入项目 ID"
          />
        </el-form-item>

        <el-form-item label="父任务 ID">
          <el-input
            v-model="createForm.parentId"
            placeholder="可选，同一项目内的父任务 ID"
          />
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

        <el-form-item label="负责人 ID">
          <el-input
            v-model="createForm.assigneeId"
            placeholder="可选，必须是项目成员"
          />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-select
            v-model="createForm.status"
            class="enum-select"
          >
            <el-option label="未开始" value="NOT_STARTED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="阻塞" value="BLOCKED" />
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
  gap: 16px;
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
  gap: 8px;
}

.filter-panel,
.content-panel {
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
}

.filter-panel {
  padding: 16px 16px 0;
}

.content-panel {
  min-height: 320px;
  padding: 20px;
}

.enum-select {
  width: 130px;
}

.feedback-state {
  display: grid;
  min-height: 240px;
  align-content: center;
  gap: 16px;
  justify-items: center;
}

.refresh-alert {
  margin-bottom: 12px;
}

.task-link {
  color: var(--fs-color-primary, #2563eb);
  text-decoration: none;
}

.task-link:hover {
  text-decoration: underline;
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
