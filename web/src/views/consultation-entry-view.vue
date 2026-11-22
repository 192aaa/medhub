<script setup lang="ts">
import { computed } from 'vue'
import { usePatientStore } from '@/stores/patient'
import { useAppointmentStore } from '@/stores/appointment'
import { useConsultationStore } from '@/stores/consultation'
import { AUTH_STATUS } from '@/api/types'

/** SC-003：实名门禁通过后的问诊入口，引导至真实业务流程。 */
const patientStore = usePatientStore()
const appointmentStore = useAppointmentStore()
const consultationStore = useConsultationStore()

const allowed = computed(
  () =>
    patientStore.authStatus === AUTH_STATUS.VERIFIED && patientStore.consultationAllowed,
)
const canContinue = computed(
  () => appointmentStore.paymentStatus === 'PAID' && !consultationStore.consultationId,
)
const detailLink = computed(() =>
  consultationStore.consultationId
    ? `/appointment/consultation/${consultationStore.consultationId}`
    : null,
)
</script>

<template>
  <main class="entry">
    <div v-if="allowed" class="card entry__card" data-testid="consultation-hub">
      <h1>线上问诊</h1>
      <p class="entry__msg">选择发起图文问诊，或继续使用 AI 健康咨询。</p>
      <div class="entry__actions">
        <RouterLink
          class="btn btn--primary"
          to="/appointment/departments"
          data-testid="start-consultation"
        >
          发起图文问诊
        </RouterLink>
        <RouterLink class="btn" to="/health-consultation">AI 健康咨询</RouterLink>
        <RouterLink
          v-if="canContinue"
          class="btn"
          to="/appointment/consultation"
          data-testid="continue-consultation"
        >
          继续提交问诊单
        </RouterLink>
        <RouterLink v-if="detailLink" class="btn" :to="detailLink" data-testid="view-consultation">
          查看问诊单
        </RouterLink>
      </div>
    </div>

    <div v-else class="card entry__card">
      <h1>线上问诊</h1>
      <p class="entry__msg" data-testid="consultation-denied">问诊门禁未放行</p>
      <RouterLink class="btn btn--primary" to="/patient/identity-verification">
        去实名认证
      </RouterLink>
    </div>
  </main>
</template>

<style scoped>
.entry {
  display: flex;
  justify-content: center;
  padding: 2rem 1rem;
}

.entry__card {
  width: 100%;
  max-width: 520px;
  padding: 2rem;
  text-align: center;
}

.entry__card h1 {
  margin: 0 0 0.75rem;
  font-size: 1.5rem;
}

.entry__msg {
  margin: 0 0 1.5rem;
  color: var(--sdas-muted);
}

.entry__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  justify-content: center;
}
</style>
