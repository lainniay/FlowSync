import { describe, expect, it, vi, beforeEach } from 'vitest'

import {
  applyThemeToDocument,
  readStoredTheme,
  resolveInitialTheme,
} from '@/shared/theme'

describe('theme utilities', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    delete document.documentElement.dataset.theme
    vi.unstubAllGlobals()
  })

  it('applies light theme to the document root', () => {
    applyThemeToDocument('light')

    expect(document.documentElement.dataset.theme).toBe('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(readStoredTheme()).toBe('light')
  })

  it('applies dark theme to the document root', () => {
    applyThemeToDocument('dark')

    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(readStoredTheme()).toBe('dark')
  })

  it('restores stored theme when available', () => {
    localStorage.setItem('flowsync-theme', 'dark')

    expect(resolveInitialTheme()).toBe('dark')
  })

  it('falls back to light when nothing is stored and system is light', () => {
    vi.stubGlobal('matchMedia', vi.fn<(query: string) => MediaQueryList>().mockImplementation(() => ({
      matches: false,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      addListener: vi.fn<() => void>(),
      removeListener: vi.fn<() => void>(),
      addEventListener: vi.fn<() => void>(),
      removeEventListener: vi.fn<() => void>(),
      dispatchEvent: vi.fn<() => boolean>(),
    })))

    expect(resolveInitialTheme()).toBe('light')
  })
})
