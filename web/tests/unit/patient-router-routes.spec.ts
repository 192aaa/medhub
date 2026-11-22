import { describe, expect, it } from 'vitest'
import router from '@/router'

function route(name: string) {
  return router.getRoutes().find((record) => record.name === name)
}

describe('patient route table', () => {
  it('registers all six patient pages', () => {
    const names = [
      'patient-register',
      'patient-login',
      'patient-profile',
      'patient-password-reset',
      'patient-identity-verification',
      'consultation-entry',
    ]
    for (const name of names) {
      expect(route(name), `${name} 应已注册`).toBeDefined()
    }
  })

  it('keeps register, login and password-reset anonymous', () => {
    for (const name of ['patient-register', 'patient-login', 'patient-password-reset']) {
      expect(route(name)?.meta.requiresAuth).toBeUndefined()
      expect(route(name)?.meta.requiresVerified).toBeUndefined()
    }
  })

  it('guards profile and identity-verification with requiresAuth', () => {
    expect(route('patient-profile')?.meta.requiresAuth).toBe(true)
    expect(route('patient-identity-verification')?.meta.requiresAuth).toBe(true)
  })

  it('guards consultation entry with requiresVerified', () => {
    expect(route('consultation-entry')?.meta.requiresAuth).toBe(true)
    expect(route('consultation-entry')?.meta.requiresVerified).toBe(true)
  })
})
