/**
 * Mock 数据：AI健康咨询（与 specs/005-ai-health-consultation/contracts/mocks.md 同源）。
 *
 * 规则（宪法原则 VI）：字段与契约逐字一致，禁止凭空手写；日期 `yyyy-MM-dd'T'HH:mm:ss`；
 * 演示数据均为合成数据。M11 患者上下文仅服务端可见，**不在本文件出现**。
 */

/** M1 会话列表（成功） */
export const MOCK_SESSION_LIST = {
  code: 200,
  message: '成功',
  data: {
    list: [
      {
        sessionId: 3001,
        title: '咳嗽有痰能吃阿莫西林吗',
        lastActiveAt: '2026-11-08T10:12:00',
        messageCount: 6,
        lastMessagePreview: '建议复诊确认是否需要继续用药…',
      },
      {
        sessionId: 3002,
        title: '术后康复注意事项',
        lastActiveAt: '2026-11-07T20:05:00',
        messageCount: 4,
        lastMessagePreview: '前两周避免负重，逐步恢复活动量…',
      },
    ],
    total: 2,
    page: 1,
    pageSize: 20,
  },
} as const

/** M2 新建会话（成功） */
export const MOCK_SESSION_CREATE = {
  code: 200,
  message: '成功',
  data: { sessionId: 3003, title: '新对话', createdAt: '2026-11-08T10:30:00' },
} as const

/** M3 重命名（成功 / 非法标题） */
export const MOCK_SESSION_RENAME = {
  code: 200,
  message: '成功',
  data: { sessionId: 3003, title: '咳嗽用药咨询' },
} as const

export const MOCK_SESSION_RENAME_INVALID = {
  code: 400,
  message: '会话标题不合法',
  data: null,
} as const

/** M4 删除单个会话 / 清空全部（成功） */
export const MOCK_SESSION_DELETE = {
  code: 200,
  message: '成功',
  data: { sessionId: 3003, deleted: true },
} as const

export const MOCK_SESSION_CLEAR_ALL = {
  code: 200,
  message: '成功',
  data: { deletedCount: 3 },
} as const

/** M5 会话不存在（越权与不存在同码） */
export const MOCK_SESSION_NOT_FOUND = {
  code: 404,
  message: '会话不存在',
  data: null,
} as const

/** M6 消息历史（成功，含未完成消息） */
export const MOCK_MESSAGES = {
  code: 200,
  message: '成功',
  data: {
    list: [
      {
        messageId: 90001,
        role: 'PATIENT',
        content: '我这两天咳嗽有痰，之前开的阿莫西林还能吃吗',
        disclaimer: null,
        emergency: false,
        streamStatus: 'COMPLETED',
        createdAt: '2026-11-08T10:10:00',
      },
      {
        messageId: 90002,
        role: 'ASSISTANT',
        content:
          '从你描述的咳嗽有痰看，常见于上呼吸道感染。既往处方中的阿莫西林属于处方抗菌药，是否继续使用需结合当时的诊断与疗程。',
        disclaimer: '仅供参考，不构成医疗诊断',
        emergency: false,
        streamStatus: 'COMPLETED',
        createdAt: '2026-11-08T10:10:05',
      },
      {
        messageId: 90003,
        role: 'ASSISTANT',
        content: '根据你最近一次就诊记录，当时诊断为',
        disclaimer: null,
        emergency: false,
        streamStatus: 'INTERRUPTED',
        createdAt: '2026-11-08T10:12:00',
      },
    ],
    total: 3,
    page: 1,
    pageSize: 50,
  },
} as const

/** M7 SSE：普通问答（逐帧字符串，供 page.route 直接拼 body） */
export const MOCK_SSE_NORMAL_EVENTS: readonly string[] = [
  'event: accepted\ndata: {"messageId":90001,"sessionId":3001}\n\n',
  'event: delta\ndata: {"text":"从你描述的咳嗽有痰看，"}\n\n',
  'event: delta\ndata: {"text":"常见于上呼吸道感染。既往处方中的阿莫西林属于处方抗菌药，"}\n\n',
  'event: delta\ndata: {"text":"是否继续使用需结合当时的诊断与疗程，建议复诊或咨询开方医生。"}\n\n',
  'event: disclaimer\ndata: {"text":"仅供参考，不构成医疗诊断"}\n\n',
  'event: done\ndata: {"messageId":90002,"truncated":false,"modelUsed":"deepseek-v4-flash"}\n\n',
]

/** M8 SSE：紧急预警（emergency 先于 delta） */
export const MOCK_SSE_EMERGENCY_EVENTS: readonly string[] = [
  'event: accepted\ndata: {"messageId":90011,"sessionId":3001}\n\n',
  'event: emergency\ndata: {"level":"HIGH","title":"症状可能存在紧急风险","advice":"请立即拨打 120 或前往最近急诊","ruleCode":"CHEST_PAIN_DYSPNEA","booking":{"departmentId":1,"departmentName":"心血管内科"}}\n\n',
  'event: delta\ndata: {"text":"你描述的胸痛伴呼吸困难属于需要立即处理的情况，"}\n\n',
  'event: disclaimer\ndata: {"text":"仅供参考，不构成医疗诊断"}\n\n',
  'event: done\ndata: {"messageId":90012,"truncated":false,"modelUsed":"deepseek-v4-flash"}\n\n',
]

/** M9 SSE：模型失败降级 */
export const MOCK_SSE_ERROR_EVENTS: readonly string[] = [
  'event: accepted\ndata: {"messageId":90021,"sessionId":3001}\n\n',
  'event: delta\ndata: {"text":"从你描述的症状看，"}\n\n',
  'event: error\ndata: {"code":503,"message":"智能助手暂时不可用，请稍后重试","retryable":true}\n\n',
]

/** M10 流建立前失败（JSON，非 SSE） */
export const MOCK_CHAT_BUSY = {
  code: 409,
  message: '当前会话正在生成回答，请稍候',
  data: null,
} as const

export const MOCK_MESSAGE_TOO_LONG = {
  code: 400,
  message: '消息内容过长',
  data: null,
} as const
