/**
 * 患者注册与实名认证 Mock（M1–M15）。
 *
 * 唯一来源：specs/001-patient-identity/contracts/mocks.md（逐字同步，禁止凭空手写）。
 * 按 method + path 组织；message 一律复制契约，不改写、不新增字段。
 */
import type { ApiResult } from '../types'

export type MockMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

export interface PatientIdentityMock {
  id: string
  method: MockMethod
  /** 前端具体路径，不含 /api 前缀 */
  path: string
  status: number
  body: ApiResult<unknown>
}

const ok = (data: unknown): ApiResult<unknown> => ({ code: 200, message: '成功', data })

export const PATIENT_IDENTITY_MOCKS: PatientIdentityMock[] = [
  {
    id: 'M1',
    method: 'POST',
    path: '/v1/verification-codes',
    status: 200,
    body: ok({
      purpose: 'REGISTER',
      maskedPhone: '138****8000',
      expiresInSeconds: 300,
      nextSendAllowedInSeconds: 60,
    }),
  },
  {
    id: 'M2',
    method: 'POST',
    path: '/v1/verification-codes',
    status: 429,
    body: { code: 429, message: '操作过于频繁，请稍后重试', data: null },
  },
  {
    id: 'M3',
    method: 'POST',
    path: '/v1/patients',
    status: 200,
    body: ok({
      patientId: 10001,
      maskedPhone: '138****8000',
      authStatus: 'UNVERIFIED',
      consultationAllowed: false,
      accessToken: 'mock-access-token-new-patient',
      accessTokenExpiresInSeconds: 7200,
      registeredAt: '2026-11-07T10:15:30',
      agreementVersion: 'v1.0',
    }),
  },
  {
    id: 'M4',
    method: 'POST',
    path: '/v1/patients',
    status: 400,
    body: { code: 400, message: '账号或密码错误，或验证信息无效', data: null },
  },
  {
    id: 'M5',
    method: 'POST',
    path: '/v1/patients',
    status: 400,
    body: {
      code: 400,
      message: '密码须为 6-20 位字母数字组合，且至少含一个字母和一个数字',
      data: null,
    },
  },
  {
    id: 'M6',
    method: 'POST',
    path: '/v1/patient-sessions',
    status: 200,
    body: ok({
      patientId: 10001,
      maskedPhone: '138****8000',
      authStatus: 'VERIFIED',
      consultationAllowed: true,
      accessToken: 'mock-access-token-verified',
      accessTokenExpiresInSeconds: 7200,
    }),
  },
  {
    id: 'M7',
    method: 'POST',
    path: '/v1/patient-sessions',
    status: 400,
    body: { code: 400, message: '账号或密码错误，或验证信息无效', data: null },
  },
  {
    id: 'M8',
    method: 'DELETE',
    path: '/v1/patient-sessions/current',
    status: 200,
    body: ok(null),
  },
  {
    id: 'M9',
    method: 'PUT',
    path: '/v1/patient-sessions/current/refresh-token',
    status: 200,
    body: ok({ accessToken: 'mock-access-token-rotated', accessTokenExpiresInSeconds: 7200 }),
  },
  {
    id: 'M10',
    method: 'PUT',
    path: '/v1/patient-sessions/current/refresh-token',
    status: 400,
    body: { code: 400, message: '账号或密码错误，或验证信息无效', data: null },
  },
  {
    id: 'M11',
    method: 'POST',
    path: '/v1/password-resets',
    status: 200,
    body: ok(null),
  },
  {
    id: 'M12',
    method: 'POST',
    path: '/v1/identity-verifications',
    status: 200,
    body: ok({
      authStatus: 'VERIFIED',
      consultationAllowed: true,
      verifiedAt: '2026-11-07T11:02:10',
      maskedName: '张*',
      maskedIdCard: '110***********002X',
    }),
  },
  {
    id: 'M13',
    method: 'POST',
    path: '/v1/identity-verifications',
    status: 400,
    body: { code: 400, message: '账号或密码错误，或验证信息无效', data: null },
  },
  {
    id: 'M14',
    method: 'GET',
    path: '/v1/patients/current/identity-status',
    status: 200,
    body: ok({
      authStatus: 'UNVERIFIED',
      consultationAllowed: false,
      verifiedAt: null,
      maskedName: null,
      maskedIdCard: null,
    }),
  },
  {
    id: 'M15',
    method: 'POST',
    path: '*',
    status: 503,
    body: { code: 503, message: '服务暂时不可用，请稍后重试', data: null },
  },
]

/** method + path → 默认场景编号；M15 需显式指定场景 */
export const DEFAULT_SCENARIO_BY_ROUTE: Record<string, string> = {
  'POST /v1/verification-codes': 'M1',
  'POST /v1/patients': 'M3',
  'POST /v1/patient-sessions': 'M6',
  'DELETE /v1/patient-sessions/current': 'M8',
  'PUT /v1/patient-sessions/current/refresh-token': 'M9',
  'POST /v1/password-resets': 'M11',
  'POST /v1/identity-verifications': 'M12',
  'GET /v1/patients/current/identity-status': 'M14',
}

export function findMock(scenarioId: string): PatientIdentityMock | undefined {
  return PATIENT_IDENTITY_MOCKS.find((item) => item.id === scenarioId)
}

export function defaultMockFor(method: string, path: string): PatientIdentityMock | undefined {
  const id = DEFAULT_SCENARIO_BY_ROUTE[`${method.toUpperCase()} ${path}`]
  return id ? findMock(id) : undefined
}
