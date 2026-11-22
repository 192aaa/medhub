<script setup lang="ts">
import { computed, watch } from 'vue'
import { useTreatmentDecisionStore } from '@/stores/treatment-decision'
import { DEGRADED_REASON } from '@/api/types'

const props = defineProps<{ consultationId: number }>()
const store = useTreatmentDecisionStore()

const visible = computed(() => store.loading || store.decision !== null || store.error !== null)
const reason = computed(() => store.decision?.degradedReason ?? DEGRADED_REASON.NONE)
const reasonText = computed(() => {
  switch (reason.value) {
    case DEGRADED_REASON.TIMEOUT:
      return '生成超时'
    case DEGRADED_REASON.UPSTREAM_ERROR:
      return '服务响应异常'
    case DEGRADED_REASON.PARSE_FAILED:
    case DEGRADED_REASON.EMPTY:
      return '返回内容不可用'
    default:
      return '本轮未生成有效内容'
  }
})
const summaryRows = computed(() => {
  const summary = store.summary
  if (!summary) {
    return []
  }
  return [
    ['主诉', summary.chiefComplaint],
    ['现病史', summary.presentIllness],
    ['对话要点', summary.dialogHighlights.join('；') || '暂无'],
    ['风险提示', summary.riskAlerts.join('；') || '暂无'],
  ]
})

watch(
  () => props.consultationId,
  (id) => {
    if (Number.isFinite(id) && id > 0) {
      void store.load(id)
    }
  },
  { immediate: true },
)
</script>

<template>
  <section v-if="visible" class="card td-panel" aria-label="AI 辅助治疗决策">
    <header class="td-panel__header">
      <h3>AI 辅助（摘要 / 建议 / 用药草稿）</h3>
      <button
        v-if="store.decision && !store.loading"
        type="button"
        class="td-panel__btn"
        @click="store.retry()"
      >
        重新生成
      </button>
    </header>

    <div v-if="store.loading" class="td-panel__skeleton" data-state="loading">
      <span class="td-panel__line" />
      <span class="td-panel__line" style="width: 80%" />
      <span class="td-panel__line" style="width: 60%" />
    </div>

    <div
      v-else-if="store.error || store.degraded"
      class="td-panel__error"
      :class="{ 'td-panel__error--warn': store.degraded }"
      role="alert"
      :data-state="store.error ? 'error' : 'degraded'"
      :data-degraded="store.degraded ? reason : null"
    >
      <p>
        {{ store.error ?? `AI 辅助暂不可用（${reasonText}），不影响正常开方。` }}
      </p>
      <button type="button" class="td-panel__btn" @click="store.retry()">重试</button>
    </div>

    <div v-else-if="store.decision" class="td-panel__content" data-state="success">
      <section v-if="store.summary" class="td-panel__summary">
        <p v-if="store.summary.evidenceLimited" class="td-panel__warn">对话信息较少，依据有限</p>
        <dl>
          <template v-for="row in summaryRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </template>
        </dl>
      </section>

      <section v-if="store.suggestions.length" class="td-panel__list">
        <h4>诊断建议（辅助参考，不作为诊断结论）</h4>
        <ul>
          <li v-for="(item, index) in store.suggestions" :key="index">
            <strong>{{ item.diagnosis }}</strong>
            <span class="td-panel__muted">{{ item.likelihood }}</span>
            <p>依据：{{ item.basis.join('；') }}</p>
            <p v-if="item.workup.length">建议检查：{{ item.workup.join('；') }}</p>
            <p v-if="item.riskAlert" class="td-panel__warn">{{ item.riskAlert }}</p>
          </li>
        </ul>
      </section>

      <section v-if="store.medicationDrafts.length" class="td-panel__list">
        <h4>用药草稿（待医生确认）</h4>
        <ul>
          <li v-for="(draft, index) in store.medicationDrafts" :key="index">
            <span>{{ draft.drugName }}</span>
            <span v-if="draft.spec" class="td-panel__muted">{{ draft.spec }}</span>
            <span v-if="draft.dosage">{{ draft.dosage }}</span>
            <span v-if="draft.needsCompletion" class="td-panel__warn">待补充用法用量</span>
          </li>
        </ul>
        <p v-if="store.decision.ignoredMedications.length" class="td-panel__warn">
          目录外药品已忽略：{{ store.decision.ignoredMedications.join('、') }}
        </p>
      </section>

      <p class="td-panel__disclaimer">{{ store.disclaimer }}</p>
    </div>
  </section>
</template>

<style scoped>
.td-panel {
  padding: 1.1rem 1.25rem;
  background: var(--sdas-surface-2);
}
.td-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.td-panel__header h3 {
  margin: 0;
  font-size: 1rem;
}
.td-panel__skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.td-panel__line {
  height: 12px;
  border-radius: 4px;
  background: var(--sdas-border);
}
.td-panel__error {
  color: var(--sdas-down);
  background: var(--sdas-down-soft);
  border: 1px solid var(--sdas-down);
  border-radius: 6px;
  padding: 12px;
}
.td-panel__error--warn {
  color: var(--sdas-warn);
  background: var(--sdas-warn-soft);
  border-color: var(--sdas-warn);
}
.td-panel__error p {
  margin: 0 0 8px;
}
.td-panel__warn {
  color: var(--sdas-warn);
}
.td-panel__muted {
  color: var(--sdas-muted);
  font-size: 12px;
  margin-left: 4px;
}
.td-panel__btn {
  border: 1px solid var(--sdas-border-strong);
  background: var(--sdas-surface);
  border-radius: 6px;
  padding: 4px 10px;
  cursor: pointer;
  font-size: 0.8rem;
  color: var(--sdas-text-soft);
}
.td-panel__summary dl {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 4px 12px;
  margin: 0 0 12px;
}
.td-panel__summary dt {
  color: var(--sdas-muted);
}
.td-panel__summary dd {
  margin: 0;
}
.td-panel__list h4 {
  margin: 0 0 8px;
  font-size: 0.9rem;
}
.td-panel__list ul {
  list-style: none;
  padding: 0;
  margin: 0 0 12px;
}
.td-panel__list li {
  background: var(--sdas-surface);
  border: 1px solid var(--sdas-border);
  border-radius: 8px;
  padding: 8px 10px;
  margin-bottom: 8px;
  font-size: 13px;
}
.td-panel__list li p {
  margin: 4px 0 0;
}
.td-panel__disclaimer {
  margin: 12px 0 0;
  color: var(--sdas-muted);
  font-size: 12px;
}
</style>
