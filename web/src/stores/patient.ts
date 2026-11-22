import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  AuthStatus,
  IdentityStatusResponse,
  IdentityVerificationResponse,
  PatientLoginResponse,
  PatientRegisterResponse,
} from '@/api/types'

/** create-request.ts 从该键读取并注入 Authorization: Bearer */
const TOKEN_KEY = 'sdas.patient.token'
const PROFILE_KEY = 'sdas.patient.profile'

interface PersistedProfile {
  patientId: number | null
  authStatus: AuthStatus
  consultationAllowed: boolean
  maskedPhone: string
  maskedName: string | null
  maskedIdCard: string | null
  verifiedAt: string | null
}

/**
 * 患者会话 store。宪法 II：store 创建时即从 localStorage 恢复，防刷新后守卫误拦截。
 */
export const usePatientStore = defineStore('patient', () => {
  const accessToken = ref<string | null>(null)
  const authStatus = ref<AuthStatus>('UNVERIFIED')
  const consultationAllowed = ref(false)
  const maskedPhone = ref('')
  const patientId = ref<number | null>(null)
  const maskedName = ref<string | null>(null)
  const maskedIdCard = ref<string | null>(null)
  const verifiedAt = ref<string | null>(null)

  function restore(): void {
    try {
      accessToken.value = localStorage.getItem(TOKEN_KEY)
      const raw = localStorage.getItem(PROFILE_KEY)
      if (raw) {
        const saved = JSON.parse(raw) as Partial<PersistedProfile>
        patientId.value = saved.patientId ?? null
        authStatus.value = saved.authStatus === 'VERIFIED' ? 'VERIFIED' : 'UNVERIFIED'
        consultationAllowed.value = saved.consultationAllowed === true
        maskedPhone.value = saved.maskedPhone ?? ''
        maskedName.value = saved.maskedName ?? null
        maskedIdCard.value = saved.maskedIdCard ?? null
        verifiedAt.value = saved.verifiedAt ?? null
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
        patientId: patientId.value,
        authStatus: authStatus.value,
        consultationAllowed: consultationAllowed.value,
        maskedPhone: maskedPhone.value,
        maskedName: maskedName.value,
        maskedIdCard: maskedIdCard.value,
        verifiedAt: verifiedAt.value,
      }
      localStorage.setItem(PROFILE_KEY, JSON.stringify(profile))
    } catch {
      // 隐私模式下写入失败时降级为内存态
    }
  }

  /** 注册 / 登录成功后统一写入会话与认证状态（两端响应的会话字段同构） */
  function applyRegisterResult(result: PatientRegisterResponse | PatientLoginResponse): void {
    accessToken.value = result.accessToken
    patientId.value = result.patientId
    authStatus.value = result.authStatus
    consultationAllowed.value = result.consultationAllowed
    maskedPhone.value = result.maskedPhone
    persist()
  }

  /** 端点 7/8 成功后统一同步认证状态；门禁判定只依赖这两个字段 */
  function applyIdentityStatus(
    result: IdentityStatusResponse | IdentityVerificationResponse,
  ): void {
    authStatus.value = result.authStatus
    consultationAllowed.value = result.consultationAllowed
    maskedName.value = result.maskedName
    maskedIdCard.value = result.maskedIdCard
    verifiedAt.value = result.verifiedAt
    persist()
  }

  /** 续期成功后原地替换 access token，其余会话字段不变 */
  function setAccessToken(token: string): void {
    accessToken.value = token
    persist()
  }

  /** 安全退出或续期失败时清理 */
  function clear(): void {
    accessToken.value = null
    authStatus.value = 'UNVERIFIED'
    consultationAllowed.value = false
    maskedPhone.value = ''
    patientId.value = null
    maskedName.value = null
    maskedIdCard.value = null
    verifiedAt.value = null
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
    authStatus,
    consultationAllowed,
    maskedPhone,
    patientId,
    maskedName,
    maskedIdCard,
    verifiedAt,
    hasSession,
    applyRegisterResult,
    applyIdentityStatus,
    setAccessToken,
    clear,
  }
})
