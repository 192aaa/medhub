import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import PatientProfileView from '@/views/patient-profile-view.vue'
import { logoutPatient } from '@/api/patient-identity'
import { usePatientStore } from '@/stores/patient'
import type { AuthStatus } from '@/api/types'

vi.mock('@/api/patient-identity', () => ({
  loginPatient: vi.fn(),
  logoutPatient: vi.fn(),
  refreshPatientToken: vi.fn(),
  registerPatient: vi.fn(),
  sendVerificationCode: vi.fn(),
}))

function createWrapper(authStatus: AuthStatus, consultationAllowed: boolean) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = usePatientStore()
  store.applyRegisterResult({
    patientId: 10001,
    maskedPhone: '138****8000',
    authStatus,
    consultationAllowed,
    accessToken: 'jwt-token',
    accessTokenExpiresInSeconds: 7200,
    registeredAt: '2026-11-07T10:15:30',
    agreementVersion: 'v1.0',
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/patient/profile', component: PatientProfileView },
      { path: '/patient/login', component: { template: '<div>login</div>' } },
      {
        path: '/patient/identity-verification',
        component: { template: '<div>verify</div>' },
      },
    ],
  })
  const wrapper = mount(PatientProfileView, { global: { plugins: [pinia, router] } })
  return { wrapper, router, store }
}

describe('patient-profile-view', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.mocked(logoutPatient).mockReset()
  })

  it('renders unverified card with verification entry', () => {
    const { wrapper } = createWrapper('UNVERIFIED', false)

    expect(wrapper.get('[data-testid="masked-phone"]').text()).toBe('138****8000')
    expect(wrapper.get('[data-testid="auth-status"]').text()).toBe('未认证')
    expect(wrapper.get('[data-testid="consultation-allowed"]').text()).toBe('线上问诊未解锁')
    expect(wrapper.find('[data-testid="identity-hint"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="verify-entry"]').exists()).toBe(true)
  })

  it('renders verified card without verification entry', () => {
    const { wrapper } = createWrapper('VERIFIED', true)

    expect(wrapper.get('[data-testid="auth-status"]').text()).toBe('已认证')
    expect(wrapper.get('[data-testid="consultation-allowed"]').text()).toBe('已解锁线上问诊')
    expect(wrapper.find('[data-testid="identity-hint"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="verify-entry"]').exists()).toBe(false)
  })

  it('clears session and navigates to login after logout', async () => {
    vi.mocked(logoutPatient).mockResolvedValue({ code: 200, message: '成功', data: null })
    const { wrapper, router, store } = createWrapper('VERIFIED', true)

    await wrapper.get('[data-testid="logout-button"]').trigger('click')

    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/patient/login'))
    expect(store.hasSession).toBe(false)
    expect(localStorage.getItem('sdas.patient.token')).toBeNull()
  })
})
