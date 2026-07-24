<script setup lang="ts">
import {
  computed,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'
import { useRoute } from 'vue-router'
import {
  ElAlert,
  ElButton,
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

import MaterialIcon from '@/components/MaterialIcon.vue'
import ProjectLink from '@/components/ProjectLink.vue'
import UserLink from '@/components/UserLink.vue'
import { formatDateTime } from '@/shared/format'
import type { FormInstance, FormRules } from 'element-plus'

import { getApiErrorMessage } from '@/shared/api/errors'
import { fetchAllPages, PAGE_SIZE } from '@/shared/api/pagination'
import type { SummaryType } from '@/shared/api/types'
import { useAuthStore } from '@/stores/auth'
import { getProject, getProjects } from '@/views/projects/api'
import type { Project } from '@/views/projects/types'
import { getTasks } from '@/views/tasks/api'
import type { Task } from '@/views/tasks/types'

import {
  createSummary,
  getSummaries,
} from './api'
import type {
  CreateSummaryBody,
  Summary,
  SummaryListFilters,
  SummaryListQuery,
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

const authStore = useAuthStore()
const route = useRoute()

const typeLabels: Record<SummaryType, string> = {
  STAGE: '阶段总结',
  FINAL: '最终总结',
}

const typeTagTypes: Record<SummaryType, TagType> = {
  STAGE: 'warning',
  FINAL: 'success',
}

function getContextProjectId(): string {
  if (props.project) return props.project.id

  return typeof route.query.projectId === 'string'
    ? route.query.projectId
    : ''
}

function createInitialFilters(): SummaryListFilters {
  return {
    projectId: getContextProjectId(),
    type: '',
  }
}

const filters = reactive<SummaryListFilters>(
  createInitialFilters(),
)

const appliedFilters = ref<SummaryListFilters>(
  createInitialFilters(),
)

const summaries = ref<Summary[]>([])
const projectOptions = ref<Project[]>([])
const writableProjectOptions = computed(() => projectOptions.value
  .filter((project) => project.archivedAt === null))
const projectNames = computed(() => new Map(
  projectOptions.value.map((project) => [project.id, project.name]),
))
const createTaskOptions = ref<Task[]>([])
const createTasksLoading = ref(false)
let createTasksRequestId = 0
const page = ref(0)
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')
let summaryRequestId = 0
const routeProjectWritable = ref<boolean | null>(
  getContextProjectId() ? null : true,
)

const canCreate = computed(() => (
  authStore.currentUser?.systemRole === 'USER'
  && (
    !getContextProjectId()
    || routeProjectWritable.value === true
  )
))

const hasActiveFilters = computed(() => {
  const current = appliedFilters.value

  return (
    (!props.embedded && current.projectId !== '')
    || current.type !== ''
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

  return summaries.value.length > 0
    ? 'success'
    : 'empty'
})

function buildQuery(): SummaryListQuery {
  const current = appliedFilters.value

  return {
    projectId: current.projectId || undefined,
    type: current.type || undefined,
    page: page.value,
    size: PAGE_SIZE,
    sort: 'createdAt,desc',
  }
}

async function loadSummaries(): Promise<void> {
  const requestId = ++summaryRequestId
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getSummaries(buildQuery())
    if (requestId !== summaryRequestId) return

    summaries.value = [...result.items]
    page.value = result.page
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error) {
    if (requestId !== summaryRequestId) return
    errorMessage.value = getApiErrorMessage(
      error,
      '总结列表加载失败，请稍后重试',
    )
  } finally {
    if (requestId === summaryRequestId) {
      loading.value = false
      loaded.value = true
    }
  }
}

async function loadRouteProjectContext(): Promise<void> {
  const routeProjectId = getContextProjectId()

  if (!routeProjectId) {
    routeProjectWritable.value = true
    return
  }

  routeProjectWritable.value = null

  if (props.project) {
    routeProjectWritable.value = props.project.archivedAt === null
    return
  }

  try {
    const project = await getProject(routeProjectId)
    routeProjectWritable.value = project.archivedAt === null
  } catch {
    routeProjectWritable.value = false
  }
}

async function handleSearch(): Promise<void> {
  appliedFilters.value = {
    projectId: filters.projectId.trim(),
    type: filters.type,
  }

  page.value = 0
  await loadSummaries()
}

async function handleReset(): Promise<void> {
  const initial = createInitialFilters()

  Object.assign(filters, initial)
  appliedFilters.value = initial
  page.value = 0

  await loadSummaries()
}

async function handlePageChange(displayedPage: number): Promise<void> {
  page.value = displayedPage - 1
  await loadSummaries()
}

// --- Create dialog ---
const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createSubmitting = ref(false)

const defaultCreateForm: {
  projectId: string
  taskId: string
  type: SummaryType
  content: string
} = {
  projectId: '',
  taskId: '',
  type: 'STAGE',
  content: '',
}

const createForm = reactive({ ...defaultCreateForm })

const createRules: FormRules = {
  projectId: [
    { required: true, message: '请选择项目', trigger: 'change' },
  ],
  content: [
    { required: true, message: '请输入总结内容', trigger: 'blur' },
  ],
}

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

async function loadCreateTasks(projectId: string): Promise<void> {
  const requestId = ++createTasksRequestId
  createForm.taskId = ''
  createTaskOptions.value = []
  if (!projectId) return

  createTasksLoading.value = true
  try {
    const tasks = await fetchAllPages(getTasks, {
      projectId,
      sort: 'title,asc',
    })
    if (requestId !== createTasksRequestId) return
    createTaskOptions.value = [...tasks]
  } catch (error) {
    if (requestId !== createTasksRequestId) return
    createTaskOptions.value = []
    ElMessage.error(getApiErrorMessage(error, '任务选项加载失败，请稍后重试'))
  } finally {
    if (requestId === createTasksRequestId) createTasksLoading.value = false
  }
}

async function openCreateDialog(): Promise<void> {
  Object.assign(createForm, {
    ...defaultCreateForm,
    projectId: getContextProjectId(),
  })
  createDialogVisible.value = true
  await loadCreateTasks(createForm.projectId)
}

async function handleCreate(): Promise<void> {
  const form = createFormRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  const content = createForm.content.trim()
  if (!content) {
    ElMessage.warning('请输入总结内容')
    return
  }

  createSubmitting.value = true

  try {
    const body: CreateSummaryBody = {
      projectId: createForm.projectId.trim(),
      taskId: createForm.taskId.trim() || null,
      type: createForm.type,
      content,
    }

    await createSummary(body)
    ElMessage.success('总结创建成功')
    createDialogVisible.value = false
    await loadSummaries()
  } catch (error) {
    ElMessage.error(
      getApiErrorMessage(error, '创建总结失败，请稍后重试'),
    )
  } finally {
    createSubmitting.value = false
  }
}

function getSummaryDetailLocation(summaryId: string) {
  const routeProjectId = getContextProjectId()

  return {
    name: 'summary-detail',
    params: { summaryId },
    ...(routeProjectId
      ? { query: { projectId: routeProjectId } }
      : {}),
  }
}

function truncateContent(content: string, maxLength = 60): string {
  return content.length > maxLength
    ? content.slice(0, maxLength) + '…'
    : content
}

onMounted(() => {
  void Promise.all([
    loadSummaries(),
    loadRouteProjectContext(),
    loadProjectOptions(),
  ])
})

watch(() => props.project?.id, (projectId) => {
  if (!props.embedded || !projectId) return

  const initial = createInitialFilters()
  Object.assign(filters, initial)
  appliedFilters.value = initial
  page.value = 0
  void Promise.all([
    loadSummaries(),
    loadRouteProjectContext(),
    loadProjectOptions(),
  ])
})

function projectName(projectId: string): string {
  return projectNames.value.get(projectId) ?? '项目不可用'
}

defineExpose({
  openCreateDialog,
  createForm,
})
</script>

<template>
  <section
    class="summary-page"
    :class="{ 'summary-page--embedded': embedded }"
  >
    <header class="page-header">
      <h1>{{ embedded ? '项目总结' : '总结' }}</h1>

      <div class="header-actions">
        <el-button
          v-if="canCreate"
          type="primary"
          @click="openCreateDialog"
        >
          <MaterialIcon name="add" />
          创建总结
        </el-button>

      </div>
    </header>

    <section class="filter-panel">
      <el-form
        class="filter-layout"
        label-position="top"
        :model="filters"
        @submit.prevent="handleSearch"
      >
        <div class="filter-fields">
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

          <el-form-item label="类型">
          <el-select
            v-model="filters.type"
            class="enum-select"
            placeholder="全部类型"
            @change="handleSearch"
          >
            <el-option label="全部类型" value="" />
            <el-option label="阶段总结" value="STAGE" />
            <el-option label="最终总结" value="FINAL" />
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
      class="content-panel"
      data-testid="summary-content"
      :data-state="pageState"
    >
      <div
        v-if="pageState === 'initialLoading'"
        aria-label="加载中"
        class="initial-loading-space"
        role="status"
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
          @click="loadSummaries"
        >
          重新加载
        </el-button>
      </div>

      <template v-else>
        <el-table
          :data="summaries"
          row-key="id"
        >
          <el-table-column
            label="内容预览"
            min-width="200"
          >
            <template #default="{ row }">
              <router-link
                class="summary-link"
                :to="getSummaryDetailLocation((row as Summary).id)"
              >
                {{ truncateContent(row.content) }}
              </router-link>
            </template>
          </el-table-column>

          <el-table-column
            v-if="!embedded"
            label="项目"
            min-width="140"
          >
            <template #default="{ row }">
              <ProjectLink
                v-if="projectNames.has((row as Summary).projectId)"
                :project-id="(row as Summary).projectId"
              >
                {{ projectName((row as Summary).projectId) }}
              </ProjectLink>
              <span v-else>项目不可用</span>
            </template>
          </el-table-column>

          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag
                :type="typeTagTypes[(row as Summary).type]"
                effect="plain"
              >
                {{ typeLabels[(row as Summary).type] }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column
            label="创建者"
            min-width="100"
          >
            <template #default="{ row }">
              <UserLink :user-id="(row as Summary).createdBy.id">
                {{ row.createdBy.displayName }}
              </UserLink>
            </template>
          </el-table-column>

          <el-table-column
            label="创建时间"
            width="140"
          >
            <template #default="{ row }">
              <span class="muted">{{ formatDateTime(row.createdAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column
            label="更新时间"
            width="140"
          >
            <template #default="{ row }">
              <span class="muted">{{ formatDateTime(row.updatedAt) }}</span>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty
              :description="
                hasActiveFilters
                  ? '没有符合条件的总结'
                  : embedded
                    ? '当前项目没有总结'
                    : '当前没有可见总结'
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
          class="summary-pagination"
          :current-page="page + 1"
          layout="total, prev, pager, next"
          :page-size="PAGE_SIZE"
          :total="totalElements"
          @current-change="handlePageChange"
        />
      </template>
    </section>

    <!-- Create Summary Dialog -->
    <el-dialog
      v-model="createDialogVisible"
      title="创建总结"
      width="560px"
      @closed="createFormRef?.resetFields()"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
      >
        <el-form-item v-if="!embedded" label="项目" prop="projectId">
          <el-select
            v-model="createForm.projectId"
            filterable
            placeholder="选择项目"
            @change="loadCreateTasks"
          >
            <el-option
              v-for="project in writableProjectOptions"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="关联任务">
          <el-select
            v-model="createForm.taskId"
            clearable
            filterable
            :loading="createTasksLoading"
            placeholder="可选，留空为项目级总结"
          >
            <el-option
              v-for="task in createTaskOptions"
              :key="task.id"
              :label="task.title"
              :value="task.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select
            v-model="createForm.type"
            class="enum-select"
          >
            <el-option label="阶段总结" value="STAGE" />
            <el-option label="最终总结" value="FINAL" />
          </el-select>
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="createForm.content"
            placeholder="请输入总结内容"
            type="textarea"
            :rows="5"
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
.summary-page {
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

.summary-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.summary-link {
  color: var(--fs-color-primary, #2563eb);
  text-decoration: none;
}

.summary-link:hover {
  text-decoration: underline;
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
