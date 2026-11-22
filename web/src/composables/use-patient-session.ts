import { computed, ref } from 'vue'
import { loginPatient, logoutPatient, refreshPatientToken } from '@/api/patient-identity'
import { API_CODE, NETWORK_ERROR_MESSAGE } from '@/api/error-codes'
import { usePatientStore } from '@/stores/patient'

/**
 * 患者会话操作集合（T072）：登录、安全退出、续期与会话判定。
 *
 * 宪法 IV：失败文案一律取后端 message，前端只兜底网络异常。
 */
export function usePatientSession() {
  const store = usePatientStore()
  const loading = ref(false)
  const error = ref('')

  const hasSession = computed(() => store.hasSession)

  async function login(phone: string, password: string): Promise<boolean> {
    if (loading.value) {
      return false
    }
    loading.value = true
    error.value = ''
    try {
      const result = await loginPatient({ phone, password })
      if (result.code === API_CODE.SUCCESS && result.data) {
        store.applyRegisterResult(result.data)
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

  async function logout(): Promise<void> {
    loading.value = true
    try {
      await logoutPatient()
    } catch {
      // 网络失败时本地同样清理；服务端会话由 refresh 过期兜底
    } finally {
      store.clear()
      loading.value = false
    }
  }

  async function refresh(): Promise<boolean> {
    try {
      const result = await refreshPatientToken()
      if (result.code === API_CODE.SUCCESS && result.data) {
        store.setAccessToken(result.data.accessToken)
        return true
      }
      store.clear()
      return false
    } catch {
      return false
    }
  }

  return { loading, error, hasSession, login, logout, refresh }
}
