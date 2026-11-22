import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import PatientIdentityVerificationView from '@/views/patient-identity-verification-view.vue'
import { fetchIdentityStatus, submitIdentityVerification } from '@/api/patient-identity'
import type { ApiResult, IdentityStatusResponse, IdentityVerificationResponse } from '@/api/types'

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

const unverifiedStatus: ApiResult<IdentityStatusResponse> = {
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

const verifiedStatus: ApiResult<IdentityStatusResponse> = {
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

const submitSuccess: ApiResult<IdentityVerificationResponse> = {
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

async function createWrapper(status: ApiResult<IdentityStatusResponse> = unverifiedStatus) {
  vi.mocked(fetchIdentityStatus).mockResolvedValue(status)
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/patient/identity-verification', component: PatientIdentityVerificationView },
      { path: '/patient/profile', component: { template: '<div>profile</div>' } },
      { path: '/patient/login', component: { template: '<div>login</div>' } },
    ],
  })
  await router.push('/patient/identity-verification')
  await router.isReady()
  const wrapper = mount(PatientIdentityVerificationView, {
    global: { plugins: [pinia, router] },
  })
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof createWrapper>>['wrapper']

async function fillValidForm(wrapper: Wrapper): Promise<void> {
  await wrapper.find('#verify-real-name').setValue('张三')
  await wrapper.find('#verify-id-card').setValue('11010519491231002X')
}

describe('patient-identity-verification-view', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(fetchIdentityStatus).mockReset()
    vi.mocked(submitIdentityVerification).mockReset()
  })

  it('loads status on mount and disables submit until both fields valid', async () => {
    const { wrapper } = await createWrapper()
    expect(wrapper.attributes('data-status-loaded')).toBe('true')
    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    await wrapper.find('#verify-real-name').setValue('张三')
    await wrapper.find('#verify-id-card').setValue('12345')
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()

    await fillValidForm(wrapper)
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()
  })

  it('shows the verified summary instead of the form when already verified', async () => {
    const { wrapper } = await createWrapper(verifiedStatus)

    expect(wrapper.get('[data-testid="verified-summary"]').text()).toContain('已完成实名认证')
    expect(wrapper.find('form').exists()).toBe(false)
  })

  it('navigates to profile and syncs masked identity after successful submit', async () => {
    vi.mocked(submitIdentityVerification).mockResolvedValue(submitSuccess)
    const { wrapper, router } = await createWrapper()
    await fillValidForm(wrapper)
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/patient/profile'))
    expect(localStorage.getItem('sdas.patient.profile')).toContain('张*')
  })

  it('shows only the unified backend message on failure', async () => {
    const unified = '账号或密码错误，或验证信息无效'
    vi.mocked(submitIdentityVerification).mockResolvedValue({
      code: 400,
      message: unified,
      data: null,
    })
    const { wrapper, router } = await createWrapper()
    await fillValidForm(wrapper)
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() =>
      expect(wrapper.find('[data-testid="verification-error"]').exists()).toBe(true),
    )
    expect(wrapper.get('[data-testid="verification-error"]').text()).toBe(unified)
    expect(router.currentRoute.value.path).toBe('/patient/identity-verification')
  })

  it('falls back to the network error text when status load fails', async () => {
    const { wrapper } = await createWrapper({
      code: 400,
      message: '账号或密码错误，或验证信息无效',
      data: null,
    })

    expect(wrapper.get('[data-testid="verification-error"]').text()).toBe(
      '账号或密码错误，或验证信息无效',
    )
  })
})
