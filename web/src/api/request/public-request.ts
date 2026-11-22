import { createRequest } from './create-request'

/** 无认证基础设施请求（GET /api/health）；不读取 token，不解析 JWT */
export const publicRequest = createRequest({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: Number(import.meta.env.VITE_REQUEST_TIMEOUT),
})
