<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElMenu,
  ElMenuItem,
  ElMessage,
  ElTag,
} from 'element-plus'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/dropdown/style/css'
import 'element-plus/es/components/dropdown-item/style/css'
import 'element-plus/es/components/dropdown-menu/style/css'
import 'element-plus/es/components/menu/style/css'
import 'element-plus/es/components/menu-item/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/tag/style/css'

import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import MaterialIcon from '@/components/MaterialIcon.vue'

type MenuItem = {
  icon: string
  label: string
  path: string
  disabled?: boolean
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const mobileMenuOpen = ref(false)
const sidebarCollapsed = ref(false)
const appMainRef = ref<HTMLElement | null>(null)
const floatingPageTitle = ref('')
const showFloatingPageTitle = ref(false)
let titleFrame = 0
let titleObserver: MutationObserver | null = null

function updateFloatingPageTitle(): void {
  const main = appMainRef.value
  const title = main?.querySelector<HTMLElement>('.page-header h1')
  if (!main || !title) {
    showFloatingPageTitle.value = false
    return
  }

  const mainRect = main.getBoundingClientRect()
  const titleRect = title.getBoundingClientRect()
  floatingPageTitle.value = title.textContent?.trim() ?? ''
  showFloatingPageTitle.value = Boolean(
    floatingPageTitle.value
    && titleRect.height > 0
    && titleRect.top + titleRect.height / 2 <= mainRect.top,
  )
}

function scheduleFloatingPageTitleUpdate(): void {
  cancelAnimationFrame(titleFrame)
  titleFrame = requestAnimationFrame(updateFloatingPageTitle)
}

watch(
  () => route.path,
  () => {
    mobileMenuOpen.value = false
    showFloatingPageTitle.value = false
    void nextTick(() => {
      if (appMainRef.value) appMainRef.value.scrollTop = 0
      scheduleFloatingPageTitleUpdate()
    })
  },
)

onMounted(() => {
  if (!appMainRef.value) return
  titleObserver = new MutationObserver(scheduleFloatingPageTitleUpdate)
  titleObserver.observe(appMainRef.value, {
    childList: true,
    subtree: true,
  })
  scheduleFloatingPageTitleUpdate()
})

onBeforeUnmount(() => {
  titleObserver?.disconnect()
  cancelAnimationFrame(titleFrame)
})

const adminMenu: readonly MenuItem[] = [
  { icon: 'dashboard', label: '工作台', path: '/overview' },
  { icon: 'manage_accounts', label: '用户管理', path: '/admin/users' },
  { icon: 'folder', label: '项目', path: '/projects' },
]

const userMenu: readonly MenuItem[] = [
  { icon: 'dashboard', label: '汇总', path: '/overview' },
  { icon: 'folder', label: '项目', path: '/projects' },
  { icon: 'checklist', label: '任务', path: '/tasks' },
  { icon: 'mail', label: '邀请', path: '/invitations' },
]

const menuItems = computed(() => (
  authStore.currentUser?.systemRole === 'ADMIN'
    ? adminMenu
    : userMenu
))

const activeMenu = computed(() => {
  if (
    route.path.startsWith('/projects')
    || route.path.startsWith('/summaries')
  ) {
    return '/projects'
  }

  if (route.path.startsWith('/admin/users')) {
    return '/admin/users'
  }

  if (route.path.startsWith('/invitations')) {
    return '/invitations'
  }

  if (route.path.startsWith('/tasks')) {
    return '/tasks'
  }

  return route.path === '/'
    ? '/overview'
    : route.path
})

const themeMenuLabel = computed(() => (
  themeStore.theme === 'dark'
    ? '切换到浅色主题'
    : '切换到深色主题'
))

async function handleLogout(): Promise<void> {
  const success = await authStore.logout()

  if (!success) {
    ElMessage.error(authStore.errorMessage)
    return
  }

  await router.replace({ name: 'login' })
}

async function handleAccountCommand(command: string): Promise<void> {
  if (command === 'profile') {
    await router.push({ name: 'profile' })
    return
  }

  if (command === 'theme') {
    themeStore.toggleTheme()
    return
  }

  if (command === 'logout') {
    await handleLogout()
  }
}
</script>

<template>
  <div
    class="app-layout"
    :class="{
      'mobile-menu-open': mobileMenuOpen,
      'sidebar-collapsed': sidebarCollapsed,
    }"
  >
    <button
      v-if="mobileMenuOpen"
      aria-label="关闭导航菜单"
      class="sidebar-backdrop"
      type="button"
      @click="mobileMenuOpen = false"
    />

    <aside class="app-sidebar">
      <RouterLink class="brand" to="/overview">
        <span class="brand-mark">F</span>
        <span class="brand-label">FlowSync</span>
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
          <MaterialIcon :name="item.icon" />
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <button
        :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
        class="sidebar-toggle"
        type="button"
        @click="sidebarCollapsed = !sidebarCollapsed"
      >
        <MaterialIcon
          :name="sidebarCollapsed ? 'left_panel_open' : 'left_panel_close'"
        />
      </button>
    </aside>

    <section class="app-workspace">
      <header class="app-header">
        <button
          :aria-expanded="mobileMenuOpen"
          aria-label="打开导航菜单"
          class="mobile-nav-toggle"
          type="button"
          @click="mobileMenuOpen = true; sidebarCollapsed = false"
        >
          <MaterialIcon name="menu" />
        </button>

        <div class="header-context">
          <Transition name="banner-title-rise">
            <strong
              v-if="showFloatingPageTitle"
              aria-hidden="true"
              class="banner-page-title"
            >
              {{ floatingPageTitle }}
            </strong>
          </Transition>
        </div>

        <el-dropdown
          v-if="authStore.currentUser"
          trigger="click"
          @command="handleAccountCommand"
        >
          <el-button
            :aria-label="`账户菜单：${authStore.currentUser.displayName}`"
            class="account-trigger"
            text
          >
            <MaterialIcon name="account_circle" :size="24" />
            <span>{{ authStore.currentUser.displayName }}</span>
            <MaterialIcon name="expand_more" />
          </el-button>

          <template #dropdown>
            <el-dropdown-menu class="account-menu">
              <el-dropdown-item disabled>
                <div class="account-summary">
                  <strong>{{ authStore.currentUser.displayName }}</strong>
                  <el-tag
                    :type="
                      authStore.currentUser.systemRole === 'ADMIN'
                        ? 'danger'
                        : 'success'
                    "
                    effect="plain"
                    size="small"
                  >
                    {{ authStore.currentUser.systemRole }}
                  </el-tag>
                </div>
              </el-dropdown-item>
              <el-dropdown-item command="profile">
                <MaterialIcon name="person" />
                个人中心
              </el-dropdown-item>
              <el-dropdown-item command="theme">
                <MaterialIcon
                  :name="themeStore.theme === 'dark' ? 'light_mode' : 'dark_mode'"
                />
                {{ themeMenuLabel }}
              </el-dropdown-item>
              <el-dropdown-item
                command="logout"
                divided
                :disabled="authStore.loading"
              >
                <MaterialIcon name="logout" />
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main
        ref="appMainRef"
        class="app-main"
        @scroll.passive="scheduleFloatingPageTitleUpdate"
      >
        <div class="app-main-content">
          <RouterView />
        </div>
      </main>
    </section>
  </div>
</template>

<style scoped>
.app-layout {
  --sidebar-motion: 220ms cubic-bezier(0.4, 0, 0.2, 1);

  display: grid;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  grid-template-columns: 224px minmax(0, 1fr);
  transition: grid-template-columns var(--sidebar-motion);
}

.app-layout.sidebar-collapsed {
  grid-template-columns: 72px minmax(0, 1fr);
}

.app-sidebar {
  display: flex;
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid var(--fs-color-border, #dbe3ee);
  background: var(--fs-color-surface, #fff);
  overflow-x: hidden;
  overflow-y: hidden;
  transition: transform 0.2s ease;
}

.brand {
  display: flex;
  height: 64px;
  align-items: center;
  gap: 10px;
  padding: 0 15px;
  color: var(--fs-color-text, #1f2937);
  font-size: 18px;
  font-weight: 700;
  text-decoration: none;
  transition: padding var(--sidebar-motion);
}

.brand-mark {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  border-radius: 8px;
  background: var(--fs-color-primary, #2563eb);
  color: var(--fs-color-on-primary, #fff);
  place-items: center;
  transition: width var(--sidebar-motion), height var(--sidebar-motion),
    border-radius var(--sidebar-motion), font-size var(--sidebar-motion);
}

.brand-label {
  display: block;
  max-width: 120px;
  overflow: hidden;
  white-space: nowrap;
  transition: max-width var(--sidebar-motion), opacity 140ms ease,
    transform var(--sidebar-motion);
}

.app-menu {
  width: 100%;
  min-height: 0;
  flex: 1;
  border-right: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.app-menu :deep(.el-menu-item) {
  overflow: hidden;
  transition: padding var(--sidebar-motion), background-color 0.2s ease,
    color 0.2s ease;
}

.app-menu :deep(.el-menu-item > span) {
  white-space: nowrap;
  transition: opacity 0.15s ease, transform 0.2s ease;
}

.sidebar-toggle {
  display: flex;
  min-height: 48px;
  flex: 0 0 48px;
  align-items: center;
  justify-content: flex-start;
  padding: 0;
  padding-left: 20px;
  border: 0;
  border-top: 1px solid var(--fs-color-border, #dbe3ee);
  background: transparent;
  color: var(--fs-color-text-secondary, #64748b);
  cursor: pointer;
  transition: padding var(--sidebar-motion), background-color 0.2s ease,
    color 0.2s ease;
}

.sidebar-toggle:hover {
  background: var(--fs-color-surface-muted, #f8fafc);
  color: var(--fs-color-primary, #2563eb);
}

.sidebar-collapsed .brand {
  padding-right: 0;
  padding-left: 24px;
}

.sidebar-collapsed .sidebar-toggle {
  padding-left: 26px;
}

.sidebar-collapsed .brand-mark {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  font-size: 14px;
}

.sidebar-collapsed .brand-label {
  max-width: 0;
  opacity: 0;
  transform: translateX(-6px);
}

.sidebar-collapsed .app-menu {
  width: 100%;
}

.sidebar-collapsed .app-menu :deep(.el-menu-item) {
  width: 100%;
  padding-right: 0 !important;
  padding-left: 26px !important;
}

.sidebar-collapsed .app-menu :deep(.el-menu-item > span) {
  opacity: 0;
  transform: translateX(-6px);
}


.app-workspace {
  display: grid;
  height: 100vh;
  height: 100dvh;
  min-width: 0;
  min-height: 0;
  background: var(--fs-color-page, #f4f7fb);
  overflow: hidden;
  grid-template-rows: 64px minmax(0, 1fr);
}

.app-header {
  z-index: 10;
  display: flex;
  height: 64px;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid var(--fs-color-border, #dbe3ee);
  background: var(--fs-color-surface, #fff);
}

.mobile-nav-toggle,
.sidebar-backdrop {
  display: none;
}

.header-context {
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.banner-page-title {
  display: block;
  overflow: hidden;
  color: var(--fs-color-text, #1f2937);
  font-size: 16px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-title-rise-enter-active,
.banner-title-rise-leave-active {
  transition: opacity 80ms ease, transform 100ms cubic-bezier(0.4, 0, 0.2, 1);
}

.banner-title-rise-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.banner-title-rise-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.account-trigger {
  display: inline-flex;
  max-width: 240px;
  align-items: center;
  gap: 6px;
}

.account-trigger span:not(.material-icon) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-summary {
  display: flex;
  min-width: 180px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.app-main {
  width: 100%;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  background: var(--fs-color-page, #f4f7fb);
}

.app-main-content {
  width: 100%;
  max-width: 1440px;
  min-height: 100%;
  margin: 0 auto;
  padding: 24px;
}

@media (max-width: 720px) {
  .app-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .app-layout.sidebar-collapsed {
    grid-template-columns: minmax(0, 1fr);
  }

  .app-sidebar {
    position: fixed;
    z-index: 30;
    top: 0;
    bottom: 0;
    left: 0;
    width: min(280px, calc(100vw - 48px));
    min-height: 100dvh;
    box-shadow: 8px 0 24px rgb(15 23 42 / 18%);
    transition: transform 0.2s ease, visibility 0s 0.2s;
    transform: translateX(-100%);
    visibility: hidden;
  }

  .mobile-menu-open .app-sidebar {
    transition: transform 0.2s ease, visibility 0s;
    transform: translateX(0);
    visibility: visible;
  }

  .mobile-nav-toggle {
    display: inline-flex;
    height: 36px;
    align-items: center;
    padding: 0 12px;
    border: 1px solid var(--fs-color-border, #dbe3ee);
    border-radius: 6px;
    background: var(--fs-color-surface, #fff);
    color: var(--fs-color-text, #1f2937);
    cursor: pointer;
  }

  .sidebar-backdrop {
    position: fixed;
    z-index: 20;
    display: block;
    border: 0;
    background: rgb(15 23 42 / 45%);
    inset: 0;
  }

  .sidebar-toggle {
    display: none;
  }

  .app-header,
  .app-main-content {
    padding-right: 16px;
    padding-left: 16px;
  }
}
</style>
