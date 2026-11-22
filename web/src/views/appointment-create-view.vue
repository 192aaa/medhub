<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { createAppointment, getDoctorDetail } from '@/api/appointment-consultation'
import type { DoctorDetailResponse } from '@/api/types'
import { useAppointmentStore } from '@/stores/appointment'

const router = useRouter()
const store = useAppointmentStore()
const detail = ref<DoctorDetailResponse | null>(null)
const error = ref('')
const loading = ref(false)

onMounted(async () => {
  if (!store.selectedDoctorId || !store.selectedScheduleId) {
    error.value = '请先选择医生'
    return
  }
  try {
    const res = await getDoctorDetail(store.selectedDoctorId)
    if (res.code === 200 && res.data) detail.value = res.data
    else error.value = res.message
  } catch {
    error.value = '网络异常'
  }
})

async function confirm() {
  if (!store.selectedDoctorId || !store.selectedScheduleId) return
  loading.value = true
  error.value = ''
  try {
    const res = await createAppointment({
      doctorId: store.selectedDoctorId,
      scheduleId: store.selectedScheduleId,
      patientNote: null,
    })
    if (res.code === 200 && res.data) {
      store.setAppointment(res.data.appointmentId)
      router.push('/appointment/pay')
    } else {
      error.value = res.message
    }
  } catch {
    error.value = '网络异常'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h1>确认预约</h1>
      <p class="muted">请核对就诊信息后提交预约</p>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="detail" class="card detail">
      <p class="detail__line">医生：{{ detail.name }}（{{ detail.title }}）</p>
      <p class="detail__line">医院：{{ detail.hospital }}</p>
      <p class="detail__line detail__fee">挂号费：¥{{ store.feeAmount }}</p>
      <button class="btn btn--primary btn--block" :disabled="loading" @click="confirm">
        {{ loading ? '提交中...' : '提交预约' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 560px;
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

.detail {
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.detail__line {
  margin: 0;
  padding-bottom: 0.85rem;
  border-bottom: 1px solid var(--sdas-border);
}

.detail__fee {
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--sdas-primary);
  border-bottom: 0;
  padding-bottom: 0.25rem;
}
</style>
