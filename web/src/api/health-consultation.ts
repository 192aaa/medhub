/**
 * AI健康咨询请求出口（契约：specs/005-ai-health-consultation/contracts/ai-health-consultation-api.md）。
 *
 * 普通请求走患者实例 `patientRequest`（自动注入 Authorization / 统一 401 续期）；
 * 流式问答（端点 7）必须走 `postSse`（fetch + ReadableStream，EventSource 不支持 POST）。
 * 路径不含 `/api`（baseURL 已含），禁止重复。
 */
import { patientRequest } from './request/patient-request'
import { csrfHeaders } from './request/csrf'
import { postSse, type SseMessage } from './sse'
import type {
  ApiResult,
  HealthMessageItem,
  HealthSessionClearAllResponse,
  HealthSessionCreateResponse,
  HealthSessionDeleteResponse,
  HealthSessionRenameResponse,
  HealthSessionSummary,
  PageData,
} from './types'

const SESSIONS = '/v1/health-consultation/sessions'

/** 端点 1：会话列表 */
export function fetchHealthSessions(page = 1, pageSize = 20) {
  return patientRequest.get<ApiResult<PageData<HealthSessionSummary>>>(SESSIONS, {
    params: { page, pageSize },
  })
}

/** 端点 2：新建会话 */
export function createHealthSession() {
  return patientRequest.post<ApiResult<HealthSessionCreateResponse>>(SESSIONS, null, {
    headers: csrfHeaders(),
  })
}

/** 端点 3：重命名会话 */
export function renameHealthSession(sessionId: number, title: string) {
  return patientRequest.patch<ApiResult<HealthSessionRenameResponse>>(
    `${SESSIONS}/${sessionId}`,
    { title },
    { headers: csrfHeaders() },
  )
}

/** 端点 4：删除单个会话 */
export function deleteHealthSession(sessionId: number) {
  return patientRequest.delete<ApiResult<HealthSessionDeleteResponse>>(
    `${SESSIONS}/${sessionId}`,
    { headers: csrfHeaders() },
  )
}

/** 端点 5：清空全部历史 */
export function clearHealthSessions() {
  return patientRequest.delete<ApiResult<HealthSessionClearAllResponse>>(SESSIONS, {
    headers: csrfHeaders(),
  })
}

/** 端点 6：会话消息历史 */
export function fetchHealthMessages(sessionId: number, page = 1, pageSize = 50) {
  return patientRequest.get<ApiResult<PageData<HealthMessageItem>>>(
    `${SESSIONS}/${sessionId}/messages`,
    { params: { page, pageSize } },
  )
}

/** 端点 7：流式问答（SSE，逐帧回调） */
export function streamHealthChat(
  sessionId: number,
  content: string,
  onEvent: (message: SseMessage) => void,
  signal?: AbortSignal,
): Promise<void> {
  return postSse(`${SESSIONS}/${sessionId}/messages`, { content }, { onEvent, signal })
}
