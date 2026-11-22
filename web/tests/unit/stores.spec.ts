import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAppStore } from '@/stores/app'
import { useAppointmentStore } from '@/stores/appointment'
import { useConsultationStore } from '@/stores/consultation'
import { useDoctorStore } from '@/stores/doctor'
import { usePharmacyStore } from '@/stores/pharmacy'

describe('pinia stores', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('app store persists preferences and session token', () => {
    localStorage.setItem(
      'sdas.app.preferences',
      JSON.stringify({ theme: 'dark', sidebarCollapsed: true }),
    )
    localStorage.setItem('sdas.session.token', 'old-token')
    const store = useAppStore()

    expect(store.isDark).toBe(true)
    expect(store.sidebarCollapsed).toBe(true)
    expect(store.hasSession).toBe(true)

    store.toggleTheme()
    store.toggleSidebar()
    store.setSessionToken(null)

    expect(store.isDark).toBe(false)
    expect(store.sidebarCollapsed).toBe(false)
    expect(store.hasSession).toBe(false)
    expect(localStorage.getItem('sdas.session.token')).toBeNull()
    expect(localStorage.getItem('sdas.app.preferences')).toContain('"theme":"light"')
  })

  it('appointment store restores, updates and resets flow state', () => {
    localStorage.setItem(
      'sdas-appointment',
      JSON.stringify({
        appointmentId: 401,
        paymentStatus: 'PAID',
        selectedDoctorId: 101,
        selectedScheduleId: 201,
        feeAmount: 30,
      }),
    )
    const store = useAppointmentStore()

    expect(store.appointmentId).toBe(401)
    expect(store.paymentStatus).toBe('PAID')

    store.selectDoctor(102, 202, 20)
    store.setAppointment(402)
    store.setPaymentStatus('UNPAID')

    expect(store.selectedDoctorId).toBe(102)
    expect(store.appointmentId).toBe(402)
    expect(store.paymentStatus).toBe('UNPAID')

    store.reset()
    expect(store.appointmentId).toBeNull()
    expect(store.feeAmount).toBe(0)
  })

  it('consultation store tracks consultation id and images', () => {
    const store = useConsultationStore()

    store.setConsultation(701, 401)
    store.addImage('/static/a.jpg')
    store.addImage('/static/a.jpg')

    expect(store.consultationId).toBe(701)
    expect(store.imageUrls).toEqual(['/static/a.jpg'])

    store.reset()
    expect(store.consultationId).toBeNull()
    expect(store.imageUrls).toEqual([])
  })

  it('doctor store applies login, switches consultation and clears session', () => {
    const store = useDoctorStore()

    store.applyLogin({
      accessToken: 'doctor-token',
      doctorId: 11,
      nameMask: '张*',
      title: '主任医师',
      departmentId: 1,
    } as never)
    store.setCurrentConsultation(501)

    expect(store.hasSession).toBe(true)
    expect(store.currentConsultationId).toBe(501)
    expect(localStorage.getItem('sdas.doctor.token')).toBe('doctor-token')

    store.clear()
    expect(store.hasSession).toBe(false)
    expect(store.currentConsultationId).toBeNull()
  })

  it('pharmacy store applies login and clears session', () => {
    const store = usePharmacyStore()

    store.applyLogin({
      accessToken: 'pharmacy-token',
      pharmacyId: 21,
      nameMask: '某*',
      pharmacyName: '示范药房',
    } as never)

    expect(store.hasSession).toBe(true)
    expect(store.pharmacyName).toBe('示范药房')

    store.clear()
    expect(store.hasSession).toBe(false)
    expect(localStorage.getItem('sdas.pharmacy.profile')).toBeNull()
  })
})
