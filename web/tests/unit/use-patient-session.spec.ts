import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosHeaders, type AxiosAdapter, type AxiosResponse } from 'axios'
import { loginPatient, logoutPatient } from '@/api/patient-identity'
import { patientRequest } from '@/api/request/patient-request'
import { usePatientSession } from '@/composables/use-patient-session'
import { usePatientStore } from '@/stores/patient'
import type { ApiResult, PatientLoginResponse } from '@/api/types'

vi.mock('@/api/patient-identity', () => ({
  loginPatient: vi.fn(),
  logoutPatient: vi.fn(),
  refreshPatientToken: vi.fn(),
  registerPatient: vi.fn(),
  sendVerificationCode: vi.fn(),
}))

const loginSuccess: ApiResult<PatientLoginResponse> = {
  code: 200,
  message: '成功',
  data: {
    patientId: 10001,
    maskedPhone: '138****8000',
    authStatus: 'UNVERIFIED',
    consultationAllowed: false,
    accessToken: 'jwt-token',
    accessTokenExpiresInSeconds: 7200,
  },
}

function okResponse(
  config: Parameters<AxiosAdapter>[0],
  status: number,
  data: unknown,
): AxiosResponse {
  return { data, status, statusText: '', headers: new AxiosHeaders(), config }
}

describe('use-patient-session', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(loginPatient).mockReset()
    vi.mocked(logoutPatient).mockReset()
  })

  it('persists token and profile after login', async () => {
    vi.mocked(loginPatient).mockResolvedValue(loginSuccess)
    const session = usePatientSession()

    await expect(session.login('13800138000', 'abc12345')).resolves.toBe(true)
    expect(localStorage.getItem('sdas.patient.token')).toBe('jwt-token')
    expect(usePatientStore().hasSession).toBe(true)
  })

  it('exposes backend message verbatim on failure', async () => {
    const backendMessage = '账号或密码错误，或验证信息无效'
    vi.mocked(loginPatient).mockResolvedValue({ code: 400, message: backendMessage, data: null })
    const session = usePatientSession()

    await expect(session.login('13800138000', 'zzz99999')).resolves.toBe(false)
    expect(session.error.value).toBe(backendMessage)
    expect(localStorage.getItem('sdas.patient.token')).toBeNull()
  })

  it('clears store and localStorage on logout', async () => {
    vi.mocked(loginPatient).mockResolvedValue(loginSuccess)
    vi.mocked(logoutPatient).mockResolvedValue({ code: 200, message: '成功', data: null })
    const session = usePatientSession()
    await session.login('13800138000', 'abc12345')

    await session.logout()

    expect(localStorage.getItem('sdas.patient.token')).toBeNull()
    expect(localStorage.getItem('sdas.patient.profile')).toBeNull()
    expect(usePatientStore().hasSession).toBe(false)
  })

  it('renews only once for concurrent 401 responses', async () => {
    localStorage.setItem('sdas.patient.token', 'stale-token')
    let refreshCount = 0
    const adapter: AxiosAdapter = async (config) => {
      if (String(config.url).includes('refresh-token')) {
        refreshCount += 1
        return okResponse(config, 200, {
          code: 200,
          message: '成功',
          data: { accessToken: 'fresh-token', accessTokenExpiresInSeconds: 7200 },
        })
      }
      const authorized = config.headers.get('Authorization') === 'Bearer fresh-token'
      return authorized
        ? okResponse(config, 200, { code: 200, message: '成功', data: {} })
        : okResponse(config, 401, { code: 401, message: '未认证或凭证已过期', data: null })
    }
    patientRequest.defaults.adapter = adapter

    const responses = await Promise.all(
      [0, 1, 2].map(() => patientRequest.get('/v1/patients/current/identity-status')),
    )

    expect(refreshCount).toBe(1)
    expect(responses.map((item) => item.status)).toEqual([200, 200, 200])
    expect(localStorage.getItem('sdas.patient.token')).toBe('fresh-token')
  })
})
