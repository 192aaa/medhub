/**
 * 药店端 Mock 骨架（功能 004 · US1）。
 * 契约来源：specs/004-prescription-dispensing/contracts/prescription-dispensing-api.md
 *
 * 只放「成功 + 代表性失败」样例，字段名与契约逐字一致；US2/US3 的核销与患者处方
 * Mock 随对应端点一并补充。仅用于本地联调与前端单测，不进入生产构建路径。
 */
import type { ApiResult, PharmacyLoginResponse, VerificationResponse } from '../types'

export const pharmacyLoginSuccess: ApiResult<PharmacyLoginResponse> = {
  code: 200,
  message: '成功',
  data: {
    accessToken: 'mock-pharmacy-access-token',
    pharmacyId: 8101,
    nameMask: '王*',
    pharmacyName: '示例大药房（总部店）',
    expiresIn: 7200,
  },
}

/** 账号不存在 / 口令错误 / 账号禁用统一使用该文案（不区分原因） */
export const pharmacyLoginFailure: ApiResult<null> = {
  code: 400,
  message: '账号或密码错误，或验证信息无效',
  data: null,
}

const baseItems = [
  {
    drugId: 1901,
    drugName: '阿莫西林胶囊',
    spec: '0.25g*20',
    dosage: '口服，一次 2 粒，一日 3 次',
    caution: '青霉素过敏者禁用',
  },
]

const pendingData: VerificationResponse = {
  verificationResult: 'VALID',
  prescriptionId: 17001,
  doctorNameMask: '张*',
  signedAt: '2026-11-08T10:12:00',
  validUntil: '2026-11-11T10:12:00',
  expired: false,
  status: 'SIGNED',
  dispenseStatus: 'PENDING',
  dispensable: true,
  dispensedAt: null,
  dispensedByMask: null,
  dispenseRecordId: null,
  watermark: '示例医院 电子处方',
  verifyCode: 'Ab3dE9fG7hJk',
  items: baseItems,
}

/** 未取药：可核销 */
export const verificationPending: ApiResult<VerificationResponse> = {
  code: 200,
  message: '成功',
  data: pendingData,
}

/** 已取药：不可重复核销（200，非错误） */
export const verificationDispensed: ApiResult<VerificationResponse> = {
  code: 200,
  message: '成功',
  data: {
    ...pendingData,
    dispenseStatus: 'DISPENSED',
    dispensable: false,
    dispensedAt: '2026-11-08T12:00:00',
    dispensedByMask: '王*',
    dispenseRecordId: 5001,
  },
}

/** 已过期：仍返回 200，前端展示「已过期」而非错误页 */
export const verificationExpired: ApiResult<VerificationResponse> = {
  code: 200,
  message: '成功',
  data: {
    ...pendingData,
    expired: true,
    status: 'EXPIRED',
    dispenseStatus: 'EXPIRED',
    dispensable: false,
  },
}

/** 已作废：与过期对称，200 + REVOKED */
export const verificationRevoked: ApiResult<VerificationResponse> = {
  code: 200,
  message: '成功',
  data: {
    ...pendingData,
    status: 'REVOKED',
    dispenseStatus: 'REVOKED',
    dispensable: false,
  },
}

/** 凭证无效（令牌结构/验签/解密/exp/验真码任一失败），响应不含处方字段 */
export const verificationInvalidProof: ApiResult<null> = {
  code: 400,
  message: '验真凭证无效，请重新扫码',
  data: null,
}

/** 草稿处方：未签名不构成可展示处方 */
export const verificationNotSigned: ApiResult<null> = {
  code: 400,
  message: '处方未签名，无法取药',
  data: null,
}
