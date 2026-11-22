/**
 * 流式问答生命周期封装。
 *
 * 职责：持有 AbortController、启动/中断一次 SSE 请求、把原始帧交给调用方分发。
 * 组件卸载或切换会话时必须调用 `abort()`，避免悬挂连接与跨会话串扰（SC-006）。
 */
import { streamHealthChat } from '@/api/health-consultation'
import type { SseMessage } from '@/api/sse'

export interface ChatStreamHandlers {
  onEvent: (message: SseMessage) => void
  onError: (error: unknown) => void
  onFinish: () => void
}

export function useHealthChatStream() {
  let controller: AbortController | null = null

  function abort(): void {
    controller?.abort()
    controller = null
  }

  async function start(
    sessionId: number,
    content: string,
    handlers: ChatStreamHandlers,
  ): Promise<void> {
    abort()
    controller = new AbortController()
    try {
      await streamHealthChat(sessionId, content, handlers.onEvent, controller.signal)
    } catch (error) {
      if ((error as { name?: string }).name === 'AbortError') {
        return
      }
      handlers.onError(error)
    } finally {
      handlers.onFinish()
      controller = null
    }
  }

  return { start, abort }
}
