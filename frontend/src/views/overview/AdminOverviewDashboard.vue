<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElEmpty,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'

import MaterialIcon from '@/components/MaterialIcon.vue'
import ProjectLink from '@/components/ProjectLink.vue'
import UserLink from '@/components/UserLink.vue'
import { getApiErrorMessage } from '@/shared/api/errors'

import { getAdminOverview } from './api'
import DashboardPanel from './components/DashboardPanel.vue'
import DashboardStatCard from './components/DashboardStatCard.vue'
import StatusDistribution from './components/StatusDistribution.vue'
import { formatRelativeTime } from './dashboardFormat'
import {
  projectStatusColors,
  projectStatusLabels,
  taskStatusColors,
  taskStatusLabels,
} from './dashboardPalette'
import type { AdminFocusProject, AdminOverview } from './types'

const overview = ref<AdminOverview | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const completionRate = computed(() => {
  const counts = overview.value?.counts
  return !counts || counts.tasks === 0
    ? 0
    : Math.round(counts.completedTasks / counts.tasks * 100)
})

const projectPieStyle = computed(() => {
  const items = overview.value?.projectsByStatus ?? []
  const total = items.reduce((sum, item) => sum + item.count, 0)
  if (total === 0) return { background: '#e2e8f0' }
  const counts = new Map(items.map((item) => [item.status, item.count]))
  const notStarted = (counts.get('NOT_STARTED') ?? 0) / total * 100
  const inProgress = notStarted + (counts.get('IN_PROGRESS') ?? 0) / total * 100
  return {
    background: `conic-gradient(${projectStatusColors.NOT_STARTED} 0 ${notStarted}%, ${projectStatusColors.IN_PROGRESS} ${notStarted}% ${inProgress}%, ${projectStatusColors.COMPLETED} ${inProgress}% 100%)`,
  }
})

const taskItems = computed(() => overview.value?.tasksByStatus.map((item) => ({
  ...item,
  label: taskStatusLabels[item.status],
  percentage: overview.value!.counts.tasks === 0
    ? 0
    : item.count / overview.value!.counts.tasks * 100,
  color: taskStatusColors[item.status],
})) ?? [])
const focusProjects = computed(() => [...(overview.value?.focusProjects ?? [])])

function projectProgress(project: AdminFocusProject): number {
  return project.tasks === 0 ? 0 : Math.round(project.completedTasks / project.tasks * 100)
}

function attention(project: AdminFocusProject): string {
  const items = []
  if (project.overdueTasks > 0) items.push(`逾期 ${project.overdueTasks}`)
  if (project.blockedTasks > 0) items.push(`阻塞 ${project.blockedTasks}`)
  return items.join(' · ') || '运行正常'
}

async function loadOverview(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    overview.value = await getAdminOverview()
  } catch (error) {
    overview.value = null
    errorMessage.value = getApiErrorMessage(error, '管理员工作台加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => void loadOverview())
</script>

<template>
  <section class="admin-dashboard" data-testid="admin-dashboard">
    <div v-if="loading" class="dashboard-state initial-loading-space" role="status" />
    <div v-else-if="errorMessage || !overview" class="dashboard-state feedback-state">
      <el-alert :closable="false" :title="errorMessage" type="error" show-icon />
      <el-button type="primary" @click="loadOverview">重新加载</el-button>
    </div>

    <template v-else>
      <section class="admin-stat-grid">
        <DashboardStatCard label="启用用户" :value="overview.counts.activeUsers"
          :detail="`停用 ${overview.counts.inactiveUsers}`" icon="person" tone="success" />
        <DashboardStatCard label="未归档项目" :value="overview.counts.projects"
          :detail="`进行中 ${overview.counts.inProgressProjects}`" icon="folder" tone="primary" />
        <DashboardStatCard label="任务总数" :value="overview.counts.tasks"
          :detail="`已完成 ${completionRate}%`" icon="task_alt" tone="info" />
        <DashboardStatCard label="逾期任务" :value="overview.counts.overdueTasks"
          :detail="`涉及 ${overview.counts.overdueProjects} 个项目`" icon="event_busy" tone="danger" />
      </section>

      <div class="dashboard-grid dashboard-grid--top">
        <DashboardPanel class="operations-panel" title="项目运行情况">
          <div class="operations-summary">
            <div class="project-pie" :style="projectPieStyle">
              <span>{{ overview.counts.projects }}</span>
            </div>
            <div class="project-status-list">
              <div v-for="item in overview.projectsByStatus" :key="item.status">
                <i :style="{ background: projectStatusColors[item.status] }" />
                <span>{{ projectStatusLabels[item.status] }}</span><strong>{{ item.count }}</strong>
              </div>
            </div>
          </div>
          <div class="completion-row">
            <span>任务完成率 {{ overview.counts.completedTasks }} / {{ overview.counts.tasks }}</span>
            <strong>{{ completionRate }}%</strong>
          </div>
          <div class="completion-track"><span :style="{ width: `${completionRate}%` }" /></div>
          <h3>任务状态分布</h3>
          <StatusDistribution :segments="taskItems.map((item) => ({
            key: item.status, label: item.label, count: item.count, color: item.color,
          }))" :total="overview.counts.tasks" />
        </DashboardPanel>

        <DashboardPanel class="user-panel" title="用户概况">
          <dl>
            <div><dt>启用</dt><dd>{{ overview.counts.activeUsers }}</dd></div>
            <div><dt>停用</dt><dd>{{ overview.counts.inactiveUsers }}</dd></div>
            <div><dt>USER</dt><dd>{{ overview.counts.users }}</dd></div>
            <div><dt>ADMIN</dt><dd>{{ overview.counts.admins }}</dd></div>
          </dl>
          <RouterLink :to="{ name: 'admin-users' }" class="view-users-link">
            查看全部用户 <MaterialIcon name="arrow_forward" :size="18" />
          </RouterLink>
        </DashboardPanel>
      </div>

      <div class="dashboard-grid">
        <DashboardPanel class="focus-panel" title="重点关注项目">
          <el-table v-if="focusProjects.length > 0" :data="focusProjects" row-key="id">
            <el-table-column label="项目" min-width="150">
              <template #default="{ row }"><ProjectLink :project-id="row.id">{{ row.name }}</ProjectLink></template>
            </el-table-column>
            <el-table-column label="Owner" min-width="120">
              <template #default="{ row }"><UserLink :user-id="row.ownerId">{{ row.ownerName }}</UserLink></template>
            </el-table-column>
            <el-table-column label="进度" min-width="150">
              <template #default="{ row }">
                <div class="project-progress-cell">
                  <div><span :style="{ width: `${projectProgress(row as AdminFocusProject)}%` }" /></div>
                  <small>{{ projectProgress(row as AdminFocusProject) }}%</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="关注事项" min-width="140">
              <template #default="{ row }">{{ attention(row as AdminFocusProject) }}</template>
            </el-table-column>
            <el-table-column label="截止日期" width="120">
              <template #default="{ row }">{{ row.endDate ?? '未设置' }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无重点关注项目" />
        </DashboardPanel>

        <DashboardPanel class="task-panel" title="任务执行情况">
          <div class="task-count-list">
            <div v-for="item in taskItems" :key="item.status">
              <span><i :style="{ background: item.color }" />{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </div>
          </div>
        </DashboardPanel>
      </div>

      <DashboardPanel class="activity-panel" title="最近活动">
        <div v-if="overview.recentActivities.length > 0" class="admin-activity-timeline">
          <article v-for="activity in overview.recentActivities" :key="`${activity.type}-${activity.resourceId}`">
            <i /><div><p>{{ activity.summary }}</p><time>{{ formatRelativeTime(activity.occurredAt) }}</time></div>
          </article>
        </div>
        <el-empty v-else description="暂无最近活动" />
      </DashboardPanel>
    </template>
  </section>
</template>

<style scoped>
.admin-dashboard { display: grid; min-width: 0; gap: 16px; grid-template-columns: minmax(0, 1fr); }
.dashboard-state { min-width: 0; padding: 20px; border: 1px solid #dbe3ee; border-radius: 8px; background: #fff; }
.operations-panel h3 { margin: 24px 0 0; color: #1f2937; font-size: 14px; }
.feedback-state { display: grid; min-height: 320px; align-content: center; gap: 16px; justify-items: center; }
.admin-stat-grid { display: grid; gap: 12px; grid-template-columns: repeat(4, minmax(0, 1fr)); }
.dashboard-grid { display: grid; min-width: 0; gap: 16px; grid-template-columns: minmax(0, 2fr) minmax(260px, .7fr); }
.operations-summary { display: flex; align-items: center; gap: 28px; margin-top: 22px; }
.project-pie { display: grid; width: 112px; height: 112px; flex-shrink: 0; border-radius: 50%; place-items: center; }
.project-pie::before { width: 72px; height: 72px; border-radius: 50%; background: #fff; content: ''; grid-area: 1 / 1; }
.project-pie span { z-index: 1; color: #1f2937; font-size: 22px; font-weight: 700; grid-area: 1 / 1; }
.project-status-list { display: grid; min-width: 180px; gap: 10px; }
.project-status-list div, .task-count-list div { display: flex; align-items: center; gap: 8px; }
.project-status-list strong, .task-count-list strong { margin-left: auto; }
.project-status-list i, .task-count-list i { width: 9px; height: 9px; border-radius: 50%; }
.completion-row { display: flex; justify-content: space-between; gap: 12px; margin-top: 24px; color: #64748b; }
.completion-row strong { color: #2563eb; }
.completion-track { display: flex; height: 10px; overflow: hidden; margin: 10px 0 24px; border-radius: 999px; background: #e2e8f0; }
.completion-track span { background: linear-gradient(90deg, #2563eb, #16a34a); }
.user-panel { display: flex; flex-direction: column; }
.user-panel dl { display: grid; gap: 12px; margin: 20px 0; }
.user-panel dl div { display: flex; justify-content: space-between; }.user-panel dd { margin: 0; font-weight: 700; }
.view-users-link { display: inline-flex; align-items: center; gap: 4px; margin-top: auto; color: #2563eb; text-decoration: none; }
.focus-panel { overflow-x: auto; }.project-progress-cell { display: flex; align-items: center; gap: 8px; }
.project-progress-cell > div { width: 90px; height: 7px; overflow: hidden; border-radius: 999px; background: #e2e8f0; }
.project-progress-cell > div span { display: block; height: 100%; background: linear-gradient(90deg, #2563eb, #16a34a); }
.task-count-list { display: grid; gap: 14px; margin-top: 20px; }.task-count-list span { display: inline-flex; align-items: center; gap: 8px; }
.activity-panel { min-height: 180px; }.admin-activity-timeline { display: grid; margin-top: 18px; }
.admin-activity-timeline article { position: relative; display: grid; gap: 10px; padding-bottom: 16px; grid-template-columns: 12px 1fr; }
.admin-activity-timeline article:not(:last-child)::before { position: absolute; top: 9px; bottom: 0; left: 5px; width: 2px; background: #dbe3ee; content: ''; }
.admin-activity-timeline article > i { z-index: 1; width: 10px; height: 10px; margin-top: 5px; border-radius: 50%; background: #2563eb; }
.admin-activity-timeline p { margin: 0; color: #1f2937; }.admin-activity-timeline time { color: #94a3b8; font-size: 12px; }
@media (max-width: 1000px) { .admin-stat-grid { grid-template-columns: repeat(2, 1fr); }.dashboard-grid { grid-template-columns: 1fr; } }
</style>
