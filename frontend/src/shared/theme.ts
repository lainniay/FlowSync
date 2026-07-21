export type ThemeId = 'light' | 'dark'

export type ThemeOption = {
  readonly id: ThemeId
  readonly label: string
  readonly description: string
}

export const themeOptions: readonly ThemeOption[] = [
  {
    id: 'light',
    label: '浅色模式',
    description: '适合白天与明亮环境',
  },
  {
    id: 'dark',
    label: '深色模式',
    description: '适合夜间与弱光环境',
  },
]

const STORAGE_KEY = 'flowsync-theme'

function isThemeId(value: string | null): value is ThemeId {
  return value === 'light' || value === 'dark'
}

export function readStoredTheme(): ThemeId | null {
  const stored = localStorage.getItem(STORAGE_KEY)
  return isThemeId(stored) ? stored : null
}

export function applyThemeToDocument(theme: ThemeId): void {
  document.documentElement.dataset.theme = theme
  document.documentElement.classList.toggle('dark', theme === 'dark')
  localStorage.setItem(STORAGE_KEY, theme)
}

export function resolveInitialTheme(): ThemeId {
  const stored = readStoredTheme()
  if (stored) return stored

  if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark'
  }

  return 'light'
}
