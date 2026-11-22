<script setup lang="ts">
/**
 * 挂号引导弹窗（FR-007 / FR-008）。
 *
 * 科室来自 `emergency.booking`（服务端按规则科室名反查），为空时按钮置灰；
 * 关闭仅隐藏弹窗，不回收提示条；确认由父组件跳转既有挂号流程。
 */
import type { HealthBooking } from '@/api/types'

defineProps<{ open: boolean; booking: HealthBooking | null }>()

const emit = defineEmits<{ (event: 'close'): void; (event: 'confirm'): void }>()

defineOptions({ name: 'HealthBookingDialog' })
</script>

<template>
  <div v-if="open" class="dialog" role="dialog" aria-modal="true" aria-label="挂号引导">
    <div class="dialog__panel">
      <h2 class="dialog__title">建议尽快到院就诊</h2>
      <p v-if="booking" class="dialog__desc">
        根据本次对话，建议科室：<strong>{{ booking.departmentName }}</strong>
      </p>
      <p v-else class="dialog__desc">暂未匹配到具体科室，请前往急诊或咨询导诊台。</p>
      <div class="dialog__actions">
        <button type="button" class="dialog__ghost" @click="emit('close')">稍后再说</button>
        <button type="button" class="dialog__primary" :disabled="!booking" @click="emit('confirm')">
          去挂号
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  z-index: 50;
}

.dialog__panel {
  width: min(24rem, 100%);
  background: var(--sdas-surface);
  border-radius: 0.75rem;
  padding: 1.25rem;
  box-shadow: 0 24px 48px rgba(15, 23, 42, 0.25);
}

.dialog__title {
  margin: 0 0 0.5rem;
  font-size: 1.05rem;
}

.dialog__desc {
  margin: 0 0 1rem;
  color: var(--sdas-text-soft);
  font-size: 0.9rem;
  line-height: 1.6;
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.dialog__ghost,
.dialog__primary {
  border-radius: 0.5rem;
  padding: 0.45rem 0.9rem;
  font-weight: 600;
  cursor: pointer;
}

.dialog__ghost {
  border: 1px solid var(--sdas-border-strong);
  background: var(--sdas-surface);
  color: var(--sdas-text-soft);
}

.dialog__primary {
  border: 1px solid #b91c1c;
  background: #b91c1c;
  color: #fff;
}

.dialog__primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
