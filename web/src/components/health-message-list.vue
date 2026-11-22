<script setup lang="ts">
/**
 * 消息气泡列表。
 *
 * 免责声明只渲染 `disclaimer` 字段（服务端常量），禁止本组件拼接文案；
 * `streamStatus != COMPLETED` 时标注「未完成」并保留已生成内容（FR-019）。
 */
import type { ChatBubble } from '@/stores/health-consultation'

defineProps<{ messages: ChatBubble[]; streaming: boolean }>()

defineOptions({ name: 'HealthMessageList' })
</script>

<template>
  <div class="messages" data-testid="health-message-list">
    <article
      v-for="message in messages"
      :key="message.key"
      class="bubble"
      :class="message.role === 'PATIENT' ? 'bubble--patient' : 'bubble--assistant'"
    >
      <p class="bubble__role">{{ message.role === 'PATIENT' ? '我' : 'AI 助手' }}</p>
      <p class="bubble__content">{{ message.content }}</p>
      <p v-if="message.streamStatus !== 'COMPLETED'" class="bubble__pending">未完成</p>
      <p v-if="message.disclaimer" class="bubble__disclaimer">{{ message.disclaimer }}</p>
    </article>
    <p v-if="streaming" class="messages__typing">正在生成回答…</p>
  </div>
</template>

<style scoped>
.messages {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0.5rem 0;
}

.bubble {
  max-width: 85%;
  border-radius: 0.75rem;
  padding: 0.65rem 0.85rem;
  font-size: 0.9rem;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble--patient {
  align-self: flex-end;
  background: var(--sdas-primary);
  color: #fff;
}

.bubble--assistant {
  align-self: flex-start;
  background: var(--sdas-bg-soft);
  color: var(--sdas-text);
}

.bubble__role {
  margin: 0 0 0.25rem;
  font-size: 0.72rem;
  opacity: 0.75;
}

.bubble__content {
  margin: 0;
}

.bubble__pending {
  margin: 0.35rem 0 0;
  font-size: 0.72rem;
  color: var(--sdas-down);
}

.bubble__disclaimer {
  margin: 0.45rem 0 0;
  padding-top: 0.35rem;
  border-top: 1px dashed var(--sdas-border);
  font-size: 0.72rem;
  color: var(--sdas-muted);
}

.messages__typing {
  margin: 0;
  font-size: 0.8rem;
  color: var(--sdas-muted);
}
</style>
