import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import ConsultationEntryView from '@/views/consultation-entry-view.vue'
import { usePatientStore } from '@/stores/patient'
import type { AuthStatus } from '@/api/types'

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
      { path: '/consultation/entry', component: ConsultationEntryView },
      {
        path: '/patient/identity-verification',
        component: { template: '<div>verify</div>' },
      },
    ],
  })
  return mount(ConsultationEntryView, { global: { plugins: [pinia, router] } })
}

describe('consultation-entry-view', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('shows only the placeholder when verified and allowed', () => {
    const wrapper = createWrapper('VERIFIED', true)

    expect(wrapper.get('[data-testid="consultation-placeholder"]').text()).toBe('线上问诊建设中')
    expect(wrapper.find('[data-testid="consultation-denied"]').exists()).toBe(false)
  })

  it('denies an unverified account and points to the verification page', () => {
    const wrapper = createWrapper('UNVERIFIED', false)

    expect(wrapper.get('[data-testid="consultation-denied"]').text()).toBe('问诊门禁未放行')
    expect(wrapper.find('[data-testid="consultation-placeholder"]').exists()).toBe(false)
    expect(wrapper.find('a[href="/patient/identity-verification"]').exists()).toBe(true)
  })

  it('denies a verified account whose consultationAllowed is false', () => {
    const wrapper = createWrapper('VERIFIED', false)

    expect(wrapper.get('[data-testid="consultation-denied"]').text()).toBe('问诊门禁未放行')
    expect(wrapper.find('[data-testid="consultation-placeholder"]').exists()).toBe(false)
  })
})
