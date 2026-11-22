<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getDoctors, getDoctorDetail } from '@/api/appointment-consultation'
import type { DoctorResponse } from '@/api/types'
import { useAppointmentStore } from '@/stores/appointment'

const router = useRouter()
const route = useRoute()
const store = useAppointmentStore()
const doctors = ref<DoctorResponse[]>([])
const error = ref('')
const loading = ref(false)
const departmentId = Number(route.query.departmentId) || undefined

onMounted(async () => {
  loading.value = true
  try {
    const res = await getDoctors({ departmentId })
    if (res.code === 200 && res.data) doctors.value = res.data
    else error.value = res.message
  } catch {
    error.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
})

async function choose(doc: DoctorResponse) {
  if (doc.onlineStatus === 'OFFLINE') {
    error.value = '该医生当前不在线，请选择其他医生'
    return
  }
  try {
    const res = await getDoctorDetail(doc.doctorId)
    if (res.code === 200 && res.data) {
      const sched = res.data.schedule.find((s) => s.slotsBooked < s.slotsTotal)
      if (!sched) {
        error.value = '该医生号源已约满'
        return
      }
      store.selectDoctor(doc.doctorId, sched.scheduleId, doc.consultationFee)
      router.push('/appointment/create')
    } else {
      error.value = res.message
    }
  } catch {
    error.value = '网络异常'
  }
}

function statusText(status: string): string {
  if (status === 'ONLINE') return '在线'
  if (status === 'BUSY') return '忙碌'
  return '离线'
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>选择医生</h1>
      <p class="muted">按科室匹配到的出诊医生，选择后进入预约确认</p>
    </header>

    <p v-if="loading" class="muted">加载中...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <ul v-else class="list">
      <li v-for="d in doctors" :key="d.doctorId" @click="choose(d)">
        <span class="doc__avatar" aria-hidden="true">👨‍⚕️</span>
        <div class="doc__info">
          <div class="doc__top">
            <strong class="doc__name">{{ d.name }}</strong>
            <span
              class="badge"
              :class="
                d.onlineStatus === 'ONLINE'
                  ? 'badge--ok'
                  : d.onlineStatus === 'BUSY'
                    ? 'badge--info'
                    : 'badge--warn'
              "
            >
              {{ statusText(d.onlineStatus) }}
            </span>
          </div>
          <span class="doc__meta">{{ d.title }} · {{ d.hospital }}</span>
          <span class="doc__meta">擅长：{{ d.goodAt }}</span>
        </div>
        <div class="doc__foot">
          <span class="doc__rating">评分 {{ d.rating }}</span>
          <span class="doc__fee">¥{{ d.consultationFee }}</span>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.page {
  max-width: 760px;
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
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.list li {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.6rem 0.9rem;
  padding: 1.1rem 1.3rem;
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

.doc__avatar {
  display: grid;
  place-items: center;
  width: 3rem;
  height: 3rem;
  flex-shrink: 0;
  border-radius: 0.9rem;
  background: var(--sdas-primary-soft);
  font-size: 1.5rem;
}

.doc__info {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.doc__top {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.doc__name {
  font-size: 1.05rem;
  color: var(--sdas-text);
}

.doc__meta {
  font-size: 0.82rem;
  color: var(--sdas-muted);
  line-height: 1.5;
}

.doc__foot {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  padding-top: 0.75rem;
  border-top: 1px solid var(--sdas-border);
}

.doc__rating {
  font-size: 0.85rem;
  color: var(--sdas-warn);
  font-weight: 600;
}

.doc__fee {
  font-size: 0.95rem;
  color: var(--sdas-primary);
  font-weight: 700;
}
</style>
