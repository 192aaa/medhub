/**
 * AI辅助治疗决策 Mock 数据（M1–M9）。
 * 唯一来源：specs/006-ai-treatment-decision/contracts/mocks.md
 * 同步要求：契约变更时同步本文件 → src/api/types.ts → E2E 断言。
 *
 * message 逐字复制自 mocks.md；演示数据均为合成数据，不含真实患者信息。
 */
import type {
  ApiResult,
  DrugListResponse,
  TreatmentAdoptionResponse,
  TreatmentDecisionResponse,
} from '../types'

/** M1 `POST /v1/workbench/consultations/501/treatment-decision` → 200 首次生成 */
export const MOCK_DECISION_SUCCESS: ApiResult<TreatmentDecisionResponse> = {
  code: 200,
  message: '成功',
  data: {
    decisionId: 1201,
    consultationId: 501,
    status: 'SUCCESS',
    triggerType: 'AUTO',
    reused: false,
    summary: {
      chiefComplaint: '咳嗽伴咳痰 3 天',
      presentIllness: '3 天前受凉后出现咳嗽，咳白色黏痰，无发热、无胸痛。',
      dialogHighlights: ['患者自述夜间咳嗽加重', '已服用阿莫西林 2 天效果一般'],
      riskAlerts: ['既往青霉素过敏史待确认'],
      evidenceLimited: false,
      generatedAt: '2026-11-08T10:20:00',
      modelUsed: 'DeepSeek-Flash-1017',
    },
    suggestions: [
      {
        diagnosis: '疑似上呼吸道感染',
        likelihood: 'MEDIUM',
        basis: ['主诉咳嗽咳痰 3 天', '无发热与胸痛'],
        workup: ['必要时血常规'],
        riskAlert: null,
      },
    ],
    medicationDrafts: [
      {
        drugId: 901,
        drugName: '阿莫西林胶囊',
        spec: '0.25g*20',
        dosage: '口服，一次 2 粒，一日 3 次',
        caution: '青霉素过敏者禁用',
        needsCompletion: false,
      },
    ],
    ignoredMedications: [],
    disclaimer: 'AI 生成，仅供参考，最终诊疗决策由医生负责',
    degradedReason: 'NONE',
    elapsedMs: 3400,
    generatedAt: '2026-11-08T10:20:00',
  },
}

/** M2 同端点 → 200 幂等复用 */
export const MOCK_DECISION_REUSED: ApiResult<TreatmentDecisionResponse> = {
  code: 200,
  message: '成功',
  data: {
    decisionId: 1201,
    consultationId: 501,
    status: 'SUCCESS',
    triggerType: 'AUTO',
    reused: true,
    summary: {
      chiefComplaint: '咳嗽伴咳痰 3 天',
      presentIllness: '3 天前受凉后出现咳嗽，咳白色黏痰，无发热、无胸痛。',
      dialogHighlights: ['患者自述夜间咳嗽加重', '已服用阿莫西林 2 天效果一般'],
      riskAlerts: ['既往青霉素过敏史待确认'],
      evidenceLimited: false,
      generatedAt: '2026-11-08T10:20:00',
      modelUsed: 'DeepSeek-Flash-1017',
    },
    suggestions: [
      {
        diagnosis: '疑似上呼吸道感染',
        likelihood: 'MEDIUM',
        basis: ['主诉咳嗽咳痰 3 天', '无发热与胸痛'],
        workup: ['必要时血常规'],
        riskAlert: null,
      },
    ],
    medicationDrafts: [
      {
        drugId: 901,
        drugName: '阿莫西林胶囊',
        spec: '0.25g*20',
        dosage: '口服，一次 2 粒，一日 3 次',
        caution: '青霉素过敏者禁用',
        needsCompletion: false,
      },
    ],
    ignoredMedications: [],
    disclaimer: 'AI 生成，仅供参考，最终诊疗决策由医生负责',
    degradedReason: 'NONE',
    elapsedMs: 0,
    generatedAt: '2026-11-08T10:20:00',
  },
}

/** M3 同端点 → 200 降级（超时） */
export const MOCK_DECISION_FALLBACK: ApiResult<TreatmentDecisionResponse> = {
  code: 200,
  message: '成功',
  data: {
    decisionId: 1202,
    consultationId: 501,
    status: 'TIMEOUT_FALLBACK',
    triggerType: 'AUTO',
    reused: false,
    summary: null,
    suggestions: [],
    medicationDrafts: [],
    ignoredMedications: [],
    disclaimer: 'AI 生成，仅供参考，最终诊疗决策由医生负责',
    degradedReason: 'TIMEOUT',
    elapsedMs: 15000,
    generatedAt: '2026-11-08T10:22:00',
  },
}

/** M4 `GET .../treatment-decision` → 404 无记录 */
export const MOCK_DECISION_NOT_FOUND: ApiResult<TreatmentDecisionResponse> = {
  code: 404,
  message: '暂无治疗决策辅助记录',
  data: null,
}

/** M5 `POST .../treatment-decision/regenerate` → 429 频控 */
export const MOCK_RATE_LIMITED: ApiResult<TreatmentDecisionResponse> = {
  code: 429,
  message: '辅助生成过于频繁，请稍后重试',
  data: null,
}

/** M6 `POST .../treatment-decision` → 409 并发 */
export const MOCK_BUSY: ApiResult<TreatmentDecisionResponse> = {
  code: 409,
  message: '辅助生成进行中，请稍候',
  data: null,
}

/** M7 `POST .../treatment-decision/adoptions` → 200 */
export const MOCK_ADOPTION: ApiResult<TreatmentAdoptionResponse> = {
  code: 200,
  message: '成功',
  data: { decisionId: 1201, appliedItemCount: 2 },
}

/** M8 `GET /v1/workbench/drugs?page=1&pageSize=20` → 200 */
export const MOCK_DRUGS: ApiResult<DrugListResponse> = {
  code: 200,
  message: '成功',
  data: {
    list: [
      {
        drugId: 901,
        drugName: '阿莫西林胶囊',
        spec: '0.25g*20',
        unit: '盒',
        prescriptionOnly: true,
      },
      {
        drugId: 902,
        drugName: '布洛芬缓释胶囊',
        spec: '0.3g*20',
        unit: '盒',
        prescriptionOnly: false,
      },
      {
        drugId: 903,
        drugName: '对乙酰氨基酚片',
        spec: '0.5g*12',
        unit: '盒',
        prescriptionOnly: false,
      },
    ],
    total: 3,
    page: 1,
    pageSize: 20,
  },
}

/** M9 `POST .../adoptions` 数量越界 → 400 */
export const MOCK_INVALID_ADOPTION: ApiResult<null> = {
  code: 400,
  message: '采纳数量不合法',
  data: null,
}
