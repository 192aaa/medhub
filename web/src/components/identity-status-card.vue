<script setup lang="ts">
import { computed } from 'vue'
import { AUTH_STATUS, type AuthStatus } from '@/api/types'

/**
 * 认证状态卡（T075）：展示 authStatus / consultationAllowed /
 * maskedName / maskedIdCard，未认证时给出引导。
 */
const props = withDefaults(
  defineProps<{
    authStatus: AuthStatus
    consultationAllowed: boolean
    maskedName?: string | null
    maskedIdCard?: string | null
  }>(),
  { maskedName: null, maskedIdCard: null },
)

const verified = computed(() => props.authStatus === AUTH_STATUS.VERIFIED)
const statusText = computed(() => (verified.value ? '已认证' : '未认证'))
const gateText = computed(() => (props.consultationAllowed ? '已解锁线上问诊' : '线上问诊未解锁'))
</script>

<template>
  <section class="identity-status-card" data-testid="identity-status-card">
    <header class="card-head">
      <h2>实名认证</h2>
      <span class="badge" :class="verified ? 'badge--ok' : 'badge--warn'">{{ statusText }}</span>
    </header>
    <dl>
      <dt>认证状态</dt>
      <dd data-testid="auth-status">{{ statusText }}</dd>
      <dt>问诊权限</dt>
      <dd data-testid="consultation-allowed">{{ gateText }}</dd>
      <template v-if="verified">
        <dt>姓名</dt>
        <dd data-testid="masked-name">{{ maskedName ?? '—' }}</dd>
        <dt>证件号</dt>
        <dd data-testid="masked-id-card">{{ maskedIdCard ?? '—' }}</dd>
      </template>
    </dl>
    <p v-if="!verified" class="hint" data-testid="identity-hint">
      完成实名认证后即可使用线上问诊服务。
    </p>
  </section>
</template>

<style scoped>
.identity-status-card {
  border: 1px solid var(--sdas-border);
  border-radius: var(--sdas-radius);
  padding: 1rem 1.25rem;
  background: var(--sdas-surface);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

h2 {
  font-size: 1rem;
  margin: 0;
}

dl {
  display: grid;
  grid-template-columns: 6rem 1fr;
  gap: 0.5rem 1rem;
  margin: 0;
}

dt {
  color: var(--sdas-muted);
}

dd {
  margin: 0;
}

.hint {
  margin: 0.75rem 0 0;
  color: var(--sdas-muted);
  font-size: 0.85rem;
}
</style>
