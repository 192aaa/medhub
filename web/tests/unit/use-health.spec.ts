import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { getHealth } from '@/api/health'
import { NETWORK_ERROR_MESSAGE } from '@/api/error-codes'
import { MOCK_HEALTH_DOWN, MOCK_HEALTH_UP } from '@/api/mocks/health'
import { useHealth } from '@/composables/use-health'

vi.mock('@/api/health', () => ({ getHealth: vi.fn() }))

const mockedGetHealth = vi.mocked(getHealth)

describe('useHealth（composable）', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('M1：code=200 时写入报告、isUp=true、无错误', async () => {
    mockedGetHealth.mockResolvedValue(MOCK_HEALTH_UP)

    const { report, isUp, error, getHealth: refresh } = useHealth()
    await refresh()

    expect(report.value?.status).toBe('UP')
    expect(report.value?.components).toHaveLength(3)
    expect(isUp.value).toBe(true)
    expect(error.value).toBeNull()
  })

  it('M2：code=503 时仍保留报告并暴露后端 message', async () => {
    mockedGetHealth.mockResolvedValue(MOCK_HEALTH_DOWN)

    const { report, isUp, error, getHealth: refresh } = useHealth()
    await refresh()

    expect(report.value?.status).toBe('DOWN')
    expect(report.value?.components[1].detail).toBe('unavailable')
    expect(isUp.value).toBe(false)
    expect(error.value).toBe(MOCK_HEALTH_DOWN.message)
  })

  it('网络异常时给出 UI 兜底文案且结束 loading', async () => {
    mockedGetHealth.mockRejectedValue(new Error('network down'))

    const { error, loading, getHealth: refresh } = useHealth()
    await refresh()

    expect(error.value).toBe(NETWORK_ERROR_MESSAGE)
    expect(loading.value).toBe(false)
  })
})
