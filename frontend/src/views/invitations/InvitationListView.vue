<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
} from 'vue'
import {
  ElAlert,
  ElButton,
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

import MaterialIcon from '@/components/MaterialIcon.vue'
import ProjectLink from '@/components/ProjectLink.vue'
import UserLink from '@/components/UserLink.vue'

import { getApiErrorMessage } from '@/shared/api/errors'
import { formatDateTime } from '@/shared/format'
import type { InvitationStatus } from '@/shared/api/types'

import {
  getReceivedInvitations,
  respondToInvitation,
} from './api'
import type { ReceivedInvitation } from './types'

type PageState =
  | 'initialLoading'
  | 'refreshing'
  | 'success'
  | 'empty'
  | 'error'

const invitationStatusLabels: Record<InvitationStatus, string> = {
  PENDING: '待处理',
  ACCEPTED: '已接受',
  REJECTED: '已拒绝',
  CANCELLED: '已取消',
}

const DEFAULT_STATUS: InvitationStatus = 'PENDING'

const invitations = ref<ReceivedInvitation[]>([])
const statusFilter = ref<InvitationStatus | ''>(DEFAULT_STATUS)
const appliedStatus = ref<InvitationStatus | ''>(DEFAULT_STATUS)
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')
const actionLoadingId = ref<string | null>(null)
let invitationRequestId = 0

const hasActiveFilters = computed(() => appliedStatus.value !== DEFAULT_STATUS)

const pageState = computed<PageState>(() => {
  if (loading.value && !loaded.value) return 'initialLoading'
  if (errorMessage.value) return 'error'
  if (loading.value) return 'refreshing'
  return invitations.value.length > 0 ? 'success' : 'empty'
})

async function loadInvitations(): Promise<void> {
  const requestId = ++invitationRequestId
  loading.value = true
  errorMessage.value = ''

  try {
    const result = await getReceivedInvitations({
      status: appliedStatus.value || undefined,
    })
    if (requestId !== invitationRequestId) return
    invitations.value = [...result]
  } catch (error) {
    if (requestId !== invitationRequestId) return
    errorMessage.value = getApiErrorMessage(
      error,
      '邀请列表加载失败，请稍后重试',
    )
  } finally {
    if (requestId === invitationRequestId) {
      loading.value = false
      loaded.value = true
    }
  }
}

async function handleSearch(): Promise<void> {
  appliedStatus.value = statusFilter.value
  await loadInvitations()
}

async function handleReset(): Promise<void> {
  statusFilter.value = DEFAULT_STATUS
  appliedStatus.value = DEFAULT_STATUS
  await loadInvitations()
}

async function handleRespond(
  invitation: ReceivedInvitation,
  status: 'ACCEPTED' | 'REJECTED',
): Promise<void> {
  const actionLabel = status === 'ACCEPTED' ? '接受' : '拒绝'

  try {
    await ElMessageBox.confirm(
      `确定要${actionLabel}项目“${invitation.project.name}”的邀请吗？`,
      `${actionLabel}邀请`,
      {
        confirmButtonText: actionLabel,
        cancelButtonText: '取消',
        type: status === 'ACCEPTED' ? 'info' : 'warning',
      },
    )
  } catch {
    return
  }

  actionLoadingId.value = invitation.id

  try {
    await respondToInvitation(invitation.id, { status })
    ElMessage.success(`邀请已${actionLabel}`)
    await loadInvitations()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      `邀请${actionLabel}失败，请稍后重试`,
    ))
  } finally {
    actionLoadingId.value = null
  }
}

onMounted(() => {
  void loadInvitations()
})
</script>

<template>
  <section class="invitation-page">
    <header class="page-header">
      <h1>收到的邀请</h1>

    </header>

    <section class="filter-panel">
      <el-form
        class="filter-layout"
        label-position="top"
        @submit.prevent="handleSearch"
      >
        <div class="filter-fields">
          <el-form-item label="邀请状态">
          <el-select
            v-model="statusFilter"
            class="status-select"
            placeholder="全部状态"
            @change="handleSearch"
          >
            <el-option label="全部状态" value="" />
            <el-option label="待处理" value="PENDING" />
            <el-option label="已接受" value="ACCEPTED" />
            <el-option label="已拒绝" value="REJECTED" />
            <el-option label="已取消" value="CANCELLED" />
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
      data-testid="invitation-content"
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
          :title="errorMessage"
          type="error"
          show-icon
        />
        <el-button
          type="primary"
          @click="loadInvitations"
        >
          重新加载
        </el-button>
      </div>

      <template v-else>
        <el-table
          :data="invitations"
          row-key="id"
        >
          <el-table-column label="项目" min-width="160">
            <template #default="{ row }">
              <ProjectLink
                v-if="row.status === 'ACCEPTED'"
                :project-id="row.project.id"
              >
                {{ row.project.name }}
              </ProjectLink>
              <span v-else>{{ row.project.name }}</span>
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

          <el-table-column label="邀请时间" min-width="150">
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>

          <el-table-column label="响应时间" min-width="150">
            <template #default="{ row }">
              {{ formatDateTime(row.respondedAt) }}
            </template>
          </el-table-column>

          <el-table-column
            fixed="right"
            label="操作"
            min-width="180"
          >
            <template #default="{ row }">
              <div
                v-if="row.status === 'PENDING'"
                class="row-actions"
              >
                <el-button
                  link
                  :loading="actionLoadingId === row.id"
                  type="primary"
                  @click="handleRespond(row as ReceivedInvitation, 'ACCEPTED')"
                >
                  <MaterialIcon name="check" :size="18" />
                  接受
                </el-button>
                <el-button
                  link
                  :loading="actionLoadingId === row.id"
                  type="danger"
                  @click="handleRespond(row as ReceivedInvitation, 'REJECTED')"
                >
                  <MaterialIcon name="close" :size="18" />
                  拒绝
                </el-button>
              </div>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty
              :description="
                hasActiveFilters
                  ? '没有符合条件的邀请'
                  : '当前没有可见邀请'
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
      </template>
    </section>
  </section>
</template>

<style scoped>
.invitation-page {
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
  min-height: 320px;
  padding: 20px;
  overflow-x: auto;
}

.status-select {
  width: 100%;
}

.feedback-state {
  display: grid;
  min-height: 240px;
  align-content: center;
  gap: 16px;
  justify-items: center;
}

.row-actions {
  display: flex;
  gap: 4px;
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
