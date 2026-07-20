<script setup lang="ts">
import {
  nextTick,
  onMounted,
  onUnmounted,
  ref,
  watch,
} from 'vue'
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
  ElSkeleton,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import type {
  FormInstance,
  FormRules,
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
import 'element-plus/es/components/skeleton/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'

import { getApiErrorMessage } from '@/shared/api/errors'
import { getUsers } from '@/views/admin/api'

import {
  addProjectMembers,
  getProjectMembers,
  removeProjectMember,
} from './api'
import type { Project, ProjectMember } from './types'

const props = defineProps<{
  project: Project
  canAddMembers: boolean
  canRemoveMembers: boolean
}>()

const members = ref<ProjectMember[]>([])
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')

const addDialogVisible = ref(false)
const addSubmitting = ref(false)
const addOptionsLoading = ref(false)
const addFormRef = ref<FormInstance>()
const selectedUserIds = ref<string[]>([])
const addUserOptions = ref<
  readonly { readonly id: string; readonly label: string }[]
>([])
const addMembersSelectWrapperRef = ref<HTMLElement | null>(null)
const maxCollapseTags = ref(999)

const TAG_HORIZONTAL_EXTRA = 38
const TAG_GAP = 6
const COLLAPSE_TAG_WIDTH = 44

let measureCanvas: HTMLCanvasElement | null = null
let resizeObserver: ResizeObserver | null = null

function measureLabelWidth(label: string): number {
  if (!measureCanvas) {
    measureCanvas = document.createElement('canvas')
  }

  const context = measureCanvas.getContext('2d')
  if (!context) {
    return label.length * 7
  }

  context.font = '12px Inter, ui-sans-serif, system-ui, sans-serif'
  return context.measureText(label).width
}

function estimateTagWidth(label: string): number {
  return measureLabelWidth(label) + TAG_HORIZONTAL_EXTRA
}

function computeMaxCollapseTags(
  containerWidth: number,
  labels: readonly string[],
): number {
  if (labels.length === 0) {
    return 0
  }

  const budget = containerWidth * (2 / 3)
  let usedWidth = 0
  let visibleCount = 0

  for (let index = 0; index < labels.length; index += 1) {
    const tagWidth = estimateTagWidth(labels[index] ?? '')
      + (index > 0 ? TAG_GAP : 0)
    const hiddenCount = labels.length - index - 1
    const collapseReserve = hiddenCount > 0
      ? COLLAPSE_TAG_WIDTH + TAG_GAP
      : 0

    if (usedWidth + tagWidth + collapseReserve <= budget) {
      usedWidth += tagWidth
      visibleCount += 1
    } else {
      break
    }
  }

  return visibleCount > 0 ? visibleCount : 1
}

function getSelectedLabels(): string[] {
  const labelById = new Map(
    addUserOptions.value.map((option) => [option.id, option.label]),
  )

  return selectedUserIds.value.map((userId) => (
    labelById.get(userId) ?? userId
  ))
}

function updateMaxCollapseTags(): void {
  const wrapper = addMembersSelectWrapperRef.value
  if (!wrapper) return

  maxCollapseTags.value = computeMaxCollapseTags(
    wrapper.clientWidth,
    getSelectedLabels(),
  )
}

function bindSelectResizeObserver(): void {
  resizeObserver?.disconnect()

  const wrapper = addMembersSelectWrapperRef.value
  if (!wrapper) return

  resizeObserver = new ResizeObserver(() => {
    updateMaxCollapseTags()
  })
  resizeObserver.observe(wrapper)
  updateMaxCollapseTags()
}

function unbindSelectResizeObserver(): void {
  resizeObserver?.disconnect()
  resizeObserver = null
}

const addFormRules: FormRules<{ userIds: string[] }> = {
  userIds: [
    {
      required: true,
      type: 'array',
      min: 1,
      message: '请至少选择一名用户',
      trigger: 'change',
    },
  ],
}

async function loadMembers(): Promise<void> {
  loading.value = true
  errorMessage.value = ''

  try {
    members.value = [...await getProjectMembers(props.project.id)]
  } catch (error) {
    errorMessage.value = getApiErrorMessage(
      error,
      '成员列表加载失败，请稍后重试',
    )
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function openAddDialog(): Promise<void> {
  selectedUserIds.value = []
  addUserOptions.value = []
  addDialogVisible.value = true
  addOptionsLoading.value = true

  try {
    const memberIds = new Set(
      members.value.map((member) => member.user.id),
    )
    const result = await getUsers({
      systemRole: 'USER',
      active: true,
      page: 0,
      size: 100,
      sort: 'username,asc',
    })

    addUserOptions.value = result.items
      .filter((user) => !memberIds.has(user.id))
      .map((user) => ({
        id: user.id,
        label: `${user.displayName} (@${user.username})`,
      }))
  } catch (error) {
    addDialogVisible.value = false
    ElMessage.error(getApiErrorMessage(
      error,
      '可选用户加载失败，请稍后重试',
    ))
  } finally {
    addOptionsLoading.value = false
  }
}

async function handleAddMembers(): Promise<void> {
  const form = addFormRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  addSubmitting.value = true

  try {
    await addProjectMembers(props.project.id, {
      userIds: selectedUserIds.value,
    })
    addDialogVisible.value = false
    selectedUserIds.value = []
    ElMessage.success('成员已添加')
    await loadMembers()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '添加成员失败，请稍后重试',
    ))
  } finally {
    addSubmitting.value = false
  }
}

async function handleRemoveMember(member: ProjectMember): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定要移除成员 ${member.user.displayName} 吗？`,
      '移除成员',
      {
        confirmButtonText: '移除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await removeProjectMember(props.project.id, member.user.id)
    ElMessage.success('成员已移除')
    await loadMembers()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '移除成员失败，请稍后重试',
    ))
  }
}

function formatDateTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

function canRemoveMember(member: ProjectMember): boolean {
  return props.canRemoveMembers
    && member.user.id !== props.project.owner.id
}

onMounted(() => {
  void loadMembers()
})

watch(selectedUserIds, () => {
  void nextTick(updateMaxCollapseTags)
}, { deep: true })

watch(addDialogVisible, (visible) => {
  if (visible) {
    void nextTick(bindSelectResizeObserver)
    return
  }

  unbindSelectResizeObserver()
})

onUnmounted(() => {
  unbindSelectResizeObserver()
})

defineExpose({ reload: loadMembers })
</script>

<template>
  <section class="members-panel">
    <header class="panel-header">
      <div>
        <h3>项目成员</h3>
      </div>

      <div class="panel-actions">
        <el-button
          :loading="loading"
          @click="loadMembers"
        >
          刷新
        </el-button>
        <el-button
          v-if="canAddMembers"
          type="primary"
          @click="openAddDialog"
        >
          添加成员
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
      <el-button @click="loadMembers">
        重新加载
      </el-button>
    </div>

    <el-table
      v-else
      :data="members"
      row-key="user.id"
    >
      <el-table-column label="成员" min-width="160">
        <template #default="{ row }">
          {{ row.user.displayName }}
          <span
            v-if="row.user.id === project.owner.id"
            class="owner-badge"
          >
            Owner
          </span>
        </template>
      </el-table-column>

      <el-table-column label="用户 ID" prop="user.id" width="100" />

      <el-table-column label="加入时间" min-width="150">
        <template #default="{ row }">
          {{ formatDateTime(row.joinedAt) }}
        </template>
      </el-table-column>

      <el-table-column
        v-if="canRemoveMembers"
        fixed="right"
        label="操作"
        width="100"
      >
        <template #default="{ row }">
          <el-button
            v-if="canRemoveMember(row as ProjectMember)"
            link
            type="danger"
            @click="handleRemoveMember(row as ProjectMember)"
          >
            移除
          </el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="当前没有成员" />
      </template>
    </el-table>

    <el-dialog
      v-model="addDialogVisible"
      title="添加成员"
      width="480px"
    >
      <p class="dialog-note">
        仅 ADMIN 可直接添加 USER 成员。从列表中选择尚未加入项目的用户。
      </p>

      <el-form
        ref="addFormRef"
        label-position="top"
        :model="{ userIds: selectedUserIds }"
        :rules="addFormRules"
        @submit.prevent="handleAddMembers"
      >
        <el-form-item label="选择用户" prop="userIds">
          <div
            ref="addMembersSelectWrapperRef"
            class="add-members-select-wrapper"
          >
            <el-select
              v-model="selectedUserIds"
              class="add-members-select"
              collapse-tags
              collapse-tags-tooltip
              filterable
              :loading="addOptionsLoading"
              :max-collapse-tags="maxCollapseTags"
              multiple
              placeholder="选择要添加的用户"
            >
              <el-option
                v-for="option in addUserOptions"
                :key="option.id"
                :label="option.label"
                :value="option.id"
              />
            </el-select>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="addDialogVisible = false">
          取消
        </el-button>
        <el-button
          :loading="addSubmitting"
          type="primary"
          @click="handleAddMembers"
        >
          添加
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.members-panel {
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

.owner-badge {
  margin-left: 8px;
  color: var(--fs-color-primary, #2563eb);
  font-size: 12px;
}

.dialog-note {
  margin: 0 0 16px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 14px;
}

.add-members-select-wrapper,
.add-members-select {
  width: 100%;
}
</style>
