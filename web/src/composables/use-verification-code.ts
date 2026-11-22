import { onUnmounted, ref } from 'vue'
import { sendVerificationCode } from '@/api/patient-identity'
import { API_CODE, NETWORK_ERROR_MESSAGE } from '@/api/error-codes'
import type { VerificationPurpose } from '@/api/types'

/**
 * 验证码发送与倒计时。错误文案只取后端 message，禁止读取响应中的验证码字段。
 */
export function useVerificationCode(purpose: VerificationPurpose) {
  const secondsLeft = ref(0)
  const error = ref('')
  const loading = ref(false)
  let timer: ReturnType<typeof setInterval> | undefined

  function stopCountdown(): void {
    if (timer !== undefined) {
      clearInterval(timer)
      timer = undefined
    }
  }

  function startCountdown(seconds: number): void {
    stopCountdown()
    secondsLeft.value = seconds
    timer = setInterval(() => {
      secondsLeft.value = Math.max(0, secondsLeft.value - 1)
      if (secondsLeft.value === 0) {
        stopCountdown()
      }
    }, 1000)
  }

  async function send(phone: string): Promise<boolean> {
    if (loading.value || secondsLeft.value > 0) {
      return false
    }
    loading.value = true
    try {
      const result = await sendVerificationCode({ phone, purpose })
      if (result.code === API_CODE.SUCCESS && result.data) {
        error.value = ''
        startCountdown(result.data.nextSendAllowedInSeconds)
        return true
      }
      error.value = result.message
      return false
    } catch {
      error.value = NETWORK_ERROR_MESSAGE
      return false
    } finally {
      loading.value = false
    }
  }

  onUnmounted(stopCountdown)

  return { secondsLeft, error, loading, send, startCountdown, stopCountdown }
}
