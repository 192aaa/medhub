import { publicRequest } from './request/public-request'
import type { ApiResult, HealthReport } from './types'

/**
 * 契约：specs/health-check/contracts/health-api.md（GET /api/health）
 * 宪法 III：baseURL 由 VITE_API_BASE_URL 提供且已含 /api，此处具体路径不得重复 /api。
 */
const HEALTH_PATH = '/health'

/** 查询服务端、数据库、Redis 健康状态；响应 code 可能为 200 或 503 */
export async function getHealth(): Promise<ApiResult<HealthReport>> {
  const response = await publicRequest.get<ApiResult<HealthReport>>(HEALTH_PATH)
  return response.data
}
