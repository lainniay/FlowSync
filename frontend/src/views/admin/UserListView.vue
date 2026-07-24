<script setup lang="ts">
import {
  computed,
  onMounted,
  reactive,
  ref,
} from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import type {
  FormInstance,
  FormRules,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/drawer/style/css'
import 'element-plus/es/components/dropdown/style/css'
import 'element-plus/es/components/dropdown-item/style/css'
import 'element-plus/es/components/dropdown-menu/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/option/style/css'
import 'element-plus/es/components/pagination/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/table-column/style/css'
import 'element-plus/es/components/tag/style/css'

import MaterialIcon from '@/components/MaterialIcon.vue'
import UserLink from '@/components/UserLink.vue'
import { getApiErrorMessage } from '@/shared/api/errors'
import { formatDateTime } from '@/shared/format'
import { PAGE_SIZE } from '@/shared/api/pagination'
import type {
  SystemRole,
  User,
} from '@/shared/api/types'

import {
  createUser,
  getUsers,
  resetUserPassword,
  updateUser,
} from './api'
import UserFormDialog from './UserFormDialog.vue'
import type {
  CreateUserRequest,
  UpdateUserRequest,
  UserListFilters,
  UserListQuery,
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

type ResetPasswordForm = {
  newPassword: string
  confirmPassword: string
}

type RowAction = 'edit' | 'toggle' | 'reset'

const roleTagTypes: Record<SystemRole, TagType> = {
  ADMIN: 'danger',
  USER: 'success',
}

function createInitialFilters(): UserListFilters {
  return {
    q: '',
    systemRole: '',
    active: true,
  }
}

const filters = reactive<UserListFilters>(createInitialFilters())
const appliedFilters = ref<UserListFilters>(createInitialFilters())

const users = ref<User[]>([])
const page = ref(0)
const totalElements = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')

const createDialogVisible = ref(false)
const editDialogVisible = ref(false)
const editingUser = ref<User | null>(null)
const formSubmitting = ref(false)

const detailDrawerVisible = ref(false)
const detailUser = ref<User | null>(null)

const resetDialogVisible = ref(false)
const resetTargetUser = ref<User | null>(null)
const resetSubmitting = ref(false)
const resetFormRef = ref<FormInstance>()
const resetForm = reactive<ResetPasswordForm>({
  newPassword: '',
  confirmPassword: '',
})

const hasActiveFilters = computed(() => {
  const current = appliedFilters.value

  return (
    current.q !== ''
    || current.systemRole !== ''
    || current.active !== true
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

  return users.value.length > 0
    ? 'success'
    : 'empty'
})

function buildQuery(): UserListQuery {
  const current = appliedFilters.value

  return {
    q: current.q || undefined,
    systemRole: current.systemRole || undefined,
    active: current.active,
    page: page.value,
      size: PAGE_SIZE,
    sort: 'createdAt,desc',
  }
}

async function loadUsers(
  targetPage = page.value,
): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  page.value = targetPage

  try {
    const result = await getUsers(buildQuery())

    users.value = [...result.items]
    page.value = result.page
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error) {
    errorMessage.value = getApiErrorMessage(
      error,
      '用户列表加载失败，请稍后重试',
    )
  } finally {
    loading.value = false
    loaded.value = true
  }
}

async function handleSearch(): Promise<void> {
  appliedFilters.value = {
    q: filters.q.trim(),
    systemRole: filters.systemRole,
    active: filters.active,
  }

  await loadUsers(0)
}

async function handleReset(): Promise<void> {
  const initial = createInitialFilters()

  Object.assign(filters, initial)
  appliedFilters.value = initial

  await loadUsers(0)
}

async function handlePageChange(
  displayedPage: number,
): Promise<void> {
  await loadUsers(displayedPage - 1)
}

function openCreateDialog(): void {
  createDialogVisible.value = true
}

function openEditDialog(user: User): void {
  editingUser.value = user
  editDialogVisible.value = true
}

function openDetailDrawer(user: User): void {
  detailUser.value = user
  detailDrawerVisible.value = true
}

function openResetDialog(user: User): void {
  resetTargetUser.value = user
  resetForm.newPassword = ''
  resetForm.confirmPassword = ''
  resetDialogVisible.value = true
}

function validateResetPassword(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
): void {
  if (value.length < 12 || value.length > 64) {
    callback(new Error('密码长度为 12 到 64 个字符'))
    return
  }

  if (new TextEncoder().encode(value).length > 72) {
    callback(new Error('密码 UTF-8 编码不能超过 72 字节'))
    return
  }

  callback()
}

function validateResetConfirm(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
): void {
  if (value !== resetForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }

  callback()
}

const resetRules: FormRules<ResetPasswordForm> = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { validator: validateResetPassword, trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateResetConfirm, trigger: 'blur' },
  ],
}

async function reloadAfterMutation(): Promise<void> {
  const nextPage = page.value > 0 && users.value.length <= 1
    ? page.value - 1
    : page.value

  await loadUsers(nextPage)
}

async function handleCreateUser(
  payload: CreateUserRequest,
): Promise<void> {
  formSubmitting.value = true

  try {
    await createUser(payload)
    createDialogVisible.value = false
    ElMessage.success('用户已创建')
    await reloadAfterMutation()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '用户创建失败，请稍后重试',
    ))
  } finally {
    formSubmitting.value = false
  }
}

async function handleEditUser(
  payload: UpdateUserRequest,
): Promise<void> {
  if (!editingUser.value) return

  formSubmitting.value = true

  try {
    await updateUser(editingUser.value.id, payload)
    editDialogVisible.value = false
    editingUser.value = null
    ElMessage.success('用户已更新')
    await reloadAfterMutation()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '用户更新失败，请稍后重试',
    ))
  } finally {
    formSubmitting.value = false
  }
}

async function handleToggleActive(user: User): Promise<void> {
  const nextActive = !user.active
  const actionLabel = nextActive ? '启用' : '停用'

  try {
    await ElMessageBox.confirm(
      `确定要${actionLabel}用户 ${user.displayName} 吗？`,
      `${actionLabel}用户`,
      {
        confirmButtonText: actionLabel,
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  try {
    await updateUser(user.id, {
      displayName: user.displayName,
      phone: user.phone,
      email: user.email,
      systemRole: user.systemRole,
      active: nextActive,
    })
    ElMessage.success(`用户已${actionLabel}`)
    await reloadAfterMutation()
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      `用户${actionLabel}失败，请稍后重试`,
    ))
  }
}

async function handleResetPasswordSubmit(): Promise<void> {
  const form = resetFormRef.value
  if (!form || !resetTargetUser.value) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  resetSubmitting.value = true

  try {
    await resetUserPassword(resetTargetUser.value.id, {
      newPassword: resetForm.newPassword,
    })
    resetDialogVisible.value = false
    resetTargetUser.value = null
    ElMessage.success('密码已重置')
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '密码重置失败，请稍后重试',
    ))
  } finally {
    resetSubmitting.value = false
  }
}

function getUserInitial(displayName: string): string {
  return displayName.trim().charAt(0) || '?'
}

function openEditFromDetail(): void {
  if (!detailUser.value) return

  const user = detailUser.value
  detailDrawerVisible.value = false
  openEditDialog(user)
}

function openResetFromDetail(): void {
  if (!detailUser.value) return

  const user = detailUser.value
  detailDrawerVisible.value = false
  openResetDialog(user)
}

function handleRowAction(
  command: string | number | object,
  user: User,
): void {
  switch (command as RowAction) {
    case 'edit':
      openEditDialog(user)
      break
    case 'toggle':
      void handleToggleActive(user)
      break
    case 'reset':
      openResetDialog(user)
      break
  }
}

onMounted(() => {
  void loadUsers()
})
</script>

<template>
  <section class="user-page">
    <header class="page-header">
      <h1>用户管理</h1>

      <div class="header-actions">
        <el-button
          type="primary"
          @click="openCreateDialog"
        >
          <MaterialIcon name="person_add" />
          创建用户
        </el-button>
      </div>
    </header>

    <section class="filter-panel">
      <el-form
        class="filter-layout"
        label-position="top"
        :model="filters"
        @submit.prevent="handleSearch"
      >
        <div class="filter-fields">
          <el-form-item label="搜索">
          <el-input
            v-model="filters.q"
            class="search-input"
            clearable
            placeholder="用户名或显示名称"
          />
          </el-form-item>

          <el-form-item label="系统角色">
          <el-select
            v-model="filters.systemRole"
            class="role-select"
            placeholder="全部角色"
          >
            <el-option label="全部角色" value="" />
            <el-option label="ADMIN" value="ADMIN" />
            <el-option label="USER" value="USER" />
          </el-select>
          </el-form-item>

          <el-form-item label="启用状态">
          <el-select
            v-model="filters.active"
            class="active-select"
          >
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
          </el-select>
          </el-form-item>
        </div>

        <div class="filter-actions" role="group" aria-label="筛选操作">
          <el-button
            native-type="submit"
            type="primary"
          >
            <MaterialIcon name="search" />
            查询
          </el-button>
          <el-button @click="handleReset">
            <MaterialIcon name="filter_list_off" />
            重置
          </el-button>
        </div>
      </el-form>
    </section>

    <section
      class="content-panel"
      data-testid="user-content"
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
          @click="loadUsers()"
        >
          重新加载
        </el-button>
      </div>

      <template v-else>
        <div class="table-card">
            <div class="table-scroll">
              <el-table
                :data="users"
                row-key="id"
              >
              <el-table-column
                label="用户名"
                min-width="108"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <UserLink :user-id="row.id">{{ row.username }}</UserLink>
                </template>
              </el-table-column>

              <el-table-column
                label="显示名称"
                min-width="108"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  <UserLink :user-id="row.id">{{ row.displayName }}</UserLink>
                </template>
              </el-table-column>

              <el-table-column label="角色" min-width="84">
                <template #default="{ row }">
                  <el-tag
                    :type="roleTagTypes[row.systemRole as SystemRole]"
                    effect="plain"
                  >
                    {{ row.systemRole }}
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column label="状态" min-width="72">
                <template #default="{ row }">
                  <el-tag
                    :type="row.active ? 'success' : 'info'"
                    effect="plain"
                  >
                    {{ row.active ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column label="创建时间" min-width="136">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt) }}
                </template>
              </el-table-column>

              <el-table-column
                fixed="right"
                label="操作"
                width="112"
              >
                <template #default="{ row }">
                  <div class="row-actions">
                    <el-button
                      link
                      type="primary"
                      @click="openDetailDrawer(row as User)"
                    >
                      查看
                    </el-button>

                    <el-dropdown
                      trigger="click"
                      @command="(command) => handleRowAction(command, row as User)"
                    >
                      <el-button link type="primary">
                        更多
                      </el-button>

                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="edit">
                            编辑
                          </el-dropdown-item>
                          <el-dropdown-item command="toggle">
                            {{ row.active ? '停用' : '启用' }}
                          </el-dropdown-item>
                          <el-dropdown-item command="reset">
                            重置密码
                          </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </div>
                </template>
              </el-table-column>

              <template #empty>
                <el-empty
                  :description="
                    hasActiveFilters
                      ? '没有符合条件的用户'
                      : '当前没有可见用户'
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
            </div>

            <footer
              v-if="totalElements > 0"
              class="table-footer"
            >
              <el-pagination
                class="user-pagination"
                :current-page="page + 1"
                layout="total, prev, pager, next"
                :page-size="PAGE_SIZE"
                :total="totalElements"
                @current-change="handlePageChange"
              />
            </footer>
        </div>
      </template>
    </section>

    <UserFormDialog
      v-model:visible="createDialogVisible"
      mode="create"
      :submitting="formSubmitting"
      @submit-create="handleCreateUser"
    />

    <UserFormDialog
      v-model:visible="editDialogVisible"
      mode="edit"
      :submitting="formSubmitting"
      :user="editingUser"
      @submit-edit="handleEditUser"
    />

    <el-drawer
      v-model="detailDrawerVisible"
      class="user-detail-drawer"
      direction="rtl"
      size="min(400px, 100%)"
      title="用户详情"
    >
      <div
        v-if="detailUser"
        class="user-detail"
      >
        <header class="detail-hero">
          <div
            aria-hidden="true"
            class="detail-avatar"
          >
            {{ getUserInitial(detailUser.displayName) }}
          </div>

          <div class="detail-hero-copy">
            <h2 class="detail-name">
              <UserLink :user-id="detailUser.id">{{ detailUser.displayName }}</UserLink>
            </h2>
            <p class="detail-username">
              <UserLink :user-id="detailUser.id">@{{ detailUser.username }}</UserLink>
            </p>

            <div class="detail-badges">
              <el-tag
                :type="roleTagTypes[detailUser.systemRole]"
                effect="plain"
                size="small"
              >
                {{ detailUser.systemRole }}
              </el-tag>
              <el-tag
                :type="detailUser.active ? 'success' : 'info'"
                effect="plain"
                size="small"
              >
                {{ detailUser.active ? '启用' : '停用' }}
              </el-tag>
            </div>
          </div>
        </header>

        <section class="detail-section">
          <h3 class="detail-section-title">
            联系方式
          </h3>

          <div class="detail-fields">
            <div class="detail-field detail-field--full">
              <MaterialIcon class="detail-field-icon" name="phone" />
              <div class="detail-field-body">
                <span class="detail-field-label">手机号</span>
                <span class="detail-field-value">
                  {{ detailUser.phone ?? '未填写' }}
                </span>
              </div>
            </div>

            <div class="detail-field detail-field--full">
              <MaterialIcon class="detail-field-icon" name="mail" />
              <div class="detail-field-body">
                <span class="detail-field-label">邮箱</span>
                <span class="detail-field-value detail-field-value--break">
                  {{ detailUser.email ?? '未填写' }}
                </span>
              </div>
            </div>
          </div>
        </section>

        <section class="detail-section">
          <h3 class="detail-section-title">
            时间记录
          </h3>

          <div class="detail-fields detail-fields--split">
            <div class="detail-field">
              <MaterialIcon class="detail-field-icon" name="schedule" />
              <div class="detail-field-body">
                <span class="detail-field-label">创建时间</span>
                <span class="detail-field-value">
                  {{ formatDateTime(detailUser.createdAt) }}
                </span>
              </div>
            </div>

            <div class="detail-field">
              <MaterialIcon class="detail-field-icon" name="schedule" />
              <div class="detail-field-body">
                <span class="detail-field-label">更新时间</span>
                <span class="detail-field-value">
                  {{ formatDateTime(detailUser.updatedAt) }}
                </span>
              </div>
            </div>
          </div>
        </section>
      </div>

      <template #footer>
        <div
          v-if="detailUser"
          class="detail-footer"
        >
          <el-button @click="detailDrawerVisible = false">
            关闭
          </el-button>
          <el-button
            @click="openResetFromDetail"
          >
            <MaterialIcon name="key" />
            重置密码
          </el-button>
          <el-button
            type="primary"
            @click="openEditFromDetail"
          >
            <MaterialIcon name="edit" />
            编辑用户
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="resetDialogVisible"
      title="重置密码"
      width="480px"
    >
      <p
        v-if="resetTargetUser"
        class="dialog-note"
      >
        为用户 {{ resetTargetUser.displayName }} 设置新密码，成功后该用户所有 Session 将失效
      </p>

      <el-form
        ref="resetFormRef"
        label-position="top"
        :model="resetForm"
        :rules="resetRules"
        @submit.prevent="handleResetPasswordSubmit"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="resetForm.newPassword"
            class="password-input"
            placeholder="12 到 64 个字符"
            show-password
            type="password"
          />
        </el-form-item>

        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="resetForm.confirmPassword"
            class="password-input"
            placeholder="请再次输入新密码"
            show-password
            type="password"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="resetDialogVisible = false">
          取消
        </el-button>
        <el-button
          :loading="resetSubmitting"
          type="primary"
          @click="handleResetPasswordSubmit"
        >
          重置密码
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.user-page {
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
  flex-wrap: wrap;
  gap: 8px;
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
}

.content-panel[data-state='error'] {
  min-height: 240px;
}

.table-card {
  width: 100%;
  max-width: 100%;
  overflow: hidden;
}

.table-scroll {
  overflow-x: auto;
}

.table-scroll :deep(.el-table) {
  width: 100%;
}

.table-scroll :deep(.el-table::before) {
  display: none;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.feedback-state {
  display: grid;
  min-height: 240px;
  align-content: center;
  gap: 16px;
  justify-items: center;
}

.user-pagination {
  justify-content: flex-end;
}

.role-select,
.active-select {
  width: 100%;
}

.search-input {
  width: 100%;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.user-detail {
  display: grid;
  gap: 16px;
}

.detail-hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: var(--fs-radius-lg, 12px);
  background: var(--fs-color-surface-muted, #f8fafc);
}

.detail-avatar {
  display: grid;
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  border-radius: var(--fs-radius-md, 8px);
  background: var(--fs-color-primary-soft, #eff6ff);
  color: var(--fs-color-primary, #2563eb);
  font-size: 20px;
  font-weight: 600;
  place-items: center;
}

.detail-hero-copy {
  min-width: 0;
}

.detail-name {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
  font-size: 18px;
  line-height: 1.3;
}

.detail-username {
  margin: 4px 0 0;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
}

.detail-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.detail-section-title {
  margin: 0 0 10px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.detail-fields {
  display: grid;
  gap: 10px;
}

.detail-fields--split {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.detail-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: var(--fs-radius-md, 8px);
  background: var(--fs-color-surface, #fff);
}

.detail-field--full {
  grid-column: 1 / -1;
}

.detail-field-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--fs-color-primary, #2563eb);
}

.detail-field-body {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.detail-field-label {
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 12px;
}

.detail-field-value {
  color: var(--fs-color-text, #1f2937);
  font-size: 14px;
  line-height: 1.45;
}

.detail-field-value--break {
  word-break: break-all;
}

.detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.user-detail-drawer :deep(.el-drawer__body) {
  padding-top: 8px;
}

@media (max-width: 480px) {
  .detail-fields--split {
    grid-template-columns: 1fr;
  }

  .detail-footer {
    flex-wrap: wrap;
  }
}

.dialog-note {
  margin: 0 0 16px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 14px;
}

.password-input {
  width: 50%;
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .content-panel {
    padding: 16px;
  }

}
</style>
