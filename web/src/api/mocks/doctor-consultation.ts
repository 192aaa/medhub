/**
 * 医生接诊 Mock 数据（M1–M9）。
 * 唯一来源：specs/003-doctor-consultation/contracts/mocks.md
 * 同步要求：契约变更时同步本文件 → src/api/types.ts → E2E 断言。
 *
 * message 逐字复制自 mocks.md；禁止出现完整手机号/身份证号/签名值明文。
 */
import type {
  DoctorLoginResponse,
  ConsultationListItem,
  WorkbenchConsultationDetail,
  AcceptResponse,
  MessageItem,
  MessageResponse,
  PrescriptionResponse,
  PrescriptionDetailResponse,
  AiDecisionResponse,
} from '../types'

export const MOCK_DOCTOR_LOGIN: DoctorLoginResponse = {
  accessToken: 'mock-doctor-access-token',
  refreshToken: 'mock-doctor-refresh-token',
  doctorId: 101,
  nameMask: '张*',
  title: '主任医师',
  departmentId: 1,
  expiresIn: 86400,
}

export const MOCK_CONSULTATION_LIST: ConsultationListItem[] = [
  {
    consultationId: 701,
    patientName: '王*',
    complaint: '咳嗽伴低热三天',
    status: 'PENDING',
    submittedAt: '2026-11-08T10:30:00',
  },
]

export const MOCK_CONSULTATION_LIST_IN_PROGRESS: ConsultationListItem[] = [
  {
    consultationId: 702,
    patientName: '李*',
    complaint: '头痛伴乏力一天',
    status: 'IN_PROGRESS',
    submittedAt: '2026-11-08T09:00:00',
  },
]

export const MOCK_CONSULTATION_DETAIL: WorkbenchConsultationDetail = {
  consultationId: 701,
  patientName: '王*',
  complaint: '咳嗽伴低热三天',
  description: '夜间加重，无痰',
  images: [{ imageId: 601, url: '/static/consultation-images/abc123.jpg' }],
  summary: '患者主诉咳嗽伴低热三天，建议排查呼吸道感染。',
  status: 'PENDING',
  acceptedAt: null,
  createdAt: '2026-11-08T10:30:00',
}

export const MOCK_ACCEPT: AcceptResponse = {
  consultationId: 701,
  status: 'IN_PROGRESS',
  acceptedAt: '2026-11-08T11:00:00',
  doctorId: 101,
}

export const MOCK_MESSAGES: MessageItem[] = [
  { messageId: 801, senderType: 'PATIENT', content: '医生您好', createdAt: '2026-11-08T11:02:00' },
  {
    messageId: 802,
    senderType: 'DOCTOR',
    content: '请描述具体症状',
    createdAt: '2026-11-08T11:03:00',
  },
]

export const MOCK_MESSAGE_REPLY: MessageResponse = {
  messageId: 803,
  senderType: 'DOCTOR',
  content: '请先测量体温并多休息',
  createdAt: '2026-11-08T11:04:00',
}

export const MOCK_PRESCRIPTION: PrescriptionResponse = {
  prescriptionId: 1001,
  consultationId: 701,
  status: 'SIGNED',
  items: [
    {
      drugId: 901,
      drugName: '阿莫西林胶囊',
      spec: '0.25g*20',
      dosage: '每日三次，每次一片',
      caution: '饭后服用',
    },
  ],
  signedAt: '2026-11-08T11:05:00',
  validUntil: '2026-11-11T11:05:00',
  qrCode: 'http://localhost:5183/verify-prescription?prescriptionId=1001&sig=abc123',
  watermark: '示例医院 1001 2026-11-08T11:05:00',
}

export const MOCK_PRESCRIPTION_DETAIL: PrescriptionDetailResponse = {
  prescriptionId: 1001,
  consultationId: 701,
  status: 'SIGNED',
  items: [
    {
      drugId: 901,
      drugName: '阿莫西林胶囊',
      spec: '0.25g*20',
      dosage: '每日三次，每次一片',
      caution: '饭后服用',
    },
  ],
  signedAt: '2026-11-08T11:05:00',
  validUntil: '2026-11-11T11:05:00',
  expired: false,
  qrCode: 'http://localhost:5183/verify-prescription?prescriptionId=1001&sig=abc123',
  watermark: '示例医院 1001 2026-11-08T11:05:00',
}

export const MOCK_AI_DECISION: AiDecisionResponse = {
  decisionId: 1101,
  suggestions: ['疑似上呼吸道感染，建议结合查体。', '阿莫西林过敏者禁用。'],
  disclaimer: '本内容为 AI 辅助参考，不作为诊断结论。',
  modelUsed: 'DeepSeek-Flash-1017',
  status: 'SUCCESS',
}

export const MOCK_AI_DECISION_TIMEOUT: AiDecisionResponse = {
  decisionId: 1102,
  suggestions: [],
  disclaimer: '本内容为 AI 辅助参考，不作为诊断结论。',
  modelUsed: null,
  status: 'TIMEOUT_FALLBACK',
}
