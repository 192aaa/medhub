import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { fetchIdentityStatus, submitIdentityVerification } from '@/api/patient-identity'
import { useIdentityVerification } from '@/composables/use-identity-verification'
import { usePatientStore } from '@/stores/patient'
import type { ApiResult, IdentityStatusResponse, IdentityVerificationResponse } from '@/api/types'

vi.mock('@/api/patient-identity', () => ({
  submitIdentityVerification: vi.fn(),
  fetchIdentityStatus: vi.fn(),
  loginPatient: vi.fn(),
  logoutPatient: vi.fn(),
  refreshPatientToken: vi.fn(),
  registerPatient: vi.fn(),
  sendVerificationCode: vi.fn(),
}))

const success: ApiResult<IdentityVerificationResponse> = {
  code: 200,
  message: '成功',
  data: {
    authStatus: 'VERIFIED',
    consultationAllowed: true,
    verifiedAt: '2026-11-07T11:02:10',
    maskedName: '张*',
    maskedIdCard: '110***********002X',
  },
}

const unverified: ApiResult<IdentityStatusResponse> = {
  code: 200,
  message: '成功',
  data: {
    authStatus: 'UNVERIFIED',
    consultationAllowed: false,
    verifiedAt: null,
    maskedName: null,
    maskedIdCard: null,
  },
}

describe('use-identity-verification', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(submitIdentityVerification).mockReset()
    vi.mocked(fetchIdentityStatus).mockReset()
  })

  it('updates store and persisted profile after successful verification', async () => {
    vi.mocked(submitIdentityVerification).mockResolvedValue(success)
    const session = useIdentityVerification()

    await expect(session.submit('张三', '11010519491231002X')).resolves.toBe(true)

    const store = usePatientStore()
    expect(store.authStatus).toBe('VERIFIED')
    expect(store.consultationAllowed).toBe(true)
    expect(store.maskedName).toBe('张*')
    expect(store.maskedIdCard).toBe('110***********002X')
    expect(localStorage.getItem('sdas.patient.profile')).toContain('张*')
  })

  it('shows only the unified backend message on failure', async () => {
    const unified = '账号或密码错误，或验证信息无效'
    vi.mocked(submitIdentityVerification).mockResolvedValue({
      code: 400,
      message: unified,
      data: null,
    })
    const session = useIdentityVerification()

    await expect(session.submit('张三', '11010519491231002X')).resolves.toBe(false)

    expect(session.error.value).toBe(unified)
    expect(session.error.value).not.toContain('白名单')
    expect(session.error.value).not.toContain('剩余')
    expect(session.error.value).not.toContain('REJECTED')
    expect(usePatientStore().authStatus).toBe('UNVERIFIED')
  })

  it('loads endpoint 8 status with three null fields when unverified', async () => {
    vi.mocked(fetchIdentityStatus).mockResolvedValue(unverified)
    const session = useIdentityVerification()

    await expect(session.loadStatus()).resolves.toBe(true)

    expect(session.status.value?.verifiedAt).toBeNull()
    expect(session.status.value?.maskedName).toBeNull()
    expect(session.status.value?.maskedIdCard).toBeNull()
    expect(usePatientStore().consultationAllowed).toBe(false)
  })

  it('uses network fallback text without guessing a reason', async () => {
    vi.mocked(submitIdentityVerification).mockRejectedValue(new Error('offline'))
    const session = useIdentityVerification()

    await expect(session.submit('张三', '11010519491231002X')).resolves.toBe(false)

    expect(session.error.value).toBe('网络异常，请稍后重试')
  })
})
