import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

export interface RequestConfig {
  baseURL: string
  timeout: number
  /** 角色独立 token key；为空时不附加认证头 */
  tokenStorageKey?: string
  /** 是否携带跨域 Cookie（患者端须为 true，契约 README 第 5 节） */
  withCredentials?: boolean
  /**
   * 401 续期回调，由角色实例各自注入（宪法 II：工厂不做角色判断）。
   *
   * <p>返回 true 表示凭证已恢复，工厂重放原请求；返回 false 则原样返回 401。</p>
   */
  onUnauthorized?: () => Promise<boolean>
}

/** 重放标记挂在请求配置上，防止同一请求二次触发续期 */
interface RetriableConfig extends InternalAxiosRequestConfig {
  sdasRetried?: boolean
}

/** 续期端点自身返回 401 时不再触发续期，避免递归 */
const REFRESH_PATH_HINT = 'refresh-token'

/**
 * 创建独立 axios 实例及独立拦截器。
 *
 * <p>宪法 II：不同角色必须用独立实例，禁止在共用拦截器里按角色 if-else 分支；
 * 本工厂只按传入配置工作，不做任何角色判断。</p>
 *
 * <p>宪法 III：validateStatus 放行 200–599，业务失败（含 401/503）由调用方按
 * code 判定，因此响应拦截器须在 onFulfilled 中处理 401，而非 onRejected。</p>
 */
export function createRequest(config: RequestConfig): AxiosInstance {
  const instance = axios.create({
    baseURL: config.baseURL,
    timeout: config.timeout,
    withCredentials: config.withCredentials ?? false,
    headers: { 'Content-Type': 'application/json' },
    // 契约 M2：HTTP 503 仍返回完整 data，交由调用方按 code 判断，禁止 axios 直接抛错
    validateStatus: (status) => status >= 200 && status < 600,
  })

  instance.interceptors.request.use((requestConfig: InternalAxiosRequestConfig) => {
    // 前端只透传 token，不解析 JWT；身份提取由后端 XxxIdExtractor 完成
    const token = config.tokenStorageKey ? localStorage.getItem(config.tokenStorageKey) : null
    if (token) {
      requestConfig.headers.set('Authorization', `Bearer ${token}`)
    }
    return requestConfig
  })

  const { onUnauthorized } = config

  instance.interceptors.response.use(async (response: AxiosResponse) => {
    const requestConfig = response.config as RetriableConfig
    const shouldRenew =
      response.status === 401 &&
      typeof onUnauthorized === 'function' &&
      requestConfig.sdasRetried !== true &&
      !String(requestConfig.url ?? '').includes(REFRESH_PATH_HINT)
    if (!shouldRenew) {
      return response
    }
    const recovered = await onUnauthorized()
    if (!recovered) {
      return response
    }
    // 请求拦截器会在重放时重新读取 localStorage，自动带上新 token
    requestConfig.sdasRetried = true
    return instance.request(requestConfig)
  })

  return instance
}
