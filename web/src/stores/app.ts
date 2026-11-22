import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'sdas.app.preferences'
const SESSION_KEY = 'sdas.session.token'

export type ThemeMode = 'light' | 'dark'

interface AppPreferences {
  theme: ThemeMode
  sidebarCollapsed: boolean
}

function readPreferences(): AppPreferences {
  const defaults: AppPreferences = { theme: 'light', sidebarCollapsed: false }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return defaults
    }
    const parsed = JSON.parse(raw) as Partial<AppPreferences>
    return {
      theme: parsed.theme === 'dark' ? 'dark' : 'light',
      sidebarCollapsed: parsed.sidebarCollapsed === true,
    }
  } catch {
    return defaults
  }
}

function readSessionToken(): string | null {
  try {
    return localStorage.getItem(SESSION_KEY)
  } catch {
    return null
  }
}

/**
 * 应用级状态示例：主题、侧栏、会话标记。
 * 宪法 II：store 创建时从 localStorage 读初始状态，防刷新后守卫误拦截。
 */
export const useAppStore = defineStore('app', () => {
  const initial = readPreferences()
  const theme = ref<ThemeMode>(initial.theme)
  const sidebarCollapsed = ref<boolean>(initial.sidebarCollapsed)
  const sessionToken = ref<string | null>(readSessionToken())

  const isDark = computed(() => theme.value === 'dark')
  // 仅判断存在性，不解析 JWT：身份提取由后端 XxxIdExtractor 完成
  const hasSession = computed(() => Boolean(sessionToken.value))

  function persist(): void {
    try {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ theme: theme.value, sidebarCollapsed: sidebarCollapsed.value }),
      )
    } catch {
      // 隐私模式下写入失败时降级为内存态
    }
  }

  function toggleTheme(): void {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    persist()
  }

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
    persist()
  }

  function setSessionToken(token: string | null): void {
    sessionToken.value = token
    try {
      if (token) {
        localStorage.setItem(SESSION_KEY, token)
      } else {
        localStorage.removeItem(SESSION_KEY)
      }
    } catch {
      // 隐私模式下写入失败时降级为内存态
    }
  }

  return {
    theme,
    sidebarCollapsed,
    sessionToken,
    isDark,
    hasSession,
    toggleTheme,
    toggleSidebar,
    setSessionToken,
  }
})
