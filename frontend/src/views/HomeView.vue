//登陆后的工作台界面

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
</script>

<template>
  <section class="overview-page">
    <header class="page-header">
      <div>
        <h1>工作台</h1>
        <p>
          欢迎回来，{{ authStore.currentUser?.displayName }}。
        </p>
      </div>
    </header>

    <div class="content-panel">
      <h2>前端公共基座</h2>
      <p>
        认证状态、嵌套路由和应用布局已经接入。
        业务统计将在工作台模块开发时连接
        <code>GET /api/overview</code>。
      </p>
    </div>
  </section>
</template>

<style scoped>
.overview-page {
  display: grid;
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
  padding: 20px;
  border: 1px solid var(--fs-color-border, #dbe3ee);
  border-radius: 8px;
  background: var(--fs-color-surface, #fff);
}

.content-panel h2 {
  margin: 0 0 12px;
  font-size: 18px;
}

.content-panel p {
  margin: 0;
  color: var(--fs-color-text-secondary, #64748b);
  line-height: 1.7;
}
</style>