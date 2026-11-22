<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePatientSession } from '@/composables/use-patient-session'

const route = useRoute()
const router = useRouter()
const { loading, error, login } = usePatientSession()

const phone = ref('')
const password = ref('')

const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const passwordValid = computed(() => password.value.length >= 6 && password.value.length <= 20)
const canSubmit = computed(() => phoneValid.value && passwordValid.value && !loading.value)

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) {
    return
  }
  if (await login(phone.value, password.value)) {
    // 守卫跳转时带 redirect，登录后回到原目标页
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    await router.push(redirect || '/patient/profile')
  }
}
</script>

<template>
  <main class="auth">
    <div class="card auth__card">
      <header class="auth__head">
        <span class="auth__mark" aria-hidden="true">🩺</span>
        <h1>患者登录</h1>
        <p>登录后即可预约挂号、图文问诊</p>
      </header>

      <form class="auth__form" @submit.prevent="onSubmit">
        <div class="field">
          <label for="login-phone">手机号</label>
          <input
            id="login-phone"
            v-model="phone"
            inputmode="numeric"
            maxlength="11"
            placeholder="请输入 11 位手机号"
            autocomplete="username"
          />
        </div>

        <div class="field">
          <label for="login-password">密码</label>
          <input
            id="login-password"
            v-model="password"
            type="password"
            maxlength="20"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>

        <button type="submit" class="btn btn--primary btn--block" :disabled="!canSubmit">
          {{ loading ? '登录中…' : '登录' }}
        </button>

        <p v-if="error" class="error" data-testid="login-error">{{ error }}</p>

        <p class="auth__switch">
          还没有账号？
          <RouterLink to="/patient/register">去注册</RouterLink>
          <RouterLink class="auth__link-right" to="/patient/password-reset">忘记密码</RouterLink>
        </p>
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

.auth__switch {
  margin: 0;
  text-align: center;
  font-size: 0.9rem;
  color: var(--sdas-muted);
}

.auth__switch a {
  font-weight: 600;
}

.auth__link-right {
  float: right;
}
</style>
