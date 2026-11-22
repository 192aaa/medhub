<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import VerificationCodeInput from '@/components/verification-code-input.vue'
import { resetPassword } from '@/api/patient-identity'
import { API_CODE, NETWORK_ERROR_MESSAGE } from '@/api/error-codes'
import { useVerificationCode } from '@/composables/use-verification-code'
import { VERIFICATION_PURPOSE } from '@/api/types'

/**
 * 端点 6：重置密码（匿名）。成功后将导向登录页，由用户用新密码登录。
 */
const router = useRouter()
const {
  secondsLeft,
  error: codeError,
  loading: codeLoading,
  send,
} = useVerificationCode(VERIFICATION_PURPOSE.RESET_PASSWORD)

const phone = ref('')
const verificationCode = ref('')
const newPassword = ref('')
const error = ref('')
const submitting = ref(false)

const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const codeValid = computed(() => /^\d{6}$/.test(verificationCode.value))
const passwordValid = computed(
  () =>
    /^[A-Za-z0-9]{6,20}$/.test(newPassword.value) &&
    /[A-Za-z]/.test(newPassword.value) &&
    /[0-9]/.test(newPassword.value),
)
const canSubmit = computed(
  () => phoneValid.value && codeValid.value && passwordValid.value && !submitting.value,
)

async function onSendCode(): Promise<void> {
  await send(phone.value)
}

async function onSubmit(): Promise<void> {
  if (!canSubmit.value) {
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const result = await resetPassword({
      phone: phone.value,
      verificationCode: verificationCode.value,
      newPassword: newPassword.value,
    })
    if (result.code === API_CODE.SUCCESS) {
      await router.push('/patient/login')
    } else {
      error.value = result.message
    }
  } catch {
    error.value = NETWORK_ERROR_MESSAGE
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth">
    <div class="card auth__card">
      <header class="auth__head">
        <span class="auth__mark" aria-hidden="true">🔑</span>
        <h1>重置密码</h1>
        <p>验证手机号后设置新密码</p>
      </header>

      <form class="auth__form" @submit.prevent="onSubmit">
        <div class="field">
          <label for="reset-phone">手机号</label>
          <input
            id="reset-phone"
            v-model="phone"
            inputmode="numeric"
            maxlength="11"
            placeholder="请输入 11 位手机号"
          />
        </div>

        <VerificationCodeInput
          v-model="verificationCode"
          :seconds-left="secondsLeft"
          :disabled="!phoneValid || codeLoading"
          @send="onSendCode"
        />

        <div class="field">
          <label for="reset-password">新密码</label>
          <input
            id="reset-password"
            v-model="newPassword"
            type="password"
            placeholder="6–20 位字母数字"
          />
        </div>

        <button type="submit" class="btn btn--primary btn--block" :disabled="!canSubmit">
          {{ submitting ? '提交中…' : '重置密码' }}
        </button>

        <p v-if="codeError" class="error" data-testid="code-error">{{ codeError }}</p>
        <p v-if="error" class="error" data-testid="form-error">{{ error }}</p>

        <p class="auth__switch">
          想起密码了？
          <RouterLink to="/patient/login">返回登录</RouterLink>
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
</style>
