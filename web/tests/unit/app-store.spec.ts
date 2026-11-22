import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAppStore } from '@/stores/app'

const PREFERENCES_KEY = 'sdas.app.preferences'
const SESSION_KEY = 'sdas.session.token'

describe('useAppStore（localStorage 初始状态）', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('模拟刷新：从 localStorage 恢复主题与会话，守卫不误拦截', () => {
    localStorage.setItem(PREFERENCES_KEY, JSON.stringify({ theme: 'dark', sidebarCollapsed: true }))
    localStorage.setItem(SESSION_KEY, 'opaque-token')
    setActivePinia(createPinia())

    const store = useAppStore()

    expect(store.isDark).toBe(true)
    expect(store.sidebarCollapsed).toBe(true)
    expect(store.hasSession).toBe(true)
    // 前端不解析 JWT，只透传不透明 token
    expect(store.sessionToken).toBe('opaque-token')
  })

  it('未知值降级为默认值', () => {
    localStorage.setItem(
      PREFERENCES_KEY,
      JSON.stringify({ theme: 'unknown', sidebarCollapsed: 'yes' }),
    )
    setActivePinia(createPinia())

    const store = useAppStore()

    expect(store.theme).toBe('light')
    expect(store.sidebarCollapsed).toBe(false)
  })

  it('切换主题后持久化到 localStorage', () => {
    const store = useAppStore()
    store.toggleTheme()

    expect(store.isDark).toBe(true)
    expect(JSON.parse(localStorage.getItem(PREFERENCES_KEY) ?? '{}')).toEqual({
      theme: 'dark',
      sidebarCollapsed: false,
    })
  })
})
