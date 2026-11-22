<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pharmacyLogin } from '@/api/pharmacy'
import { usePharmacyStore } from '@/stores/pharmacy'

const route = useRoute()
const router = useRouter()
const store = usePharmacyStore()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref('')

async function submit(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    const res = await pharmacyLogin({ username: username.value.trim(), password: password.value })
    if (res.code !== 200 || !res.data) {
      errorMessage.value = res.message || '登录失败'
      return
    }
    store.applyLogin(res.data)
    // 扫码直达时 redirect 携带 ?t=...，登录后原样回跳以保留令牌
    const redirect =
      typeof route.query.redirect === 'string' ? route.query.redirect : '/pharmacy/verify'
    await router.replace(redirect)
  } catch (error) {
    const message = (error as { response?: { data?: { message?: string } } })?.response?.data
      ?.message
    errorMessage.value = message || '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth">
    <div class="card auth__card">
      <header class="auth__head">
        <span class="auth__mark" aria-hidden="true">💊</span>
        <h1>药店登录</h1>
        <p>仅药店工作人员可核对并核销电子处方</p>
      </header>

      <form class="auth__form" @submit.prevent="submit">
        <div class="field">
          <label for="pharmacy-username">登录名</label>
          <input
            id="pharmacy-username"
            v-model="username"
            name="username"
            autocomplete="username"
            maxlength="64"
            placeholder="请输入登录名"
            required
          />
        </div>

        <div class="field">
          <label for="pharmacy-password">口令</label>
          <input
            id="pharmacy-password"
            v-model="password"
            name="password"
            type="password"
            autocomplete="current-password"
            maxlength="100"
            placeholder="请输入口令"
            required
          />
        </div>

        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>

        <button type="submit" class="btn btn--primary btn--block" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </form>
    </div>
  </main>
</template>

<style scoped>
.auth {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 70vh;
  padding: 2rem 1rem;
}

.auth__card {
  width: 100%;
  max-width: 24rem;
  padding: 2rem 1.75rem;
}

.auth__head {
  text-align: center;
  margin-bottom: 1.5rem;
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

.error {
  margin: 0;
  font-size: 0.88rem;
}
</style>
