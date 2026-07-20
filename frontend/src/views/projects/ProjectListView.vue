<script setup lang="ts">
import {
  computed,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { useRouter } from 'vue-router'
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
  ElSkeleton,
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
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import { getApiErrorMessage } from '@/shared/api/errors'
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
} from './api'
import ProjectFormDialog from './ProjectFormDialog.vue'
import type {
  CreateProjectRequest,
  Project,
  ProjectListFilters,
  ProjectListQuery,
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

const router = useRouter()
const authStore = useAuthStore()

function createInitialFilters(): ProjectListFilters {
  return {
    q: '',
    status: '',
    ownerId: '',
    archived: false,
  }
}

const filters = reactive<ProjectListFilters>(
  createInitialFilters(),
)

const appliedFilters = ref<ProjectListFilters>(
  createInitialFilters(),
)

const projects = ref<Project[]>([])
const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')

const createDialogVisible = ref(false)
const formSubmitting = ref(false)
const ownerOptions = ref<User[]>([])

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
    || current.ownerId !== ''
    || current.archived
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
    ownerId: current.ownerId || undefined,
    archived: current.archived,
    page: page.value,
    size: size.value,
    sort: 'createdAt,desc',
  }
}

async function loadProjects(): Promise<void> {
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getProjects(buildQuery())

    projects.value = [...result.items]
    page.value = result.page
    size.value = result.size
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error) {
    errorMessage.value = getApiErrorMessage(
      error,
      '项目列表加载失败，请稍后重试',
    )
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function handleSearch(): Promise<void> {
  appliedFilters.value = {
    q: filters.q.trim(),
    status: filters.status,
    ownerId: filters.ownerId.trim(),
    archived: filters.archived,
  }

  page.value = 0
  await loadProjects()
}

async function handleReset(): Promise<void> {
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

async function handleSizeChange(
  nextSize: number,
): Promise<void> {
  size.value = nextSize
  page.value = 0
  await loadProjects()
}

function formatDateRange(project: Project): string {
  if (!project.startDate && !project.endDate) {
    return '未设置'
  }

  return `${project.startDate ?? '未设置'} 至 ${project.endDate ?? '未设置'}`
}

async function loadOwnerOptions(): Promise<void> {
  if (!isAdmin.value) return

  const result = await getUsers({
    systemRole: 'USER',
    active: true,
    page: 0,
    size: 100,
    sort: 'username,asc',
  })

  ownerOptions.value = [...result.items]
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

function goToProject(projectId: string): void {
  void router.push({
    name: 'project-detail',
    params: { projectId },
  })
}

onMounted(() => {
  void loadProjects()
})
</script>

<template>
  <section class="project-page">
    <header class="page-header">
      <div>
        <h1>项目</h1>
        <p>
          ADMIN 查看全部项目，USER 只查看自己参与的项目。
        </p>
      </div>

      <div class="header-actions">
        <el-button
          :loading="loading"
          @click="loadProjects"
        >
          刷新
        </el-button>
        <el-button
          v-if="canCreateProject"
          type="primary"
          @click="openCreateDialog"
        >
          创建项目
        </el-button>
      </div>
    </header>

    <section class="filter-panel">
      <el-form
        class="filter-form"
        :inline="true"
        :model="filters"
        @submit.prevent="handleSearch"
      >
        <el-form-item label="项目名称">
          <el-input
            v-model="filters.q"
            clearable
            placeholder="按名称搜索"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="filters.status"
            class="status-select"
            placeholder="全部状态"
          >
            <el-option label="全部状态" value="" />
            <el-option label="未开始" value="NOT_STARTED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>

        <el-form-item label="Owner ID">
          <el-input
            v-model="filters.ownerId"
            clearable
            placeholder="可选"
          />
        </el-form-item>

        <el-form-item label="项目范围">
          <el-select
            v-model="filters.archived"
            class="archived-select"
          >
            <el-option label="当前项目" :value="false" />
            <el-option label="归档项目" :value="true" />
          </el-select>
        </el-form-item>

        <el-form-item class="filter-actions">
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
      data-testid="project-content"
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
          @click="loadProjects"
        >
          重新加载
        </el-button>
      </div>

      <template v-else>
        <el-alert
          v-if="pageState === 'refreshing'"
          class="refresh-alert"
          :closable="false"
          title="正在刷新项目数据"
          type="info"
          show-icon
        />

        <el-table
          :data="projects"
          row-key="id"
        >
          <el-table-column
            label="项目名称"
            min-width="180"
          >
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click="goToProject((row as Project).id)"
              >
                {{ (row as Project).name }}
              </el-button>
            </template>
          </el-table-column>

          <el-table-column
            label="Owner"
            min-width="120"
          >
            <template #default="{ row }">
              {{ row.owner.displayName }}
            </template>
          </el-table-column>

          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag
                :type="statusTagTypes[row.status as ProjectStatus]"
                effect="plain"
              >
                {{ statusLabels[row.status as ProjectStatus] }}
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
            label="项目日期"
            min-width="210"
          >
            <template #default="{ row }">
              {{ formatDateRange(row as Project) }}
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

          <el-table-column label="归档" width="120">
            <template #default="{ row }">
              <el-tag
                v-if="row.archivedAt"
                effect="plain"
                type="info"
              >
                已归档
              </el-tag>

              <span v-else class="muted">否</span>
            </template>
          </el-table-column>

          <el-table-column
            fixed="right"
            label="操作"
            width="100"
          >
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click="goToProject((row as Project).id)"
              >
                查看
              </el-button>
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
          layout="total, sizes, prev, pager, next"
          :page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalElements"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
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

.filter-form {
  display: flex;
  width: 100%;
  flex-wrap: wrap;
  align-items: flex-end;
}

.filter-form :deep(.filter-actions) {
  margin-left: auto;
}

.content-panel {
  min-height: 320px;
  padding: 20px;
}

.status-select,
.archived-select {
  width: 140px;
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
    overflow-x: auto;
  }
}
</style>