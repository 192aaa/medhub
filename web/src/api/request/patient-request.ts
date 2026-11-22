import { createRequest } from './create-request'
import { csrfHeaders } from './csrf'
import { API_CODE } from '../error-codes'
import type { ApiResult, RefreshTokenResponse } from '../types'

/** create-request.ts 读取该键注入 Authorization；续期成功后原地覆盖 */
const TOKEN_KEY = 'sdas.patient.token'
const PROFILE_KEY = 'sdas.patient.profile'
const REFRESH_PATH = '/v1/patient-sessions/current/refresh-token'
const LOGIN_PATH = '/patient/login'

/** 患者端独立 axios 实例（宪法 II：patientRequest） */
export const patientRequest = createRequest({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: Number(import.meta.env.VITE_REQUEST_TIMEOUT),
  tokenStorageKey: TOKEN_KEY,
  // 契约 README 第 5 节：跨域部署下 sdas_refresh / sdas_csrf 需要随请求携带
  withCredentials: true,
  onUnauthorized: beginRefresh,
})

/** 单飞：并发 401 只发起一次续期，其余请求复用同一 Promise */
let refreshing: Promise<boolean> | null = null

function beginRefresh(): Promise<boolean> {
  const pending =
    refreshing ??
    refreshSession().finally(() => {
      refreshing = null
    })
  refreshing = pending
  return pending
}

async function refreshSession(): Promise<boolean> {
  try {
    const response = await patientRequest.put<ApiResult<RefreshTokenResponse>>(REFRESH_PATH, null, {
      headers: csrfHeaders(),
    })
    const payload = response.data
    if (payload.code === API_CODE.SUCCESS && payload.data?.accessToken) {
      localStorage.setItem(TOKEN_KEY, payload.data.accessToken)
      return true
    }
  } catch {
    // 网络异常按续期失败处理，走统一清理
  }
  clearSession()
  void redirectToLogin()
  return false
}

function clearSession(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(PROFILE_KEY)
  } catch {
    // 隐私模式下无持久化可清理
  }
}

/** 动态引入路由，避免 api → router → view → api 的静态循环依赖 */
async function redirectToLogin(): Promise<void> {
  try {
    const { default: router } = await import('@/router')
    if (router.currentRoute.value.path !== LOGIN_PATH) {
      await router.replace(LOGIN_PATH)
    }
  } catch {
    // 无路由环境（单测）时忽略跳转
  }
}
