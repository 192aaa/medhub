<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { payAppointment, getPaymentStatus } from '@/api/appointment-consultation'
import { useAppointmentStore } from '@/stores/appointment'

const router = useRouter()
const store = useAppointmentStore()
const error = ref('')
const loading = ref(false)
const paid = ref(false)

onMounted(async () => {
  if (!store.appointmentId) {
    error.value = '无有效预约'
    return
  }
  try {
    const res = await getPaymentStatus(store.appointmentId)
    if (res.code === 200 && res.data && res.data.paymentStatus === 'PAID') {
      paid.value = true
      store.setPaymentStatus('PAID')
    }
  } catch {
    /* 忽略查询失败，继续展示支付按钮 */
  }
})

async function pay() {
  if (!store.appointmentId) return
  loading.value = true
  error.value = ''
  try {
    const res = await payAppointment(store.appointmentId, {
      channel: 'MOCK',
      amount: store.feeAmount,
    })
    if (res.code === 200 && res.data) {
      store.setPaymentStatus('PAID')
      paid.value = true
      router.push('/appointment/consultation')
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
      <h1>支付挂号费</h1>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="paid" class="card ok">已支付，正在进入问诊...</div>
    <div v-else class="card pay">
      <p class="pay__line pay__fee">金额：¥{{ store.feeAmount }}</p>
      <p class="pay__hint">（模拟支付，仅用于演示）</p>
      <button class="btn btn--primary btn--block" :disabled="loading" @click="pay">
        {{ loading ? '支付中...' : '确认支付' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 460px;
  margin: 0 auto;
}

.page__head {
  margin-bottom: 1.25rem;
}

.page__head h1 {
  margin: 0;
  font-size: 1.5rem;
}

.pay {
  padding: 1.75rem;
  text-align: center;
}

.pay__line {
  margin: 0 0 0.25rem;
}

.pay__fee {
  font-size: 2.2rem;
  font-weight: 800;
  color: var(--sdas-text);
}

.pay__hint {
  margin: 0 0 1.25rem;
  color: var(--sdas-muted);
  font-size: 0.85rem;
}

.ok {
  padding: 1.5rem;
  color: var(--sdas-up);
  font-weight: 600;
  text-align: center;
}
</style>
