import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import HomeView from '../views/HomeView.vue'

describe('HomeView', () => {
  it('shows the product name', () => {
    const wrapper = mount(HomeView)

    expect(wrapper.get('h1').text()).toBe('FlowSync')
  })
})
