<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
  watch,
  type Component,
} from 'vue'
import {
  CircleCheck,
  Document,
  Folder,
  List,
  User,
  Warning,
} from '@element-plus/icons-vue'
import {
  ElAlert,
  ElButton,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElOption,
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
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import { getApiErrorMessage } from '@/shared/api/errors'
import { fetchAllPages } from '@/shared/api/pagination'
import type { TaskStatus } from '@/shared/api/types'
import { useAuthStore } from '@/stores/auth'
import { getProjects } from '@/views/projects/api'
import type { Project } from '@/views/projects/types'

import { getOverview } from './api'
import type { Overview } from './types'

type PageState =
  | 'initialLoading'
  | 'success'
  | 'error'

type TagType =
  | 'primary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'

const taskStatusLabels: Record<TaskStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  BLOCKED: '已阻塞',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const taskStatusTagTypes: Record<TaskStatus, TagType> = {
  NOT_STARTED: 'info',
  IN_PROGRESS: 'primary',
  BLOCKED: 'danger',
  COMPLETED: 'success',
  CANCELLED: 'info',
}

const authStore = useAuthStore()

const overview = ref<Overview | null>(null)
const projectOptions = ref<Project[]>([])
const projectOptionsError = ref('')
const selectedProjectId = ref('')
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')

const pageState = computed<PageState>(() => {
  if (loading.value && !loaded.value) return 'initialLoading'
  if (errorMessage.value || !overview.value) return 'error'
  return 'success'
})

type StatTone =
  | 'primary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'

type StatCard = {
  label: string
  value: number
  icon: Component
  tone: StatTone
}

const statCards = computed((): StatCard[] => {
  if (!overview.value) return []

  const { counts } = overview.value

  return [
    {
      label: '项目',
      value: counts.projects,
      icon: Folder,
      tone: 'primary',
    },
    {
      label: '任务',
      value: counts.tasks,
      icon: List,
      tone: 'info',
    },
    {
      label: '已完成任务',
      value: counts.completedTasks,
      icon: CircleCheck,
      tone: 'success',
    },
    {
      label: '逾期任务',
      value: counts.overdueTasks,
      icon: Warning,
      tone: 'danger',
    },
    {
      label: '总结',
      value: counts.summaries,
      icon: Document,
      tone: 'warning',
    },
    {
      label: '成员',
      value: counts.members,
      icon: User,
      tone: 'primary',
    },
  ]
})

async function loadProjectOptions(): Promise<void> {
  projectOptionsError.value = ''

  try {
    projectOptions.value = [...await fetchAllPages(getProjects, {
      archived: false,
      sort: 'name,asc',
    })]
  } catch (error) {
    projectOptions.value = []
    projectOptionsError.value = getApiErrorMessage(
      error,
      '项目筛选项加载失败',
    )
  }
}

async function loadOverview(): Promise<void> {
  loading.value = true
  errorMessage.value = ''

  try {
    overview.value = await getOverview({
      projectId: selectedProjectId.value || undefined,
    })
  } catch (error) {
    overview.value = null
    errorMessage.value = getApiErrorMessage(
      error,
      '工作台数据加载失败，请稍后重试',
    )
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function handleResetFilter(): Promise<void> {
  selectedProjectId.value = ''
  await loadOverview()
}

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

watch(selectedProjectId, () => {
  void loadOverview()
})

onMounted(() => {
  void loadProjectOptions()
  void loadOverview()
})
</script>

<template>
  <section class="overview-page">
    <header class="page-header">
      <div>
        <h1>工作台</h1>
        <p>
          欢迎回来，{{ authStore.currentUser?.displayName }}。
          查看可见范围内的项目统计与最近活动。
        </p>
      </div>

      <el-button
        :loading="loading"
        @click="loadOverview"
      >
        刷新
      </el-button>
    </header>

    <section class="filter-panel">
      <el-alert
        v-if="projectOptionsError"
        :closable="false"
        :title="projectOptionsError"
        class="project-options-alert"
        show-icon
        type="warning"
      />

      <el-form :inline="true">
        <el-form-item label="项目筛选">
          <el-select
            v-model="selectedProjectId"
            class="project-select"
            clearable
            placeholder="全部可见项目"
          >
            <el-option
              v-for="project in projectOptions"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button @click="handleResetFilter">
            清除筛选
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <section
      class="content-panel"
      data-testid="overview-content"
      :data-state="pageState"
    >
      <el-skeleton
        v-if="pageState === 'initialLoading'"
        animated
        :rows="8"
      />

      <div
        v-else-if="pageState === 'error'"
        class="feedback-state"
      >
        <el-alert
          :closable="false"
          :title="errorMessage || '工作台数据加载失败，请稍后重试'"
          type="error"
          show-icon
        />
        <el-button
          type="primary"
          @click="loadOverview"
        >
          重新加载
        </el-button>
      </div>

      <template v-else-if="overview">
        <div class="stat-grid">
          <article
            v-for="card in statCards"
            :key="card.label"
            class="stat-card"
            :class="`stat-card--${card.tone}`"
          >
            <p class="stat-label">
              {{ card.label }}
            </p>
            <div class="stat-card-body">
              <strong class="stat-value">{{ card.value }}</strong>
              <div class="stat-icon">
                <el-icon :size="20">
                  <component :is="card.icon" />
                </el-icon>
              </div>
            </div>
          </article>
        </div>

        <section class="section-block">
          <h2>任务状态分布</h2>

          <el-table
            :data="[...overview.tasksByStatus]"
            row-key="status"
          >
            <el-table-column label="状态" width="140">
              <template #default="{ row }">
                <el-tag
                  :type="taskStatusTagTypes[row.status as TaskStatus]"
                  effect="plain"
                >
                  {{ taskStatusLabels[row.status as TaskStatus] }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column
              label="任务数"
              prop="count"
              width="120"
            />
          </el-table>
        </section>

        <section class="section-block">
          <h2>最近活动</h2>

          <el-table
            v-if="overview.recentActivities.length > 0"
            :data="[...overview.recentActivities]"
            row-key="resourceId"
          >
            <el-table-column
              label="摘要"
              min-width="280"
              prop="summary"
            />

            <el-table-column label="类型" prop="type" width="180" />

            <el-table-column label="时间" min-width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.occurredAt) }}
              </template>
            </el-table-column>
          </el-table>

          <el-empty
            v-else
            description="当前没有最近活动"
          />
        </section>
      </template>
    </section>
  </section>
</template>

<style scoped>
.overview-page {
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

.filter-panel,
.content-panel {
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
}

.filter-panel {
  padding: 16px 16px 0;
}

.project-options-alert {
  margin-bottom: 12px;
}

.content-panel {
  min-height: 320px;
  padding: 20px;
}

.project-select {
  width: 240px;
}

.stat-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  margin-bottom: 24px;
}

.stat-card {
  padding: 16px;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface-muted, #f8fafc);
}

.stat-card-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.stat-icon {
  display: grid;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 8px;
  place-items: center;
}

.stat-card--primary .stat-icon {
  background: var(--fs-color-primary-soft, #eff6ff);
  color: var(--fs-color-primary, #2563eb);
}

.stat-card--success .stat-icon {
  background: var(--fs-color-success-soft, #ecfdf3);
  color: var(--fs-color-success, #16a34a);
}

.stat-card--warning .stat-icon {
  background: var(--fs-color-warning-soft, #fffbeb);
  color: var(--fs-color-warning, #d97706);
}

.stat-card--danger .stat-icon {
  background: var(--fs-color-danger-soft, #fef2f2);
  color: var(--fs-color-danger, #dc2626);
}

.stat-card--info .stat-icon {
  background: var(--fs-color-info-soft, #f1f5f9);
  color: var(--fs-color-info, #475569);
}

.stat-label {
  margin: 0 0 8px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
  line-height: 1.4;
}

.stat-value {
  color: var(--fs-color-text, #1f2937);
  font-size: 28px;
  line-height: 1;
}

.section-block {
  display: grid;
  gap: 12px;
  margin-bottom: 24px;
}

.section-block:last-child {
  margin-bottom: 0;
}

.section-block h2 {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
  font-size: 18px;
}

.feedback-state {
  display: grid;
  min-height: 240px;
  align-content: center;
  gap: 16px;
  justify-items: center;
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
