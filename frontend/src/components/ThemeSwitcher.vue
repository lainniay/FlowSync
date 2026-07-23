<script setup lang="ts">
import { computed } from 'vue'
import { Moon, Sunny } from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
import 'element-plus/es/components/icon/style/css'

import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

const isDark = computed(() => themeStore.theme === 'dark')

const switchTitle = computed(() => (
  isDark.value ? '切换到浅色主题' : '切换到深色主题'
))

function toggleTheme(): void {
  themeStore.toggleTheme()
}
</script>

<template>
  <button
    type="button"
    role="switch"
    class="theme-switch"
    :aria-checked="isDark"
    :aria-label="switchTitle"
    :class="{ 'is-dark': isDark }"
    :title="switchTitle"
    @click="toggleTheme"
  >
    <span class="check">
      <span class="icon">
        <el-icon class="sun" :size="12">
          <Sunny />
        </el-icon>
        <el-icon class="moon" :size="12">
          <Moon />
        </el-icon>
      </span>
    </span>
  </button>
</template>

<style scoped>
.theme-switch {
  position: relative;
  display: block;
  width: 40px;
  height: 22px;
  flex-shrink: 0;
  padding: 0;
  border: 1px solid var(--fs-switch-border, var(--fs-color-border, #dbe3ee));
  border-radius: 11px;
  background-color: var(--fs-switch-bg, var(--fs-color-surface-muted, #f8fafc));
  cursor: pointer;
  transition: border-color 0.25s;
}

.theme-switch:hover {
  border-color: var(--fs-color-primary, #2563eb);
}

.check {
  position: absolute;
  top: 1px;
  left: 1px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background-color: var(--fs-switch-thumb, #ffffff);
  box-shadow: var(--fs-switch-shadow, 0 1px 2px rgba(0, 0, 0, 0.06));
  transition: transform 0.25s;
}

.icon {
  position: relative;
  display: block;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  overflow: hidden;
}

.icon :deep(.el-icon) {
  position: absolute;
  top: 3px;
  left: 3px;
  color: var(--fs-switch-icon, var(--fs-color-text-secondary, #64748b));
  transition: opacity 0.25s, color 0.25s;
}

.sun {
  opacity: 1;
}

.moon {
  opacity: 0;
}

.theme-switch.is-dark .check {
  transform: translateX(18px);
}

.theme-switch.is-dark .sun {
  opacity: 0;
}

.theme-switch.is-dark .moon {
  opacity: 1;
}
</style>
