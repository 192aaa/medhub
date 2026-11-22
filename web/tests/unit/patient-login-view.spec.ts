import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import PatientLoginView from '@/views/patient-login-view.vue'
import { loginPatient } from '@/api/patient-identity'
import type { ApiResult, PatientLoginResponse } from '@/api/types'

vi.mock('@/api/patient-identity', () => ({
  loginPatient: vi.fn(),
  logoutPatient: vi.fn(),
  refreshPatientToken: vi.fn(),
  registerPatient: vi.fn(),
  sendVerificationCode: vi.fn(),
  resetPassword: vi.fn(),
  submitIdentityVerification: vi.fn(),
  fetchIdentityStatus: vi.fn(),
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

async function createWrapper(query: Record<string, string> = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/patient/login', component: PatientLoginView },
      { path: '/patient/profile', component: { template: '<div>profile</div>' } },
      { path: '/consultation/entry', component: { template: '<div>entry</div>' } },
      { path: '/patient/register', component: { template: '<div>register</div>' } },
    ],
  })
  await router.push({ path: '/patient/login', query })
  await router.isReady()
  const wrapper = mount(PatientLoginView, { global: { plugins: [pinia, router] } })
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof createWrapper>>['wrapper']

async function fillValidForm(wrapper: Wrapper): Promise<void> {
  await wrapper.find('#login-phone').setValue('13800138000')
  await wrapper.find('#login-password').setValue('abc12345')
}

describe('patient-login-view', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(loginPatient).mockReset()
  })

  it('disables submit until phone and password are both valid', async () => {
    const { wrapper } = await createWrapper()
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    await wrapper.find('#login-phone').setValue('12345')
    await wrapper.find('#login-password').setValue('abc12345')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    await wrapper.find('#login-phone').setValue('13800138000')
    await wrapper.find('#login-password').setValue('abc')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    await wrapper.find('#login-password').setValue('abc12345')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('navigates to profile after successful login', async () => {
    vi.mocked(loginPatient).mockResolvedValue(loginSuccess)
    const { wrapper, router } = await createWrapper()
    await fillValidForm(wrapper)
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/patient/profile'))
    expect(localStorage.getItem('sdas.patient.token')).toBe('jwt-token')
  })

  it('returns to the redirect target carried by the guard', async () => {
    vi.mocked(loginPatient).mockResolvedValue(loginSuccess)
    const { wrapper, router } = await createWrapper({ redirect: '/consultation/entry' })
    await fillValidForm(wrapper)
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/consultation/entry'))
  })

  it('shows the unified backend message on failure and stays on the page', async () => {
    const unified = '账号或密码错误，或验证信息无效'
    vi.mocked(loginPatient).mockResolvedValue({ code: 400, message: unified, data: null })
    const { wrapper, router } = await createWrapper()
    await fillValidForm(wrapper)
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(wrapper.find('[data-testid="login-error"]').exists()).toBe(true))
    expect(wrapper.get('[data-testid="login-error"]').text()).toBe(unified)
    expect(router.currentRoute.value.path).toBe('/patient/login')
  })

  it('falls back to the network error text without guessing a reason', async () => {
    vi.mocked(loginPatient).mockRejectedValue(new Error('offline'))
    const { wrapper } = await createWrapper()
    await fillValidForm(wrapper)
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(wrapper.find('[data-testid="login-error"]').exists()).toBe(true))
    expect(wrapper.get('[data-testid="login-error"]').text()).toBe('网络异常，请稍后重试')
  })
})
