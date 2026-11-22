import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { PharmacyLoginResponse } from '@/api/types'

/** pharmacy-request.ts 从该键读取并注入 Authorization: Bearer */
const TOKEN_KEY = 'sdas.pharmacy.token'
const PROFILE_KEY = 'sdas.pharmacy.profile'

interface PersistedProfile {
  pharmacyId: number | null
  nameMask: string
  pharmacyName: string
}

/**
 * 药店会话 store。宪法 II：store 创建时即从 localStorage 恢复，防刷新后守卫误拦截。
 */
export const usePharmacyStore = defineStore('pharmacy', () => {
  const accessToken = ref<string | null>(null)
  const pharmacyId = ref<number | null>(null)
  const nameMask = ref('')
  const pharmacyName = ref('')

  function restore(): void {
    try {
      accessToken.value = localStorage.getItem(TOKEN_KEY)
      const raw = localStorage.getItem(PROFILE_KEY)
      if (raw) {
        const saved = JSON.parse(raw) as Partial<PersistedProfile>
        pharmacyId.value = saved.pharmacyId ?? null
        nameMask.value = saved.nameMask ?? ''
        pharmacyName.value = saved.pharmacyName ?? ''
      }
    } catch {
      // 隐私模式下读取失败时保持内存默认值
    }
  }

  function persist(): void {
    try {
      if (accessToken.value) {
        localStorage.setItem(TOKEN_KEY, accessToken.value)
      } else {
        localStorage.removeItem(TOKEN_KEY)
      }
      const profile: PersistedProfile = {
        pharmacyId: pharmacyId.value,
        nameMask: nameMask.value,
        pharmacyName: pharmacyName.value,
      }
      localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
    } catch {
      // 隐私模式下写入失败时降级为内存态
    }
  }

  /** 登录成功后写入会话 */
  function applyLogin(result: PharmacyLoginResponse): void {
    accessToken.value = result.accessToken
    pharmacyId.value = result.pharmacyId
    nameMask.value = result.nameMask
    pharmacyName.value = result.pharmacyName
    persist()
  }

  function clear(): void {
    accessToken.value = null
    pharmacyId.value = null
    nameMask.value = ''
    pharmacyName.value = ''
    persist()
    try {
      localStorage.removeItem(PROFILE_KEY)
    } catch {
      // 内存态已清理
    }
  }

  const hasSession = computed(() => Boolean(accessToken.value))

  restore()

  return {
    accessToken,
    pharmacyId,
    nameMask,
    pharmacyName,
    hasSession,
    applyLogin,
    clear,
  }
})
