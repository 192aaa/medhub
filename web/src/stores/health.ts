import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { API_CODE } from '@/api/error-codes'
import { HEALTH_STATUS, type ApiResult, type HealthReport, type HealthStatus } from '@/api/types'

/** 健康检查跨组件状态（宪法 II：禁止组件内直接管理跨组件状态） */
export const useHealthStore = defineStore('health', () => {
  const result = ref<ApiResult<HealthReport> | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const report = computed<HealthReport | null>(() => result.value?.data ?? null)
  const overallStatus = computed<HealthStatus | null>(() => report.value?.status ?? null)
  const isUp = computed(
    () => result.value?.code === API_CODE.SUCCESS && overallStatus.value === HEALTH_STATUS.UP,
  )

  function setResult(value: ApiResult<HealthReport>): void {
    result.value = value
  }

  function setLoading(value: boolean): void {
    loading.value = value
  }

  function setError(value: string | null): void {
    error.value = value
  }

  return { result, loading, error, report, overallStatus, isUp, setResult, setLoading, setError }
})
