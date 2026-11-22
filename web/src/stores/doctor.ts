import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { DoctorLoginResponse } from '@/api/types'

/** create-request.ts 从该键读取并注入 Authorization: Bearer */
const TOKEN_KEY = 'sdas.doctor.token'
const PROFILE_KEY = 'sdas.doctor.profile'

interface PersistedProfile {
  doctorId: number | null
  nameMask: string
  title: string | null
  departmentId: number | null
}

/**
 * 医生会话 store。宪法 II：store 创建时即从 localStorage 恢复，防刷新后守卫误拦截。
 */
export const useDoctorStore = defineStore('doctor', () => {
  const accessToken = ref<string | null>(null)
  const doctorId = ref<number | null>(null)
  const nameMask = ref('')
  const title = ref<string | null>(null)
  const departmentId = ref<number | null>(null)
  const currentConsultationId = ref<number | null>(null)

  function restore(): void {
    try {
      accessToken.value = localStorage.getItem(TOKEN_KEY)
      const raw = localStorage.getItem(PROFILE_KEY)
      if (raw) {
        const saved = JSON.parse(raw) as Partial<PersistedProfile>
        doctorId.value = saved.doctorId ?? null
        nameMask.value = saved.nameMask ?? ''
        title.value = saved.title ?? null
        departmentId.value = saved.departmentId ?? null
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
        doctorId: doctorId.value,
        nameMask: nameMask.value,
        title: title.value,
        departmentId: departmentId.value,
      }
      localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
    } catch {
      // 隐私模式下写入失败时降级为内存态
    }
  }

  /** 登录成功后写入会话 */
  function applyLogin(result: DoctorLoginResponse): void {
    accessToken.value = result.accessToken
    doctorId.value = result.doctorId
    nameMask.value = result.nameMask
    title.value = result.title
    departmentId.value = result.departmentId
    persist()
  }

  function setCurrentConsultation(consultationId: number | null): void {
    currentConsultationId.value = consultationId
  }

  function clear(): void {
    accessToken.value = null
    doctorId.value = null
    nameMask.value = ''
    title.value = null
    departmentId.value = null
    currentConsultationId.value = null
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
    doctorId,
    nameMask,
    title,
    departmentId,
    currentConsultationId,
    hasSession,
    applyLogin,
    setCurrentConsultation,
    clear,
  }
})
