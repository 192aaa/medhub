/**
 * 预约挂号与图文问诊 Mock 数据（M1–M16）。
 * 唯一来源：specs/002-appointment-consultation/contracts/mocks.md
 * 同步要求：契约变更时同步本文件 → src/api/types.ts → E2E 断言（契约 README 第 5 节）。
 *
 * message 逐字复制自 mocks.md；禁止出现完整手机号/身份证号/支付敏感信息明文。
 * 键格式：`METHOD /v1/path`；`#SCENARIO` 后缀表示异常/降级分支，供 E2E 按场景覆盖。
 */
import type {
  ApiResult,
  DepartmentResponse,
  DoctorResponse,
  DoctorDetailResponse,
  TriageResponse,
  AppointmentCreateResponse,
  PaymentResponse,
  PaymentStatusResponse,
  ImageUploadResponse,
  ConsultationSubmitResponse,
  ConsultationDetailResponse,
} from '../types'

export const MOCK_DEPARTMENTS: DepartmentResponse[] = [
  {
    departmentId: 1,
    name: '内科',
    description: '常见内科疾病',
    iconUrl: null,
    sortOrder: 1,
    enabled: true,
  },
  {
    departmentId: 2,
    name: '外科',
    description: '外科诊疗',
    iconUrl: null,
    sortOrder: 2,
    enabled: true,
  },
]

export const MOCK_DOCTORS: DoctorResponse[] = [
  {
    doctorId: 101,
    name: '张*',
    title: '主任医师',
    departmentId: 1,
    hospital: '示例医院',
    yearsOfExperience: 15,
    goodAt: '呼吸系统疾病',
    consultationFee: 30.0,
    rating: 4.8,
    consultationCount: 1200,
    onlineStatus: 'ONLINE',
    avatarUrl: null,
  },
]

export const MOCK_DOCTOR_DETAIL: DoctorDetailResponse = {
  doctorId: 101,
  name: '张*',
  title: '主任医师',
  departmentId: 1,
  hospital: '示例医院',
  yearsOfExperience: 15,
  goodAt: '呼吸系统疾病',
  consultationFee: 30.0,
  rating: 4.8,
  consultationCount: 1200,
  onlineStatus: 'ONLINE',
  avatarUrl: null,
  schedule: [
    { scheduleId: 201, workDate: '2026-11-10', period: 'MORNING', slotsTotal: 20, slotsBooked: 5 },
  ],
}

export const MOCK_TRIAGE_SUCCESS: TriageResponse = {
  triageSessionId: 301,
  status: 'SUCCESS',
  recommendedDepartmentIds: [1],
  recommendedDepartments: [{ departmentId: 1, name: '内科' }],
  fallbackDepartments: [],
  modelUsed: 'deepseek-v4-flash',
  elapsedMs: 820,
}

export const MOCK_TRIAGE_TIMEOUT: TriageResponse = {
  triageSessionId: 302,
  status: 'TIMEOUT_FALLBACK',
  recommendedDepartmentIds: [],
  recommendedDepartments: [],
  fallbackDepartments: [
    { departmentId: 1, name: '内科' },
    { departmentId: 2, name: '外科' },
  ],
  modelUsed: null,
  elapsedMs: 5021,
}

export const MOCK_APPOINTMENT: AppointmentCreateResponse = {
  appointmentId: 401,
  doctorId: 101,
  departmentId: 1,
  appointmentDate: '2026-11-10',
  period: 'MORNING',
  feeAmount: 30.0,
  paymentStatus: 'UNPAID',
  appointmentStatus: 'PENDING_PAYMENT',
  createdAt: '2026-11-08T10:20:00',
}

export const MOCK_PAYMENT: PaymentResponse = {
  appointmentId: 401,
  paymentId: 501,
  amount: 30.0,
  channel: 'MOCK',
  status: 'SUCCESS',
  paidAt: '2026-11-08T10:25:00',
  paymentStatus: 'PAID',
  appointmentStatus: 'PAID',
}

export const MOCK_PAYMENT_PENDING: PaymentResponse = {
  appointmentId: 401,
  paymentId: 501,
  amount: 30.0,
  channel: 'MOCK',
  status: 'PENDING',
  paidAt: null,
  paymentStatus: 'UNPAID',
  appointmentStatus: 'PENDING_PAYMENT',
}

export const MOCK_PAYMENT_STATUS: PaymentStatusResponse = {
  appointmentId: 401,
  paymentId: 501,
  amount: 30.0,
  channel: 'MOCK',
  status: 'PAID',
  paidAt: '2026-11-08T10:25:00',
  paymentStatus: 'PAID',
  appointmentStatus: 'PAID',
}

export const MOCK_IMAGE: ImageUploadResponse = {
  imageId: 601,
  url: '/static/consultation-images/abc123.jpg',
  contentType: 'image/jpeg',
  sizeBytes: 123456,
}

export const MOCK_CONSULTATION: ConsultationSubmitResponse = {
  consultationId: 701,
  appointmentId: 401,
  doctorId: 101,
  departmentId: 1,
  complaint: '咳嗽伴低热三天',
  imageCount: 1,
  status: 'PENDING',
  summary: '患者主诉咳嗽伴低热三天，建议排查呼吸道感染。',
  createdAt: '2026-11-08T10:30:00',
}

export const MOCK_CONSULTATION_DETAIL: ConsultationDetailResponse = {
  consultationId: 701,
  appointmentId: 401,
  doctorId: 101,
  departmentId: 1,
  complaint: '咳嗽伴低热三天',
  description: '夜间加重，无痰',
  images: [{ imageId: 601, url: '/static/consultation-images/abc123.jpg' }],
  summary: '患者主诉咳嗽伴低热三天，建议排查呼吸道感染。',
  status: 'PENDING',
  createdAt: '2026-11-08T10:30:00',
  updatedAt: '2026-11-08T10:30:00',
}

type AnyResult = ApiResult<
  | DepartmentResponse[]
  | DoctorResponse[]
  | DoctorDetailResponse
  | TriageResponse
  | AppointmentCreateResponse
  | PaymentResponse
  | PaymentStatusResponse
  | ImageUploadResponse
  | ConsultationSubmitResponse
  | ConsultationDetailResponse
  | null
>

/** Mock 路由表：M1–M16。#SCENARIO 后缀为异常/降级分支覆盖。 */
export const appointmentConsultationMockResponses: Record<string, AnyResult> = {
  // M1 科室列表
  'GET /v1/departments': { code: 200, message: '成功', data: MOCK_DEPARTMENTS },
  // M2 医生列表
  'GET /v1/doctors': { code: 200, message: '成功', data: MOCK_DOCTORS },
  // M3 医生详情
  'GET /v1/doctors/101': { code: 200, message: '成功', data: MOCK_DOCTOR_DETAIL },
  // M4 AI 导诊成功
  'POST /v1/triage-sessions': { code: 200, message: '成功', data: MOCK_TRIAGE_SUCCESS },
  // M5 AI 导诊超时降级
  'POST /v1/triage-sessions#TIMEOUT_FALLBACK': {
    code: 200,
    message: '成功',
    data: MOCK_TRIAGE_TIMEOUT,
  },
  // M6 创建挂号预约
  'POST /v1/appointments': { code: 200, message: '成功', data: MOCK_APPOINTMENT },
  // M7 创建待支付流水
  'POST /v1/appointments/401/payments': { code: 200, message: '成功', data: MOCK_PAYMENT_PENDING },
  // M7b 确认支付（沙箱回调）
  'POST /v1/appointments/401/payments/501/confirm': {
    code: 200,
    message: '成功',
    data: MOCK_PAYMENT,
  },
  // M8 支付状态查询
  'GET /v1/appointments/401/payments': { code: 200, message: '成功', data: MOCK_PAYMENT_STATUS },
  // M9 上传图片
  'POST /v1/consultations/images': { code: 200, message: '成功', data: MOCK_IMAGE },
  // M10 提交问诊单
  'POST /v1/consultations': { code: 200, message: '成功', data: MOCK_CONSULTATION },
  // M11 查询问诊单详情
  'GET /v1/consultations/701': { code: 200, message: '成功', data: MOCK_CONSULTATION_DETAIL },
  // M12 复诊门禁拒绝
  'POST /v1/appointments#REVISIT_NOT_ELIGIBLE': {
    code: 403,
    message: '请先完成实名认证或确认复诊资格',
    data: null,
  },
  // M13 主诉格式越界
  'POST /v1/consultations#INVALID_COMPLAINT_FORMAT': {
    code: 400,
    message: '主诉长度须为 10-200 个字符',
    data: null,
  },
  // M14 图片超 5MB
  'POST /v1/consultations/images#INVALID_IMAGE_SIZE': {
    code: 400,
    message: '单张图片不得超过 5 MB',
    data: null,
  },
  // M15 越权访问他人问诊单
  'GET /v1/consultations/701#CONSULTATION_FORBIDDEN': {
    code: 403,
    message: '禁止访问他人问诊资料',
    data: null,
  },
  // M16 依赖降级
  'GET /v1/departments#SERVICE_UNAVAILABLE': {
    code: 503,
    message: '服务暂时不可用，请稍后重试',
    data: null,
  },
}
