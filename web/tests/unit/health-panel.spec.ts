import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import HealthPanel from '@/components/health-panel.vue'
import { MOCK_HEALTH_DOWN, MOCK_HEALTH_UP } from '@/api/mocks/health'

describe('HealthPanel（公共组件）', () => {
  it('M1：展示整体 UP 与 3 个组件状态', () => {
    const wrapper = mount(HealthPanel, {
      props: { report: MOCK_HEALTH_UP.data, loading: false, error: null },
    })

    expect(wrapper.find('.health-panel__overall').text()).toContain('UP')
    expect(wrapper.findAll('.health-card')).toHaveLength(3)
    expect(wrapper.text()).toContain('SELECT 1 = 1')
  })

  it('M2：展示 DOWN、unavailable 与错误提示，并抛出 refresh 事件', async () => {
    const wrapper = mount(HealthPanel, {
      props: {
        report: MOCK_HEALTH_DOWN.data,
        loading: false,
        error: MOCK_HEALTH_DOWN.message,
      },
    })

    expect(wrapper.find('.health-panel__overall').text()).toContain('DOWN')
    expect(wrapper.text()).toContain('unavailable')
    expect(wrapper.find('[role="alert"]').text()).toBe(MOCK_HEALTH_DOWN.message)

    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)
  })

  it('加载中禁用刷新按钮', () => {
    const wrapper = mount(HealthPanel, {
      props: { report: null, loading: true, error: null },
    })

    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.health-panel__overall').text()).toContain('检查中')
  })
})
