/**
 * 患者注册与实名认证 API（契约：specs/001-patient-identity/contracts/patient-identity-api.md）。
 *
 * baseURL 由 VITE_API_BASE_URL 提供且已含 /api，此处具体路径不得再带 /api（宪法 III）。
 * 修改类请求须携带 X-CSRF-Token，值取自 sdas_csrf Cookie（契约 README 第 4/5 节）。
 */
import { patientRequest } from './request/patient-request'
import { csrfHeaders } from './request/csrf'
import type {
  ApiResult,
  IdentityStatusResponse,
  IdentityVerificationRequest,
  IdentityVerificationResponse,
  PatientLoginRequest,
  PatientLoginResponse,
  PatientLogoutResponse,
  PasswordResetRequest,
  PasswordResetResponse,
  PatientRegisterRequest,
  PatientRegisterResponse,
  RefreshTokenResponse,
  VerificationCodeSendRequest,
  VerificationCodeSendResponse,
} from './types'

const VERIFICATION_CODE_PATH = '/v1/verification-codes'
const PATIENT_PATH = '/v1/patients'
const SESSION_PATH = '/v1/patient-sessions'
const IDENTITY_VERIFICATION_PATH = '/v1/identity-verifications'
const IDENTITY_STATUS_PATH = `${PATIENT_PATH}/current/identity-status`
const PASSWORD_RESET_PATH = '/v1/password-resets'

/** 端点 1：签发验证码；响应 data 中不含验证码字段 */
export async function sendVerificationCode(
  payload: VerificationCodeSendRequest,
): Promise<ApiResult<VerificationCodeSendResponse>> {
  const response = await patientRequest.post<ApiResult<VerificationCodeSendResponse>>(
    VERIFICATION_CODE_PATH,
    payload,
    { headers: csrfHeaders() },
  )
  return response.data
}

/** 端点 2：注册并自动登录 */
export async function registerPatient(
  payload: PatientRegisterRequest,
  idempotencyKey?: string,
): Promise<ApiResult<PatientRegisterResponse>> {
  const headers = csrfHeaders()
  if (idempotencyKey) {
    headers['X-Idempotency-Key'] = idempotencyKey
  }
  const response = await patientRequest.post<ApiResult<PatientRegisterResponse>>(
    PATIENT_PATH,
    payload,
    { headers },
  )
  return response.data
}

/** 端点 3：手机号密码登录；响应体不含 refresh token 明文（仅 Cookie 下发） */
export async function loginPatient(
  payload: PatientLoginRequest,
): Promise<ApiResult<PatientLoginResponse>> {
  const response = await patientRequest.post<ApiResult<PatientLoginResponse>>(
    SESSION_PATH,
    payload,
    { headers: csrfHeaders() },
  )
  return response.data
}

/** 端点 4：安全退出；成功时 data 恒为 null，重复调用仍返回 200 */
export async function logoutPatient(): Promise<ApiResult<PatientLogoutResponse>> {
  const response = await patientRequest.delete<ApiResult<PatientLogoutResponse>>(
    `${SESSION_PATH}/current`,
    { headers: csrfHeaders() },
  )
  return response.data
}

/** 端点 5：续期并轮换 refresh token；旧凭证复用会返回统一失败文案 */
export async function refreshPatientToken(): Promise<ApiResult<RefreshTokenResponse>> {
  const response = await patientRequest.put<ApiResult<RefreshTokenResponse>>(
    `${SESSION_PATH}/current/refresh-token`,
    null,
    { headers: csrfHeaders() },
  )
  return response.data
}

/** 端点 7：提交实名认证；POST 自动附带 CSRF 双重 Cookie 头 */
export async function submitIdentityVerification(
  payload: IdentityVerificationRequest,
): Promise<ApiResult<IdentityVerificationResponse>> {
  const response = await patientRequest.post<ApiResult<IdentityVerificationResponse>>(
    IDENTITY_VERIFICATION_PATH,
    payload,
    { headers: csrfHeaders() },
  )
  return response.data
}

/** 端点 8：查询认证状态；GET 无需 CSRF，三个未认证 null 字段仍须存在 */
export async function fetchIdentityStatus(): Promise<ApiResult<IdentityStatusResponse>> {
  const response = await patientRequest.get<ApiResult<IdentityStatusResponse>>(IDENTITY_STATUS_PATH)
  return response.data
}

/** 端点 6：重置密码；POST 自动附带 CSRF 双重 Cookie 头，响应 data 恒为 null */
export async function resetPassword(
  payload: PasswordResetRequest,
): Promise<ApiResult<PasswordResetResponse>> {
  const response = await patientRequest.post<ApiResult<PasswordResetResponse>>(
    PASSWORD_RESET_PATH,
    payload,
    { headers: csrfHeaders() },
  )
  return response.data
}
