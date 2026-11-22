/**
 * 预约挂号与图文问诊 API 封装（功能 002）。
 * 契约来源：specs/002-appointment-consultation/contracts/appointment-consultation-api.md
 * Mock 来源：specs/002-appointment-consultation/contracts/mocks.md（M1–M16）
 *
 * patientRequest 是独立 axios 实例（宪法 II），baseURL 含 /api，故路径写 /v1/...。
 * 修改类请求附带 CSRF 头（双重 Cookie 校验，宪法原则 IV）。每个方法返回已解包的 ApiResult，
 * 调用方按 res.code / res.data 判定（与 001 调用约定一致）。
 */
import { patientRequest } from './request/patient-request'
import { csrfHeaders } from './request/csrf'
import type {
  ApiResult,
  DepartmentResponse,
  DoctorResponse,
  DoctorDetailResponse,
  TriageRequest,
  TriageResponse,
  AppointmentCreateRequest,
  AppointmentCreateResponse,
  PaymentRequest,
  PaymentResponse,
  PaymentStatusResponse,
  ImageUploadResponse,
  ConsultationSubmitRequest,
  ConsultationSubmitResponse,
  ConsultationDetailResponse,
} from './types'

/** 端点 1：科室列表（无需认证） */
export async function getDepartments(): Promise<ApiResult<DepartmentResponse[]>> {
  const res = await patientRequest.get<ApiResult<DepartmentResponse[]>>('/v1/departments')
  return res.data
}

/** 端点 2：医生列表（可按科室 / 在线状态筛选，无需认证） */
export async function getDoctors(params: {
  departmentId?: number
  online?: string
}): Promise<ApiResult<DoctorResponse[]>> {
  const qs = new URLSearchParams()
  if (params.departmentId !== undefined) qs.set('departmentId', String(params.departmentId))
  if (params.online !== undefined) qs.set('online', params.online)
  const query = qs.toString()
  const res = await patientRequest.get<ApiResult<DoctorResponse[]>>(
    `/v1/doctors${query ? `?${query}` : ''}`,
  )
  return res.data
}

/** 端点 3：医生详情（含排班，无需认证） */
export async function getDoctorDetail(doctorId: number): Promise<ApiResult<DoctorDetailResponse>> {
  const res = await patientRequest.get<ApiResult<DoctorDetailResponse>>(`/v1/doctors/${doctorId}`)
  return res.data
}

/** 端点 4：AI 智能导诊（允许匿名，CSRF 必带） */
export async function createTriage(payload: TriageRequest): Promise<ApiResult<TriageResponse>> {
  const res = await patientRequest.post<ApiResult<TriageResponse>>('/v1/triage-sessions', payload, {
    headers: csrfHeaders(),
  })
  return res.data
}

/** 端点 5：创建挂号预约（需认证 + 门禁） */
export async function createAppointment(
  payload: AppointmentCreateRequest,
): Promise<ApiResult<AppointmentCreateResponse>> {
  const res = await patientRequest.post<ApiResult<AppointmentCreateResponse>>(
    '/v1/appointments',
    payload,
    {
      headers: csrfHeaders(),
    },
  )
  return res.data
}

/** 端点 6a：挂号费支付（需认证，MVP 模拟支付） */
export async function payAppointment(
  appointmentId: number,
  payload: PaymentRequest,
): Promise<ApiResult<PaymentResponse>> {
  const res = await patientRequest.post<ApiResult<PaymentResponse>>(
    `/v1/appointments/${appointmentId}/payments`,
    payload,
    { headers: csrfHeaders() },
  )
  return res.data
}

/** 端点 6c：确认沙箱支付（需认证，真实网关回调的接入点） */
export async function confirmPayment(
  appointmentId: number,
  paymentId: number,
): Promise<ApiResult<PaymentResponse>> {
  const res = await patientRequest.post<ApiResult<PaymentResponse>>(
    `/v1/appointments/${appointmentId}/payments/${paymentId}/confirm`,
    null,
    { headers: csrfHeaders() },
  )
  return res.data
}

/** 端点 6b：查询支付状态（需认证） */
export async function getPaymentStatus(
  appointmentId: number,
): Promise<ApiResult<PaymentStatusResponse>> {
  const res = await patientRequest.get<ApiResult<PaymentStatusResponse>>(
    `/v1/appointments/${appointmentId}/payments`,
  )
  return res.data
}

/** 端点 7：上传问诊图片（需认证，multipart） */
export async function uploadImage(file: File): Promise<ApiResult<ImageUploadResponse>> {
  const form = new FormData()
  form.append('file', file)
  // FormData 不设 Content-Type，由浏览器自动加 boundary；CSRF 头仍须带（宪法原则 IV）
  const res = await patientRequest.post<ApiResult<ImageUploadResponse>>(
    '/v1/consultations/images',
    form,
    {
      headers: csrfHeaders(),
    },
  )
  return res.data
}

/** 端点 8：提交图文问诊单（需认证，关联已支付挂号单） */
export async function submitConsultation(
  payload: ConsultationSubmitRequest,
): Promise<ApiResult<ConsultationSubmitResponse>> {
  const res = await patientRequest.post<ApiResult<ConsultationSubmitResponse>>(
    '/v1/consultations',
    payload,
    {
      headers: csrfHeaders(),
    },
  )
  return res.data
}

/** 端点 9：查询问诊单详情（需认证，越权拒绝） */
export async function getConsultation(
  consultationId: number,
): Promise<ApiResult<ConsultationDetailResponse>> {
  const res = await patientRequest.get<ApiResult<ConsultationDetailResponse>>(
    `/v1/consultations/${consultationId}`,
  )
  return res.data
}
