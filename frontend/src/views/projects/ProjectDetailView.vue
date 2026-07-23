<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus'
import type {
  FormInstance,
  FormRules,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/tab-pane/style/css'
import 'element-plus/es/components/tabs/style/css'
import 'element-plus/es/components/tag/style/css'

import { fetchAllPages } from '@/shared/api/pagination'
import { getApiErrorMessage } from '@/shared/api/errors'
import { formatDateTime } from '@/shared/format'
import { useAuthStore } from '@/stores/auth'
import MaterialIcon from '@/components/MaterialIcon.vue'
import UserLink from '@/components/UserLink.vue'
import { getUsers } from '@/views/admin/api'
import AiTaskPlanView from '@/views/ai/AiTaskPlanView.vue'
import SummaryListView from '@/views/summaries/SummaryListView.vue'
import TaskListView from '@/views/tasks/TaskListView.vue'
import type {
  Priority,
  ProjectStatus,
} from '@/shared/api/types'

import {
  archiveProject,
  deleteProject,
  getProject,
  getProjectMembers,
  restoreProject,
  transferProjectOwner,
  updateProject,
} from './api'
import ProjectFormDialog from './ProjectFormDialog.vue'
import ProjectInvitationsPanel from './ProjectInvitationsPanel.vue'
import ProjectMembersPanel from './ProjectMembersPanel.vue'
import type { Project, UpdateProjectRequest } from './types'

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

const statusLabels: Record<ProjectStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
}

const priorityLabels: Record<Priority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
}

const statusTagTypes: Record<ProjectStatus, TagType> = {
  NOT_STARTED: 'info',
  IN_PROGRESS: 'primary',
  COMPLETED: 'success',
}

const priorityTagTypes: Record<Priority, TagType> = {
  LOW: 'info',
  MEDIUM: 'warning',
  HIGH: 'danger',
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const project = ref<Project | null>(null)
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')
const activeTab = ref(
  route.query.tab === 'summaries' ? 'summaries' : 'overview',
)

const editDialogVisible = ref(false)
const aiPlanDialogVisible = ref(false)
const formSubmitting = ref(false)
const deleteDialogVisible = ref(false)
const deleteConfirmation = ref('')
const deleteSubmitting = ref(false)

const transferDialogVisible = ref(false)
const transferSubmitting = ref(false)
const transferOptionsLoading = ref(false)
const transferFormRef = ref<FormInstance>()
const transferOwnerId = ref('')
type TransferOwnerOption = {
  readonly id: string
  readonly label: string
  readonly isMember: boolean
}

const transferOwnerOptions = ref<readonly TransferOwnerOption[]>([])

const membersPanelRef = ref<InstanceType<typeof ProjectMembersPanel> | null>(null)
const tasksPanelRef = ref<InstanceType<typeof TaskListView> | null>(null)
const invitationsPanelRef = ref<InstanceType<
  typeof ProjectInvitationsPanel
> | null>(null)

const projectId = computed(() => String(route.params.projectId ?? ''))

const pageState = computed<PageState>(() => {
  if (loading.value && !loaded.value) return 'initialLoading'
  if (errorMessage.value || !project.value) return 'error'
  return 'success'
})

const isArchived = computed(() => Boolean(project.value?.archivedAt))

const isOwner = computed(() => (
  authStore.currentUser?.id === project.value?.owner.id
))

const isAdmin = computed(() => (
  authStore.currentUser?.systemRole === 'ADMIN'
))

const canManage = computed(() => (
  Boolean(project.value)
  && (isOwner.value || isAdmin.value)
))

const canWrite = computed(() => canManage.value && !isArchived.value)

const canAddMembers = computed(() => (
  isAdmin.value && !isArchived.value
))

const canRemoveMembers = computed(() => canWrite.value)

const canCreateInvitations = computed(() => (
  isOwner.value
  && authStore.currentUser?.systemRole === 'USER'
  && !isArchived.value
))

const canCancelInvitations = computed(() => canWrite.value)

const canViewInvitations = computed(() => (
  isOwner.value || isAdmin.value
))

const canUseAiPlan = computed(() => (
  isOwner.value
  && authStore.currentUser?.systemRole === 'USER'
  && !isArchived.value
))

const taskProgress = computed(() => {
  const stats = project.value?.taskStats
  if (!stats || stats.total === 0) return 0
  return Math.min(100, Math.max(0, Math.round((stats.completed / stats.total) * 100)))
})

const remainingTime = computed(() => {
  const endDate = project.value?.endDate
  if (!endDate) return { label: '剩余', value: '未设置' }

  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const deadline = new Date(`${endDate}T00:00:00`)
  const days = Math.round((deadline.getTime() - today.getTime()) / 86_400_000)

  if (days === 0) return { label: '截止', value: '今天' }
  return days >= 0
    ? { label: '剩余', value: `${days} 天` }
    : { label: '已逾期', value: `${Math.abs(days)} 天` }
})

const canConfirmDelete = computed(() => (
  Boolean(project.value)
  && deleteConfirmation.value === project.value?.name
  && !deleteSubmitting.value
))

const transferRules: FormRules<{ ownerId: string }> = {
  ownerId: [
    { required: true, message: '请选择新的 owner', trigger: 'change' },
  ],
}

async function loadProject(): Promise<void> {
  if (!projectId.value) return

  loading.value = true
  errorMessage.value = ''

  try {
    project.value = await getProject(projectId.value)
  } catch (error) {
    project.value = null
    errorMessage.value = getApiErrorMessage(
      error,
      '项目详情加载失败，请稍后重试',
    )
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function reloadPanels(): Promise<void> {
  const reloadTasks = [
    membersPanelRef.value?.reload(),
  ]

  if (canViewInvitations.value) {
    reloadTasks.push(invitationsPanelRef.value?.reload())
  }

  await Promise.all(reloadTasks)
}

function openInvitationDialog(): void {
  invitationsPanelRef.value?.openCreateDialog()
}

async function handleAiPlanImported(): Promise<void> {
  await Promise.all([
    loadProject(),
    tasksPanelRef.value?.reload() ?? Promise.resolve(),
  ])
}

async function handleEditProject(
  payload: UpdateProjectRequest,
): Promise<void> {
  if (!project.value) return

  formSubmitting.value = true

  try {
    project.value = await updateProject(project.value.id, payload)
    editDialogVisible.value = false
    ElMessage.success('项目已更新')
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '项目更新失败，请稍后重试',
    ))
  } finally {
    formSubmitting.value = false
  }
}

async function handleArchiveProject(): Promise<void> {
  if (!project.value) return

  try {
    await ElMessageBox.confirm(
      `确定要归档项目 ${project.value.name} 吗？归档后将禁止内容写入`,
      '归档项目',
      {
        confirmButtonText: '归档',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    project.value = await archiveProject(project.value.id)
    ElMessage.success('项目已归档')
    await reloadPanels()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '项目归档失败，请稍后重试',
    ))
  }
}

async function handleRestoreProject(): Promise<void> {
  if (!project.value) return

  try {
    project.value = await restoreProject(project.value.id)
    ElMessage.success('项目已恢复')
    await reloadPanels()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '项目恢复失败，请稍后重试',
    ))
  }
}

function openDeleteDialog(): void {
  deleteConfirmation.value = ''
  deleteDialogVisible.value = true
}

async function handleDeleteProject(): Promise<void> {
  if (!project.value) return

  if (!canConfirmDelete.value) return

  deleteSubmitting.value = true
  try {
    await deleteProject(project.value.id)
    deleteDialogVisible.value = false
    ElMessage.success('项目已删除')
    await router.replace({ name: 'projects' })
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '项目删除失败，请稍后重试',
    ))
  } finally {
    deleteSubmitting.value = false
  }
}

async function openTransferDialog(): Promise<void> {
  if (!project.value) return

  transferOwnerId.value = ''
  transferOwnerOptions.value = []
  transferDialogVisible.value = true
  transferOptionsLoading.value = true

  try {
    const members = await getProjectMembers(project.value.id)
    const memberIds = new Set(
      members.map((member) => member.user.id),
    )
    const currentOwnerId = project.value.owner.id

    if (isAdmin.value) {
      const users = await fetchAllPages(getUsers, {
        systemRole: 'USER',
        active: true,
        sort: 'username,asc',
      })

      transferOwnerOptions.value = users
        .filter((user) => user.id !== currentOwnerId)
        .map((user) => ({
          id: user.id,
          label: `${user.displayName} (@${user.username})`,
          isMember: memberIds.has(user.id),
        }))
        .sort((left, right) => {
          if (left.isMember !== right.isMember) {
            return left.isMember ? -1 : 1
          }

          return left.label.localeCompare(right.label, 'zh-CN')
        })
    } else {
      transferOwnerOptions.value = members
        .filter((member) => member.user.id !== currentOwnerId)
        .map((member) => ({
          id: member.user.id,
          label: member.user.displayName,
          isMember: true,
        }))
    }
  } catch (error) {
    transferDialogVisible.value = false
    ElMessage.error(getApiErrorMessage(
      error,
      '可选负责人加载失败，请稍后重试',
    ))
  } finally {
    transferOptionsLoading.value = false
  }
}

async function handleTransferOwner(): Promise<void> {
  if (!project.value) return

  const form = transferFormRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  transferSubmitting.value = true

  try {
    project.value = await transferProjectOwner(project.value.id, {
      ownerId: transferOwnerId.value,
    })
    transferDialogVisible.value = false
    transferOwnerId.value = ''
    ElMessage.success('项目负责人已转移')
    await reloadPanels()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '转移 owner 失败，请稍后重试',
    ))
  } finally {
    transferSubmitting.value = false
  }
}

function formatDateRange(current: Project): string {
  if (!current.startDate && !current.endDate) return '未设置'
  return `${current.startDate ?? '未设置'} 至 ${current.endDate ?? '未设置'}`
}

watch(projectId, () => {
  loaded.value = false
  activeTab.value = route.query.tab === 'summaries' ? 'summaries' : 'overview'
  void loadProject()
})

watch(activeTab, (tab) => {
  if (tab === 'overview' && loaded.value) void loadProject()
})

onMounted(() => {
  void loadProject()
})
</script>

<template>
  <section class="project-detail-page">
    <header class="page-header">
      <div>
        <p class="breadcrumb">
          <RouterLink to="/projects">
            项目
          </RouterLink>
          <span>/</span>
          <span>{{ project?.name ?? '项目详情' }}</span>
        </p>
        <h1>{{ project?.name ?? '项目详情' }}</h1>
        <p v-if="project">
          Owner <UserLink :user-id="project.owner.id">{{ project.owner.displayName }}</UserLink> ·
          {{ formatDateRange(project) }}
        </p>
      </div>

      <div
        v-if="project && canManage"
        class="header-actions"
      >
        <el-button
          v-if="canWrite"
          @click="editDialogVisible = true"
        >
          <MaterialIcon name="edit" />
          编辑
        </el-button>
        <el-button
          v-if="canWrite"
          @click="openTransferDialog"
        >
          <MaterialIcon name="switch_account" />
          转移 Owner
        </el-button>
        <el-button
          v-if="canUseAiPlan"
          data-testid="project-ai-plan-entry"
          type="primary"
          @click="aiPlanDialogVisible = true"
        >
          <MaterialIcon name="auto_awesome" />
          AI 任务计划
        </el-button>
        <el-button
          v-if="canWrite"
          type="warning"
          @click="handleArchiveProject"
        >
          <MaterialIcon name="archive" />
          归档
        </el-button>
        <el-button
          v-if="canManage && isArchived"
          type="primary"
          @click="handleRestoreProject"
        >
          <MaterialIcon name="unarchive" />
          恢复
        </el-button>
        <el-button
          v-if="canManage && isArchived"
          type="danger"
          @click="openDeleteDialog"
        >
          <MaterialIcon name="delete_forever" />
          永久删除
        </el-button>
      </div>
    </header>

    <section
      class="content-panel"
      data-testid="project-detail-content"
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
          :title="errorMessage || '项目详情加载失败，请稍后重试'"
          type="error"
          show-icon
        />
        <el-button
          type="primary"
          @click="loadProject"
        >
          重新加载
        </el-button>
      </div>

      <template v-else-if="project">
        <div class="meta-row">
          <el-tag
            :type="statusTagTypes[project.status]"
            effect="plain"
          >
            {{ statusLabels[project.status] }}
          </el-tag>
          <el-tag
            :type="priorityTagTypes[project.priority]"
            effect="plain"
          >
            {{ priorityLabels[project.priority] }}
          </el-tag>
          <el-tag
            v-if="project.archivedAt"
            effect="plain"
            type="info"
          >
            已归档
          </el-tag>
        </div>

        <el-alert
          v-if="isArchived"
          :closable="false"
          show-icon
          title="项目已归档，内容写操作已禁用，仅可恢复或永久删除"
          type="warning"
        />

        <el-tabs v-model="activeTab">
          <el-tab-pane label="概览" name="overview">
            <div class="overview-layout">
              <section class="overview-stats" aria-label="项目统计">
                <div class="overview-stat">
                  <span>成员</span>
                  <strong data-testid="overview-member-count">{{ project.memberCount }}</strong>
                </div>
                <div class="overview-stat">
                  <span>任务</span>
                  <strong data-testid="overview-task-count">{{ project.taskStats.total }}</strong>
                </div>
                <div class="overview-stat">
                  <span>已完成</span>
                  <strong data-testid="overview-completed-count">
                    {{ project.taskStats.completed }}
                  </strong>
                </div>
                <div class="overview-stat">
                  <span>{{ remainingTime.label }}</span>
                  <strong data-testid="overview-remaining-time">{{ remainingTime.value }}</strong>
                </div>
              </section>

              <section class="overview-card progress-card">
                <header>
                  <h3>项目整体进度</h3>
                  <strong>{{ taskProgress }}%</strong>
                </header>
                <div
                  class="progress-track"
                  role="progressbar"
                  aria-label="项目整体进度"
                  aria-valuemin="0"
                  aria-valuemax="100"
                  :aria-valuenow="taskProgress"
                >
                  <span :style="{ width: `${taskProgress}%` }" />
                </div>
                <p>已完成 {{ project.taskStats.completed }} / {{ project.taskStats.total }} 个任务</p>
              </section>

              <div class="overview-details">
                <section class="overview-card">
                  <h3>项目说明</h3>
                  <p class="project-description">{{ project.description ?? '暂无项目说明' }}</p>
                </section>
                <section class="overview-card project-information">
                  <h3>项目信息</h3>
                  <dl>
                    <div>
                      <dt>负责人</dt>
                      <dd><UserLink :user-id="project.owner.id">{{ project.owner.displayName }}</UserLink></dd>
                    </div>
                    <div><dt>项目日期</dt><dd>{{ formatDateRange(project) }}</dd></div>
                    <div><dt>状态</dt><dd>{{ statusLabels[project.status] }}</dd></div>
                    <div><dt>优先级</dt><dd>{{ priorityLabels[project.priority] }}</dd></div>
                    <div><dt>更新时间</dt><dd>{{ formatDateTime(project.updatedAt) }}</dd></div>
                  </dl>
                </section>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="成员" name="members">
            <ProjectMembersPanel
              ref="membersPanelRef"
              :can-add-members="canAddMembers"
              :can-invite-members="canCreateInvitations"
              :can-remove-members="canRemoveMembers"
              :project="project"
              @invite="openInvitationDialog"
            />
            <ProjectInvitationsPanel
              v-if="canViewInvitations"
              ref="invitationsPanelRef"
              class="member-invitations"
              :can-cancel-invitations="canCancelInvitations"
              :can-create-invitations="canCreateInvitations"
              :project="project"
              :show-create-action="false"
            />
          </el-tab-pane>

          <el-tab-pane label="任务" name="tasks">
            <TaskListView
              ref="tasksPanelRef"
              data-testid="project-tasks-entry"
              embedded
              :project="project"
            />
          </el-tab-pane>

          <el-tab-pane label="总结" name="summaries">
            <SummaryListView
              data-testid="project-summaries-entry"
              embedded
              :project="project"
            />
          </el-tab-pane>

        </el-tabs>
      </template>
    </section>

    <el-dialog
      v-model="aiPlanDialogVisible"
      align-center
      destroy-on-close
      :teleported="false"
      title="AI 任务计划"
      width="min(1400px, 94vw)"
    >
      <AiTaskPlanView
        v-if="project"
        auto-generate
        dialog-mode
        :project-id="project.id"
        @close="aiPlanDialogVisible = false"
        @imported="handleAiPlanImported"
      />
    </el-dialog>

    <ProjectFormDialog
      v-model:visible="editDialogVisible"
      mode="edit"
      :project="project"
      :submitting="formSubmitting"
      @submit-edit="handleEditProject"
    />

    <el-dialog
      v-model="transferDialogVisible"
      title="转移 Owner"
      width="480px"
    >
      <p class="dialog-note">
        选择新的 USER 作为项目负责人，原 owner 会保留为普通成员
      </p>

      <el-form
        ref="transferFormRef"
        label-position="top"
        :model="{ ownerId: transferOwnerId }"
        :rules="transferRules"
        @submit.prevent="handleTransferOwner"
      >
        <el-form-item label="新 Owner" prop="ownerId">
          <el-select
            v-model="transferOwnerId"
            class="transfer-owner-select"
            filterable
            :loading="transferOptionsLoading"
            placeholder="选择新的项目负责人"
          >
            <el-option
              v-for="option in transferOwnerOptions"
              :key="option.id"
              :label="option.label"
              :value="option.id"
            >
              <div class="transfer-owner-option">
                <span class="transfer-owner-option-label">
                  {{ option.label }}
                </span>
                <el-tag
                  v-if="option.isMember"
                  effect="plain"
                  size="small"
                  type="success"
                >
                  项目成员
                </el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="transferDialogVisible = false">
          取消
        </el-button>
        <el-button
          :loading="transferSubmitting"
          type="primary"
          @click="handleTransferOwner"
        >
          确认转移
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="deleteDialogVisible"
      title="永久删除项目"
      width="520px"
      @closed="deleteConfirmation = ''"
    >
      <div v-if="project" class="delete-confirmation">
        <p>
          仅已归档项目可以永久删除，当前项目归档于
          {{ formatDateTime(project.archivedAt) }}
        </p>
        <p>删除后，以下数据将无法恢复：</p>
        <ul>
          <li>任务与进度记录</li>
          <li>项目总结</li>
          <li>邀请与成员关系</li>
        </ul>
        <label for="project-delete-confirmation">
          输入项目名称 <strong>{{ project.name }}</strong> 以确认
        </label>
        <el-input
          id="project-delete-confirmation"
          v-model="deleteConfirmation"
          autocomplete="off"
          :disabled="deleteSubmitting"
          :placeholder="project.name"
        />
      </div>

      <template #footer>
        <el-button
          :disabled="deleteSubmitting"
          @click="deleteDialogVisible = false"
        >
          取消
        </el-button>
        <el-button
          :disabled="!canConfirmDelete"
          :loading="deleteSubmitting"
          type="danger"
          @click="handleDeleteProject"
        >
          永久删除
        </el-button>
      </template>
    </el-dialog>

  </section>
</template>

<style scoped>
.project-detail-page {
  display: grid;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.content-panel {
  min-height: 360px;
  padding: 20px;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.overview-layout {
  display: grid;
  gap: 20px;
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.overview-stat {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
}

.overview-stat span {
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 14px;
}

.overview-stat strong {
  color: var(--fs-color-text, #1f2937);
  font-size: 24px;
}

.overview-card {
  display: grid;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
}

.overview-card h3,
.overview-card p,
.overview-card dl {
  margin: 0;
}

.progress-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.progress-card header strong {
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

.progress-card p,
.project-description {
  color: var(--fs-color-text-secondary, #64748b);
  line-height: 1.7;
}

.overview-details {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.8fr);
  gap: 16px;
}

.project-information dl {
  display: grid;
  gap: 12px;
}

.project-information dl div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.project-information dt {
  color: var(--fs-color-text-secondary, #64748b);
}

.project-information dd {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
  text-align: right;
}

.member-invitations {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--fs-color-border, #dbe3ee);
}

.dialog-note {
  margin: 0 0 16px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 14px;
}

.delete-confirmation {
  display: grid;
  gap: 12px;
}

.delete-confirmation p,
.delete-confirmation ul {
  margin: 0;
}

.delete-confirmation label {
  margin-top: 4px;
}

.transfer-owner-select {
  width: 100%;
}

.transfer-owner-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.transfer-owner-option-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
    overflow-x: auto;
  }

  .overview-stats,
  .overview-details {
    grid-template-columns: 1fr;
  }
}
</style>
