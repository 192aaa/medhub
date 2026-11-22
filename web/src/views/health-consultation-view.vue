<script setup lang="ts">
/**
 * AI健康咨询对话页（PC 侧栏 / 手机抽屉，FR-016）。
 *
 * 视图只做编排：会话列表、消息气泡、输入区、紧急提示条与挂号弹窗；
 * 免责声明、流式状态与预警状态全部来自 store（服务端事件），禁止本页拼装文案。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import HealthBookingDialog from '@/components/health-booking-dialog.vue'
import HealthEmergencyBanner from '@/components/health-emergency-banner.vue'
import HealthMessageList from '@/components/health-message-list.vue'
import HealthSessionList from '@/components/health-session-list.vue'
import { useHealthConsultationStore } from '@/stores/health-consultation'

const store = useHealthConsultationStore()
const {
  sessions,
  activeSessionId,
  messages,
  streaming,
  streamError,
  emergency,
  drawerOpen,
  loading,
} = storeToRefs(store)

const router = useRouter()
const draft = ref('')
const bookingOpen = ref(false)

/** 与后端 sdas.ai.health.message-max-chars 默认值一致；超限由服务端再次校验 */
const MESSAGE_MAX_CHARS = 2000

watch(emergency, (value) => {
  if (value) {
    bookingOpen.value = true
  }
})

onMounted(async () => {
  store.restoreActive()
  await store.loadSessions()
  if (activeSessionId.value !== null) {
    try {
      await store.loadMessages(activeSessionId.value)
    } catch {
      await store.createSession()
    }
  } else {
    await store.createSession()
  }
})

onBeforeUnmount(() => {
  store.stop()
})

function send(): void {
  const content = draft.value.trim()
  if (!content || streaming.value) {
    return
  }
  if (content.length > MESSAGE_MAX_CHARS) {
    window.alert(`消息内容过长，请控制在 ${MESSAGE_MAX_CHARS} 字以内`)
    return
  }
  draft.value = ''
  void store.send(content)
}

async function createSession(): Promise<void> {
  await store.createSession()
  draft.value = ''
}

function selectSession(sessionId: number): void {
  void store.switchSession(sessionId)
  store.toggleDrawer(false)
}

function renameSession(sessionId: number, title: string): void {
  void store.renameSession(sessionId, title)
}

function deleteSession(sessionId: number): void {
  void store.deleteSession(sessionId)
}

function clearAll(): void {
  void store.clearAll()
}

function confirmBooking(): void {
  const booking = emergency.value?.booking
  bookingOpen.value = false
  if (booking) {
    void router.push({
      path: '/appointment/departments',
      query: { departmentId: String(booking.departmentId) },
    })
  }
}
</script>

<template>
  <section class="chat">
    <button type="button" class="chat__drawer-toggle" @click="store.toggleDrawer()">
      对话列表
    </button>

    <HealthSessionList
      :sessions="sessions"
      :active-session-id="activeSessionId"
      :drawer-open="drawerOpen"
      :loading="loading"
      @create="createSession"
      @select="selectSession"
      @rename="renameSession"
      @delete="deleteSession"
      @clear="clearAll"
      @close="store.toggleDrawer(false)"
    />

    <main class="chat__main">
      <header class="chat__header">
        <h1 class="chat__title">AI健康咨询</h1>
        <p class="chat__hint">回答仅供参考，紧急症状请立即就医或拨打 120</p>
      </header>

      <HealthEmergencyBanner v-if="emergency" :emergency="emergency" @book="bookingOpen = true" />

      <div class="chat__messages">
        <HealthMessageList :messages="messages" :streaming="streaming" />
      </div>

      <p v-if="streamError" class="chat__error" role="status">
        {{ streamError.message }}
        <button v-if="streamError.retryable" type="button" @click="store.retry()">重试</button>
      </p>

      <form class="chat__composer" @submit.prevent="send">
        <textarea
          v-model="draft"
          class="chat__input"
          rows="3"
          :maxlength="MESSAGE_MAX_CHARS"
          placeholder="描述你的症状或想咨询的问题，例如：咳嗽有痰，之前的阿莫西林还能吃吗"
          aria-label="咨询内容"
        />
        <div class="chat__composer-actions">
          <span class="chat__counter">{{ draft.length }}/{{ MESSAGE_MAX_CHARS }}</span>
          <button v-if="streaming" type="button" class="chat__stop" @click="store.stop()">
            停止生成
          </button>
          <button v-else type="submit" class="chat__send" :disabled="!draft.trim()">发送</button>
        </div>
      </form>
    </main>

    <HealthBookingDialog
      :open="bookingOpen"
      :booking="emergency?.booking ?? null"
      @close="bookingOpen = false"
      @confirm="confirmBooking"
    />
  </section>
</template>

<style scoped>
.chat {
  display: grid;
  grid-template-columns: 17rem 1fr;
  gap: 0;
  min-height: calc(100vh - 4rem);
  background: var(--sdas-bg);
}

.chat__drawer-toggle {
  display: none;
}

.chat__main {
  display: flex;
  flex-direction: column;
  padding: 1rem 1.25rem 1.5rem;
  min-width: 0;
}

.chat__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid var(--sdas-border);
  padding-bottom: 0.6rem;
  margin-bottom: 0.75rem;
}

.chat__title {
  margin: 0;
  font-size: 1.15rem;
}

.chat__hint {
  margin: 0.15rem 0 0;
  font-size: 0.75rem;
  color: var(--sdas-muted);
}

.chat__messages {
  flex: 1;
  overflow-y: auto;
  min-height: 12rem;
  max-height: 60vh;
}

.chat__error {
  margin: 0.5rem 0 0;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--sdas-down);
  border-radius: 0.5rem;
  background: var(--sdas-down-soft);
  color: var(--sdas-down);
  font-size: 0.8rem;
}

.chat__error button {
  margin-left: 0.5rem;
  border: 1px solid var(--sdas-down);
  background: var(--sdas-surface);
  color: var(--sdas-down);
  border-radius: 0.35rem;
  padding: 0.1rem 0.5rem;
  cursor: pointer;
}

.chat__composer {
  border-top: 1px solid var(--sdas-border);
  padding-top: 0.75rem;
  margin-top: 0.75rem;
}

.chat__input {
  width: 100%;
  resize: vertical;
  border: 1px solid var(--sdas-border-strong);
  border-radius: 0.5rem;
  padding: 0.6rem 0.75rem;
  font-size: 0.9rem;
  font-family: inherit;
  background: var(--sdas-surface);
  color: var(--sdas-text);
}

.chat__composer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.chat__counter {
  margin-right: auto;
  font-size: 0.72rem;
  color: var(--sdas-muted);
}

.chat__send,
.chat__stop {
  border-radius: 0.5rem;
  padding: 0.4rem 1rem;
  font-weight: 600;
  cursor: pointer;
}

.chat__send {
  border: 1px solid var(--sdas-primary);
  background: var(--sdas-primary);
  color: #fff;
}

.chat__send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.chat__stop {
  border: 1px solid var(--sdas-border-strong);
  background: var(--sdas-surface);
  color: var(--sdas-text-soft);
}

@media (max-width: 767px) {
  .chat {
    grid-template-columns: 1fr;
  }

  .chat__drawer-toggle {
    display: block;
    margin: 0.75rem 1rem 0;
    border: 1px solid var(--sdas-border-strong);
    background: var(--sdas-surface);
    border-radius: 0.5rem;
    padding: 0.4rem 0.75rem;
    font-size: 0.8rem;
    cursor: pointer;
  }
}
</style>
