import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTreatmentDecisionStore } from '@/stores/treatment-decision'
import {
  generateTreatmentDecision,
  getTreatmentDecision,
  regenerateTreatmentDecision,
  reportTreatmentAdoption,
} from '@/api/treatment-decision'
import {
  MOCK_DECISION_FALLBACK,
  MOCK_DECISION_NOT_FOUND,
  MOCK_DECISION_REUSED,
  MOCK_DECISION_SUCCESS,
  MOCK_BUSY,
} from '@/api/mocks/treatment-decision'

vi.mock('@/api/treatment-decision', () => ({
  getTreatmentDecision: vi.fn(),
  generateTreatmentDecision: vi.fn(),
  regenerateTreatmentDecision: vi.fn(),
  reportTreatmentAdoption: vi.fn(),
}))

const get = vi.mocked(getTreatmentDecision)
const generate = vi.mocked(generateTreatmentDecision)
const regenerate = vi.mocked(regenerateTreatmentDecision)
const adopt = vi.mocked(reportTreatmentAdoption)

describe('treatment decision store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('404 后自动生成并记录已加载问诊单', async () => {
    get.mockResolvedValue(MOCK_DECISION_NOT_FOUND)
    generate.mockResolvedValue(MOCK_DECISION_SUCCESS)

    const store = useTreatmentDecisionStore()
    await store.load(501)

    expect(get).toHaveBeenCalledWith(501)
    expect(generate).toHaveBeenCalledWith(501)
    expect(store.decision?.status).toBe('SUCCESS')
    expect(store.loadedFor).toBe(501)
    expect(store.degraded).toBe(false)
  })

  it('同一问诊单重复调用只触发一次', async () => {
    get.mockResolvedValue(MOCK_DECISION_REUSED)

    const store = useTreatmentDecisionStore()
    await Promise.all([store.load(501), store.load(501)])
    await store.load(501)

    expect(get).toHaveBeenCalledTimes(1)
    expect(store.decision?.reused).toBe(true)
  })

  it('force 走重新生成端点', async () => {
    get.mockResolvedValue(MOCK_DECISION_SUCCESS)
    regenerate.mockResolvedValue(MOCK_DECISION_SUCCESS)

    const store = useTreatmentDecisionStore()
    await store.load(501, { force: true })

    expect(regenerate).toHaveBeenCalledWith(501)
    expect(get).not.toHaveBeenCalled()
  })

  it('降级结果置 degraded=true 且摘要为空', async () => {
    get.mockResolvedValue(MOCK_DECISION_FALLBACK)

    const store = useTreatmentDecisionStore()
    await store.load(501)

    expect(store.degraded).toBe(true)
    expect(store.summary).toBeNull()
    expect(store.decision?.degradedReason).toBe('TIMEOUT')
  })

  it('非 404 失败直接展示后端 message，不再生成', async () => {
    get.mockResolvedValue(MOCK_BUSY)

    const store = useTreatmentDecisionStore()
    await store.load(501)

    expect(generate).not.toHaveBeenCalled()
    expect(store.error).toBe('辅助生成进行中，请稍候')
    expect(store.decision).toBeNull()
  })

  it('采纳回填失败静默，不抛出异常', async () => {
    get.mockResolvedValue(MOCK_DECISION_SUCCESS)
    adopt.mockRejectedValue(new Error('network down'))

    const store = useTreatmentDecisionStore()
    await store.load(501)
    await expect(store.reportAdoption(2)).resolves.toBeUndefined()

    expect(adopt).toHaveBeenCalledWith(501, 2)
  })
})
