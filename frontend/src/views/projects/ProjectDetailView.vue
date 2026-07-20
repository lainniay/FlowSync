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
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSkeleton,
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
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/tab-pane/style/css'
import 'element-plus/es/components/tabs/style/css'
import 'element-plus/es/components/tag/style/css'

import { getApiErrorMessage } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'
import { getUsers } from '@/views/admin/api'
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
const activeTab = ref('overview')

const editDialogVisible = ref(false)
const formSubmitting = ref(false)

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
  await Promise.all([
    membersPanelRef.value?.reload(),
    invitationsPanelRef.value?.reload(),
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
      `确定要归档项目 ${project.value.name} 吗？归档后将禁止内容写入。`,
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

async function handleDeleteProject(): Promise<void> {
  if (!project.value) return

  try {
    await ElMessageBox.confirm(
      `确定要永久删除项目 ${project.value.name} 吗？此操作不可撤销。`,
      '永久删除项目',
      {
        confirmButtonText: '永久删除',
        cancelButtonText: '取消',
        type: 'error',
      },
    )
  } catch {
    return
  }

  try {
    await deleteProject(project.value.id)
    ElMessage.success('项目已删除')
    await router.replace({ name: 'projects' })
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '项目删除失败，请稍后重试',
    ))
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
      const result = await getUsers({
        systemRole: 'USER',
        active: true,
        page: 0,
        size: 100,
        sort: 'username,asc',
      })

      transferOwnerOptions.value = result.items
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

function formatDateTime(value: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

watch(projectId, () => {
  loaded.value = false
  activeTab.value = 'overview'
  void loadProject()
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
          Owner {{ project.owner.displayName }} ·
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
          编辑
        </el-button>
        <el-button
          v-if="canWrite"
          @click="openTransferDialog"
        >
          转移 Owner
        </el-button>
        <el-button
          v-if="canWrite && !isArchived"
          type="warning"
          @click="handleArchiveProject"
        >
          归档
        </el-button>
        <el-button
          v-if="canManage && isArchived"
          type="primary"
          @click="handleRestoreProject"
        >
          恢复
        </el-button>
        <el-button
          v-if="canManage && isArchived"
          type="danger"
          @click="handleDeleteProject"
        >
          永久删除
        </el-button>
      </div>
    </header>

    <section
      class="content-panel"
      data-testid="project-detail-content"
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
          title="项目已归档，内容写操作已禁用，仅可恢复或永久删除。"
          type="warning"
        />

        <el-tabs v-model="activeTab">
          <el-tab-pane label="概览" name="overview">
            <dl class="overview-grid">
              <div>
                <dt>项目名称</dt>
                <dd>{{ project.name }}</dd>
              </div>
              <div>
                <dt>Owner</dt>
                <dd>{{ project.owner.displayName }}</dd>
              </div>
              <div>
                <dt>项目描述</dt>
                <dd>{{ project.description ?? '—' }}</dd>
              </div>
              <div>
                <dt>成员数量</dt>
                <dd>{{ project.memberCount }}</dd>
              </div>
              <div>
                <dt>任务统计</dt>
                <dd>
                  {{ project.taskStats.completed }}/{{ project.taskStats.total }}
                </dd>
              </div>
              <div>
                <dt>创建时间</dt>
                <dd>{{ formatDateTime(project.createdAt) }}</dd>
              </div>
              <div>
                <dt>更新时间</dt>
                <dd>{{ formatDateTime(project.updatedAt) }}</dd>
              </div>
              <div>
                <dt>归档时间</dt>
                <dd>{{ formatDateTime(project.archivedAt) }}</dd>
              </div>
            </dl>
          </el-tab-pane>

          <el-tab-pane label="成员" name="members">
            <ProjectMembersPanel
              ref="membersPanelRef"
              :can-add-members="canAddMembers"
              :can-remove-members="canRemoveMembers"
              :project="project"
            />
          </el-tab-pane>

          <el-tab-pane label="邀请" name="invitations">
            <ProjectInvitationsPanel
              ref="invitationsPanelRef"
              :can-cancel-invitations="canCancelInvitations"
              :can-create-invitations="canCreateInvitations"
              :project="project"
            />
          </el-tab-pane>

          <el-tab-pane label="任务" name="tasks">
            <p class="placeholder-note">
              项目任务列表由任务模块负责，将在后续接入。
            </p>
          </el-tab-pane>

          <el-tab-pane label="总结" name="summaries">
            <p class="placeholder-note">
              项目总结列表由总结模块负责，将在后续接入。
            </p>
          </el-tab-pane>
        </el-tabs>
      </template>
    </section>

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
        选择新的 USER 作为项目负责人。原 owner 会保留为普通成员。
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

.overview-grid {
  display: grid;
  gap: 16px;
  margin: 0;
}

.overview-grid div {
  display: grid;
  gap: 4px;
}

.overview-grid dt {
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
}

.overview-grid dd {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
  font-size: 14px;
}

.placeholder-note,
.dialog-note {
  margin: 0 0 16px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 14px;
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
  }
}
</style>
