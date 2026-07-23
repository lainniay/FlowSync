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
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/pagination/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import MaterialIcon from '@/components/MaterialIcon.vue'
import ProjectLink from '@/components/ProjectLink.vue'
import UserLink from '@/components/UserLink.vue'

import { getApiErrorMessage } from '@/shared/api/errors'
import { fetchAllPages, PAGE_SIZE } from '@/shared/api/pagination'
import type {
  Priority,
  ProjectStatus,
  User,
} from '@/shared/api/types'
import { useAuthStore } from '@/stores/auth'
import { getUsers } from '@/views/admin/api'

import {
  createProject,
  getProjects,
  getUserOptions,
} from './api'
import ProjectFormDialog from './ProjectFormDialog.vue'
import type {
  CreateProjectRequest,
  Project,
  ProjectListFilters,
  ProjectListQuery,
  UserOption,
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

const statusLabels: Record<ProjectStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
}

const statusTagTypes: Record<ProjectStatus, TagType> = {
  NOT_STARTED: 'info',
  IN_PROGRESS: 'primary',
  COMPLETED: 'success',
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

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isArchivedView = computed(() => route.query.archived === 'true')

function createInitialFilters(): ProjectListFilters {
  return {
    q: '',
    status: '',
    userId: '',
    myRole: '',
    archived: isArchivedView.value,
  }
}

const filters = reactive<ProjectListFilters>(
  createInitialFilters(),
)

const appliedFilters = ref<ProjectListFilters>(
  createInitialFilters(),
)

const projects = ref<Project[]>([])
const participatingProjects = ref<Project[]>([])
const projectListRef = ref<HTMLElement | null>(null)
const page = ref(0)
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined
let projectRequestId = 0

const createDialogVisible = ref(false)
const formSubmitting = ref(false)
const ownerOptions = ref<User[]>([])
const userOptions = ref<UserOption[]>([])
const userOptionsLoading = ref(false)

const isAdmin = computed(() => (
  authStore.currentUser?.systemRole === 'ADMIN'
))

const canCreateProject = computed(() => (
  authStore.currentUser?.systemRole === 'ADMIN'
  || authStore.currentUser?.systemRole === 'USER'
))

const hasActiveFilters = computed(() => {
  const current = appliedFilters.value

  return (
    current.q !== ''
    || current.status !== ''
    || current.userId !== ''
    || current.myRole !== ''
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

  return projects.value.length > 0
    ? 'success'
    : 'empty'
})

function buildQuery(): ProjectListQuery {
  const current = appliedFilters.value

  return {
    q: current.q || undefined,
    status: current.status || undefined,
    userId: current.userId || undefined,
    myRole: current.myRole || undefined,
    archived: current.archived,
    page: page.value,
    size: PAGE_SIZE,
    sort: 'createdAt,desc',
  }
}

async function loadProjects(): Promise<void> {
  const requestId = ++projectRequestId
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getProjects(buildQuery())
    if (requestId !== projectRequestId) return

    projects.value = [...result.items]
    page.value = result.page
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error) {
    if (requestId !== projectRequestId) return
    errorMessage.value = getApiErrorMessage(
      error,
      '项目列表加载失败，请稍后重试',
    )
  } finally {
    if (requestId === projectRequestId) {
      loading.value = false
      loaded.value = true
    }
  }
}

async function loadProjectSummary(): Promise<void> {
  if (isAdmin.value || isArchivedView.value) {
    participatingProjects.value = []
    return
  }

  try {
    participatingProjects.value = [...await fetchAllPages(getProjects, {
      archived: false,
      sort: 'endDate,asc',
    })]
  } catch {
    participatingProjects.value = []
  }
}

const projectSummaryCards = computed(() => [
  {
    key: 'COMPLETED',
    label: '已完成',
    icon: 'task_alt',
    count: participatingProjects.value.filter((project) => project.status === 'COMPLETED').length,
  },
  {
    key: 'IN_PROGRESS',
    label: '进行中',
    icon: 'schedule',
    count: participatingProjects.value.filter((project) => project.status === 'IN_PROGRESS').length,
  },
  {
    key: 'OWNER',
    label: '我负责',
    icon: 'account_circle',
    count: participatingProjects.value.filter(
      (project) => project.owner.id === authStore.currentUser?.id,
    ).length,
  },
  {
    key: 'MEMBER',
    label: '作为成员参与',
    icon: 'group',
    count: participatingProjects.value.filter(
      (project) => project.owner.id !== authStore.currentUser?.id,
    ).length,
  },
] as const)

const endingSoonProjects = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const deadline = new Date(today)
  deadline.setDate(deadline.getDate() + 7)

  return participatingProjects.value.filter((project) => {
    if (!project.endDate || project.status === 'COMPLETED') return false
    const endDate = new Date(`${project.endDate}T00:00:00`)
    return endDate >= today && endDate <= deadline
  })
})

const endingSoonTitle = computed(() => {
  const names = endingSoonProjects.value.slice(0, 3).map((project) => project.name)
  const suffix = endingSoonProjects.value.length > 3 ? ' 等' : ''
  return `7 天内即将结束：${names.join('、')}${suffix}（共 ${endingSoonProjects.value.length} 个）`
})

function isSummaryCardActive(key: string): boolean {
  return key === appliedFilters.value.status || key === appliedFilters.value.myRole
}

async function applySummaryFilter(
  key: 'COMPLETED' | 'IN_PROGRESS' | 'OWNER' | 'MEMBER',
): Promise<void> {
  clearTimeout(searchTimer)
  searchTimer = undefined
  const initial = createInitialFilters()
  if (key === 'COMPLETED' || key === 'IN_PROGRESS') {
    initial.status = key
  } else {
    initial.myRole = key
  }
  Object.assign(filters, initial)
  appliedFilters.value = initial
  page.value = 0
  await loadProjects()
  await nextTick()
  projectListRef.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}

async function handleSearch(): Promise<void> {
  clearTimeout(searchTimer)
  searchTimer = undefined
  appliedFilters.value = {
    q: filters.q.trim(),
    status: filters.status,
    userId: filters.userId,
    myRole: filters.myRole,
    archived: filters.archived,
  }

  page.value = 0
  await loadProjects()
}

function scheduleSearch(): void {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void handleSearch(), 300)
}

async function handleReset(): Promise<void> {
  clearTimeout(searchTimer)
  searchTimer = undefined
  const initial = createInitialFilters()

  Object.assign(filters, initial)
  appliedFilters.value = initial
  page.value = 0

  await loadProjects()
}

async function handlePageChange(
  displayedPage: number,
): Promise<void> {
  page.value = displayedPage - 1
  await loadProjects()
}

function projectRole(project: Project): string {
  if (project.owner.id === authStore.currentUser?.id) return 'Owner'
  return authStore.currentUser?.systemRole === 'ADMIN' ? '管理员' : '成员'
}

function projectStatusLabel(project: Project): string {
  return project.archivedAt ? '已归档' : statusLabels[project.status]
}

function projectStatusTagType(project: Project): TagType {
  return project.archivedAt ? 'info' : statusTagTypes[project.status]
}

async function loadOwnerOptions(): Promise<void> {
  if (!isAdmin.value) return

  const users = await fetchAllPages(getUsers, {
    systemRole: 'USER',
    active: true,
    sort: 'username,asc',
  })

  ownerOptions.value = [...users]
}

async function loadUserFilterOptions(): Promise<void> {
  userOptionsLoading.value = true
  try {
    userOptions.value = [...await getUserOptions()]
  } catch (error) {
    userOptions.value = []
    ElMessage.error(getApiErrorMessage(error, '用户列表加载失败，请稍后重试'))
  } finally {
    userOptionsLoading.value = false
  }
}

async function openCreateDialog(): Promise<void> {
  if (isAdmin.value) {
    await loadOwnerOptions()
  }

  createDialogVisible.value = true
}

async function handleCreateProject(
  payload: CreateProjectRequest,
): Promise<void> {
  formSubmitting.value = true

  try {
    const created = await createProject(payload)
    createDialogVisible.value = false
    ElMessage.success('项目已创建')
    await loadProjects()
    await router.push({
      name: 'project-detail',
      params: { projectId: created.id },
    })
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '项目创建失败，请稍后重试',
    ))
  } finally {
    formSubmitting.value = false
  }
}

function toggleArchivedView(): void {
  void router.push({
    name: 'projects',
    ...(isArchivedView.value ? {} : { query: { archived: 'true' } }),
  })
}

watch(isArchivedView, (archived) => {
  clearTimeout(searchTimer)
  searchTimer = undefined
  const initial = createInitialFilters()
  initial.archived = archived
  Object.assign(filters, initial)
  appliedFilters.value = initial
  page.value = 0
  void loadProjects()
  if (!archived) void loadProjectSummary()
})

onMounted(() => {
  void Promise.all([loadProjects(), loadUserFilterOptions(), loadProjectSummary()])
})

onUnmounted(() => clearTimeout(searchTimer))
</script>

<template>
  <section class="project-page">
    <header class="page-header">
      <h1>{{ isArchivedView ? '归档项目' : '项目' }}</h1>

      <div class="header-actions">
        <el-button
          v-if="canCreateProject && !isArchivedView"
          type="primary"
          @click="openCreateDialog"
        >
          <MaterialIcon name="add" />
          创建项目
        </el-button>
        <el-button @click="toggleArchivedView">
          <MaterialIcon :name="isArchivedView ? 'arrow_back' : 'archive'" />
          {{ isArchivedView ? '当前项目' : '归档项目' }}
        </el-button>
      </div>
    </header>

    <template v-if="!isAdmin && !isArchivedView">
      <div
        v-if="endingSoonProjects.length > 0"
        class="deadline-alert"
        data-testid="deadline-alert"
        role="alert"
      >
        <MaterialIcon name="warning" :size="24" />
        <strong>{{ endingSoonTitle }}</strong>
      </div>

      <section
        class="project-summary-grid"
        aria-label="参与项目汇总"
      >
        <button
          v-for="card in projectSummaryCards"
          :key="card.key"
          class="project-summary-card"
          :class="{ active: isSummaryCardActive(card.key) }"
          :data-testid="`project-summary-${card.key}`"
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
          <el-form-item label="项目名称">
          <el-input
            v-model="filters.q"
            clearable
            placeholder="按名称搜索"
            @clear="handleSearch"
            @input="scheduleSearch"
          />
          </el-form-item>

          <el-form-item label="用户">
          <el-select
            v-model="filters.userId"
            class="user-filter-select"
            clearable
            filterable
            :loading="userOptionsLoading"
            placeholder="选择用户名"
            @change="handleSearch"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="user.username"
              :value="user.id"
            >
              <span class="username-option" :title="user.username">
                {{ user.username }}
              </span>
            </el-option>
          </el-select>
          </el-form-item>

          <el-form-item label="状态">
          <el-select
            v-model="filters.status"
            class="status-select"
            placeholder="全部状态"
            @change="handleSearch"
          >
            <el-option label="全部状态" value="" />
            <el-option label="未开始" value="NOT_STARTED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
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
    </section>

    <section
      ref="projectListRef"
      class="content-panel"
      data-testid="project-content"
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
          @click="loadProjects"
        >
          重新加载
        </el-button>
      </div>

      <template v-else>
        <el-table
          :data="projects"
          row-key="id"
        >
          <el-table-column
            label="项目名称"
            min-width="180"
          >
            <template #default="{ row }">
              <ProjectLink :project-id="(row as Project).id">
                {{ (row as Project).name }}
              </ProjectLink>
            </template>
          </el-table-column>

          <el-table-column
            label="我的角色"
            width="100"
          >
            <template #default="{ row }">
              <el-tag
                :type="projectRole(row as Project) === 'Owner' ? 'success' : 'info'"
                effect="plain"
              >
                {{ projectRole(row as Project) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            label="Owner"
            min-width="120"
          >
            <template #default="{ row }">
              <UserLink :user-id="(row as Project).owner.id">
                {{ row.owner.displayName }}
              </UserLink>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag
                :type="projectStatusTagType(row as Project)"
                effect="plain"
              >
                {{ projectStatusLabel(row as Project) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="优先级" width="90">
            <template #default="{ row }">
              <el-tag
                :type="priorityTagTypes[row.priority as Priority]"
                effect="plain"
              >
                {{ priorityLabels[row.priority as Priority] }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            label="成员"
            prop="memberCount"
            width="80"
          />

          <el-table-column label="任务" width="110">
            <template #default="{ row }">
              {{ row.taskStats.completed }}/{{ row.taskStats.total }}
            </template>
          </el-table-column>

          <el-table-column
            label="截止日期"
            width="120"
          >
            <template #default="{ row }">
              {{ (row as Project).endDate ?? '未设置' }}
            </template>
          </el-table-column>

          <template #empty>
            <el-empty
              :description="
                hasActiveFilters
                  ? '没有符合条件的项目'
                  : '当前没有可见项目'
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
          class="project-pagination"
          :current-page="page + 1"
          layout="total, prev, pager, next"
          :page-size="PAGE_SIZE"
          :total="totalElements"
          @current-change="handlePageChange"
        />
      </template>
    </section>

    <ProjectFormDialog
      v-model:visible="createDialogVisible"
      mode="create"
      :owner-options="ownerOptions"
      :require-owner-id="isAdmin"
      :submitting="formSubmitting"
      @submit-create="handleCreateProject"
    />
  </section>
</template>

<style scoped>
.project-page {
  display: grid;
  min-width: 0;
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

.project-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.project-summary-card {
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

.project-summary-card:hover,
.project-summary-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 14px rgb(37 99 235 / 12%);
}

.project-summary-card.active {
  background: var(--el-color-primary-light-9);
}

.project-summary-card .summary-icon {
  display: inline-flex;
  padding: 10px;
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.project-summary-card strong,
.project-summary-card small {
  display: block;
}

.project-summary-card strong {
  font-size: 24px;
  line-height: 1.1;
}

.project-summary-card small {
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

.content-panel {
  min-width: 0;
  min-height: 320px;
  padding: 20px;
}

.status-select {
  width: 100%;
}

.username-option {
  display: block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-filter-select :deep(.el-select__selected-item) {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.feedback-state {
  display: grid;
  min-height: 240px;
  align-content: center;
  gap: 16px;
  justify-items: center;
}

.project-pagination {
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
  }

}
</style>
