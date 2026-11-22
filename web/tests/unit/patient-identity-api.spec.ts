import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  fetchIdentityStatus,
  loginPatient,
  logoutPatient,
  refreshPatientToken,
  registerPatient,
  resetPassword,
  sendVerificationCode,
  submitIdentityVerification,
} from '@/api/patient-identity'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  get: vi.fn(),
  del: vi.fn(),
  put: vi.fn(),
  csrfHeaders: vi.fn(),
}))

vi.mock('@/api/request/patient-request', () => ({
  patientRequest: {
    post: mocks.post,
    get: mocks.get,
    delete: mocks.del,
    put: mocks.put,
  },
}))

vi.mock('@/api/request/csrf', () => ({ csrfHeaders: mocks.csrfHeaders }))

const CSRF = { 'X-CSRF-Token': 'csrf-value' }

/** 断言修改类请求携带双重 Cookie CSRF 头，且路径不带重复的 /api 前缀 */
function expectPost(path: string, payload: unknown): void {
  expect(mocks.post).toHaveBeenCalledWith(path, payload, { headers: CSRF })
  expect(path.startsWith('/api')).toBe(false)
}

describe('patient-identity API', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    // 每次返回新对象，避免 registerPatient 写入幂等键后污染其他用例
    mocks.csrfHeaders.mockImplementation(() => ({ ...CSRF }))
    for (const fn of [mocks.post, mocks.get, mocks.del, mocks.put]) {
      fn.mockResolvedValue({ data: { code: 200, message: '成功', data: null } })
    }
  })

  it('endpoint 1 sends verification code with CSRF header', async () => {
    const payload = { phone: '13800138000', purpose: 'REGISTER' as const }
    const result = await sendVerificationCode(payload)

    expectPost('/v1/verification-codes', payload)
    expect(result.code).toBe(200)
    expect(result.data).toBeNull()
  })

  it('endpoint 2 registers with CSRF and optional idempotency key', async () => {
    const payload = {
      phone: '13800138000',
      verificationCode: '888888',
      password: 'abc12345',
      agreementAccepted: true,
      agreementVersion: 'v1.0',
    }

    await registerPatient(payload)
    expectPost('/v1/patients', payload)
    expect(mocks.post.mock.calls[0][2].headers).not.toHaveProperty('X-Idempotency-Key')

    await registerPatient(payload, 'uuid-1234')
    expect(mocks.post.mock.calls[1][2].headers['X-Idempotency-Key']).toBe('uuid-1234')
  })

  it('endpoint 3 logs in with CSRF header', async () => {
    const payload = { phone: '13800138000', password: 'abc12345' }
    await loginPatient(payload)

    expectPost('/v1/patient-sessions', payload)
  })

  it('endpoint 4 logs out from current session', async () => {
    const result = await logoutPatient()

    expect(mocks.del).toHaveBeenCalledWith('/v1/patient-sessions/current', { headers: CSRF })
    expect(result.data).toBeNull()
  })

  it('endpoint 5 refreshes token with a null body', async () => {
    await refreshPatientToken()

    expect(mocks.put).toHaveBeenCalledWith('/v1/patient-sessions/current/refresh-token', null, {
      headers: CSRF,
    })
  })

  it('endpoint 6 resets password with CSRF header', async () => {
    const payload = {
      phone: '13800138000',
      verificationCode: '888888',
      newPassword: 'xyz98765',
    }
    const result = await resetPassword(payload)

    expectPost('/v1/password-resets', payload)
    expect(result.data).toBeNull()
  })

  it('endpoint 7 submits identity verification with CSRF header', async () => {
    const payload = { realName: '张三', idCardNumber: '11010519491231002X' }
    await submitIdentityVerification(payload)

    expectPost('/v1/identity-verifications', payload)
  })

  it('endpoint 8 reads status without CSRF or /api duplication', async () => {
    const result = await fetchIdentityStatus()

    expect(mocks.get).toHaveBeenCalledWith('/v1/patients/current/identity-status')
    expect(mocks.get.mock.calls[0][1]).toBeUndefined()
    expect(result.code).toBe(200)
  })
})
