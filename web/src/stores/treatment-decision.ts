import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  generateTreatmentDecision,
  getTreatmentDecision,
  regenerateTreatmentDecision,
  reportTreatmentAdoption,
} from '@/api/treatment-decision'
import { API_CODE, NETWORK_ERROR_MESSAGE } from '@/api/error-codes'
import {
  TREATMENT_DECISION_STATUS,
  type ApiResult,
  type TreatmentDecisionResponse,
} from '@/api/types'

/**
 * AI辅助治疗决策 store（FR-001/FR-002/FR-016）。
 *
 * 宪法 II：自动触发只发一次，`loadedFor === consultationId` 时直接返回，避免
 * 组件重复挂载导致重复调用模型；刷新恢复走端点 2（GET），不重新生成。
 */
export const useTreatmentDecisionStore = defineStore('treatmentDecision', () => {
  const decision = ref<TreatmentDecisionResponse | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  /** 已完成加载（含降级）的问诊单，用于防止重复自动触发 */
  const loadedFor = ref<number | null>(null)

  let lastConsultationId: number | null = null
  /** 进行中的加载，防止同一问诊单并发自动触发（面板与页面同时挂载） */
  let pendingFor: number | null = null

  const degraded = computed(
    () => decision.value !== null && decision.value.status !== TREATMENT_DECISION_STATUS.SUCCESS,
  )

  const summary = computed(() => decision.value?.summary ?? null)
  const suggestions = computed(() => decision.value?.suggestions ?? [])
  const medicationDrafts = computed(() => decision.value?.medicationDrafts ?? [])
  const disclaimer = computed(() => decision.value?.disclaimer ?? '')

  function accept(result: ApiResult<TreatmentDecisionResponse>, consultationId: number): boolean {
    if (result.code === API_CODE.SUCCESS && result.data) {
      decision.value = result.data
      loadedFor.value = consultationId
      return true
    }
    return false
  }

  /**
   * 加载辅助结果：先 GET 恢复，404 再 POST 生成；`force` 走强制重新生成。
   *
   * @param consultationId 问诊单主键
   * @param options force=true 时跳过复用直接重新生成
   */
  async function load(consultationId: number, options: { force?: boolean } = {}): Promise<void> {
    if (!Number.isFinite(consultationId) || consultationId <= 0) {
      return
    }
    lastConsultationId = consultationId
    if (!options.force && (loadedFor.value === consultationId || pendingFor === consultationId)) {
      return
    }
    pendingFor = consultationId
    loading.value = true
    error.value = null
    try {
      const first = options.force
        ? await regenerateTreatmentDecision(consultationId)
        : await getTreatmentDecision(consultationId)
      if (accept(first, consultationId)) {
        return
      }
      if (!options.force && first.code === API_CODE.TREATMENT_DECISION_NOT_FOUND) {
        const created = await generateTreatmentDecision(consultationId)
        if (!accept(created, consultationId)) {
          error.value = created.message
        }
        return
      }
      error.value = first.message
    } catch {
      error.value = NETWORK_ERROR_MESSAGE
    } finally {
      pendingFor = null
      loading.value = false
    }
  }

  /** 降级/失败后重试：强制重新生成（消耗一次重新生成额度） */
  async function retry(): Promise<void> {
    if (lastConsultationId === null) {
      return
    }
    await load(lastConsultationId, { force: true })
  }

  /** 回填采纳数量：调用失败静默，不阻断医生开方 */
  async function reportAdoption(appliedItemCount: number): Promise<void> {
    if (lastConsultationId === null) {
      return
    }
    try {
      await reportTreatmentAdoption(lastConsultationId, appliedItemCount)
    } catch {
      // 回填失败不影响处方提交（FR-016）
    }
  }

  function reset(): void {
    decision.value = null
    loading.value = false
    error.value = null
    loadedFor.value = null
    lastConsultationId = null
  }

  return {
    decision,
    loading,
    error,
    loadedFor,
    degraded,
    summary,
    suggestions,
    medicationDrafts,
    disclaimer,
    load,
    retry,
    reportAdoption,
    reset,
  }
})
