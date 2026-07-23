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
import type { SummaryType } from '@/shared/api/types'
import { useAuthStore } from '@/stores/auth'
import { getProject } from '@/views/projects/api'

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

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const typeLabels: Record<SummaryType, string> = {
  STAGE: '阶段总结',
  FINAL: '最终总结',
}

const typeTagTypes: Record<SummaryType, TagType> = {
  STAGE: 'warning',
  FINAL: 'success',
}

function getRouteProjectId(): string {
  return typeof route.query.projectId === 'string'
    ? route.query.projectId
    : ''
}

function createInitialFilters(): SummaryListFilters {
  return {
    projectId: getRouteProjectId(),
    taskId: '',
    type: '',
    createdBy: '',
  }
}

const filters = reactive<SummaryListFilters>(
  createInitialFilters(),
)

const appliedFilters = ref<SummaryListFilters>(
  createInitialFilters(),
)

const summaries = ref<Summary[]>([])
const page = ref(0)
const size = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')
const routeProjectWritable = ref<boolean | null>(
  getRouteProjectId() ? null : true,
)

const canCreate = computed(() => (
  authStore.currentUser?.systemRole === 'USER'
  && (
    !getRouteProjectId()
    || routeProjectWritable.value === true
  )
))

const hasActiveFilters = computed(() => {
  const current = appliedFilters.value

  return (
    current.projectId !== ''
    || current.taskId !== ''
    || current.type !== ''
    || current.createdBy !== ''
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
    taskId: current.taskId || undefined,
    type: current.type || undefined,
    createdBy: current.createdBy || undefined,
    page: page.value,
    size: size.value,
    sort: 'createdAt,desc',
  }
}

async function loadSummaries(): Promise<void> {
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getSummaries(buildQuery())

    summaries.value = [...result.items]
    page.value = result.page
    size.value = result.size
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error) {
    errorMessage.value = getApiErrorMessage(
      error,
      '总结列表加载失败，请稍后重试',
    )
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function loadRouteProjectContext(): Promise<void> {
  const routeProjectId = getRouteProjectId()

  if (!routeProjectId) {
    routeProjectWritable.value = true
    return
  }

  routeProjectWritable.value = null

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
    taskId: filters.taskId.trim(),
    type: filters.type,
    createdBy: filters.createdBy.trim(),
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

async function handleSizeChange(nextSize: number): Promise<void> {
  size.value = nextSize
  page.value = 0
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
    { required: true, message: '请输入项目 ID', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '请输入总结内容', trigger: 'blur' },
  ],
}

function openCreateDialog(): void {
  Object.assign(createForm, {
    ...defaultCreateForm,
    projectId: getRouteProjectId(),
  })
  createDialogVisible.value = true
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

function goToSummary(summaryId: string): void {
  const routeProjectId = getRouteProjectId()

  void router.push({
    name: 'summary-detail',
    params: { summaryId },
    ...(routeProjectId
      ? { query: { projectId: routeProjectId } }
      : {}),
  })
}

function goToProject(): void {
  const routeProjectId = getRouteProjectId()
  if (!routeProjectId) return

  void router.push({
    name: 'project-detail',
    params: { projectId: routeProjectId },
  })
}

function truncateContent(content: string, maxLength = 60): string {
  return content.length > maxLength
    ? content.slice(0, maxLength) + '...'
    : content
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
  void Promise.all([
    loadSummaries(),
    loadRouteProjectContext(),
  ])
})

defineExpose({
  openCreateDialog,
  createForm,
})
</script>

<template>
  <section class="summary-page">
    <header class="page-header">
      <div>
        <h1>总结</h1>
        <p>
          查看可见项目内的阶段总结和最终总结。
        </p>
      </div>

      <div class="header-actions">
        <el-button
          v-if="getRouteProjectId()"
          data-testid="back-to-project"
          @click="goToProject"
        >
          返回项目
        </el-button>

        <el-button
          v-if="canCreate"
          type="primary"
          @click="openCreateDialog"
        >
          创建总结
        </el-button>

        <el-button
          :loading="loading"
          @click="loadSummaries"
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
        <el-form-item label="项目 ID">
          <el-input
            v-model="filters.projectId"
            clearable
            placeholder="可选"
          />
        </el-form-item>

        <el-form-item label="任务 ID">
          <el-input
            v-model="filters.taskId"
            clearable
            placeholder="可选，输入 none 查询项目级总结"
          />
        </el-form-item>

        <el-form-item label="类型">
          <el-select
            v-model="filters.type"
            class="enum-select"
            placeholder="全部类型"
          >
            <el-option label="全部类型" value="" />
            <el-option label="阶段总结" value="STAGE" />
            <el-option label="最终总结" value="FINAL" />
          </el-select>
        </el-form-item>

        <el-form-item label="创建者 ID">
          <el-input
            v-model="filters.createdBy"
            clearable
            placeholder="可选"
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
      data-testid="summary-content"
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
          @click="loadSummaries"
        >
          重新加载
        </el-button>
      </div>

      <template v-else>
        <el-alert
          v-if="pageState === 'refreshing'"
          class="refresh-alert"
          :closable="false"
          title="正在刷新总结数据"
          type="info"
          show-icon
        />

        <el-table
          :data="summaries"
          row-key="id"
        >
          <el-table-column
            label="内容预览"
            min-width="220"
          >
            <template #default="{ row }">
              {{ truncateContent(row.content) }}
            </template>
          </el-table-column>

          <el-table-column
            label="项目"
            prop="projectId"
            width="100"
          />

          <el-table-column
            label="关联任务"
            width="110"
          >
            <template #default="{ row }">
              {{ row.taskId ?? '项目级' }}
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
              {{ row.createdBy.displayName }}
            </template>
          </el-table-column>

          <el-table-column
            label="创建时间"
            min-width="160"
          >
            <template #default="{ row }">
              <span class="muted">{{ formatDateTime(row.createdAt) }}</span>
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

          <el-table-column
            fixed="right"
            label="操作"
            width="100"
          >
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click="goToSummary((row as Summary).id)"
              >
                查看
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty
              :description="
                hasActiveFilters
                  ? '没有符合条件的总结'
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
          layout="total, sizes, prev, pager, next"
          :page-size="size"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalElements"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
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
        <el-form-item label="项目 ID" prop="projectId">
          <el-input
            v-model="createForm.projectId"
            placeholder="输入项目 ID"
          />
        </el-form-item>

        <el-form-item label="关联任务 ID">
          <el-input
            v-model="createForm.taskId"
            placeholder="可选，留空为项目级总结"
          />
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

.summary-pagination {
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
