<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElMenu,
  ElMenuItem,
  ElMessage,
  ElTag,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/menu/style/css'
import 'element-plus/es/components/menu-item/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/tag/style/css'

import { useAuthStore } from '@/stores/auth'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'

type MenuItem = {
  label: string
  path: string
  disabled?: boolean
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const adminMenu: readonly MenuItem[] = [
  { label: '工作台', path: '/overview' },
  { label: '用户管理', path: '/admin/users' },
  { label: '项目', path: '/projects' },
  { label: '任务', path: '/tasks', disabled: true },
  { label: '总结', path: '/summaries', disabled: true },
  { label: '个人中心', path: '/profile' },
]

const userMenu: readonly MenuItem[] = [
  { label: '工作台', path: '/overview' },
  { label: '项目', path: '/projects' },
  { label: '任务', path: '/tasks', disabled: true },
  { label: '总结', path: '/summaries', disabled: true },
  { label: '收到的邀请', path: '/invitations' },
  { label: '个人中心', path: '/profile' },
]

const menuItems = computed(() => (
  authStore.currentUser?.systemRole === 'ADMIN'
    ? adminMenu
    : userMenu
))

const activeMenu = computed(() => {
  if (route.path.startsWith('/projects')) {
    return '/projects'
  }

  if (route.path.startsWith('/admin/users')) {
    return '/admin/users'
  }

  if (route.path.startsWith('/invitations')) {
    return '/invitations'
  }

  return route.path === '/'
    ? '/overview'
    : route.path
})

async function handleLogout(): Promise<void> {
  const success = await authStore.logout()

  if (!success) {
    ElMessage.error(authStore.errorMessage)
    return
  }

  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-layout">
    <aside class="app-sidebar">
      <RouterLink class="brand" to="/overview">
        <span class="brand-mark">F</span>
        <span>FlowSync</span>
      </RouterLink>

      <el-menu
        class="app-menu"
        :default-active="activeMenu"
        router
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :disabled="item.disabled"
          :index="item.path"
        >
          {{ item.label }}
        </el-menu-item>
      </el-menu>

      <p class="menu-note">
        灰色菜单将在对应业务模块接入后启用。
      </p>
    </aside>

    <section class="app-workspace">
      <header class="app-header">
        <p class="header-product">
          小组任务协同管理系统
        </p>

        <div class="header-actions">
          <ThemeSwitcher />

          <div v-if="authStore.currentUser" class="user-area">
            <div class="user-copy">
              <strong>{{ authStore.currentUser.displayName }}</strong>
              <span>@{{ authStore.currentUser.username }}</span>
            </div>

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
              @click="handleLogout"
            >
              退出
            </el-button>
          </div>
        </div>
      </header>

      <main class="app-main">
        <RouterView />
      </main>
    </section>
  </div>
</template>

<style scoped>
.app-layout {
  display: grid;
  min-height: 100vh;
  grid-template-columns: 224px minmax(0, 1fr);
}

.app-sidebar {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  border-right: 1px solid var(--fs-color-border, #dbe3ee);
  background: var(--fs-color-surface, #fff);
}

.brand {
  display: flex;
  height: 64px;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  color: var(--fs-color-text, #1f2937);
  font-size: 18px;
  font-weight: 700;
  text-decoration: none;
}

.brand-mark {
  display: grid;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--fs-color-primary, #2563eb);
  color: var(--fs-color-on-primary, #fff);
  place-items: center;
}

.app-menu {
  flex: 1;
  border-right: 0;
}

.menu-note {
  margin: 0;
  padding: 16px 20px;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 12px;
  line-height: 1.5;
}

.app-workspace {
  min-width: 0;
}

.app-header {
  display: flex;
  height: 64px;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--fs-color-border, #dbe3ee);
  background: var(--fs-color-surface, #fff);
}

.header-product {
  margin: 0;
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 14px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-copy {
  display: grid;
  gap: 2px;
  text-align: right;
}

.user-copy strong {
  color: var(--fs-color-text, #1f2937);
  font-size: 14px;
}

.user-copy span {
  color: var(--fs-color-text-secondary, #64748b);
  font-size: 12px;
}

.app-main {
  min-height: calc(100vh - 64px);
  padding: 24px;
  background: var(--fs-color-page, #f4f7fb);
}

@media (max-width: 720px) {
  .app-layout {
    grid-template-columns: 168px minmax(0, 1fr);
  }

  .header-product,
  .user-copy {
    display: none;
  }

  .app-header,
  .app-main {
    padding-right: 16px;
    padding-left: 16px;
  }
}
</style>