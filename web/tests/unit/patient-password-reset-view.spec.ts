import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import PatientPasswordResetView from '@/views/patient-password-reset-view.vue'
import { resetPassword, sendVerificationCode } from '@/api/patient-identity'
import type { ApiResult, PasswordResetResponse } from '@/api/types'

vi.mock('@/api/patient-identity', () => ({
  resetPassword: vi.fn(),
  sendVerificationCode: vi.fn(),
}))

function createWrapper() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/patient/password-reset', component: PatientPasswordResetView },
      { path: '/patient/login', component: { template: '<div>login</div>' } },
    ],
  })
  return mount(PatientPasswordResetView, { global: { plugins: [createPinia(), router] } })
}

async function fillValidForm(wrapper: ReturnType<typeof createWrapper>): Promise<void> {
  await wrapper.find('#reset-phone').setValue('13800138000')
  await wrapper.find('#verification-code').setValue('888888')
  await wrapper.find('#reset-password').setValue('abc12345')
}

describe('patient-password-reset-view', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(resetPassword).mockReset()
    vi.mocked(sendVerificationCode).mockReset()
  })

  it('disables submit for a password without digits', async () => {
    const wrapper = createWrapper()
    await wrapper.find('#reset-phone').setValue('13800138000')
    await wrapper.find('#verification-code').setValue('888888')
    await wrapper.find('#reset-password').setValue('abcdefgh')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(vi.mocked(resetPassword)).not.toHaveBeenCalled()
  })

  it('shows backend message on failure and never hardcodes it', async () => {
    const backendMessage = '账号或密码错误，或验证信息无效'
    vi.mocked(resetPassword).mockResolvedValue({
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

  it('navigates to login on success', async () => {
    const success: ApiResult<PasswordResetResponse> = { code: 200, message: '成功', data: null }
    vi.mocked(resetPassword).mockResolvedValue(success)
    const wrapper = createWrapper()
    await fillValidForm(wrapper)
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() =>
      expect(wrapper.vm.$router.currentRoute.value.path).toBe('/patient/login'),
    )
  })
})
