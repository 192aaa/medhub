/**
 * 类型同步来源：
 * - specs/health-check/contracts/health-api.md、specs/health-check/data-model.md（健康检查）
 * - specs/001-patient-identity/contracts/patient-identity-api.md（8 个端点）
 * 枚举与字段定义：specs/001-patient-identity/data-model.md 1.1–1.9
 * 契约是前后端唯一参考；contracts 变更时须同步本文件与 src/api/mocks/。
 */

/** 统一响应 { code, message, data }（宪法 III）；失败响应 data 恒为 null */
export interface ApiResult<T> {
  code: number
  message: string
  data: T | null
}

/** 分页响应 data 结构（宪法 III） */
export interface PageData<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/** 状态枚举唯一来源：specs/health-check/data-model.md 1.1 */
export const HEALTH_STATUS = {
  UP: 'UP',
  DOWN: 'DOWN',
} as const

export type HealthStatus = (typeof HEALTH_STATUS)[keyof typeof HEALTH_STATUS]

/** 组件名固定 3 项：specs/health-check/data-model.md 2.3 */
export type HealthComponentName = 'server' | 'database' | 'redis'

/** 契约响应结构：specs/health-check/contracts/health-api.md */
export interface ComponentStatus {
  name: HealthComponentName
  status: HealthStatus
  detail: string
  elapsedMs: number
}

/** 契约响应结构：specs/health-check/contracts/health-api.md */
export interface HealthReport {
  status: HealthStatus
  components: ComponentStatus[]
  /** ISO 8601：yyyy-MM-dd'T'HH:mm:ss，禁止时间戳数组 */
  checkedAt: string
}

/* ============ 患者注册与实名认证：001-patient-identity ============ */

/** 枚举唯一来源：specs/001-patient-identity/data-model.md 1.1 */
export const AUTH_STATUS = {
  UNVERIFIED: 'UNVERIFIED',
  VERIFIED: 'VERIFIED',
} as const

export type AuthStatus = (typeof AUTH_STATUS)[keyof typeof AUTH_STATUS]

/** 枚举唯一来源：specs/001-patient-identity/data-model.md 1.2 */
export const VERIFICATION_PURPOSE = {
  REGISTER: 'REGISTER',
  RESET_PASSWORD: 'RESET_PASSWORD',
} as const

export type VerificationPurpose = (typeof VERIFICATION_PURPOSE)[keyof typeof VERIFICATION_PURPOSE]

/** 端点 1：POST /v1/verification-codes */
export interface VerificationCodeSendRequest {
  phone: string
  purpose: VerificationPurpose
}

export interface VerificationCodeSendResponse {
  purpose: VerificationPurpose
  maskedPhone: string
  expiresInSeconds: number
  nextSendAllowedInSeconds: number
}

/** 端点 2：POST /v1/patients */
export interface PatientRegisterRequest {
  phone: string
  verificationCode: string
  password: string
  agreementAccepted: boolean
  agreementVersion: string
}

export interface PatientRegisterResponse {
  patientId: number
  maskedPhone: string
  authStatus: AuthStatus
  consultationAllowed: boolean
  accessToken: string
  accessTokenExpiresInSeconds: number
  registeredAt: string
  agreementVersion: string
}

/** 端点 3：POST /v1/patient-sessions */
export interface PatientLoginRequest {
  phone: string
  password: string
}

export interface PatientLoginResponse {
  patientId: number
  maskedPhone: string
  authStatus: AuthStatus
  consultationAllowed: boolean
  accessToken: string
  accessTokenExpiresInSeconds: number
}

/** 端点 4：DELETE /v1/patient-sessions/current（data 恒为 null） */
export type PatientLogoutResponse = null

/** 端点 5：PUT /v1/patient-sessions/current/refresh-token */
export interface RefreshTokenResponse {
  accessToken: string
  accessTokenExpiresInSeconds: number
}

/** 端点 6：POST /v1/password-resets（data 恒为 null） */
export interface PasswordResetRequest {
  phone: string
  verificationCode: string
  newPassword: string
}

export type PasswordResetResponse = null

/** 端点 7：POST /v1/identity-verifications */
export interface IdentityVerificationRequest {
  realName: string
  idCardNumber: string
}

export interface IdentityVerificationResponse {
  authStatus: AuthStatus
  consultationAllowed: boolean
  verifiedAt: string
  maskedName: string
  maskedIdCard: string
}

/** 端点 8：GET /v1/patients/current/identity-status（未认证时三个字段仍须存在且为 null） */
export interface IdentityStatusResponse {
  authStatus: AuthStatus
  consultationAllowed: boolean
  verifiedAt: string | null
  maskedName: string | null
  maskedIdCard: string | null
}

/* ============ 预约挂号与图文问诊：002-appointment-consultation ============ */
/* 枚举唯一来源：specs/002-appointment-consultation/data-model.md 1.1–1.8 */

export const APPOINTMENT_STATUS = {
  PENDING_PAYMENT: 'PENDING_PAYMENT',
  PAID: 'PAID',
  CANCELLED: 'CANCELLED',
  CLOSED: 'CLOSED',
} as const
export type AppointmentStatus = (typeof APPOINTMENT_STATUS)[keyof typeof APPOINTMENT_STATUS]

export const PAYMENT_STATUS = {
  UNPAID: 'UNPAID',
  PAID: 'PAID',
  REFUNDED: 'REFUNDED',
  CLOSED: 'CLOSED',
} as const
export type PaymentStatus = (typeof PAYMENT_STATUS)[keyof typeof PAYMENT_STATUS]

export const PAYMENT_RECORD_STATUS = {
  PENDING: 'PENDING',
  SUCCESS: 'SUCCESS',
  FAILED: 'FAILED',
  CANCELLED: 'CANCELLED',
} as const
export type PaymentRecordStatus = (typeof PAYMENT_RECORD_STATUS)[keyof typeof PAYMENT_RECORD_STATUS]

export const PAYMENT_CHANNEL = {
  MOCK: 'MOCK',
  WECHAT: 'WECHAT',
  ALIPAY: 'ALIPAY',
} as const
export type PaymentChannel = (typeof PAYMENT_CHANNEL)[keyof typeof PAYMENT_CHANNEL]

export const CONSULTATION_STATUS = {
  PENDING: 'PENDING',
  IN_PROGRESS: 'IN_PROGRESS',
  REPLIED: 'REPLIED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
} as const
export type ConsultationStatus = (typeof CONSULTATION_STATUS)[keyof typeof CONSULTATION_STATUS]

export const DOCTOR_ONLINE_STATUS = {
  ONLINE: 'ONLINE',
  OFFLINE: 'OFFLINE',
  BUSY: 'BUSY',
} as const
export type DoctorOnlineStatus = (typeof DOCTOR_ONLINE_STATUS)[keyof typeof DOCTOR_ONLINE_STATUS]

export const SCHEDULE_PERIOD = {
  MORNING: 'MORNING',
  AFTERNOON: 'AFTERNOON',
  EVENING: 'EVENING',
} as const
export type SchedulePeriod = (typeof SCHEDULE_PERIOD)[keyof typeof SCHEDULE_PERIOD]

export const TRIAGE_RESULT_STATUS = {
  SUCCESS: 'SUCCESS',
  TIMEOUT_FALLBACK: 'TIMEOUT_FALLBACK',
} as const
export type TriageResultStatus = (typeof TRIAGE_RESULT_STATUS)[keyof typeof TRIAGE_RESULT_STATUS]

/* 端点 1：GET /v1/departments */
export interface DepartmentResponse {
  departmentId: number
  name: string
  description: string | null
  iconUrl: string | null
  sortOrder: number
  enabled: boolean
}

/* 端点 2：GET /v1/doctors */
export interface DoctorResponse {
  doctorId: number
  name: string
  title: string | null
  departmentId: number
  hospital: string | null
  yearsOfExperience: number | null
  goodAt: string | null
  consultationFee: number
  rating: number | null
  consultationCount: number
  onlineStatus: DoctorOnlineStatus
  avatarUrl: string | null
}

/* 端点 3：GET /v1/doctors/{doctorId} */
export interface ScheduleResponse {
  scheduleId: number
  workDate: string
  period: SchedulePeriod
  slotsTotal: number
  slotsBooked: number
}
export interface DoctorDetailResponse {
  doctorId: number
  name: string
  title: string | null
  departmentId: number
  hospital: string | null
  yearsOfExperience: number | null
  goodAt: string | null
  consultationFee: number
  rating: number | null
  consultationCount: number
  onlineStatus: DoctorOnlineStatus
  avatarUrl: string | null
  schedule: ScheduleResponse[]
}

/* 端点 4：POST /v1/triage-sessions */
export interface TriageRequest {
  complaint: string
}
export interface TriageDepartment {
  departmentId: number
  name: string
}
export interface TriageResponse {
  triageSessionId: number
  status: TriageResultStatus
  recommendedDepartmentIds: number[]
  recommendedDepartments: TriageDepartment[]
  fallbackDepartments: TriageDepartment[]
  modelUsed: string | null
  elapsedMs: number
}

/* 端点 5：POST /v1/appointments */
export interface AppointmentCreateRequest {
  doctorId: number
  scheduleId: number
  patientNote?: string | null
}
export interface AppointmentCreateResponse {
  appointmentId: number
  doctorId: number
  departmentId: number
  appointmentDate: string
  period: SchedulePeriod
  feeAmount: number
  paymentStatus: PaymentStatus
  appointmentStatus: AppointmentStatus
  createdAt: string
}

/* 端点 6a：POST /v1/appointments/{appointmentId}/payments */
export interface PaymentRequest {
  channel: PaymentChannel
  amount: number
}
export interface PaymentResponse {
  appointmentId: number
  paymentId: number
  amount: number
  channel: PaymentChannel
  status: PaymentRecordStatus
  paidAt: string | null
  paymentStatus: PaymentStatus
  appointmentStatus: AppointmentStatus
}

/* 端点 6b：GET /v1/appointments/{appointmentId}/payments */
export interface PaymentStatusResponse {
  appointmentId: number
  paymentId: number | null
  amount: number | null
  channel: PaymentChannel | null
  status: PaymentRecordStatus | PaymentStatus
  paidAt: string | null
  paymentStatus: PaymentStatus
  appointmentStatus: AppointmentStatus
}

/* 端点 7：POST /v1/consultations/images */
export interface ImageUploadResponse {
  imageId: number
  url: string
  contentType: string
  sizeBytes: number
}

/* 端点 8：POST /v1/consultations */
export interface ConsultationSubmitRequest {
  appointmentId: number
  complaint: string
  description?: string | null
  imageUrls?: string[] | null
}
export interface ConsultationSubmitResponse {
  consultationId: number
  appointmentId: number
  doctorId: number
  departmentId: number
  complaint: string
  imageCount: number
  status: ConsultationStatus
  summary: string | null
  createdAt: string
}

/* 端点 9：GET /v1/consultations/{consultationId} */
export interface ConsultationImageDto {
  imageId: number
  url: string
}
export interface ConsultationDetailResponse {
  consultationId: number
  appointmentId: number
  doctorId: number
  departmentId: number
  complaint: string
  description: string | null
  images: ConsultationImageDto[]
  summary: string | null
  status: ConsultationStatus
  createdAt: string
  updatedAt: string
}

/* ============ 医生接诊：003-doctor-consultation ============ */
/* 枚举唯一来源：specs/003-doctor-consultation/data-model.md 1.2–1.5 */

export const PRESCRIPTION_STATUS = {
  DRAFT: 'DRAFT',
  SIGNED: 'SIGNED',
  EXPIRED: 'EXPIRED',
  REVOKED: 'REVOKED',
} as const
export type PrescriptionStatus = (typeof PRESCRIPTION_STATUS)[keyof typeof PRESCRIPTION_STATUS]

export const MESSAGE_TYPE = {
  DOCTOR: 'DOCTOR',
  PATIENT: 'PATIENT',
  SYSTEM: 'SYSTEM',
} as const
export type MessageType = (typeof MESSAGE_TYPE)[keyof typeof MESSAGE_TYPE]

export const CONSULTATION_TIMEOUT_STATUS = {
  NONE: 'NONE',
  ADMIN_NOTIFIED: 'ADMIN_NOTIFIED',
  TRANSFERRED: 'TRANSFERRED',
} as const
export type ConsultationTimeoutStatus =
  (typeof CONSULTATION_TIMEOUT_STATUS)[keyof typeof CONSULTATION_TIMEOUT_STATUS]

export const DOCTOR_ACCOUNT_STATUS = {
  ACTIVE: 'ACTIVE',
  DISABLED: 'DISABLED',
} as const
export type DoctorAccountStatus = (typeof DOCTOR_ACCOUNT_STATUS)[keyof typeof DOCTOR_ACCOUNT_STATUS]

export const AI_DECISION_STATUS = {
  SUCCESS: 'SUCCESS',
  TIMEOUT_FALLBACK: 'TIMEOUT_FALLBACK',
} as const
export type AiDecisionStatus = (typeof AI_DECISION_STATUS)[keyof typeof AI_DECISION_STATUS]

/* 端点 1：POST /v1/workbench/sessions */
export interface DoctorLoginRequest {
  username: string
  password: string
}
export interface DoctorLoginResponse {
  accessToken: string
  refreshToken: string
  doctorId: number
  nameMask: string
  title: string | null
  departmentId: number
  expiresIn: number
}

/* 端点 2：GET /v1/workbench/consultations */
export interface ConsultationListItem {
  consultationId: number
  patientName: string
  complaint: string
  status: ConsultationStatus
  submittedAt: string
}

/* 端点 3：GET /v1/workbench/consultations/{id} */
export interface WorkbenchConsultationDetail {
  consultationId: number
  patientName: string
  complaint: string
  description: string | null
  images: ConsultationImageDto[]
  summary: string | null
  status: ConsultationStatus
  acceptedAt: string | null
  createdAt: string
}

/* 端点 4：POST /v1/workbench/consultations/{id}/accept */
export interface AcceptResponse {
  consultationId: number
  status: ConsultationStatus
  acceptedAt: string
  doctorId: number
}

/* 端点 5/6：对话消息 */
export interface MessageItem {
  messageId: number
  senderType: MessageType
  content: string
  createdAt: string
}
export interface MessageRequest {
  content: string
}
export interface MessageResponse {
  messageId: number
  senderType: MessageType
  content: string
  createdAt: string
}

/* 端点 7：POST /v1/workbench/consultations/{id}/prescriptions */
export interface PrescriptionItemRequest {
  drugId: number
  spec?: string | null
  dosage: string
  caution?: string | null
}
export interface PrescriptionCreateRequest {
  items: PrescriptionItemRequest[]
}
export interface PrescriptionItemResponse {
  drugId: number
  drugName: string
  spec: string | null
  dosage: string
  caution: string | null
}
export interface PrescriptionResponse {
  prescriptionId: number
  consultationId: number
  status: PrescriptionStatus
  items: PrescriptionItemResponse[]
  signedAt: string
  validUntil: string
  qrCode: string
  watermark: string
}

/* 端点 8：GET /v1/workbench/prescriptions/{id} */
export interface PrescriptionDetailResponse {
  prescriptionId: number
  consultationId: number
  status: PrescriptionStatus
  items: PrescriptionItemResponse[]
  signedAt: string | null
  validUntil: string | null
  expired: boolean
  qrCode: string | null
  watermark: string | null
}

/* 端点 9：POST /v1/workbench/consultations/{id}/ai-decision */
export interface AiDecisionRequest {
  query?: string | null
}
export interface AiDecisionResponse {
  decisionId: number
  suggestions: string[]
  disclaimer: string
  modelUsed: string | null
  status: AiDecisionStatus
}

/* ---------- 004 处方取药：specs/004-prescription-dispensing/contracts ---------- */

/* 端点 1：POST /v1/pharmacy/sessions */
export interface PharmacyLoginRequest {
  username: string
  password: string
}
export interface PharmacyLoginResponse {
  accessToken: string
  pharmacyId: number
  nameMask: string
  pharmacyName: string
  expiresIn: number
}

/** 处方明细视图（验真与核销响应共用） */
export interface PrescriptionItemView {
  drugId: number
  drugName: string | null
  spec: string | null
  dosage: string
  caution: string | null
}

/** 派生取药状态（不落库） */
export type DispenseStatus = 'PENDING' | 'DISPENSED' | 'EXPIRED' | 'REVOKED'

/* 端点 2：POST /v1/pharmacy/prescription-verifications（两个分支互斥） */
export interface VerificationRequest {
  qrContent?: string
  prescriptionId?: number
  verifyCode?: string
}
export interface VerificationResponse {
  verificationResult: 'VALID'
  prescriptionId: number
  doctorNameMask: string | null
  signedAt: string | null
  validUntil: string | null
  expired: boolean
  status: string
  dispenseStatus: DispenseStatus
  dispensable: boolean
  dispensedAt: string | null
  dispensedByMask: string | null
  dispenseRecordId: number | null
  watermark: string | null
  verifyCode: string
  items: PrescriptionItemView[]
}

/* ============ AI健康咨询：005-ai-health-consultation ============ */

/** 枚举唯一来源：specs/005-ai-health-consultation/data-model.md 1.1 */
export const MESSAGE_ROLE = {
  PATIENT: 'PATIENT',
  ASSISTANT: 'ASSISTANT',
} as const

export type MessageRole = (typeof MESSAGE_ROLE)[keyof typeof MESSAGE_ROLE]

/** 枚举唯一来源：specs/005-ai-health-consultation/data-model.md 1.2 */
export const SESSION_STATUS = {
  ACTIVE: 'ACTIVE',
  DELETED: 'DELETED',
} as const

export type SessionStatus = (typeof SESSION_STATUS)[keyof typeof SESSION_STATUS]

/** 枚举唯一来源：specs/005-ai-health-consultation/data-model.md 1.3 */
export const STREAM_STATUS = {
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
  INTERRUPTED: 'INTERRUPTED',
} as const

export type StreamStatus = (typeof STREAM_STATUS)[keyof typeof STREAM_STATUS]

/** 端点 1：GET /v1/health-consultation/sessions */
export interface HealthSessionSummary {
  sessionId: number
  title: string
  lastActiveAt: string
  messageCount: number
  lastMessagePreview: string | null
}

/** 端点 2：POST /v1/health-consultation/sessions */
export interface HealthSessionCreateResponse {
  sessionId: number
  title: string
  createdAt: string
}

/** 端点 3：PATCH /v1/health-consultation/sessions/{sessionId} */
export interface HealthSessionRenameRequest {
  title: string
}

export interface HealthSessionRenameResponse {
  sessionId: number
  title: string
}

/** 端点 4：DELETE /v1/health-consultation/sessions/{sessionId} */
export interface HealthSessionDeleteResponse {
  sessionId: number
  deleted: boolean
}

/** 端点 5：DELETE /v1/health-consultation/sessions */
export interface HealthSessionClearAllResponse {
  deletedCount: number
}

/** 端点 6：GET /v1/health-consultation/sessions/{sessionId}/messages */
export interface HealthMessageItem {
  messageId: number
  role: MessageRole
  /** 正文；助手消息不含免责声明（声明在 disclaimer 独立字段） */
  content: string
  disclaimer: string | null
  emergency: boolean
  streamStatus: StreamStatus
  createdAt: string
}

/** 端点 7：POST /v1/health-consultation/sessions/{sessionId}/messages（SSE） */
export interface HealthChatRequest {
  content: string
}

export interface HealthAcceptedEvent {
  messageId: number
  sessionId: number
}

export interface HealthBooking {
  departmentId: number
  departmentName: string
}

/** 仅紧急时发送，必须先于任何 delta */
export interface HealthEmergencyEvent {
  level: string
  title: string
  advice: string
  ruleCode: string
  booking: HealthBooking | null
}

export interface HealthDeltaEvent {
  text: string
}

/** 服务端固定文案，前端只渲染、禁止拼接 */
export interface HealthDisclaimerEvent {
  text: string
}

export interface HealthDoneEvent {
  messageId: number
  truncated: boolean
  modelUsed: string | null
}

/** 与 done 互斥；已发送的 delta 内容保留 */
export interface HealthErrorEvent {
  code: number
  message: string
  retryable: boolean
}

/* ============ AI辅助治疗决策：006-ai-treatment-decision ============ */
/* 枚举唯一来源：specs/006-ai-treatment-decision/data-model.md 1.1–1.3 */

export const TREATMENT_DECISION_STATUS = {
  SUCCESS: 'SUCCESS',
  TIMEOUT_FALLBACK: 'TIMEOUT_FALLBACK',
  ERROR_FALLBACK: 'ERROR_FALLBACK',
  EMPTY_FALLBACK: 'EMPTY_FALLBACK',
} as const
export type TreatmentDecisionStatus =
  (typeof TREATMENT_DECISION_STATUS)[keyof typeof TREATMENT_DECISION_STATUS]

export const TREATMENT_TRIGGER_TYPE = {
  AUTO: 'AUTO',
  REGENERATE: 'REGENERATE',
} as const
export type TreatmentTriggerType =
  (typeof TREATMENT_TRIGGER_TYPE)[keyof typeof TREATMENT_TRIGGER_TYPE]

export const DEGRADED_REASON = {
  NONE: 'NONE',
  TIMEOUT: 'TIMEOUT',
  UPSTREAM_ERROR: 'UPSTREAM_ERROR',
  PARSE_FAILED: 'PARSE_FAILED',
  EMPTY: 'EMPTY',
  KNOWLEDGE_UNAVAILABLE: 'KNOWLEDGE_UNAVAILABLE',
} as const
export type DegradedReason = (typeof DEGRADED_REASON)[keyof typeof DEGRADED_REASON]

/** 端点 1/2/3 响应 summary；降级时为 null */
export interface CaseSummaryView {
  chiefComplaint: string
  presentIllness: string
  dialogHighlights: string[]
  riskAlerts: string[]
  evidenceLimited: boolean
  /** 摘要文本生成时间（复用时为首次生成时间） */
  generatedAt: string
  modelUsed: string | null
}

/** 端点 1/2/3 响应 suggestions[] */
export interface DiagnosisSuggestionView {
  diagnosis: string
  likelihood: string
  basis: string[]
  workup: string[]
  riskAlert: string | null
}

/** 端点 1/2/3 响应 medicationDrafts[]；drugId 由服务端映射 */
export interface MedicationDraftView {
  drugId: number | null
  drugName: string
  spec: string | null
  dosage: string | null
  caution: string | null
  needsCompletion: boolean
}

/** 端点 1/2/3 共用响应 data；降级时 summary=null、数组为空 */
export interface TreatmentDecisionResponse {
  decisionId: number
  consultationId: number
  status: TreatmentDecisionStatus
  triggerType: TreatmentTriggerType
  reused: boolean
  summary: CaseSummaryView | null
  suggestions: DiagnosisSuggestionView[]
  medicationDrafts: MedicationDraftView[]
  ignoredMedications: string[]
  disclaimer: string
  degradedReason: DegradedReason
  elapsedMs: number
  generatedAt: string
}

/** 端点 4 请求体（0–20，缺省或越界返回 400） */
export interface TreatmentAdoptionRequest {
  appliedItemCount: number
}

export interface TreatmentAdoptionResponse {
  decisionId: number
  appliedItemCount: number
}

/** 端点 5 药品条目 */
export interface DrugView {
  drugId: number
  drugName: string
  spec: string | null
  unit: string | null
  prescriptionOnly: boolean
}

/** 端点 5 分页 data，与统一分页结构一致 */
export type DrugListResponse = PageData<DrugView>
