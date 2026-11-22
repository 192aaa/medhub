<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import IdentityStatusCard from '@/components/identity-status-card.vue'
import { usePatientSession } from '@/composables/use-patient-session'
import { usePatientStore } from '@/stores/patient'
import { AUTH_STATUS } from '@/api/types'

const router = useRouter()
const store = usePatientStore()
const { loading, logout } = usePatientSession()

const verified = computed(() => store.authStatus === AUTH_STATUS.VERIFIED)
const maskedPhone = computed(() => store.maskedPhone || '—')

async function onLogout(): Promise<void> {
  await logout()
  await router.push('/patient/login')
}
</script>

<template>
  <main class="profile">
    <header class="profile__head">
      <h1>个人中心</h1>
      <button
        type="button"
        class="btn btn--ghost"
        :disabled="loading"
        data-testid="logout-button"
        @click="onLogout"
      >
        安全退出
      </button>
    </header>

    <section class="card account" data-testid="account-summary">
      <div class="account__avatar" aria-hidden="true">👤</div>
      <div>
        <p class="account__label">绑定手机号</p>
        <p class="account__phone" data-testid="masked-phone">{{ maskedPhone }}</p>
      </div>
    </section>

    <IdentityStatusCard
      :auth-status="store.authStatus"
      :consultation-allowed="store.consultationAllowed"
      :masked-name="store.maskedName"
      :masked-id-card="store.maskedIdCard"
    />

    <div v-if="!verified" class="actions">
      <RouterLink class="btn btn--primary" to="/patient/identity-verification" data-testid="verify-entry">
        去实名认证
      </RouterLink>
    </div>
  </main>
</template>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 34rem;
  margin: 0 auto;
}

.profile__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.profile__head h1 {
  margin: 0;
  font-size: 1.5rem;
}

.account {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem 1.4rem;
}

.account__avatar {
  display: grid;
  place-items: center;
  width: 3rem;
  height: 3rem;
  border-radius: 1rem;
  background: var(--sdas-primary-soft);
  font-size: 1.5rem;
}

.account__label {
  margin: 0;
  color: var(--sdas-muted);
  font-size: 0.8rem;
}

.account__phone {
  margin: 0.1rem 0 0;
  font-size: 1.15rem;
  font-weight: 700;
}

.actions {
  display: flex;
}
</style>
