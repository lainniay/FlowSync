<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElSpace,
  ElTag,
} from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/form-item/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/space/style/css'
import 'element-plus/es/components/tag/style/css'

import { useAuthStore } from '@/stores/auth'

type LoginFormModel = {
  username: string
  password: string
}

const stack = ['Vue 3', 'TypeScript', 'Element Plus', 'Spring Boot']
const authStore = useAuthStore()

const loginFormRef = ref<FormInstance>()
const loginForm = reactive<LoginFormModel>({
  username: '',
  password: '',
})

const loginRules: FormRules<LoginFormModel> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
  ],
}

async function handleLogin(): Promise<void> {
  const form = loginFormRef.value
  if (!form) return

  const valid = await form.validate().catch(() => false)
  if (!valid) return

  await authStore.login({ ...loginForm })
}

onMounted(async () => {
  await authStore.loadCurrentUser()
})
</script>

<template>
  <main class="home">
    <el-card class="intro" shadow="never">
      <p class="eyebrow">小组任务协同管理系统</p>
      <h1>FlowSync</h1>
      <p class="description">前端工程已就绪，下一步实现认证与项目协作流程。</p>

      <section class="session-status" aria-live="polite">
        <div v-if="authStore.currentUser" class="current-user">
          <div>
            <p class="welcome">
              你好，{{ authStore.currentUser.displayName }}
            </p>
            <p class="username">
              用户名：{{ authStore.currentUser.username }}
            </p>
          </div>

          <div class="user-actions">
            <el-tag
              :type="
                authStore.currentUser.systemRole === 'ADMIN'
                  ? 'danger'
                  : 'success'
              "
              effect="plain"
            >
              {{ authStore.currentUser.systemRole }}
            </el-tag>

            <el-button
              :loading="authStore.loading"
              @click="authStore.logout"
            >
              退出登录
            </el-button>
          </div>
        </div>

        <el-form
          v-else
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

          <p v-if="authStore.errorMessage" class="error-message">
            {{ authStore.errorMessage }}
          </p>
        </el-form>
      </section>

      <el-space wrap>
        <el-tag
          v-for="item in stack"
          :key="item"
          effect="plain"
        >
          {{ item }}
        </el-tag>
      </el-space>
    </el-card>
  </main>
</template>

<style scoped>
.home {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 24px;
}

.intro {
  width: min(640px, 100%);
  border: 0;
}

.eyebrow {
  margin: 0 0 8px;
  color: #409eff;
  font-weight: 600;
}

h1 {
  margin: 0;
  font-size: clamp(40px, 8vw, 64px);
}

.description {
  margin: 16px 0 24px;
  color: #606266;
  line-height: 1.6;
}

.session-status {
  min-height: 76px;
  margin-bottom: 24px;
  padding: 16px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #f8f9fa;
}

.session-status p {
  margin: 0;
}

.current-user {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.welcome {
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.username {
  margin-top: 6px !important;
  color: #909399;
  font-size: 14px;
}


.user-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.login-button {
  width: 100%;
}

.login-form .error-message,
.error-message {
  margin-top: 12px;
  color: #f56c6c;
}
</style>