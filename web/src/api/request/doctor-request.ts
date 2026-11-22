import { createRequest } from './create-request'

/** 医生端独立 axios 实例（宪法 II：doctorRequest），token 键与患者端隔离 */
export const doctorRequest = createRequest({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: Number(import.meta.env.VITE_REQUEST_TIMEOUT),
  tokenStorageKey: 'sdas.doctor.token',
  // 契约 README 第 5 节：跨域部署下 sdas_csrf 需随请求携带（双重 Cookie 校验）
  withCredentials: true,
})
