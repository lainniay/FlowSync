import { formatDateTime } from '@/shared/format'

export function formatRelativeTime(value: string): string {
  const elapsed = Date.now() - new Date(value).getTime()
  if (elapsed < 0) return formatDateTime(value)
  const minutes = Math.floor(elapsed / 60_000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  return days < 7 ? `${days} 天前` : formatDateTime(value)
}
