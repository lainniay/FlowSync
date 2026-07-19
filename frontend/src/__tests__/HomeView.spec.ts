import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import HomeView from '../views/HomeView.vue'

describe('HomeView', () => {
  it('shows the workspace heading', () => {
    const wrapper = mount(HomeView, {
      global: {
        plugins: [createPinia()],
      },
    })

    expect(wrapper.get('h1').text()).toBe('工作台')
  })
})