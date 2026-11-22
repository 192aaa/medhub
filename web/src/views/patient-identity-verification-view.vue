<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import IdentityStatusCard from '@/components/identity-status-card.vue'
import { useIdentityVerification } from '@/composables/use-identity-verification'
import { usePatientStore } from '@/stores/patient'
import { AUTH_STATUS } from '@/api/types'

const router = useRouter()
const store = usePatientStore()
const { loading, error, loadStatus, submit } = useIdentityVerification()

const realName = ref('')
const idCardNumber = ref('')
const statusLoaded = ref(false)

const verified = computed(() => store.authStatus === AUTH_STATUS.VERIFIED)
const canSubmit = computed(
  () =>
    realName.value.trim().length > 0 && /^\d{17}[\dXx]$/.test(idCardNumber.value) && !loading.value,
)

onMounted(async () => {
  await loadStatus()
  statusLoaded.value = true
})

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) {
    return
  }
  if (await submit(realName.value, idCardNumber.value)) {
    await router.push('/patient/profile')
  }
}
</script>

<template>
  <main class="auth" :data-status-loaded="statusLoaded">
    <div class="card auth__card auth__card--wide">
      <header class="auth__head">
        <span class="auth__mark" aria-hidden="true">✅</span>
        <h1>实名认证</h1>
        <p>完成实名认证后即可使用线上问诊服务</p>
      </header>

      <IdentityStatusCard
        :auth-status="store.authStatus"
        :consultation-allowed="store.consultationAllowed"
        :masked-name="store.maskedName"
        :masked-id-card="store.maskedIdCard"
      />

      <section v-if="verified" class="verified" data-testid="verified-summary">
        <p>已完成实名认证</p>
        <RouterLink class="btn btn--primary" to="/patient/profile">返回个人中心</RouterLink>
      </section>

      <form v-else class="auth__form" @submit.prevent="onSubmit">
        <div class="field">
          <label for="verify-real-name">姓名</label>
          <input
            id="verify-real-name"
            v-model="realName"
            maxlength="50"
            placeholder="请输入真实姓名"
            autocomplete="name"
          />
        </div>

        <div class="field">
          <label for="verify-id-card">身份证号</label>
          <input
            id="verify-id-card"
            v-model="idCardNumber"
            maxlength="18"
            placeholder="请输入 18 位身份证号"
          />
        </div>

        <button type="submit" class="btn btn--primary btn--block" :disabled="!canSubmit">
          {{ loading ? '认证中…' : '提交认证' }}
        </button>
        <p v-if="error" class="error" data-testid="verification-error">{{ error }}</p>
      </form>
    </div>
  </main>
</template>

<style scoped>
.auth {
  display: flex;
  justify-content: center;
  padding: 2rem 1rem;
}

.auth__card {
  width: 100%;
  max-width: 26rem;
  padding: 2rem 1.75rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.auth__head {
  text-align: center;
}

.auth__mark {
  display: grid;
  place-items: center;
  width: 3.25rem;
  height: 3.25rem;
  margin: 0 auto 0.75rem;
  border-radius: 1rem;
  background: var(--sdas-primary-soft);
  font-size: 1.6rem;
}

.auth__head h1 {
  margin: 0 0 0.25rem;
  font-size: 1.4rem;
}

.auth__head p {
  margin: 0;
  color: var(--sdas-muted);
  font-size: 0.88rem;
}

.auth__form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.verified {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  align-items: center;
  padding: 1rem;
  border-radius: var(--sdas-radius);
  background: var(--sdas-up-soft);
  color: var(--sdas-up);
}

.verified p {
  margin: 0;
  font-weight: 600;
}
</style>
