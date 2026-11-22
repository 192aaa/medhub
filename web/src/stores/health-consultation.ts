import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  clearHealthSessions,
  createHealthSession,
  deleteHealthSession,
  fetchHealthMessages,
  fetchHealthSessions,
  renameHealthSession,
} from '@/api/health-consultation'
import { SseHttpError, type SseMessage } from '@/api/sse'
import { NETWORK_ERROR_MESSAGE } from '@/api/error-codes'
import type {
  HealthAcceptedEvent,
  HealthDeltaEvent,
  HealthDisclaimerEvent,
  HealthDoneEvent,
  HealthEmergencyEvent,
  HealthErrorEvent,
  HealthSessionSummary,
  StreamStatus,
} from '@/api/types'
import { useHealthChatStream } from '@/composables/use-health-chat-stream'

/** 最近打开的会话，用于刷新恢复（SC-007） */
const SESSION_KEY = 'sdas.health.session'

export interface ChatBubble {
  /** 本地稳定 key（accepted/done 前后 messageId 可能为空） */
  key: string
  messageId: number | null
  role: 'PATIENT' | 'ASSISTANT'
  content: string
  /** 免责声明：只来自服务端 disclaimer 事件，前端禁止拼接 */
  disclaimer: string | null
  emergency: boolean
  streamStatus: StreamStatus
  createdAt: string
}

export interface StreamError {
  code: number
  message: string
  retryable: boolean
}

/** 事件载荷联合（字段按事件类型分布，读取时按需取值） */
type EventPayload = Partial<
  HealthAcceptedEvent &
    HealthEmergencyEvent &
    HealthDeltaEvent &
    HealthDisclaimerEvent &
    HealthDoneEvent &
    HealthErrorEvent
>

function nowString(): string {
  const now = new Date()
  const pad = (value: number): string => String(value).padStart(2, '0')
  return (
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}` +
    `T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  )
}

/**
 * AI健康咨询 store：会话列表、当前会话消息、流式状态与紧急预警。
 *
 * 宪法 II：创建时从 localStorage 恢复最近会话；切换会话必须中断上一条流（防串扰）。
 */
export const useHealthConsultationStore = defineStore('healthConsultation', () => {
  const sessions = ref<HealthSessionSummary[]>([])
  const activeSessionId = ref<number | null>(null)
  const messages = ref<ChatBubble[]>([])
  const streaming = ref(false)
  const streamError = ref<StreamError | null>(null)
  const emergency = ref<HealthEmergencyEvent | null>(null)
  const drawerOpen = ref(false)
  const loading = ref(false)

  const stream = useHealthChatStream()
  let retryText: string | null = null
  let lastExchange: { patientKey: string; assistantKey: string } | null = null

  function restoreActive(): void {
    try {
      const raw = localStorage.getItem(SESSION_KEY)
      const parsed = raw === null ? null : Number(raw)
      activeSessionId.value = parsed !== null && Number.isFinite(parsed) ? parsed : null
    } catch {
      activeSessionId.value = null
    }
  }

  function persistActive(): void {
    try {
      if (activeSessionId.value === null) {
        localStorage.removeItem(SESSION_KEY)
      } else {
        localStorage.setItem(SESSION_KEY, String(activeSessionId.value))
      }
    } catch {
      // 隐私模式下忽略
    }
  }

  async function loadSessions(): Promise<void> {
    loading.value = true
    try {
      const response = await fetchHealthSessions(1, 20)
      sessions.value = response.data.data?.list ?? []
    } finally {
      loading.value = false
    }
  }

  async function createSession(): Promise<number | null> {
    const response = await createHealthSession()
    const created = response.data.data
    if (!created) {
      return null
    }
    await loadSessions()
    activeSessionId.value = created.sessionId
    persistActive()
    messages.value = []
    streamError.value = null
    emergency.value = null
    return created.sessionId
  }

  async function loadMessages(sessionId: number): Promise<void> {
    const response = await fetchHealthMessages(sessionId, 1, 50)
    messages.value = (response.data.data?.list ?? []).map((item) => ({
      key: `s-${item.messageId}`,
      messageId: item.messageId,
      role: item.role,
      content: item.content,
      disclaimer: item.disclaimer,
      emergency: item.emergency,
      streamStatus: item.streamStatus,
      createdAt: item.createdAt,
    }))
  }

  async function switchSession(sessionId: number): Promise<void> {
    if (activeSessionId.value === sessionId) {
      return
    }
    stream.abort()
    streaming.value = false
    activeSessionId.value = sessionId
    persistActive()
    streamError.value = null
    emergency.value = null
    await loadMessages(sessionId)
  }

  async function renameSession(sessionId: number, title: string): Promise<void> {
    await renameHealthSession(sessionId, title)
    await loadSessions()
  }

  async function deleteSession(sessionId: number): Promise<void> {
    await deleteHealthSession(sessionId)
    if (activeSessionId.value === sessionId) {
      stream.abort()
      activeSessionId.value = null
      persistActive()
      messages.value = []
      emergency.value = null
    }
    await loadSessions()
  }

  async function clearAll(): Promise<void> {
    await clearHealthSessions()
    stream.abort()
    streaming.value = false
    sessions.value = []
    messages.value = []
    activeSessionId.value = null
    emergency.value = null
    streamError.value = null
    persistActive()
  }

  function handleEvent(message: SseMessage): void {
    let payload: EventPayload
    try {
      payload = JSON.parse(message.data) as EventPayload
    } catch {
      return
    }
    const patientBubble = lastExchange
      ? messages.value.find((item) => item.key === lastExchange?.patientKey)
      : undefined
    const assistantBubble = lastExchange
      ? messages.value.find((item) => item.key === lastExchange?.assistantKey)
      : undefined

    switch (message.event) {
      case 'accepted':
        if (patientBubble && typeof payload.messageId === 'number') {
          patientBubble.messageId = payload.messageId
        }
        break
      case 'emergency':
        emergency.value = {
          level: payload.level ?? 'HIGH',
          title: payload.title ?? '',
          advice: payload.advice ?? '',
          ruleCode: payload.ruleCode ?? '',
          booking: payload.booking ?? null,
        }
        if (assistantBubble) {
          assistantBubble.emergency = true
        }
        break
      case 'delta':
        if (assistantBubble) {
          assistantBubble.content += payload.text ?? ''
        }
        break
      case 'disclaimer':
        if (assistantBubble) {
          assistantBubble.disclaimer = payload.text ?? null
        }
        break
      case 'done':
        if (assistantBubble) {
          assistantBubble.messageId = payload.messageId ?? null
          assistantBubble.streamStatus = 'COMPLETED'
        }
        retryText = null
        void loadSessions()
        break
      case 'error':
        if (assistantBubble) {
          assistantBubble.streamStatus = 'FAILED'
        }
        streamError.value = {
          code: payload.code ?? 503,
          message: payload.message ?? NETWORK_ERROR_MESSAGE,
          retryable: payload.retryable ?? true,
        }
        retryText = patientBubble?.content ?? null
        break
      default:
        break
    }
  }

  async function send(content: string): Promise<void> {
    const sessionId = activeSessionId.value
    const text = content.trim()
    if (!sessionId || streaming.value || !text) {
      return
    }
    streamError.value = null
    streaming.value = true
    const stamp = Date.now()
    const patientKey = `p-${stamp}`
    const assistantKey = `a-${stamp}`
    lastExchange = { patientKey, assistantKey }
    messages.value.push({
      key: patientKey,
      messageId: null,
      role: 'PATIENT',
      content: text,
      disclaimer: null,
      emergency: false,
      streamStatus: 'COMPLETED',
      createdAt: nowString(),
    })
    messages.value.push({
      key: assistantKey,
      messageId: null,
      role: 'ASSISTANT',
      content: '',
      disclaimer: null,
      emergency: false,
      streamStatus: 'COMPLETED',
      createdAt: nowString(),
    })

    await stream.start(sessionId, text, {
      onEvent: handleEvent,
      onError: (error: unknown) => {
        const bubble = messages.value.find((item) => item.key === assistantKey)
        if (bubble) {
          bubble.streamStatus = 'FAILED'
        }
        streamError.value =
          error instanceof SseHttpError
            ? { code: error.code, message: error.message, retryable: true }
            : { code: 0, message: NETWORK_ERROR_MESSAGE, retryable: true }
        retryText = text
      },
      onFinish: () => {
        streaming.value = false
      },
    })
  }

  async function retry(): Promise<void> {
    if (!retryText) {
      return
    }
    if (lastExchange) {
      messages.value = messages.value.filter(
        (item) => item.key !== lastExchange?.patientKey && item.key !== lastExchange?.assistantKey,
      )
    }
    const text = retryText
    retryText = null
    await send(text)
  }

  function stop(): void {
    stream.abort()
    streaming.value = false
  }

  function dismissEmergency(): void {
    emergency.value = null
  }

  function toggleDrawer(value?: boolean): void {
    drawerOpen.value = value ?? !drawerOpen.value
  }

  return {
    sessions,
    activeSessionId,
    messages,
    streaming,
    streamError,
    emergency,
    drawerOpen,
    loading,
    restoreActive,
    loadSessions,
    createSession,
    switchSession,
    loadMessages,
    renameSession,
    deleteSession,
    clearAll,
    send,
    retry,
    stop,
    dismissEmergency,
    toggleDrawer,
  }
})
