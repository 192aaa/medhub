import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { sendVerificationCode } from '@/api/patient-identity'
import { useVerificationCode } from '@/composables/use-verification-code'
import type { ApiResult, VerificationCodeSendResponse } from '@/api/types'

vi.mock('@/api/patient-identity', () => ({ sendVerificationCode: vi.fn() }))

const successResponse: ApiResult<VerificationCodeSendResponse> = {
  code: 200,
  message: '成功',
  data: {
    purpose: 'REGISTER',
    maskedPhone: '138****8000',
    expiresInSeconds: 300,
    nextSendAllowedInSeconds: 60,
  },
}

describe('useVerificationCode', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.mocked(sendVerificationCode).mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('starts a 60 second countdown on success', async () => {
    vi.mocked(sendVerificationCode).mockResolvedValue(successResponse)
    const state = useVerificationCode('REGISTER')
    expect(await state.send('13800138000')).toBe(true)
    expect(state.secondsLeft.value).toBe(60)
    vi.advanceTimersByTime(1000)
    expect(state.secondsLeft.value).toBe(59)
    expect(state.error.value).toBe('')
  })

  it('shows the backend RATE_LIMITED message', async () => {
    vi.mocked(sendVerificationCode).mockResolvedValue({
      code: 429,
      message: '操作过于频繁，请稍后重试',
      data: null,
    })
    const state = useVerificationCode('REGISTER')
    expect(await state.send('13800138000')).toBe(false)
    expect(state.error.value).toBe('操作过于频繁，请稍后重试')
    expect(state.secondsLeft.value).toBe(0)
  })

  it('never reads a code field from response data', async () => {
    const leaked = {
      code: 200,
      message: '成功',
      data: { ...successResponse.data, verificationCode: '888888' },
    } as unknown as ApiResult<VerificationCodeSendResponse>
    vi.mocked(sendVerificationCode).mockResolvedValue(leaked)
    const state = useVerificationCode('REGISTER')
    expect(await state.send('13800138000')).toBe(true)
    expect(state.error.value).toBe('')
    expect(JSON.stringify(state)).not.toContain('888888')
  })
})
