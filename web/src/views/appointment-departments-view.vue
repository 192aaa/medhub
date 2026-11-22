<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDepartments } from '@/api/appointment-consultation'
import type { DepartmentResponse } from '@/api/types'

const router = useRouter()
const departments = ref<DepartmentResponse[]>([])
const error = ref('')
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await getDepartments()
    if (res.code === 200 && res.data) departments.value = res.data
    else error.value = res.message
  } catch {
    error.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
})

function choose(dept: DepartmentResponse) {
  router.push({ path: '/appointment/doctors', query: { departmentId: String(dept.departmentId) } })
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>选择科室</h1>
      <p class="muted">选择您要就诊的科室，继续预约医生</p>
    </header>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <ul v-else class="list">
      <li v-for="d in departments" :key="d.departmentId" @click="choose(d)">
        <span class="dept__icon" aria-hidden="true">🏥</span>
        <div class="dept__body">
          <strong class="dept__name">{{ d.name }}</strong>
          <span class="dept__desc">{{ d.description }}</span>
        </div>
        <span class="dept__go" aria-hidden="true">→</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.page {
  max-width: 860px;
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

.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 0.9rem;
}

.list li {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1.1rem 1.2rem;
  border: 1px solid var(--sdas-border);
  border-radius: var(--sdas-radius);
  background: var(--sdas-surface);
  box-shadow: var(--sdas-shadow);
  cursor: pointer;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease,
    border-color 0.15s ease;
}

.list li:hover {
  transform: translateY(-2px);
  box-shadow: var(--sdas-shadow-lg);
  border-color: var(--sdas-primary);
}

.dept__icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}

.dept__body {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  flex: 1;
  min-width: 0;
}

.dept__name {
  font-size: 1.02rem;
  font-weight: 700;
  color: var(--sdas-text);
}

.dept__desc {
  font-size: 0.8rem;
  color: var(--sdas-muted);
  line-height: 1.5;
}

.dept__go {
  color: var(--sdas-primary);
  font-weight: 600;
  flex-shrink: 0;
}
</style>
