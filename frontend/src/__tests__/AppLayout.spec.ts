import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AppLayout from '@/layouts/AppLayout.vue'

const authState = vi.hoisted(() => ({
  currentUser: {
    id: '2',
    username: 'zhangsan',
    displayName: '张三',
    systemRole: 'USER' as const,
  },
  errorMessage: '',
  loading: false,
  logout: vi.fn<() => Promise<boolean>>(),
}))

const routerState = vi.hoisted(() => ({
  push: vi.fn<() => Promise<void>>(),
  replace: vi.fn<() => Promise<void>>(),
}))

const themeState = vi.hoisted(() => ({
  theme: 'light',
  toggleTheme: vi.fn<() => void>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}))

vi.mock('@/stores/theme', () => ({
  useThemeStore: () => themeState,
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/overview' }),
  useRouter: () => routerState,
}))

beforeEach(() => {
  authState.logout.mockReset()
  routerState.push.mockReset()
  routerState.replace.mockReset()
  themeState.toggleTheme.mockReset()
})

describe('AppLayout', () => {
  it('raises the page title into the banner after it scrolls past', async () => {
    const wrapper = mount(AppLayout, {
      global: {
        stubs: {
          ElDropdown: true,
          RouterLink: { template: '<a><slot /></a>' },
          RouterView: {
            template: '<section><header class="page-header"><h1>我的任务</h1></header></section>',
          },
        },
      },
    })
    const main = wrapper.get('.app-main').element as HTMLElement
    const title = wrapper.get('.page-header h1').element as HTMLElement
    vi.spyOn(main, 'getBoundingClientRect').mockReturnValue({
      top: 64,
    } as DOMRect)
    vi.spyOn(title, 'getBoundingClientRect').mockReturnValue({
      top: 40,
      bottom: 72,
      height: 32,
    } as DOMRect)

    await wrapper.get('.app-main').trigger('scroll')
    await vi.waitFor(() => {
      expect(wrapper.get('.banner-page-title').text()).toBe('我的任务')
    })

    vi.mocked(title.getBoundingClientRect).mockReturnValue({
      top: 80,
      bottom: 120,
      height: 32,
    } as DOMRect)
    await wrapper.get('.app-main').trigger('scroll')
    await vi.waitFor(() => {
      expect(wrapper.find('.banner-page-title').exists()).toBe(false)
    })
    expect(wrapper.get('.header-context').text()).toBe('')
  })

  it('keeps the header outside the independently scrolling content', () => {
    const wrapper = shallowMount(AppLayout)
    const workspace = wrapper.get('.app-workspace')

    expect(workspace.get('.app-header').element.nextElementSibling)
      .toBe(workspace.get('.app-main').element)
    expect(workspace.find('.app-main-content').exists()).toBe(true)
  })

  it('moves profile, theme, and logout into the username menu', () => {
    const wrapper = shallowMount(AppLayout, {
      global: {
        stubs: {
          ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
          ElDropdownItem: { template: '<div><slot /></div>' },
          ElDropdownMenu: { template: '<div><slot /></div>' },
          ElMenu: { template: '<nav><slot /></nav>' },
          ElMenuItem: { template: '<div><slot /></div>' },
          RouterLink: true,
          RouterView: true,
        },
      },
    })

    expect(wrapper.find('[aria-label="账户菜单：张三"]').exists())
      .toBe(true)
    expect(wrapper.text()).toContain('个人中心')
    expect(wrapper.text()).toContain('切换到深色主题')
    expect(wrapper.text()).toContain('退出登录')
    expect(wrapper.text().match(/个人中心/g)).toHaveLength(1)
  })

  it('handles account menu commands', async () => {
    authState.logout.mockResolvedValue(true)
    const wrapper = shallowMount(AppLayout, {
      global: {
        stubs: {
          ElDropdown: {
            template: `<div>
              <slot />
              <button data-command="profile" @click="$emit('command', 'profile')" />
              <button data-command="theme" @click="$emit('command', 'theme')" />
              <button data-command="logout" @click="$emit('command', 'logout')" />
            </div>`,
          },
          RouterLink: true,
          RouterView: true,
        },
      },
    })

    await wrapper.get('[data-command="profile"]').trigger('click')
    await wrapper.get('[data-command="theme"]').trigger('click')
    await wrapper.get('[data-command="logout"]').trigger('click')
    await flushPromises()

    expect(routerState.push).toHaveBeenCalledWith({ name: 'profile' })
    expect(themeState.toggleTheme).toHaveBeenCalledOnce()
    expect(authState.logout).toHaveBeenCalledOnce()
    expect(routerState.replace).toHaveBeenCalledWith({ name: 'login' })
  })

  it('opens and closes the mobile navigation overlay', async () => {
    const wrapper = shallowMount(AppLayout, {
      global: {
        stubs: {
          RouterLink: true,
          RouterView: true,
        },
      },
    })

    await wrapper.get('[aria-label="打开导航菜单"]').trigger('click')

    expect(wrapper.classes()).toContain('mobile-menu-open')
    expect(wrapper.find('[aria-label="关闭导航菜单"]').exists()).toBe(true)

    await wrapper.get('[aria-label="关闭导航菜单"]').trigger('click')

    expect(wrapper.classes()).not.toContain('mobile-menu-open')
    expect(wrapper.find('[aria-label="关闭导航菜单"]').exists()).toBe(false)
  })

  it('collapses the sidebar while keeping navigation icons available', async () => {
    const wrapper = mount(AppLayout, {
      global: {
        stubs: {
          ElDropdown: true,
          RouterLink: { template: '<a><slot /></a>' },
          RouterView: true,
        },
      },
    })

    expect(wrapper.classes()).not.toContain('sidebar-collapsed')
    await wrapper.get('[aria-label="收起侧边栏"]').trigger('click')

    expect(wrapper.classes()).toContain('sidebar-collapsed')
    expect(wrapper.find('.el-menu--collapse').exists()).toBe(false)
    const menuItem = wrapper.get('.el-menu-item')
    const icon = menuItem.get('.material-icon')
    expect(icon.element.parentElement).toBe(menuItem.element)
    expect(wrapper.find('.el-menu-tooltip__trigger').exists()).toBe(false)
    expect(wrapper.find('[aria-label="展开侧边栏"]').exists()).toBe(true)
  })
})
