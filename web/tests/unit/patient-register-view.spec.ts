import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import PatientRegisterView from '@/views/patient-register-view.vue'
import { registerPatient, sendVerificationCode } from '@/api/patient-identity'
import type { ApiResult, PatientRegisterResponse } from '@/api/types'

vi.mock('@/api/patient-identity', () => ({
  sendVerificationCode: vi.fn(),
  registerPatient: vi.fn(),
}))

const success: ApiResult<PatientRegisterResponse> = {
  code: 200,
  message: '成功',
  data: {
    patientId: 10001,
    maskedPhone: '138****8000',
    authStatus: 'UNVERIFIED',
    consultationAllowed: false,
    accessToken: 'jwt-token',
    accessTokenExpiresInSeconds: 7200,
    registeredAt: '2026-11-07T10:15:30',
    agreementVersion: 'v1.0',
  },
}

function createWrapper() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/patient/register', component: PatientRegisterView },
      { path: '/patient/profile', component: { template: '<div>profile</div>' } },
    ],
  })
  return mount(PatientRegisterView, { global: { plugins: [createPinia(), router] } })
}

async function fillValidForm(wrapper: ReturnType<typeof createWrapper>): Promise<void> {
  await wrapper.find('#register-phone').setValue('13800138000')
  await wrapper.find('#verification-code').setValue('888888')
  await wrapper.find('#register-password').setValue('abc12345')
  await wrapper.find('#agreement-accepted').setValue(true)
}

describe('patient-register-view', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(registerPatient).mockReset()
    vi.mocked(sendVerificationCode).mockReset()
  })

  it('disables submit while agreement is not accepted', async () => {
    const wrapper = createWrapper()
    await wrapper.find('#register-phone').setValue('13800138000')
    await wrapper.find('#verification-code').setValue('888888')
    await wrapper.find('#register-password').setValue('abc12345')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(vi.mocked(registerPatient)).not.toHaveBeenCalled()
  })

  it('disables submit for a password without digits', async () => {
    const wrapper = createWrapper()
    await wrapper.find('#register-phone').setValue('13800138000')
    await wrapper.find('#verification-code').setValue('888888')
    await wrapper.find('#register-password').setValue('abcdefgh')
    await wrapper.find('#agreement-accepted').setValue(true)
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('shows backend message on failure and never hardcodes it', async () => {
    const backendMessage = '账号或密码错误，或验证信息无效'
    vi.mocked(registerPatient).mockResolvedValue({
      code: 400,
      message: backendMessage,
      data: null,
    })
    const wrapper = createWrapper()
    await fillValidForm(wrapper)
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper.find('[data-testid="form-error"]').exists()).toBe(true))
    expect(wrapper.find('[data-testid="form-error"]').text()).toBe(backendMessage)
  })

  it('stores session and navigates on success', async () => {
    vi.mocked(registerPatient).mockResolvedValue(success)
    const wrapper = createWrapper()
    await fillValidForm(wrapper)
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(localStorage.getItem('sdas.patient.token')).toBe('jwt-token'))
  })
})
