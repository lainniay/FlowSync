//导航规则工具文件
//登录后能不能安全返回原地址
//当前系统角色能不能进入受限路由

import type { SystemRole } from '@/shared/api/types'

export const DEFAULT_AUTHENTICATED_PATH = '/overview'

export function getSafeRedirect(value: unknown): string {
  const candidate = Array.isArray(value) ? value[0] : value

  if (
    typeof candidate !== 'string'
    || !candidate.startsWith('/')
    || candidate.startsWith('//')
    || candidate.startsWith('/login')
  ) {
    return DEFAULT_AUTHENTICATED_PATH
  }

  return candidate
}

export function hasRequiredRole(
  role: SystemRole,
  allowedRoles?: readonly SystemRole[],
): boolean {
  return !allowedRoles || allowedRoles.includes(role)
}