import { ref } from 'vue'
import { fetchIdentityStatus, submitIdentityVerification } from '@/api/patient-identity'
import { API_CODE, NETWORK_ERROR_MESSAGE } from '@/api/error-codes'
import { usePatientStore } from '@/stores/patient'
import type { IdentityStatusResponse } from '@/api/types'

/**
 * 实名认证（T090）：提交认证、拉取状态，成功后同步 patient store。
 *
 * 宪法 IV：失败文案一律取后端 message，前端不推断白名单命中 / 剩余次数等可枚举原因。
 */
export function useIdentityVerification() {
  const store = usePatientStore()
  const loading = ref(false)
  const error = ref('')
  const status = ref<IdentityStatusResponse | null>(null)

  async function loadStatus(): Promise<boolean> {
    loading.value = true
    error.value = ''
    try {
      const result = await fetchIdentityStatus()
      if (result.code === API_CODE.SUCCESS && result.data) {
        status.value = result.data
        store.applyIdentityStatus(result.data)
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

  async function submit(realName: string, idCardNumber: string): Promise<boolean> {
    if (loading.value) {
      return false
    }
    loading.value = true
    error.value = ''
    try {
      const result = await submitIdentityVerification({ realName, idCardNumber })
      if (result.code === API_CODE.SUCCESS && result.data) {
        store.applyIdentityStatus(result.data)
        status.value = result.data
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

  return { loading, error, status, loadStatus, submit }
}
