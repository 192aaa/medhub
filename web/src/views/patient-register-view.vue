<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AgreementConsent from '@/components/agreement-consent.vue'
import VerificationCodeInput from '@/components/verification-code-input.vue'
import { registerPatient } from '@/api/patient-identity'
import { API_CODE, NETWORK_ERROR_MESSAGE } from '@/api/error-codes'
import { useVerificationCode } from '@/composables/use-verification-code'
import { usePatientStore } from '@/stores/patient'
import { VERIFICATION_PURPOSE } from '@/api/types'

const router = useRouter()
const store = usePatientStore()
const {
  secondsLeft,
  error: codeError,
  loading: codeLoading,
  send,
} = useVerificationCode(VERIFICATION_PURPOSE.REGISTER)

const agreementVersion = import.meta.env.VITE_AGREEMENT_VERSION || 'v1.0'
const phone = ref('')
const verificationCode = ref('')
const password = ref('')
const agreementAccepted = ref(false)
const error = ref('')
const submitting = ref(false)

const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const codeValid = computed(() => /^\d{6}$/.test(verificationCode.value))
const passwordValid = computed(
  () =>
    /^[A-Za-z0-9]{6,20}$/.test(password.value) &&
    /[A-Za-z]/.test(password.value) &&
    /[0-9]/.test(password.value),
)
const canSubmit = computed(
  () =>
    phoneValid.value &&
    codeValid.value &&
    passwordValid.value &&
    agreementAccepted.value &&
    !submitting.value,
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
    const idempotencyKey =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : undefined
    const result = await registerPatient(
      {
        phone: phone.value,
        verificationCode: verificationCode.value,
        password: password.value,
        agreementAccepted: agreementAccepted.value,
        agreementVersion,
      },
      idempotencyKey,
    )
    if (result.code === API_CODE.SUCCESS && result.data) {
      store.applyRegisterResult(result.data)
      await router.push('/patient/profile')
    } else {
      // 字段级错误文案一律来自后端 message，前端不硬编码业务文案
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
        <span class="auth__mark" aria-hidden="true">🪪</span>
        <h1>患者注册</h1>
        <p>手机号注册并完成实名认证</p>
      </header>

      <form class="auth__form" @submit.prevent="onSubmit">
        <div class="field">
          <label for="register-phone">手机号</label>
          <input
            id="register-phone"
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
          <label for="register-password">密码</label>
          <input
            id="register-password"
            v-model="password"
            type="password"
            placeholder="6–20 位字母数字，至少 1 字母 + 1 数字"
          />
        </div>

        <AgreementConsent v-model="agreementAccepted" :version="agreementVersion" />

        <button type="submit" class="btn btn--primary btn--block" :disabled="!canSubmit">
          {{ submitting ? '注册中…' : '注册并登录' }}
        </button>

        <p v-if="codeError" class="error" data-testid="code-error">{{ codeError }}</p>
        <p v-if="error" class="error" data-testid="form-error">{{ error }}</p>

        <p class="auth__switch">
          已有账号？
          <RouterLink to="/patient/login">去登录</RouterLink>
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
