<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElTag,
} from 'element-plus'
import type {
  FormInstance,
  FormRules,
} from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/tag/style/css'

import {
  getApiErrorMessage,
} from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

import {
  changePassword,
  updateProfile,
} from './api'

type ProfileFormModel = {
  displayName: string
  phone: string
  email: string
}

type PasswordFormModel = {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

type PageState =
  | 'initialLoading'
  | 'success'
  | 'error'

const router = useRouter()
const authStore = useAuthStore()

const profileFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()

const profileForm = reactive<ProfileFormModel>({
  displayName: '',
  phone: '',
  email: '',
})

const passwordForm = reactive<PasswordFormModel>({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const loadErrorMessage = ref('')
const profileSubmitting = ref(false)
const passwordSubmitting = ref(false)

const pageState = computed<PageState>(() => {
  if (!authStore.initialized || authStore.loading) {
    return 'initialLoading'
  }

  if (!authStore.currentUser) {
    return 'error'
  }

  return 'success'
})

const profileRules: FormRules<ProfileFormModel> = {
  displayName: [
    {
      required: true,
      message: '请输入显示名称',
      trigger: 'blur',
    },
    {
      min: 1,
      max: 50,
      message: '显示名称长度为 1 到 50 个字符',
      trigger: 'blur',
    },
  ],
  phone: [
    {
      max: 20,
      message: '手机号不能超过 20 个字符',
      trigger: 'blur',
    },
  ],
  email: [
    {
      max: 100,
      message: '邮箱不能超过 100 个字符',
      trigger: 'blur',
    },
    {
      type: 'email',
      message: '请输入合法邮箱地址',
      trigger: 'blur',
    },
  ],
}

function validateNewPassword(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
): void {
  if (value.length < 12 || value.length > 64) {
    callback(new Error('新密码长度为 12 到 64 个字符'))
    return
  }

  if (new TextEncoder().encode(value).length > 72) {
    callback(new Error('新密码 UTF-8 编码不能超过 72 字节'))
    return
  }

  callback()
}

function validateConfirmPassword(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
): void {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }

  callback()
}

const passwordRules: FormRules<PasswordFormModel> = {
  currentPassword: [
    {
      required: true,
      message: '请输入当前密码',
      trigger: 'blur',
    },
  ],
  newPassword: [
    {
      required: true,
      message: '请输入新密码',
      trigger: 'blur',
    },
    {
      validator: validateNewPassword,
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    {
      required: true,
      message: '请再次输入新密码',
      trigger: 'blur',
    },
    {
      validator: validateConfirmPassword,
      trigger: 'blur',
    },
  ],
}

function syncProfileForm(): void {
  const user = authStore.currentUser
  if (!user) return

  profileForm.displayName = user.displayName
  profileForm.phone = user.phone ?? ''
  profileForm.email = user.email ?? ''
}

function normalizeNullable(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

async function handleReloadProfile(): Promise<void> {
  loadErrorMessage.value = ''
  await authStore.loadCurrentUser()

  if (!authStore.currentUser) {
    loadErrorMessage.value = authStore.errorMessage
      || '个人资料加载失败，请稍后重试'
  }
}

async function handleProfileSubmit(): Promise<void> {
  const form = profileFormRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  profileSubmitting.value = true

  try {
    const updated = await updateProfile({
      displayName: profileForm.displayName.trim(),
      phone: normalizeNullable(profileForm.phone),
      email: normalizeNullable(profileForm.email),
    })

    authStore.currentUser = updated
    syncProfileForm()
    ElMessage.success('个人资料已更新')
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '个人资料更新失败，请稍后重试',
    ))
  } finally {
    profileSubmitting.value = false
  }
}

async function handlePasswordSubmit(): Promise<void> {
  const form = passwordFormRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  passwordSubmitting.value = true

  try {
    await changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })

    authStore.clearSession()
    ElMessage.success('密码已修改，请重新登录')
    await router.replace({ name: 'login' })
  } catch (error) {
    ElMessage.error(getApiErrorMessage(
      error,
      '密码修改失败，请稍后重试',
    ))
  } finally {
    passwordSubmitting.value = false
  }
}

watch(
  () => authStore.currentUser,
  () => {
    syncProfileForm()
  },
  { immediate: true },
)
</script>

<template>
  <section class="profile-page">
    <header class="page-header">
      <div>
        <h1>个人中心</h1>
        <p>管理个人资料、联系方式和账号安全。</p>
      </div>
    </header>

    <section
      class="content-panel"
      data-testid="profile-content"
      :data-state="pageState"
    >
      <template v-if="pageState === 'initialLoading'">
        <div aria-label="加载中" class="initial-loading-space" />
      </template>

      <template v-else-if="pageState === 'error'">
        <div class="feedback-state">
          <el-alert
            :closable="false"
            show-icon
            :title="loadErrorMessage || '个人资料加载失败，请稍后重试'"
            type="error"
          />
          <el-button @click="handleReloadProfile">
            重新加载
          </el-button>
        </div>
      </template>

      <template v-else-if="authStore.currentUser">
        <section class="profile-section">
          <h2>账号信息</h2>

          <dl class="readonly-grid">
            <div>
              <dt>用户 ID</dt>
              <dd>{{ authStore.currentUser.id }}</dd>
            </div>
            <div>
              <dt>用户名</dt>
              <dd>{{ authStore.currentUser.username }}</dd>
            </div>
            <div>
              <dt>系统角色</dt>
              <dd>
                <el-tag
                  :type="
                    authStore.currentUser.systemRole === 'ADMIN'
                      ? 'danger'
                      : 'success'
                  "
                  effect="plain"
                >
                  {{ authStore.currentUser.systemRole === 'ADMIN' ? '管理员' : '普通用户' }}
                </el-tag>
              </dd>
            </div>
            <div>
              <dt>账号状态</dt>
              <dd>
                <el-tag
                  :type="authStore.currentUser.active ? 'success' : 'info'"
                  effect="plain"
                >
                  {{ authStore.currentUser.active ? '已启用' : '已停用' }}
                </el-tag>
              </dd>
            </div>
            <div>
              <dt>手机号</dt>
              <dd>{{ authStore.currentUser.phone ?? '未填写' }}</dd>
            </div>
            <div>
              <dt>邮箱</dt>
              <dd>{{ authStore.currentUser.email ?? '未填写' }}</dd>
            </div>
          </dl>
        </section>

        <section class="profile-section">
          <h2>个人资料</h2>

          <el-form
            ref="profileFormRef"
            class="profile-form"
            label-position="top"
            :model="profileForm"
            :rules="profileRules"
            @submit.prevent="handleProfileSubmit"
          >
            <el-form-item label="显示名称" prop="displayName">
              <el-input
                v-model="profileForm.displayName"
                maxlength="50"
                placeholder="请输入显示名称"
              />
            </el-form-item>

            <el-form-item label="手机号" prop="phone">
              <el-input
                v-model="profileForm.phone"
                maxlength="20"
                placeholder="可选，留空表示清空"
              />
            </el-form-item>

            <el-form-item label="邮箱" prop="email">
              <el-input
                v-model="profileForm.email"
                maxlength="100"
                placeholder="可选，留空表示清空"
              />
            </el-form-item>

            <el-button
              :loading="profileSubmitting"
              native-type="submit"
              type="primary"
            >
              保存修改
            </el-button>
          </el-form>
        </section>

        <section class="profile-section">
          <h2>安全设置</h2>
          <p class="section-note">
            修改成功后当前 Session 会失效，需要重新登录
          </p>

          <el-form
            ref="passwordFormRef"
            class="profile-form"
            label-position="top"
            :model="passwordForm"
            :rules="passwordRules"
            @submit.prevent="handlePasswordSubmit"
          >
            <el-form-item label="当前密码" prop="currentPassword">
              <el-input
                v-model="passwordForm.currentPassword"
                autocomplete="current-password"
                placeholder="请输入当前密码"
                show-password
                type="password"
              />
            </el-form-item>

            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="passwordForm.newPassword"
                autocomplete="new-password"
                placeholder="12 到 64 个字符"
                show-password
                type="password"
              />
            </el-form-item>

            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="passwordForm.confirmPassword"
                autocomplete="new-password"
                placeholder="请再次输入新密码"
                show-password
                type="password"
              />
            </el-form-item>

            <el-button
              :loading="passwordSubmitting"
              native-type="submit"
              type="primary"
            >
              修改密码
            </el-button>
          </el-form>
        </section>
      </template>
    </section>
  </section>
</template>

<style scoped>
.profile-page {
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

.content-panel {
  display: grid;
  width: 100%;
  max-width: 860px;
  gap: 36px;
  min-height: 320px;
  padding: 24px;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
  box-shadow: none;
}

.profile-section h2 {
  margin: 0 0 24px;
  color: var(--fs-color-text, #1f2937);
  font-size: 18px;
}

.profile-section {
  width: 100%;
  padding: 0;
  border: 0;
}

.section-note {
  margin: 0 0 16px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 14px;
}

.readonly-grid {
  display: grid;
  gap: 24px 32px;
  margin: 0;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.readonly-grid div {
  display: grid;
  gap: 4px;
}

.readonly-grid dt {
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 13px;
}

.readonly-grid dd {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
  font-size: 14px;
}

.profile-form {
  width: 100%;
  max-width: none;
}

.feedback-state {
  display: grid;
  min-height: 240px;
  align-content: center;
  gap: 16px;
  justify-items: center;
}

@media (max-width: 720px) {
  .content-panel {
    gap: 28px;
    padding: 16px;
  }

  .profile-section {
    padding: 0;
  }

  .readonly-grid {
    grid-template-columns: 1fr;
  }
}
</style>
