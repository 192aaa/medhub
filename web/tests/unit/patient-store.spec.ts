import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePatientStore } from '@/stores/patient'
import type { PatientRegisterResponse } from '@/api/types'

const registerResult: PatientRegisterResponse = {
  patientId: 10001,
  maskedPhone: '138****8000',
  authStatus: 'UNVERIFIED',
  consultationAllowed: false,
  accessToken: 'jwt-token',
  accessTokenExpiresInSeconds: 7200,
  registeredAt: '2026-11-07T10:15:30',
  agreementVersion: 'v1.0',
}

describe('patient store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('restores session and profile from localStorage on creation', () => {
    localStorage.setItem('sdas.patient.token', 'stored-token')
    localStorage.setItem(
      'sdas.patient.profile',
      JSON.stringify({
        patientId: 42,
        authStatus: 'VERIFIED',
        consultationAllowed: true,
        maskedPhone: '139****0000',
      }),
    )
    const store = usePatientStore()
    expect(store.accessToken).toBe('stored-token')
    expect(store.hasSession).toBe(true)
    expect(store.authStatus).toBe('VERIFIED')
    expect(store.consultationAllowed).toBe(true)
    expect(store.maskedPhone).toBe('139****0000')
  })

  it('writes authStatus / consultationAllowed after registration', () => {
    const store = usePatientStore()
    store.applyRegisterResult(registerResult)
    expect(store.authStatus).toBe('UNVERIFIED')
    expect(store.consultationAllowed).toBe(false)
    expect(store.maskedPhone).toBe('138****8000')
    expect(localStorage.getItem('sdas.patient.token')).toBe('jwt-token')
  })

  it('clears session and persisted profile', () => {
    const store = usePatientStore()
    store.applyRegisterResult(registerResult)
    store.clear()
    expect(store.hasSession).toBe(false)
    expect(store.authStatus).toBe('UNVERIFIED')
    expect(localStorage.getItem('sdas.patient.token')).toBeNull()
  })
})
