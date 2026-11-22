<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { createTriage } from '@/api/appointment-consultation'
import type { TriageResponse } from '@/api/types'

const router = useRouter()
const complaint = ref('')
const error = ref('')
const loading = ref(false)
const result = ref<TriageResponse | null>(null)

async function submit() {
  error.value = ''
  result.value = null
  if (!complaint.value.trim()) {
    error.value = '请输入症状描述'
    return
  }
  loading.value = true
  try {
    const res = await createTriage({ complaint: complaint.value.trim() })
    if (res.code === 200 && res.data) result.value = res.data
    else error.value = res.message
  } catch {
    error.value = '网络异常'
  } finally {
    loading.value = false
  }
}

function goDoctors(departmentId?: number) {
  router.push({
    path: '/appointment/doctors',
    query: departmentId ? { departmentId: String(departmentId) } : {},
  })
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>🤖 智能导诊</h1>
      <p class="muted">描述您的症状，AI 将推荐科室（仅供参考）。超时将展示全部科室供手动选择。</p>
    </header>

    <div class="card panel">
      <textarea v-model="complaint" rows="4" placeholder="例如：持续咳嗽伴低热" aria-label="症状描述" />
      <button class="btn btn--primary" :disabled="loading" @click="submit">
        {{ loading ? '分析中...' : '开始导诊' }}
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="result" class="card result">
      <p v-if="result.status === 'TIMEOUT_FALLBACK'" class="result__warn">
        AI 推荐超时，已为您展示全部科室：
      </p>
      <p v-else>为您推荐以下科室：</p>
      <ul class="result__list">
        <li
          v-for="d in result.recommendedDepartments.length
            ? result.recommendedDepartments
            : result.fallbackDepartments"
          :key="d.departmentId"
        >
          <a @click="goDoctors(d.departmentId)">{{ d.name }}</a>
        </li>
      </ul>
      <p class="hint">也可 <a @click="goDoctors()">浏览全部科室</a></p>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 720px;
  margin: 0 auto;
}

.page__head {
  margin-bottom: 1.25rem;
}

.page__head h1 {
  margin: 0 0 0.25rem;
  font-size: 1.5rem;
}

.page__head p {
  margin: 0;
}

.panel {
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.result {
  margin-top: 1.25rem;
  padding: 1.25rem;
}

.result__warn {
  color: var(--sdas-warn);
}

.result__list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  list-style: none;
  margin: 0.75rem 0;
  padding: 0;
}

.result__list li a {
  display: inline-block;
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--sdas-border-strong);
  border-radius: 999px;
  background: var(--sdas-surface);
  color: var(--sdas-primary);
  font-weight: 600;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease;
}

.result__list li a:hover {
  border-color: var(--sdas-primary);
  background: var(--sdas-primary-soft);
}

.hint {
  margin: 0;
  color: var(--sdas-muted);
  font-size: 0.88rem;
}

.hint a {
  color: var(--sdas-primary);
  font-weight: 600;
  cursor: pointer;
}
</style>
