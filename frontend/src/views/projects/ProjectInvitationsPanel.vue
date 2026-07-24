<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElMessage,
  ElMessageBox,
  ElOption,
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
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import { getApiErrorMessage } from '@/shared/api/errors'
import { formatDateTime } from '@/shared/format'
import MaterialIcon from '@/components/MaterialIcon.vue'
import UserLink from '@/components/UserLink.vue'
import type { InvitationStatus } from '@/shared/api/types'

import {
  cancelProjectInvitation,
  createProjectInvitations,
  getInvitationCandidates,
  getProjectInvitations,
} from './api'
import type {
  InvitationCandidate,
  Project,
  ProjectInvitation,
} from './types'

const props = withDefaults(defineProps<{
  project: Project
  canCreateInvitations: boolean
  canCancelInvitations: boolean
  showCreateAction?: boolean
}>(), {
  showCreateAction: true,
})

const invitationStatusLabels: Record<InvitationStatus, string> = {
  PENDING: '待处理',
  ACCEPTED: '已接受',
  REJECTED: '已拒绝',
  CANCELLED: '已取消',
}

const invitations = ref<ProjectInvitation[]>([])
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')

const createDialogVisible = ref(false)
const createSubmitting = ref(false)
const candidateLoading = ref(false)
const candidateOptions = ref<InvitationCandidate[]>([])
const selectedCandidateIds = ref<string[]>([])

function openCreateDialog(): void {
  candidateOptions.value = []
  selectedCandidateIds.value = []
  createDialogVisible.value = true
}

async function searchCandidates(query: string): Promise<void> {
  const normalized = query.trim()
  if (normalized.length < 2) {
    candidateOptions.value = []
    return
  }

  candidateLoading.value = true
  try {
    candidateOptions.value = [...await getInvitationCandidates(
      props.project.id,
      normalized,
    )]
  } catch (error) {
    ElMessage.error(getApiErrorMessage(error, '候选用户加载失败，请稍后重试'))
  } finally {
    candidateLoading.value = false
  }
}

async function loadInvitations(): Promise<void> {
  loading.value = true
  errorMessage.value = ''

  try {
    invitations.value = [...await getProjectInvitations(props.project.id)]
  } catch (error) {
    errorMessage.value = getApiErrorMessage(
      error,
      '邀请列表加载失败，请稍后重试',
    )
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function handleCreateInvitations(): Promise<void> {
  const userIds = selectedCandidateIds.value

  if (userIds.length === 0) {
    ElMessage.warning('请选择至少一个用户')
    return
  }

  createSubmitting.value = true

  try {
    await createProjectInvitations(props.project.id, { userIds })
    createDialogVisible.value = false
    selectedCandidateIds.value = []
    ElMessage.success('邀请已发送')
    await loadInvitations()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '创建邀请失败，请稍后重试',
    ))
  } finally {
    createSubmitting.value = false
  }
}

async function handleCancelInvitation(
  invitation: ProjectInvitation,
): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定要取消对 ${invitation.invitee.displayName} 的邀请吗？`,
      '取消邀请',
      {
        confirmButtonText: '取消邀请',
        cancelButtonText: '返回',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await cancelProjectInvitation(props.project.id, invitation.id)
    ElMessage.success('邀请已取消')
    await loadInvitations()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '取消邀请失败，请稍后重试',
    ))
  }
}

onMounted(() => {
  void loadInvitations()
})

defineExpose({ reload: loadInvitations, openCreateDialog })
</script>

<template>
  <section class="invitations-panel">
    <header class="panel-header">
      <div>
        <h3>项目邀请</h3>
        <p>仅 owner 可发起邀请，owner 或 ADMIN 可取消待处理邀请</p>
      </div>

      <div class="panel-actions">
        <el-button
          v-if="canCreateInvitations && showCreateAction"
          type="primary"
          @click="openCreateDialog"
        >
          <MaterialIcon name="person_add" />
          发起邀请
        </el-button>
      </div>
    </header>

    <div
      v-if="loading && !loaded"
      aria-label="加载中"
      class="initial-loading-space"
    />

    <div
      v-else-if="errorMessage"
      class="feedback-state"
    >
      <el-alert
        :closable="false"
        :title="errorMessage"
        type="error"
        show-icon
      />
      <el-button @click="loadInvitations">
        重新加载
      </el-button>
    </div>

    <el-table
      v-else
      :data="invitations"
      row-key="id"
    >
      <el-table-column label="被邀请人" min-width="140">
        <template #default="{ row }">
          <UserLink :user-id="row.invitee.id">{{ row.invitee.displayName }}</UserLink>
        </template>
      </el-table-column>

      <el-table-column label="邀请人" min-width="140">
        <template #default="{ row }">
          <UserLink :user-id="row.invitedBy.id">{{ row.invitedBy.displayName }}</UserLink>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag
            :type="row.status === 'PENDING' ? 'warning' : 'info'"
            effect="plain"
          >
            {{ invitationStatusLabels[row.status as InvitationStatus] }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="创建时间" min-width="150">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>

      <el-table-column label="处理时间" min-width="150">
        <template #default="{ row }">
          {{ formatDateTime(row.respondedAt) }}
        </template>
      </el-table-column>

      <el-table-column
        v-if="canCancelInvitations"
        fixed="right"
        label="操作"
        width="100"
      >
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'PENDING'"
            link
            type="danger"
            @click="handleCancelInvitation(row as ProjectInvitation)"
          >
            <MaterialIcon name="close" :size="18" />
            取消
          </el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="当前没有邀请记录" />
      </template>
    </el-table>

    <el-dialog
      v-model="createDialogVisible"
      title="发起邀请"
      width="480px"
    >
      <el-form @submit.prevent="handleCreateInvitations">
        <el-form-item label="邀请用户">
          <el-select
            v-model="selectedCandidateIds"
            filterable
            :loading="candidateLoading"
            multiple
            placeholder="输入展示名或用户名搜索"
            remote
            :remote-method="searchCandidates"
          >
            <el-option
              v-for="candidate in candidateOptions"
              :key="candidate.id"
              :label="`${candidate.displayName}（${candidate.username}）`"
              :value="candidate.id"
            />
          </el-select>
          <p class="dialog-note">请输入至少 2 个字符搜索用户</p>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="createDialogVisible = false">
          取消
        </el-button>
        <el-button
          :loading="createSubmitting"
          type="primary"
          @click="handleCreateInvitations"
        >
          发送邀请
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.invitations-panel {
  display: grid;
  gap: 16px;
}

.invitations-panel :deep(.el-select) {
  width: 100%;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.panel-header h3 {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
  font-size: 18px;
}

.panel-header p {
  margin: 4px 0 0;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 14px;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.feedback-state {
  display: grid;
  gap: 16px;
  justify-items: start;
}

.dialog-note {
  margin: 0 0 16px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 14px;
}
</style>
