import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { registerGuards } from '@/router/guards'
import { usePatientStore } from '@/stores/patient'

const stub = { template: '<div>stub</div>' }

function createRouterWithGuard() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: stub },
      { path: '/patient/login', name: 'patient-login', component: stub },
      {
        path: '/patient/identity-verification',
        name: 'patient-identity-verification',
        component: stub,
      },
      {
        path: '/consultation/entry',
        name: 'consultation-entry',
        component: stub,
        meta: { requiresAuth: true, requiresVerified: true },
      },
    ],
  })
  registerGuards(router)
  return router
}

function session(authStatus: 'UNVERIFIED' | 'VERIFIED', consultationAllowed: boolean): void {
  const store = usePatientStore()
  store.setAccessToken('jwt-token')
  store.authStatus = authStatus
  store.consultationAllowed = consultationAllowed
}

describe('requiresVerified guard', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('redirects to login when there is no session', async () => {
    const router = createRouterWithGuard()
    await router.push('/consultation/entry')
    expect(router.currentRoute.value.path).toBe('/patient/login')
  })

  it('redirects unverified account to identity-verification', async () => {
    session('UNVERIFIED', false)
    const router = createRouterWithGuard()
    await router.push('/consultation/entry')
    expect(router.currentRoute.value.path).toBe('/patient/identity-verification')
  })

  it('redirects when authStatus is VERIFIED but consultationAllowed is false', async () => {
    session('VERIFIED', false)
    const router = createRouterWithGuard()
    await router.push('/consultation/entry')
    expect(router.currentRoute.value.path).toBe('/patient/identity-verification')
  })

  it('redirects when consultationAllowed is true but authStatus is UNVERIFIED', async () => {
    session('UNVERIFIED', true)
    const router = createRouterWithGuard()
    await router.push('/consultation/entry')
    expect(router.currentRoute.value.path).toBe('/patient/identity-verification')
  })

  it('allows access when both fields indicate verified', async () => {
    session('VERIFIED', true)
    const router = createRouterWithGuard()
    await router.push('/consultation/entry')
    expect(router.currentRoute.value.path).toBe('/consultation/entry')
  })
})
