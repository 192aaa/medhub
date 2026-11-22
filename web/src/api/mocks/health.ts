/**
 * Mock 唯一来源：specs/health-check/contracts/health-api.md M1 / M2 / M3。
 * 宪法 VI：禁止凭空手写 mock；contracts 变更时须同步更新本文件。
 */
import type { ApiResult, HealthReport } from '../types'

/** M1：全部可用，HTTP 200 */
export const MOCK_HEALTH_UP: ApiResult<HealthReport> = {
  code: 200,
  message: '成功',
  data: {
    status: 'UP',
    components: [
      { name: 'server', status: 'UP', detail: 'running', elapsedMs: 0 },
      { name: 'database', status: 'UP', detail: 'SELECT 1 = 1', elapsedMs: 3 },
      { name: 'redis', status: 'UP', detail: 'PING = PONG', elapsedMs: 1 },
    ],
    checkedAt: '2026-11-07T10:31:15',
  },
}

/** M2：数据库不可用，HTTP 503 */
export const MOCK_HEALTH_DOWN: ApiResult<HealthReport> = {
  code: 503,
  message: '依赖组件不可用',
  data: {
    status: 'DOWN',
    components: [
      { name: 'server', status: 'UP', detail: 'running', elapsedMs: 0 },
      { name: 'database', status: 'DOWN', detail: 'unavailable', elapsedMs: 2001 },
      { name: 'redis', status: 'UP', detail: 'PING = PONG', elapsedMs: 1 },
    ],
    checkedAt: '2026-11-07T10:31:17',
  },
}

/** M3：方法不允许，HTTP 405 */
export const MOCK_METHOD_NOT_ALLOWED: ApiResult<null> = {
  code: 405,
  message: '请求方法不支持',
  data: null,
}
