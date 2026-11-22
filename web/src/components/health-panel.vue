<script setup lang="ts">
import { computed } from 'vue'
import { HEALTH_STATUS, type HealthReport } from '@/api/types'

defineOptions({ name: 'HealthPanel' })

const props = defineProps<{
  report: HealthReport | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{ (event: 'refresh'): void }>()

const overallText = computed(() => {
  if (props.loading) {
    return '检查中'
  }
  if (!props.report) {
    return '未获取'
  }
  return props.report.status === HEALTH_STATUS.UP ? 'UP' : 'DOWN'
})

const statusClass = computed(() => {
  if (props.loading || !props.report) {
    return 'status--unknown'
  }
  return props.report.status === HEALTH_STATUS.UP ? 'status--up' : 'status--down'
})

function refresh(): void {
  emit('refresh')
}
</script>

<template>
  <section class="health-panel" aria-label="服务健康状态">
    <header class="health-panel__header">
      <div>
        <h2>服务健康检查</h2>
        <p class="health-panel__hint">
          GET /api/health · 契约来源 specs/health-check/contracts/health-api.md
        </p>
      </div>
      <button type="button" class="health-panel__refresh" :disabled="loading" @click="refresh">
        {{ loading ? '检查中…' : '重新检查' }}
      </button>
    </header>

    <div class="health-panel__overall" :class="statusClass">
      整体状态：<strong>{{ overallText }}</strong>
      <span v-if="report" class="health-panel__time">检查时间 {{ report.checkedAt }}</span>
    </div>

    <p v-if="error" class="health-panel__error" role="alert">{{ error }}</p>

    <ul v-if="report" class="health-panel__grid">
      <li v-for="component in report.components" :key="component.name" class="health-card">
        <span class="health-card__name">{{ component.name }}</span>
        <span
          class="health-card__badge"
          :class="component.status === HEALTH_STATUS.UP ? 'status--up' : 'status--down'"
        >
          {{ component.status }}
        </span>
        <span class="health-card__detail">{{ component.detail }}</span>
        <span class="health-card__cost">{{ component.elapsedMs }} ms</span>
      </li>
    </ul>
    <p v-else-if="!loading" class="health-panel__empty">暂无数据，请点击「重新检查」。</p>
  </section>
</template>

<style scoped>
.health-panel {
  padding: 1.25rem;
  border: 1px solid var(--sdas-border);
  border-radius: 0.75rem;
  background: var(--sdas-surface);
}

.health-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.health-panel h2 {
  margin: 0;
  font-size: 1.125rem;
}

.health-panel__hint {
  margin: 0.375rem 0 0;
  color: var(--sdas-muted);
  font-size: 0.75rem;
  word-break: break-all;
}

.health-panel__refresh {
  flex-shrink: 0;
  padding: 0.5rem 1rem;
  border: 1px solid var(--sdas-primary);
  border-radius: 0.5rem;
  background: transparent;
  color: var(--sdas-primary);
}

.health-panel__refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.health-panel__overall {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin: 1rem 0;
  padding: 0.75rem 1rem;
  border-radius: 0.5rem;
  background: var(--sdas-bg);
}

.health-panel__time {
  color: var(--sdas-muted);
  font-size: 0.75rem;
}

.status--up {
  color: var(--sdas-up);
}

.status--down {
  color: var(--sdas-down);
}

.status--unknown {
  color: var(--sdas-muted);
}

.health-panel__error {
  margin: 0.75rem 0;
  padding: 0.625rem 0.875rem;
  border-radius: 0.5rem;
  background: rgba(220, 38, 38, 0.1);
  color: var(--sdas-down);
  font-size: 0.875rem;
}

.health-panel__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.health-card {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
  padding: 0.875rem;
  border: 1px solid var(--sdas-border);
  border-radius: 0.5rem;
}

.health-card__name {
  font-weight: 600;
}

.health-card__badge {
  font-size: 0.8125rem;
  font-weight: 700;
}

.health-card__detail,
.health-card__cost {
  color: var(--sdas-muted);
  font-size: 0.75rem;
  word-break: break-all;
}

.health-panel__empty {
  color: var(--sdas-muted);
  font-size: 0.875rem;
}

@media (max-width: 640px) {
  .health-panel__header {
    flex-direction: column;
  }

  .health-panel__overall {
    flex-direction: column;
    align-items: flex-start;
  }

  .health-panel__grid {
    grid-template-columns: 1fr;
  }
}
</style>
