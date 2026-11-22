/**
 * SSE 消费工具（宪法 III：契约端点 7 为 text/event-stream）。
 *
 * `EventSource` 不支持 POST，故统一使用 `fetch` + `ReadableStream`。
 * 帧格式：`event: <name>\n data: <json>\n\n`；本模块只负责分帧与字段解析，
 * 不解释业务语义（业务分发在 composable / store 中）。
 */
import { csrfHeaders } from './request/csrf'

/** 患者令牌键，与 patient-request.ts 保持一致 */
const TOKEN_KEY = 'sdas.patient.token'

export interface SseMessage {
  event: string
  data: string
}

/** 流建立前失败（后端返回 JSON 统一响应） */
export class SseHttpError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'SseHttpError'
    this.code = code
  }
}

/**
 * 创建增量帧解析器：每次喂入一个 chunk，返回本次完整帧数组。
 */
export function createSseParser(): (chunk: string) => SseMessage[] {
  let buffer = ''
  return (chunk: string): SseMessage[] => {
    buffer += chunk
    const messages: SseMessage[] = []
    let index = buffer.indexOf('\n\n')
    while (index >= 0) {
      const frame = buffer.slice(0, index)
      buffer = buffer.slice(index + 2)
      const parsed = parseFrame(frame)
      if (parsed) {
        messages.push(parsed)
      }
      index = buffer.indexOf('\n\n')
    }
    return messages
  }
}

function parseFrame(frame: string): SseMessage | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const rawLine of frame.split('\n')) {
    const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
    if (!line || line.startsWith(':')) {
      continue
    }
    const separator = line.indexOf(':')
    const field = separator === -1 ? line : line.slice(0, separator)
    let value = separator === -1 ? '' : line.slice(separator + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    if (field === 'event') {
      event = value
    } else if (field === 'data') {
      dataLines.push(value)
    }
  }
  return dataLines.length === 0 ? null : { event, data: dataLines.join('\n') }
}

export interface PostSseOptions {
  /** 每个完整事件回调（data 为原始字符串，由调用方 JSON.parse） */
  onEvent: (message: SseMessage) => void
  signal?: AbortSignal
}

/**
 * POST 方式发起 SSE 请求并逐帧回调。
 *
 * @throws SseHttpError 流建立前失败（401/403/404/409/400/503）
 */
export async function postSse(path: string, body: unknown, options: PostSseOptions): Promise<void> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    ...csrfHeaders(),
  }
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const base = import.meta.env.VITE_API_BASE_URL ?? ''
  const response = await fetch(`${base}${path}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
    credentials: 'include',
    signal: options.signal,
  })

  if (!response.ok || !response.body) {
    const payload = (await response.json().catch(() => null)) as {
      code?: number
      message?: string
    } | null
    throw new SseHttpError(payload?.code ?? response.status, payload?.message ?? '请求失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const parse = createSseParser()

  for (;;) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    const chunk = decoder.decode(value, { stream: true })
    for (const message of parse(chunk)) {
      options.onEvent(message)
    }
  }
}
