<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElAlert, ElButton, ElTag } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/tag/style/css'

import { getApiErrorMessage } from '@/shared/api/errors'
import { useAuthStore } from '@/stores/auth'

import { getPublicUserProfile } from './api'
import type { PublicUserProfile } from './types'

const props = defineProps<{ userId: string }>()
const authStore = useAuthStore()
const router = useRouter()
const profile = ref<PublicUserProfile | null>(null)
const loading = ref(true)
const errorMessage = ref('')
let requestId = 0

const pageState = computed(() => {
  if (loading.value) return 'initialLoading'
  if (errorMessage.value || !profile.value) return 'error'
  return 'success'
})

async function loadProfile(): Promise<void> {
  if (props.userId === authStore.currentUser?.id) {
    await router.replace({ name: 'profile' })
    return
  }

  const currentRequest = ++requestId
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await getPublicUserProfile(props.userId)
    if (currentRequest !== requestId) return
    profile.value = result
  } catch (error) {
    if (currentRequest !== requestId) return
    profile.value = null
    errorMessage.value = getApiErrorMessage(error, '用户资料加载失败，请稍后重试')
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}

watch(() => props.userId, () => void loadProfile())
onMounted(() => void loadProfile())
</script>

<template>
  <section class="user-profile-page">
    <header class="page-header">
      <div>
        <h1>{{ profile?.displayName ?? '用户资料' }}</h1>
        <p>查看用户的账号与联系方式。</p>
      </div>
    </header>

    <section
      class="profile-card"
      data-testid="public-profile-content"
      :data-state="pageState"
    >
      <div v-if="pageState === 'initialLoading'" class="initial-loading-space" role="status" />
      <div v-else-if="pageState === 'error'" class="feedback-state">
        <el-alert :closable="false" :title="errorMessage" type="error" show-icon />
        <el-button @click="loadProfile">重新加载</el-button>
      </div>
      <template v-else-if="profile">
        <h2>账号信息</h2>
        <dl class="readonly-grid">
          <div><dt>用户名</dt><dd>{{ profile.username }}</dd></div>
          <div>
            <dt>系统角色</dt>
            <dd><el-tag :type="profile.systemRole === 'ADMIN' ? 'danger' : 'success'" effect="plain">
              {{ profile.systemRole === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag></dd>
          </div>
          <div>
            <dt>账号状态</dt>
            <dd><el-tag :type="profile.active ? 'success' : 'info'" effect="plain">
              {{ profile.active ? '已启用' : '已停用' }}
            </el-tag></dd>
          </div>
          <div><dt>手机号</dt><dd>{{ profile.phone ?? '未填写' }}</dd></div>
          <div><dt>邮箱</dt><dd>{{ profile.email ?? '未填写' }}</dd></div>
        </dl>
      </template>
    </section>
  </section>
</template>

<style scoped>
.user-profile-page { display: grid; min-width: 0; gap: 16px; grid-template-columns: minmax(0, 1fr); }
.page-header h1 { margin: 0; color: #1f2937; font-size: 24px; }
.page-header p { margin: 6px 0 0; color: #64748b; }
.profile-card { width: 100%; max-width: 860px; min-height: 220px; padding: 20px; border: 1px solid #dbe3ee; border-radius: 8px; background: #fff; }
.profile-card h2 { margin: 0 0 24px; color: #1f2937; font-size: 18px; }
.readonly-grid { display: grid; gap: 24px 32px; margin: 0; grid-template-columns: repeat(3, minmax(0, 1fr)); }
.readonly-grid div { display: grid; min-width: 0; gap: 6px; }
.readonly-grid dt { color: #64748b; font-size: 13px; }
.readonly-grid dd { margin: 0; color: #1f2937; overflow-wrap: anywhere; }
.feedback-state { display: grid; min-height: 180px; align-content: center; gap: 16px; justify-items: center; }
@media (max-width: 720px) {
  .profile-card { padding: 16px; }
  .readonly-grid { grid-template-columns: 1fr; }
}
</style>
