import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  patient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
  doctor: { get: vi.fn(), post: vi.fn() },
  pharmacy: { post: vi.fn() },
  publicRequest: { get: vi.fn() },
  csrfHeaders: vi.fn(),
  postSse: vi.fn(),
}))

vi.mock('@/api/request/patient-request', () => ({ patientRequest: mocks.patient }))
vi.mock('@/api/request/doctor-request', () => ({ doctorRequest: mocks.doctor }))
vi.mock('@/api/request/pharmacy-request', () => ({ pharmacyRequest: mocks.pharmacy }))
vi.mock('@/api/request/public-request', () => ({ publicRequest: mocks.publicRequest }))
vi.mock('@/api/request/csrf', () => ({ csrfHeaders: mocks.csrfHeaders }))
vi.mock('@/api/sse', () => ({ postSse: mocks.postSse }))

import * as appointmentApi from '@/api/appointment-consultation'
import * as doctorApi from '@/api/doctor-consultation'
import * as treatmentApi from '@/api/treatment-decision'
import * as pharmacyApi from '@/api/pharmacy'
import * as healthApi from '@/api/health'
import * as healthConsultationApi from '@/api/health-consultation'

const OK = { code: 200, message: '成功', data: null }

describe('API wrappers', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.csrfHeaders.mockImplementation(() => ({ 'X-CSRF-Token': 'csrf' }))
    for (const fn of Object.values(mocks.patient)) fn.mockResolvedValue({ data: OK })
    for (const fn of Object.values(mocks.doctor)) fn.mockResolvedValue({ data: OK })
    for (const fn of Object.values(mocks.pharmacy)) fn.mockResolvedValue({ data: OK })
    mocks.publicRequest.get.mockResolvedValue({ data: OK })
    mocks.postSse.mockResolvedValue(undefined)
  })

  it('appointment consultation wrappers call the expected endpoints', async () => {
    await appointmentApi.getDepartments()
    await appointmentApi.getDoctors({ departmentId: 1, online: 'true' })
    await appointmentApi.getDoctorDetail(101)
    await appointmentApi.createTriage({ symptom: '头痛' } as never)
    await appointmentApi.createAppointment({ doctorId: 101 } as never)
    await appointmentApi.payAppointment(401, { channel: 'MOCK', amount: 30 } as never)
    await appointmentApi.confirmPayment(401, 501)
    await appointmentApi.getPaymentStatus(401)
    await appointmentApi.uploadImage(new File(['x'], 'a.jpg', { type: 'image/jpeg' }))
    await appointmentApi.submitConsultation({ appointmentId: 401 } as never)
    await appointmentApi.getConsultation(701)

    expect(mocks.patient.get).toHaveBeenCalledWith('/v1/departments')
    expect(mocks.patient.get).toHaveBeenCalledWith('/v1/doctors?departmentId=1&online=true')
    expect(mocks.patient.get).toHaveBeenCalledWith('/v1/doctors/101')
    expect(mocks.patient.post).toHaveBeenCalledWith(
      '/v1/appointments/401/payments/501/confirm',
      null,
      { headers: { 'X-CSRF-Token': 'csrf' } },
    )
    expect(mocks.patient.delete).not.toHaveBeenCalled()
  })

  it('doctor consultation wrappers call the expected endpoints', async () => {
    await doctorApi.doctorLogin({ username: 'doctor_zhang', password: 'password' } as never)
    await doctorApi.listConsultations('WAITING')
    await doctorApi.getConsultationDetail(501)
    await doctorApi.acceptConsultation(501)
    await doctorApi.listMessages(501)
    await doctorApi.sendMessage(501, { content: '你好' } as never)
    await doctorApi.createPrescription(501, { items: [] } as never)
    await doctorApi.getPrescription(901)
    await doctorApi.getAiDecision(501)

    expect(mocks.doctor.post).toHaveBeenCalledWith(
      '/v1/workbench/consultations/501/accept',
      null,
      { headers: { 'X-CSRF-Token': 'csrf' } },
    )
    expect(mocks.doctor.get).toHaveBeenCalledWith('/v1/workbench/prescriptions/901')
  })

  it('treatment decision wrappers call the expected endpoints', async () => {
    await treatmentApi.generateTreatmentDecision(501)
    await treatmentApi.getTreatmentDecision(501)
    await treatmentApi.regenerateTreatmentDecision(501)
    await treatmentApi.reportTreatmentAdoption(501, 2)
    const drugs = await treatmentApi.listDrugs('阿莫', 2, 10)

    expect(drugs.code).toBe(200)
    expect(mocks.doctor.get).toHaveBeenCalledWith('/v1/workbench/drugs', {
      params: { keyword: '阿莫', page: 2, pageSize: 10 },
    })
  })

  it('pharmacy and health wrappers call the expected endpoints', async () => {
    await pharmacyApi.pharmacyLogin({ username: 'pharmacy', password: 'password' } as never)
    await pharmacyApi.verifyPrescription({ token: 't' } as never)
    const health = await healthApi.getHealth()

    expect(health.code).toBe(200)
    expect(mocks.pharmacy.post).toHaveBeenCalledWith(
      '/v1/pharmacy/prescription-verifications',
      { token: 't' },
      { headers: { 'X-CSRF-Token': 'csrf' } },
    )
    expect(mocks.publicRequest.get).toHaveBeenCalledWith('/health')
  })

  it('health consultation wrappers call the expected endpoints', async () => {
    await healthConsultationApi.fetchHealthSessions(2, 10)
    await healthConsultationApi.createHealthSession()
    await healthConsultationApi.renameHealthSession(11, '标题')
    await healthConsultationApi.deleteHealthSession(11)
    await healthConsultationApi.clearHealthSessions()
    await healthConsultationApi.fetchHealthMessages(11, 3, 20)
    await healthConsultationApi.streamHealthChat(11, '你好', vi.fn())

    expect(mocks.patient.patch).toHaveBeenCalledWith(
      '/v1/health-consultation/sessions/11',
      { title: '标题' },
      { headers: { 'X-CSRF-Token': 'csrf' } },
    )
    expect(mocks.postSse).toHaveBeenCalledWith(
      '/v1/health-consultation/sessions/11/messages',
      { content: '你好' },
      expect.objectContaining({ onEvent: expect.any(Function) }),
    )
  })
})
