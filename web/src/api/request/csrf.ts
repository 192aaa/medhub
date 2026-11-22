/**
 * CSRF 双 Cookie 协议的前端辅助（契约 README 第 4/5 节）。
 *
 * sdas_csrf 为非 HttpOnly Cookie，前端读取后放入 X-CSRF-Token 请求头；
 * 首次访问尚无 Cookie 时生成随机值并写入，服务端做常量时间比对。
 */
export const CSRF_COOKIE = 'sdas_csrf'
export const CSRF_HEADER = 'X-CSRF-Token'

const COOKIE_PATH = '/api/v1'

/** 读取 CSRF Cookie，不存在时返回 null */
export function readCsrfCookie(): string | null {
  const matched = document.cookie.match(/(?:^|;\s*)sdas_csrf=([^;]+)/)
  return matched ? decodeURIComponent(matched[1]) : null
}

function randomToken(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    const bytes = new Uint8Array(16)
    crypto.getRandomValues(bytes)
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  }
  return Math.random().toString(36).slice(2) + Date.now().toString(36)
}

/** 生成修改类请求所需的 CSRF 请求头；缺失 Cookie 时先补种再复用 */
export function csrfHeaders(): Record<string, string> {
  let token = readCsrfCookie()
  if (!token) {
    token = randomToken()
    document.cookie = `${CSRF_COOKIE}=${encodeURIComponent(token)}; Path=${COOKIE_PATH}; SameSite=Lax`
  }
  return { [CSRF_HEADER]: token }
}
