/**
 * 医生接诊 API 封装（功能 003）。
 * 契约来源：specs/003-doctor-consultation/contracts/doctor-consultation-api.md
 * Mock 来源：specs/003-doctor-consultation/contracts/mocks.md（M1–M9）
 *
 * doctorRequest 是独立 axios 实例（宪法 II），baseURL 含 /api，故路径写 /v1/...。
 * 修改类请求附带 CSRF 头（双重 Cookie 校验，宪法原则 IV）。每个方法返回已解包的 ApiResult。
 */
import { doctorRequest } from './request/doctor-request'
import { csrfHeaders } from './request/csrf'
import type {
  ApiResult,
  DoctorLoginRequest,
  DoctorLoginResponse,
  ConsultationListItem,
  WorkbenchConsultationDetail,
  AcceptResponse,
  MessageItem,
  MessageRequest,
  MessageResponse,
  PrescriptionCreateRequest,
  PrescriptionResponse,
  PrescriptionDetailResponse,
  AiDecisionRequest,
  AiDecisionResponse,
} from './types'

/** 端点 1：医生登录 */
export async function doctorLogin(
  payload: DoctorLoginRequest,
): Promise<ApiResult<DoctorLoginResponse>> {
  const res = await doctorRequest.post<ApiResult<DoctorLoginResponse>>(
    '/v1/workbench/sessions',
    payload,
    { headers: csrfHeaders() },
  )
  return res.data
}

/** 端点 2：待接诊 / 已接诊列表 */
export async function listConsultations(
  status?: string,
): Promise<ApiResult<ConsultationListItem[]>> {
  const query = status ? `?status=${status}` : ''
  const res = await doctorRequest.get<ApiResult<ConsultationListItem[]>>(
    `/v1/workbench/consultations${query}`,
  )
  return res.data
}

/** 端点 3：问诊单详情 */
export async function getConsultationDetail(
  consultationId: number,
): Promise<ApiResult<WorkbenchConsultationDetail>> {
  const res = await doctorRequest.get<ApiResult<WorkbenchConsultationDetail>>(
    `/v1/workbench/consultations/${consultationId}`,
  )
  return res.data
}

/** 端点 4：接诊 */
export async function acceptConsultation(
  consultationId: number,
): Promise<ApiResult<AcceptResponse>> {
  const res = await doctorRequest.post<ApiResult<AcceptResponse>>(
    `/v1/workbench/consultations/${consultationId}/accept`,
    null,
    { headers: csrfHeaders() },
  )
  return res.data
}

/** 端点 5：查询对话消息 */
export async function listMessages(consultationId: number): Promise<ApiResult<MessageItem[]>> {
  const res = await doctorRequest.get<ApiResult<MessageItem[]>>(
    `/v1/workbench/consultations/${consultationId}/messages`,
  )
  return res.data
}

/** 端点 6：发送回复 */
export async function sendMessage(
  consultationId: number,
  payload: MessageRequest,
): Promise<ApiResult<MessageResponse>> {
  const res = await doctorRequest.post<ApiResult<MessageResponse>>(
    `/v1/workbench/consultations/${consultationId}/messages`,
    payload,
    { headers: csrfHeaders() },
  )
  return res.data
}

/** 端点 7：开具电子处方 */
export async function createPrescription(
  consultationId: number,
  payload: PrescriptionCreateRequest,
): Promise<ApiResult<PrescriptionResponse>> {
  const res = await doctorRequest.post<ApiResult<PrescriptionResponse>>(
    `/v1/workbench/consultations/${consultationId}/prescriptions`,
    payload,
    { headers: csrfHeaders() },
  )
  return res.data
}

/** 端点 8：查询处方详情 */
export async function getPrescription(
  prescriptionId: number,
): Promise<ApiResult<PrescriptionDetailResponse>> {
  const res = await doctorRequest.get<ApiResult<PrescriptionDetailResponse>>(
    `/v1/workbench/prescriptions/${prescriptionId}`,
  )
  return res.data
}

/** 端点 9：AI 辅助决策 */
export async function getAiDecision(
  consultationId: number,
  payload?: AiDecisionRequest,
): Promise<ApiResult<AiDecisionResponse>> {
  const res = await doctorRequest.post<ApiResult<AiDecisionResponse>>(
    `/v1/workbench/consultations/${consultationId}/ai-decision`,
    payload ?? {},
    { headers: csrfHeaders() },
  )
  return res.data
}
