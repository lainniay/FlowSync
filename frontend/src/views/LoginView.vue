<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
} from 'element-plus'
import type {
  FormInstance,
  FormRules,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'

import { getSafeRedirect } from '@/router/navigation'
import { useAuthStore } from '@/stores/auth'

type LoginFormModel = {
  username: string
  password: string
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loginFormRef = ref<FormInstance>()
const loginForm = reactive<LoginFormModel>({
  username: '',
  password: '',
})

const loginRules: FormRules<LoginFormModel> = {
  username: [
    {
      required: true,
      message: '请输入用户名',
      trigger: 'blur',
    },
    {
      max: 50,
      message: '用户名不能超过 50 个字符',
      trigger: 'blur',
    },
  ],
  password: [
    {
      required: true,
      message: '请输入密码',
      trigger: 'blur',
    },
  ],
}

async function handleLogin(): Promise<void> {
  const form = loginFormRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  const success = await authStore.login({ ...loginForm })
  if (!success) return

  await router.replace(
    getSafeRedirect(route.query.redirect),
  )
}
</script>

<template>
  <main class="login-page">
    <el-card class="login-card" shadow="never">
      <p class="eyebrow">小组任务协同管理系统</p>
      <h1>FlowSync</h1>
      <p class="description">
        登录后进入项目与任务协作空间。
      </p>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-position="top"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="loginForm.username"
            autocomplete="username"
            placeholder="请输入用户名"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="loginForm.password"
            autocomplete="current-password"
            placeholder="请输入密码"
            show-password
            type="password"
          />
        </el-form-item>

        <el-button
          class="login-button"
          :loading="authStore.loading"
          native-type="submit"
          type="primary"
        >
          登录
        </el-button>

        <p
          v-if="authStore.errorMessage"
          class="error-message"
          role="alert"
        >
          {{ authStore.errorMessage }}
        </p>
      </el-form>
    </el-card>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  background: var(--fs-color-page, #f4f7fb);
  place-items: center;
}

.login-card {
  width: min(420px, 100%);
  border-color: var(--fs-color-border, #dbe3ee);
  border-radius: 12px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--fs-color-primary, #2563eb);
  font-size: 14px;
  font-weight: 600;
}

h1 {
  margin: 0;
  color: var(--fs-color-text, #1f2937);
  font-size: 48px;
  line-height: 1.1;
}

.description {
  margin: 12px 0 24px;
  color: var(--fs-color-text-secondary, #64748b);
  line-height: 1.6;
}

.login-button {
  width: 100%;
}

.error-message {
  margin: 12px 0 0;
  color: var(--fs-color-danger, #dc2626);
  font-size: 14px;
}
</style>