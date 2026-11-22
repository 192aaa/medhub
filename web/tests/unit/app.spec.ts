import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '@/App.vue'
import { useAppStore } from '@/stores/app'

describe('App shell', () => {
  it('renders navigation and toggles theme through the app store', async () => {
    localStorage.clear()
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div>home</div>' } }],
    })
    await router.push('/')
    await router.isReady()

    const wrapper = mount(App, { global: { plugins: [pinia, router] } })
    const store = useAppStore()

    expect(wrapper.find('.app-brand').exists()).toBe(true)
    expect(wrapper.find('.app-nav').exists()).toBe(true)
    expect(store.isDark).toBe(false)

    await wrapper.get('button.app-theme').trigger('click')
    expect(store.isDark).toBe(true)
  })
})
