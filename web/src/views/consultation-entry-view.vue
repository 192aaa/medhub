<script setup lang="ts">
import { computed } from 'vue'
import { usePatientStore } from '@/stores/patient'
import { AUTH_STATUS } from '@/api/types'

/**
 * SC-003 唯一验证载体（T092）：最小占位门禁入口。
 *
 * 仅展示门禁状态与引导，不调用任何问诊 API、不实现问诊业务；
 * 未认证访问由 requiresVerified 守卫直接重定向至认证页。
 */
const store = usePatientStore()
const allowed = computed(
  () => store.authStatus === AUTH_STATUS.VERIFIED && store.consultationAllowed,
)
</script>

<template>
  <main class="entry">
    <div class="card entry__card">
      <h1>线上问诊</h1>
      <p v-if="allowed" class="entry__msg" data-testid="consultation-placeholder">
        线上问诊建设中
      </p>
      <template v-else>
        <p class="entry__msg" data-testid="consultation-denied">问诊门禁未放行</p>
        <RouterLink class="btn btn--primary" to="/patient/identity-verification">
          去实名认证
        </RouterLink>
      </template>
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
  max-width: 26rem;
  padding: 2rem 1.75rem;
  text-align: center;
}

.entry__card h1 {
  margin: 0 0 0.75rem;
  font-size: 1.4rem;
}

.entry__msg {
  margin: 0 0 1rem;
  color: var(--sdas-muted);
}
</style>
