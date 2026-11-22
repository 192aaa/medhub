/**
 * 药店端 API 封装（功能 004 · US1）。
 * 契约来源：specs/004-prescription-dispensing/contracts/prescription-dispensing-api.md
 *
 * pharmacyRequest 是独立 axios 实例（宪法 II），baseURL 含 /api，故路径写 /v1/...。
 * 修改类请求附带 CSRF 头（双重 Cookie 校验，宪法原则 IV）。
 */
import { pharmacyRequest } from './request/pharmacy-request'
import { csrfHeaders } from './request/csrf'
import type {
  ApiResult,
  PharmacyLoginRequest,
  PharmacyLoginResponse,
  VerificationRequest,
  VerificationResponse,
} from './types'

/** 端点 1：药店登录（不签发 Refresh Token） */
export async function pharmacyLogin(
  payload: PharmacyLoginRequest,
): Promise<ApiResult<PharmacyLoginResponse>> {
  const res = await pharmacyRequest.post<ApiResult<PharmacyLoginResponse>>(
    '/v1/pharmacy/sessions',
    payload,
    { headers: csrfHeaders() },
  )
  return res.data
}

/**
 * 端点 2：处方验真（只读、可重复调用）。
 * 过期 / 作废走 200 + dispenseStatus，不抛异常；仅 DRAFT 与凭证无效走 4xx。
 */
export async function verifyPrescription(
  payload: VerificationRequest,
): Promise<ApiResult<VerificationResponse>> {
  const res = await pharmacyRequest.post<ApiResult<VerificationResponse>>(
    '/v1/pharmacy/prescription-verifications',
    payload,
    { headers: csrfHeaders() },
  )
  return res.data
}
