/**
 * AI辅助治疗决策 API 封装（功能 006，端点 1–5）。
 * 契约来源：specs/006-ai-treatment-decision/contracts/ai-treatment-decision-api.md
 * Mock 来源：specs/006-ai-treatment-decision/contracts/mocks.md（M1–M9）
 *
 * doctorRequest 的 baseURL 含 /api，故路径写 /v1/...；修改类请求附带 CSRF 头。
 * 每个方法返回已解包的 ApiResult；AI 降级仍为 code=200，由 data.status 表达。
 */
import { doctorRequest } from './request/doctor-request'
import { csrfHeaders } from './request/csrf'
import type {
  ApiResult,
  DrugListResponse,
  TreatmentAdoptionResponse,
  TreatmentDecisionResponse,
} from './types'

/** 端点 1：生成或复用治疗决策辅助 */
export async function generateTreatmentDecision(
  consultationId: number,
): Promise<ApiResult<TreatmentDecisionResponse>> {
  const res = await doctorRequest.post<ApiResult<TreatmentDecisionResponse>>(
    `/v1/workbench/consultations/${consultationId}/treatment-decision`,
    null,
    { headers: csrfHeaders() },
  )
  return res.data
}

/** 端点 2：查询最近一次辅助记录 */
export async function getTreatmentDecision(
  consultationId: number,
): Promise<ApiResult<TreatmentDecisionResponse>> {
  const res = await doctorRequest.get<ApiResult<TreatmentDecisionResponse>>(
    `/v1/workbench/consultations/${consultationId}/treatment-decision`,
  )
  return res.data
}

/** 端点 3：强制重新生成（消耗频控额度） */
export async function regenerateTreatmentDecision(
  consultationId: number,
): Promise<ApiResult<TreatmentDecisionResponse>> {
  const res = await doctorRequest.post<ApiResult<TreatmentDecisionResponse>>(
    `/v1/workbench/consultations/${consultationId}/treatment-decision/regenerate`,
    null,
    { headers: csrfHeaders() },
  )
  return res.data
}

/** 端点 4：回填采纳数量（失败不阻断开方，调用方须静默） */
export async function reportTreatmentAdoption(
  consultationId: number,
  appliedItemCount: number,
): Promise<ApiResult<TreatmentAdoptionResponse>> {
  const res = await doctorRequest.post<ApiResult<TreatmentAdoptionResponse>>(
    `/v1/workbench/consultations/${consultationId}/treatment-decision/adoptions`,
    { appliedItemCount },
    { headers: csrfHeaders() },
  )
  return res.data
}

/** 端点 5：药品目录分页（keyword ≤50，pageSize 1–50） */
export async function listDrugs(
  keyword = '',
  page = 1,
  pageSize = 20,
): Promise<ApiResult<DrugListResponse>> {
  const res = await doctorRequest.get<ApiResult<DrugListResponse>>('/v1/workbench/drugs', {
    params: { keyword, page, pageSize },
  })
  return res.data
}
