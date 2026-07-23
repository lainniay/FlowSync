import { ref } from 'vue'
import { defineStore } from 'pinia'

import {
  applyThemeToDocument,
  resolveInitialTheme,
  type ThemeId,
} from '@/shared/theme'

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<ThemeId>('light')

  function setTheme(next: ThemeId): void {
    theme.value = next
    applyThemeToDocument(next)
  }

  function toggleTheme(): void {
    setTheme(theme.value === 'dark' ? 'light' : 'dark')
  }

  function initialize(): void {
    setTheme(resolveInitialTheme())
  }

  return {
    theme,
    initialize,
    setTheme,
    toggleTheme,
  }
})
