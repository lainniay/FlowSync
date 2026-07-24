<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElEmpty,
  ElOption,
  ElSelect,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'

import MaterialIcon from '@/components/MaterialIcon.vue'
import ProjectLink from '@/components/ProjectLink.vue'
import { getApiErrorMessage } from '@/shared/api/errors'
import { fetchAllPages } from '@/shared/api/pagination'
import { useAuthStore } from '@/stores/auth'
import { getProjects } from '@/views/projects/api'
import type { Project } from '@/views/projects/types'

import { getOverview } from './api'
import AdminOverviewDashboard from './AdminOverviewDashboard.vue'
import DashboardPanel from './components/DashboardPanel.vue'
import DashboardStatCard from './components/DashboardStatCard.vue'
import StatusDistribution from './components/StatusDistribution.vue'
import { formatRelativeTime } from './dashboardFormat'
import { projectStatusLabels, taskStatusColors, taskStatusLabels } from './dashboardPalette'
import type { Overview } from './types'

type PageState = 'adminDashboard' | 'initialLoading' | 'success' | 'error'
type StatTone = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const authStore = useAuthStore()
const overview = ref<Overview | null>(null)
const projectOptions = ref<Project[]>([])
const selectedProjectId = ref('')
const loading = ref(true)
const loaded = ref(false)
const errorMessage = ref('')
let overviewRequestId = 0
const isAdmin = computed(() => authStore.currentUser?.systemRole === 'ADMIN')

const pageState = computed<PageState>(() => {
  if (isAdmin.value) return 'adminDashboard'
  if (loading.value && !loaded.value) return 'initialLoading'
  if (errorMessage.value || !overview.value) return 'error'
  return 'success'
})

const completionRate = computed(() => {
  const counts = overview.value?.counts
  return !counts || counts.tasks === 0
    ? 0
    : Math.round(counts.completedTasks / counts.tasks * 100)
})

function localDate(daysFromToday: number): string {
  const date = new Date()
  date.setHours(0, 0, 0, 0)
  date.setDate(date.getDate() + daysFromToday)
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

function taskLocation(filters: Record<string, string> = {}) {
  return {
    name: 'tasks',
    query: {
      ...(selectedProjectId.value ? { projectId: selectedProjectId.value } : {}),
      ...filters,
    },
  }
}

const statCards = computed(() => {
  if (!overview.value) return []
  const { counts } = overview.value
  return [
    {
      label: '参与项目',
      value: String(counts.projects),
      detail: `进行中 ${counts.inProgressProjects}`,
      icon: 'folder',
      tone: 'primary' as StatTone,
      to: { name: 'projects' },
    },
    {
      label: '任务完成率',
      value: `${completionRate.value}%`,
      detail: `${counts.completedTasks} / ${counts.tasks}`,
      icon: 'task_alt',
      tone: 'success' as StatTone,
      to: taskLocation({ status: 'COMPLETED' }),
    },
    {
      label: '逾期任务',
      value: String(counts.overdueTasks),
      detail: '需优先处理',
      icon: 'event_busy',
      tone: 'danger' as StatTone,
      to: taskLocation({ dueBefore: localDate(-1), incomplete: 'true' }),
    },
    {
      label: '阻塞任务',
      value: String(counts.blockedTasks),
      detail: `超 3 天未更新 ${counts.staleBlockedTasks}`,
      icon: 'warning',
      tone: 'warning' as StatTone,
      to: taskLocation({ status: 'BLOCKED' }),
    },
    {
      label: '即将截止',
      value: String(counts.dueSoonTasks),
      detail: '未来 7 天',
      icon: 'schedule',
      tone: 'info' as StatTone,
      to: taskLocation({ dueAfter: localDate(1), dueBefore: localDate(7), incomplete: 'true' }),
    },
  ]
})

const statusItems = computed(() => overview.value?.tasksByStatus.map((item) => ({
  ...item,
  label: taskStatusLabels[item.status],
  percentage: overview.value!.counts.tasks === 0
    ? 0
    : item.count / overview.value!.counts.tasks * 100,
  color: taskStatusColors[item.status],
  to: taskLocation({ status: item.status }),
})) ?? [])

const todoItems = computed(() => {
  if (!overview.value) return []
  const { counts } = overview.value
  return [
    {
      label: '逾期任务', count: counts.myOverdueTasks, icon: 'event_busy', tone: 'danger',
      to: taskLocation({ dueBefore: localDate(-1), incomplete: 'true' }),
    },
    {
      label: '阻塞任务', count: counts.myBlockedTasks, icon: 'warning', tone: 'warning',
      to: taskLocation({ status: 'BLOCKED' }),
    },
    {
      label: '今日截止', count: counts.myTodayDueTasks, icon: 'today', tone: 'primary',
      to: taskLocation({
        dueAfter: localDate(0), dueBefore: localDate(0), incomplete: 'true',
      }),
    },
  ]
})

function projectProgress(tasks: number, completedTasks: number): number {
  return tasks === 0 ? 0 : Math.round(completedTasks / tasks * 100)
}

async function loadOverview(): Promise<void> {
  const requestId = ++overviewRequestId
  loading.value = true
  errorMessage.value = ''

  try {
    const result = selectedProjectId.value
      ? await getOverview({ projectId: selectedProjectId.value })
      : await getOverview()
    if (requestId !== overviewRequestId) return
    overview.value = result
  } catch (error) {
    if (requestId !== overviewRequestId) return
    overview.value = null
    errorMessage.value = getApiErrorMessage(error, '工作台数据加载失败，请稍后重试')
  } finally {
    if (requestId === overviewRequestId) {
      loading.value = false
      loaded.value = true
    }
  }
}

async function loadProjectOptions(): Promise<void> {
  try {
    projectOptions.value = [...await fetchAllPages(getProjects, {
      archived: false,
      sort: 'name,asc',
    })]
  } catch {
    projectOptions.value = []
  }
}

onMounted(() => {
  if (isAdmin.value) {
    loading.value = false
    loaded.value = true
    return
  }
  void Promise.all([loadOverview(), loadProjectOptions()])
})
</script>

<template>
  <section class="overview-page">
    <header class="page-header">
      <h1>工作台</h1>
      <el-select
        v-if="!isAdmin"
        v-model="selectedProjectId"
        class="project-filter"
        filterable
        placeholder="全部项目"
        @change="loadOverview"
      >
        <el-option label="全部项目" value="" />
        <el-option
          v-for="project in projectOptions"
          :key="project.id"
          :label="project.name"
          :value="project.id"
        />
      </el-select>
    </header>

    <section
      class="overview-content"
      data-testid="overview-content"
      :data-state="pageState"
    >
      <AdminOverviewDashboard v-if="pageState === 'adminDashboard'" />

      <div
        v-else-if="pageState === 'initialLoading'"
        aria-label="加载中"
        class="content-panel initial-loading-space"
        role="status"
      />

      <div v-else-if="pageState === 'error'" class="content-panel feedback-state">
        <el-alert
          :closable="false"
          :title="errorMessage || '工作台数据加载失败，请稍后重试'"
          type="error"
          show-icon
        />
        <el-button type="primary" @click="loadOverview">重新加载</el-button>
      </div>

      <template v-else-if="overview">
        <section class="stat-grid" aria-label="工作台指标">
          <DashboardStatCard
            v-for="card in statCards"
            :key="card.label"
            v-bind="card"
          />
        </section>

        <div class="dashboard-row dashboard-row--primary">
          <DashboardPanel class="execution-card" title="任务执行情况">
            <StatusDistribution :segments="statusItems.map((item) => ({
              key: item.status,
              label: item.label,
              count: item.count,
              color: item.color,
              to: item.to,
            }))" :total="overview.counts.tasks" />
            <div class="completion-summary">
              <span>完成进度 {{ overview.counts.completedTasks }} / {{ overview.counts.tasks }}</span>
              <strong>{{ completionRate }}%</strong>
            </div>
          </DashboardPanel>

          <DashboardPanel class="todo-card" title="我的待办">
            <div class="todo-list">
              <router-link
                v-for="item in todoItems"
                :key="item.label"
                :class="`todo-${item.tone}`"
                :to="item.to"
              >
                <span><MaterialIcon :name="item.icon" :size="20" />{{ item.label }}</span>
                <strong>{{ item.count }}</strong>
              </router-link>
            </div>
          </DashboardPanel>
        </div>

        <div class="dashboard-row">
          <DashboardPanel class="health-card" title="项目健康度">
            <div v-if="overview.projectHealth.length > 0" class="health-list">
              <article v-for="project in overview.projectHealth" :key="project.id">
                <header>
                  <ProjectLink :project-id="project.id">{{ project.name }}</ProjectLink>
                  <div>
                    <span v-if="project.isOwner" class="owner-badge">Owner</span>
                    <span>{{ projectStatusLabels[project.status] }}</span>
                  </div>
                </header>
                <div class="health-progress">
                  <span :style="{ width: `${projectProgress(project.tasks, project.completedTasks)}%` }" />
                </div>
                <footer>
                  <span>进度 {{ project.completedTasks }}/{{ project.tasks }}</span>
                  <span>逾期 {{ project.overdueTasks }}</span>
                  <span>阻塞 {{ project.blockedTasks }}</span>
                  <span>截止 {{ project.endDate ?? '未设置' }}</span>
                </footer>
              </article>
            </div>
            <el-empty v-else description="暂无项目数据" />
          </DashboardPanel>

          <DashboardPanel class="activity-card" title="最近活动">
            <div v-if="overview.recentActivities.length > 0" class="activity-timeline">
              <article v-for="activity in overview.recentActivities" :key="`${activity.type}-${activity.resourceId}`">
                <i />
                <div>
                  <p>{{ activity.summary }}</p>
                  <time :datetime="activity.occurredAt">{{ formatRelativeTime(activity.occurredAt) }}</time>
                </div>
              </article>
            </div>
            <el-empty v-else description="当前没有最近活动" />
          </DashboardPanel>
        </div>
      </template>
    </section>
  </section>
</template>

<style scoped>
.overview-page,
.overview-content {
  display: grid;
  min-width: 0;
  gap: 16px;
  grid-template-columns: minmax(0, 1fr);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-header h1 {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
}

.page-header h1 { font-size: 24px; }
.project-filter { width: 240px; }

.content-panel {
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
}

.content-panel { min-height: 320px; padding: 20px; }
.feedback-state { display: grid; align-content: center; gap: 16px; justify-items: center; }

.stat-grid {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.dashboard-row { display: grid; min-width: 0; gap: 16px; grid-template-columns: minmax(0, 1.7fr) minmax(280px, .8fr); }
.execution-card :deep(.status-distribution) { margin-top: 28px; }
.completion-summary { display: flex; justify-content: space-between; gap: 16px; margin-top: 28px; color: #64748b; }
.completion-summary strong { color: #2563eb; font-size: 20px; }

.todo-list { display: grid; gap: 10px; margin-top: 18px; }
.todo-list > a { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px; border-radius: 6px; background: #f8fafc; color: inherit; text-decoration: none; }
.todo-list > a:hover { background: #eff6ff; }
.todo-list span { display: inline-flex; align-items: center; gap: 8px; }
.todo-list strong { font-size: 20px; }
.todo-danger { color: #dc2626; }
.todo-warning { color: #d97706; }
.todo-primary { color: #2563eb; }

.health-list,
.activity-timeline { display: grid; max-height: 360px; overflow-y: auto; margin-top: 18px; }
.health-list article { display: grid; gap: 9px; padding: 14px 0; border-bottom: 1px solid #e2e8f0; }
.health-list article:first-child { padding-top: 0; }
.health-list article:last-child { border-bottom: 0; }
.health-list header { display: flex; justify-content: space-between; gap: 12px; }
.health-list a { color: #2563eb; font-weight: 600; text-decoration: none; }
.health-list header > div { display: flex; align-items: center; gap: 8px; }
.health-list header span { color: #64748b; font-size: 13px; }
.health-list .owner-badge { padding: 2px 7px; border-radius: 999px; background: #eff6ff; color: #2563eb; font-weight: 600; }
.health-progress { height: 8px; overflow: hidden; border-radius: 999px; background: #e2e8f0; }
.health-progress span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #2563eb, #16a34a); }
.health-list footer { display: flex; flex-wrap: wrap; gap: 6px 14px; color: #64748b; font-size: 12px; }

.activity-timeline article { position: relative; display: grid; gap: 10px; padding: 0 0 18px; grid-template-columns: 12px minmax(0, 1fr); }
.activity-timeline article:not(:last-child)::before { position: absolute; top: 10px; bottom: 0; left: 5px; width: 2px; background: #dbe3ee; content: ''; }
.activity-timeline i { z-index: 1; width: 10px; height: 10px; margin-top: 5px; border-radius: 50%; background: #2563eb; }
.activity-timeline p { margin: 0; color: #1f2937; line-height: 1.5; }
.activity-timeline time { color: #94a3b8; font-size: 12px; }

@media (max-width: 1000px) {
  .stat-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .dashboard-row { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 720px) {
  .page-header { align-items: stretch; flex-direction: column; }
  .project-filter { width: 100%; }
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
