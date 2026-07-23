<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
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
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import { getApiErrorMessage } from '@/shared/api/errors'
import type { InvitationStatus } from '@/shared/api/types'

import {
  cancelProjectInvitation,
  createProjectInvitations,
  getProjectInvitations,
} from './api'
import type { Project, ProjectInvitation } from './types'

const props = defineProps<{
  project: Project
  canCreateInvitations: boolean
  canCancelInvitations: boolean
}>()

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
const userIdsInput = ref('')

function parseUserIds(value: string): string[] {
  return [...new Set(
    value
      .split(/[\s,，]+/)
      .map((item) => item.trim())
      .filter(Boolean),
  )]
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
  const userIds = parseUserIds(userIdsInput.value)

  if (userIds.length === 0) {
    ElMessage.warning('请输入至少一个用户 ID')
    return
  }

  createSubmitting.value = true

  try {
    await createProjectInvitations(props.project.id, { userIds })
    createDialogVisible.value = false
    userIdsInput.value = ''
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

function formatDateTime(value: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(() => {
  void loadInvitations()
})

defineExpose({ reload: loadInvitations })
</script>

<template>
  <section class="invitations-panel">
    <header class="panel-header">
      <div>
        <h3>项目邀请</h3>
        <p>owner 可发起邀请，owner 或 ADMIN 可取消待处理邀请。</p>
      </div>

      <div class="panel-actions">
        <el-button
          :loading="loading"
          @click="loadInvitations"
        >
          刷新
        </el-button>
        <el-button
          v-if="canCreateInvitations"
          type="primary"
          @click="createDialogVisible = true"
        >
          发起邀请
        </el-button>
      </div>
    </header>

    <el-skeleton
      v-if="loading && !loaded"
      animated
      :rows="4"
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
          {{ row.invitee.displayName }}
        </template>
      </el-table-column>

      <el-table-column label="邀请人" min-width="140">
        <template #default="{ row }">
          {{ row.invitedBy.displayName }}
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
      <p class="dialog-note">
        仅项目 owner 可以发起邀请。输入 USER 的用户 ID，多个 ID 可用逗号或换行分隔。
      </p>

      <el-form @submit.prevent="handleCreateInvitations">
        <el-form-item label="用户 ID 列表">
          <el-input
            v-model="userIdsInput"
            placeholder="例如：3, 4"
            :rows="4"
            type="textarea"
          />
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
