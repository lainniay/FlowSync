import { ref } from 'vue'
import { defineStore } from 'pinia'

import {
  getCurrentUser,
  login as requestLogin,
  logout as requestLogout,
} from '@/shared/api/auth'
import {
  getApiErrorMessage,
  hasApiStatus,
} from '@/shared/api/errors'
import type { LoginRequest, User } from '@/shared/api/types'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<User | null>(null)
  const loading = ref(false)
  const initialized = ref(false)
  const errorMessage = ref('')

  async function loadCurrentUser(): Promise<void> {
    loading.value = true
    errorMessage.value = ''

    try {
      currentUser.value = await getCurrentUser()
    } catch (error) {
      currentUser.value = null

      if (!hasApiStatus(error, 401)) {
        errorMessage.value = getApiErrorMessage(
          error,
          '当前登录状态读取失败',
        )
        console.error('Failed to load current user', error)
      }
    } finally {
      loading.value = false
      initialized.value = true
    }
  }

  async function login(
    credentials: LoginRequest,
  ): Promise<boolean> {
    loading.value = true
    errorMessage.value = ''

    try {
      currentUser.value = await requestLogin(credentials)
      return true
    } catch (error) {
      currentUser.value = null
      errorMessage.value = getApiErrorMessage(
        error,
        '登录失败，请稍后重试',
      )
      return false
    } finally {
      loading.value = false
      initialized.value = true
    }
  }

  async function logout(): Promise<void> {
    loading.value = true
    errorMessage.value = ''

    try {
      await requestLogout()
      currentUser.value = null
    } catch (error) {
      errorMessage.value = getApiErrorMessage(
        error,
        '退出登录失败',
      )
    } finally {
      loading.value = false
      initialized.value = true
    }
  }

  return {
    currentUser,
    loading,
    initialized,
    errorMessage,
    loadCurrentUser,
    login,
    logout,
  }
})