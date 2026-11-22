<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { doctorLogin } from '@/api/doctor-consultation'
import { API_CODE } from '@/api/error-codes'
import { useDoctorStore } from '@/stores/doctor'

const router = useRouter()
const doctorStore = useDoctorStore()
const username = ref('')
const password = ref('')
const errorMessage = ref('')
const loading = ref(false)

async function submit() {
  if (!username.value || !password.value) {
    errorMessage.value = '请输入账号和密码'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const res = await doctorLogin({ username: username.value, password: password.value })
    if (res.code === API_CODE.SUCCESS && res.data) {
      doctorStore.applyLogin(res.data)
      await router.replace('/workbench/consultations')
    } else {
      errorMessage.value = res.message
    }
  } catch {
    errorMessage.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth">
    <div class="card auth__card">
      <header class="auth__head">
        <span class="auth__mark" aria-hidden="true">🩺</span>
        <h1>医生工作台登录</h1>
        <p>接诊问诊单 · 开具电子处方</p>
      </header>

      <form class="auth__form" @submit.prevent="submit">
        <div class="field">
          <label for="doctor-username">账号</label>
          <input
            id="doctor-username"
            v-model="username"
            type="text"
            placeholder="请输入医生账号"
            autocomplete="username"
          />
        </div>

        <div class="field">
          <label for="doctor-password">密码</label>
          <input
            id="doctor-password"
            v-model="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>

        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

        <button type="submit" class="btn btn--primary btn--block" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
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
