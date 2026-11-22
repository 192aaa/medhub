import { storeToRefs } from 'pinia'
import { getHealth as fetchHealthApi } from '@/api/health'
import { API_CODE, NETWORK_ERROR_MESSAGE } from '@/api/error-codes'
import { useHealthStore } from '@/stores/health'

/**
 * 健康检查组合式函数：编排 API 调用与 Pinia 状态。
 *
 * <p>宪法 V：本模块内函数名 getHealth 与 src/api/health.ts 导出的 getHealth 同名，
 * 故引入时使用 as 别名，禁止同名冲突。</p>
 */
export function useHealth() {
  const healthStore = useHealthStore()
  const { report, loading, error, overallStatus, isUp } = storeToRefs(healthStore)

  async function getHealth(): Promise<void> {
    healthStore.setLoading(true)
    healthStore.setError(null)
    try {
      const result = await fetchHealthApi()
      healthStore.setResult(result)
      // 契约：必须按响应 code 判断，不能只看 HTTP 状态或 UI
      if (result.code !== API_CODE.SUCCESS) {
        healthStore.setError(result.message)
      }
    } catch {
      healthStore.setError(NETWORK_ERROR_MESSAGE)
    } finally {
      healthStore.setLoading(false)
    }
  }

  return { report, loading, error, overallStatus, isUp, getHealth }
}
