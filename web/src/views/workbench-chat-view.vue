<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getConsultationDetail,
  acceptConsultation,
  listMessages,
  sendMessage,
  getAiDecision,
} from '@/api/doctor-consultation'
import { API_CODE } from '@/api/error-codes'
import type { WorkbenchConsultationDetail, MessageItem, AiDecisionResponse } from '@/api/types'

const route = useRoute()
const router = useRouter()
const consultationId = Number(route.params.id)

const detail = ref<WorkbenchConsultationDetail | null>(null)
const messages = ref<MessageItem[]>([])
const content = ref('')
const errorMessage = ref('')
const aiDecision = ref<AiDecisionResponse | null>(null)
const aiLoading = ref(false)
const loading = ref(false)

async function loadDetail() {
  const res = await getConsultationDetail(consultationId)
  if (res.code === API_CODE.SUCCESS && res.data) {
    detail.value = res.data
  } else {
    errorMessage.value = res.message
  }
}

async function loadMessages() {
  const res = await listMessages(consultationId)
  if (res.code === API_CODE.SUCCESS && res.data) {
    messages.value = res.data
  }
}

async function accept() {
  errorMessage.value = ''
  const res = await acceptConsultation(consultationId)
  if (res.code === API_CODE.SUCCESS && res.data) {
    await loadDetail()
  } else {
    errorMessage.value = res.message
  }
}

async function send() {
  errorMessage.value = ''
  if (!content.value.trim()) {
    errorMessage.value = '回复内容不能为空'
    return
  }
  const res = await sendMessage(consultationId, { content: content.value })
  if (res.code === API_CODE.SUCCESS) {
    content.value = ''
    await loadMessages()
  } else {
    errorMessage.value = res.message
  }
}

async function requestAi() {
  aiLoading.value = true
  errorMessage.value = ''
  const res = await getAiDecision(consultationId)
  aiLoading.value = false
  if (res.code === API_CODE.SUCCESS && res.data) {
    aiDecision.value = res.data
  } else {
    errorMessage.value = res.message
  }
}

function openPrescription() {
  router.push(`/workbench/prescriptions/new?consultationId=${consultationId}`)
}

onMounted(() => {
  loading.value = true
  Promise.all([loadDetail(), loadMessages()]).finally(() => {
    loading.value = false
  })
})
</script>

<template>
  <div class="page">
    <div v-if="detail" class="card header">
      <div class="header__top">
        <h1>{{ detail.patientName }}</h1>
        <button
          v-if="detail.status === 'PENDING'"
          class="btn btn--primary"
          @click="accept"
        >
          接诊
        </button>
        <button v-else class="btn btn--primary" @click="openPrescription">开具处方</button>
      </div>
      <p class="header__complaint">{{ detail.complaint }}</p>
      <p v-if="detail.description" class="header__desc">{{ detail.description }}</p>
      <p v-if="detail.summary" class="header__summary">{{ detail.summary }}</p>
    </div>

    <p v-if="loading" class="muted">加载中…</p>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

    <div class="messages">
      <div
        v-for="msg in messages"
        :key="msg.messageId"
        class="message"
        :class="msg.senderType.toLowerCase()"
      >
        <span class="message__sender">{{ msg.senderType === 'DOCTOR' ? '我' : '患者' }}</span>
        <p class="message__content">{{ msg.content }}</p>
        <span class="message__time">{{ msg.createdAt }}</span>
      </div>
    </div>

    <div class="composer">
      <input
        v-model="content"
        type="text"
        placeholder="输入回复（≤1000 字）"
        @keyup.enter="send"
      />
      <button class="btn btn--primary" :disabled="detail?.status !== 'IN_PROGRESS'" @click="send">
        发送
      </button>
    </div>

    <div class="card ai-panel">
      <div class="ai-panel__head">
        <h2>AI 辅助决策</h2>
        <button class="btn btn--ghost" :disabled="aiLoading" @click="requestAi">
          {{ aiLoading ? 'AI 分析中…' : '重新分析' }}
        </button>
      </div>
      <div v-if="aiDecision">
        <p class="disclaimer">{{ aiDecision.disclaimer }}</p>
        <ul class="ai-panel__list">
          <li v-for="(s, i) in aiDecision.suggestions" :key="i">{{ s }}</li>
        </ul>
      </div>
      <p v-else class="muted">点击上方按钮，获取 AI 对本次问诊的分析建议。</p>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 760px;
  margin: 0 auto;
}

.header {
  padding: 1.25rem 1.4rem;
  margin-bottom: 1.25rem;
}

.header__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
}

.header__top h1 {
  margin: 0;
  font-size: 1.25rem;
}

.header__complaint {
  margin: 0 0 0.3rem;
  font-weight: 600;
}

.header__desc,
.header__summary {
  margin: 0.3rem 0 0;
  color: var(--sdas-muted);
  font-size: 0.9rem;
}

.header__summary {
  padding: 0.6rem 0.8rem;
  border-radius: var(--sdas-radius-sm);
  background: var(--sdas-bg-soft);
}

.messages {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-bottom: 1.25rem;
}

.message {
  max-width: 80%;
  padding: 0.7rem 0.9rem;
  border-radius: var(--sdas-radius);
  background: var(--sdas-surface);
  border: 1px solid var(--sdas-border);
}

.message.doctor {
  align-self: flex-end;
  background: var(--sdas-primary-soft);
  border-color: var(--sdas-primary-soft);
}

.message__sender {
  font-size: 0.75rem;
  color: var(--sdas-muted);
}

.message__content {
  margin: 0.3rem 0;
}

.message__time {
  font-size: 0.7rem;
  color: var(--sdas-muted-light);
}

.composer {
  display: flex;
  gap: 0.6rem;
  margin-bottom: 1.25rem;
}

.composer input {
  flex: 1;
}

.ai-panel {
  padding: 1.25rem 1.4rem;
}

.ai-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
}

.ai-panel__head h2 {
  margin: 0;
  font-size: 1.05rem;
}

.ai-panel__list {
  margin: 0.75rem 0 0;
  padding-left: 1.2rem;
}

.disclaimer {
  margin: 0;
  color: var(--sdas-warn);
  font-size: 0.8rem;
}

.error {
  color: var(--sdas-down);
}

.muted {
  color: var(--sdas-muted);
}
</style>
