<script setup lang="ts">
/**
 * 会话列表（PC 侧栏 / 手机抽屉，FR-016）。
 *
 * 排序由后端保证（最近活跃倒序）；重命名为行内编辑，清空历史需二次确认。
 */
import { ref } from 'vue'
import type { HealthSessionSummary } from '@/api/types'

defineProps<{
  sessions: HealthSessionSummary[]
  activeSessionId: number | null
  drawerOpen: boolean
  loading: boolean
}>()

const emit = defineEmits<{
  (event: 'create'): void
  (event: 'select', sessionId: number): void
  (event: 'rename', sessionId: number, title: string): void
  (event: 'delete', sessionId: number): void
  (event: 'clear'): void
  (event: 'close'): void
}>()

const editingId = ref<number | null>(null)
const editingTitle = ref('')

defineOptions({ name: 'HealthSessionList' })

function startRename(session: HealthSessionSummary): void {
  editingId.value = session.sessionId
  editingTitle.value = session.title
}

function submitRename(sessionId: number): void {
  const title = editingTitle.value.trim()
  if (!title || title.length > 64) {
    editingId.value = null
    return
  }
  emit('rename', sessionId, title)
  editingId.value = null
}

function cancelRename(): void {
  editingId.value = null
}

function confirmDelete(session: HealthSessionSummary): void {
  if (window.confirm(`删除会话「${session.title}」？删除后不可恢复。`)) {
    emit('delete', session.sessionId)
  }
}

function confirmClear(): void {
  if (window.confirm('清空全部咨询历史？该操作不可恢复。')) {
    emit('clear')
  }
}

function formatTime(value: string): string {
  return value.length >= 16 ? value.slice(5, 16).replace('T', ' ') : value
}
</script>

<template>
  <aside class="sessions" :class="{ 'sessions--open': drawerOpen }">
    <header class="sessions__head">
      <h2 class="sessions__title">我的对话</h2>
      <button type="button" class="sessions__close" aria-label="关闭" @click="emit('close')">
        ×
      </button>
    </header>
    <button type="button" class="sessions__new" @click="emit('create')">+ 新建对话</button>
    <p v-if="loading" class="sessions__tip">加载中…</p>
    <p v-else-if="sessions.length === 0" class="sessions__tip">还没有对话，开始提问吧。</p>
    <ul class="sessions__list">
      <li
        v-for="session in sessions"
        :key="session.sessionId"
        class="session"
        :class="{ 'session--active': session.sessionId === activeSessionId }"
      >
        <button type="button" class="session__main" @click="emit('select', session.sessionId)">
          <span class="session__name">{{ session.title }}</span>
          <span class="session__meta">{{ formatTime(session.lastActiveAt) }}</span>
          <span class="session__preview">{{ session.lastMessagePreview ?? '暂无消息' }}</span>
        </button>
        <div class="session__actions">
          <button type="button" aria-label="重命名" @click="startRename(session)">改</button>
          <button type="button" aria-label="删除" @click="confirmDelete(session)">删</button>
        </div>
        <form
          v-if="editingId === session.sessionId"
          class="session__edit"
          @submit.prevent="submitRename(session.sessionId)"
        >
          <input v-model="editingTitle" maxlength="64" aria-label="会话标题" />
          <button type="submit">保存</button>
          <button type="button" @click="cancelRename">取消</button>
        </form>
      </li>
    </ul>
    <button v-if="sessions.length > 0" type="button" class="sessions__clear" @click="confirmClear">
      清空全部历史
    </button>
  </aside>
</template>

<style scoped>
.sessions {
  border-right: 1px solid var(--sdas-border);
  padding: 1rem;
  background: var(--sdas-surface);
  min-height: 100%;
}

.sessions__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sessions__title {
  margin: 0;
  font-size: 1rem;
}

.sessions__close {
  display: none;
  border: 0;
  background: transparent;
  font-size: 1.25rem;
  cursor: pointer;
}

.sessions__new {
  width: 100%;
  margin: 0.75rem 0;
  border: 1px dashed var(--sdas-primary);
  border-radius: 0.5rem;
  background: var(--sdas-primary-soft);
  color: var(--sdas-primary);
  padding: 0.5rem;
  font-weight: 600;
  cursor: pointer;
}

.sessions__tip {
  font-size: 0.8rem;
  color: var(--sdas-muted);
}

.sessions__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.session {
  border-radius: 0.5rem;
  padding: 0.4rem;
}

.session--active {
  background: var(--sdas-primary-soft);
}

.session__main {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  padding: 0;
}

.session__name {
  font-weight: 600;
  font-size: 0.875rem;
}

.session__meta {
  font-size: 0.72rem;
  color: var(--sdas-muted-light);
}

.session__preview {
  font-size: 0.75rem;
  color: var(--sdas-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session__actions {
  display: flex;
  gap: 0.25rem;
  margin-top: 0.25rem;
}

.session__actions button,
.session__edit button {
  border: 1px solid var(--sdas-border);
  background: var(--sdas-surface);
  color: var(--sdas-text-soft);
  border-radius: 0.35rem;
  font-size: 0.72rem;
  padding: 0.1rem 0.4rem;
  cursor: pointer;
}

.session__edit {
  display: flex;
  gap: 0.25rem;
  margin-top: 0.35rem;
}

.session__edit input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--sdas-border-strong);
  border-radius: 0.35rem;
  padding: 0.2rem 0.35rem;
  font-size: 0.8rem;
  background: var(--sdas-surface);
  color: var(--sdas-text);
}

.sessions__clear {
  width: 100%;
  margin-top: 1rem;
  border: 1px solid var(--sdas-down);
  background: var(--sdas-down-soft);
  color: var(--sdas-down);
  border-radius: 0.5rem;
  padding: 0.45rem;
  font-size: 0.8rem;
  cursor: pointer;
}

@media (max-width: 767px) {
  .sessions {
    position: fixed;
    inset: 0 auto 0 0;
    width: min(20rem, 85vw);
    transform: translateX(-100%);
    transition: transform 0.2s ease;
    z-index: 40;
    box-shadow: 0 0 24px rgba(15, 23, 42, 0.18);
  }

  .sessions--open {
    transform: translateX(0);
  }

  .sessions__close {
    display: block;
  }
}
</style>
