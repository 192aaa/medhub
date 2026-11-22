<script setup lang="ts">
/**
 * 紧急预警提示条（FR-006 / FR-008）。
 *
 * 文案全部取 SSE `emergency` 事件字段，禁止前端编造或改写；
 * 组件不提供「永久关闭」：弹窗关闭后仍需保留入口可重开挂号引导。
 */
import type { HealthEmergencyEvent } from '@/api/types'

defineProps<{ emergency: HealthEmergencyEvent }>()

const emit = defineEmits<{ (event: 'book'): void }>()

defineOptions({ name: 'HealthEmergencyBanner' })
</script>

<template>
  <section class="emergency" role="alert">
    <div class="emergency__head">
      <span class="emergency__badge">紧急</span>
      <strong class="emergency__title">{{ emergency.title }}</strong>
    </div>
    <p class="emergency__advice">{{ emergency.advice }}</p>
    <button type="button" class="emergency__action" @click="emit('book')">去挂号</button>
  </section>
</template>

<style scoped>
.emergency {
  border: 2px solid #b91c1c;
  border-radius: 0.75rem;
  background: #b91c1c;
  color: #fff;
  padding: 1rem;
  margin-bottom: 1rem;
}

.emergency__head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.emergency__badge {
  border: 1px solid #fff;
  border-radius: 999px;
  padding: 0.05rem 0.5rem;
  font-size: 0.75rem;
  font-weight: 700;
}

.emergency__title {
  font-size: 1.05rem;
}

.emergency__advice {
  margin: 0.5rem 0 0.75rem;
  font-size: 0.9rem;
  line-height: 1.6;
}

.emergency__action {
  border: 0;
  border-radius: 0.5rem;
  background: #fff;
  color: #b91c1c;
  font-weight: 700;
  padding: 0.45rem 0.9rem;
  cursor: pointer;
}
</style>
