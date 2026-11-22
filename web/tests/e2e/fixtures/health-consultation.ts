import type { Page } from '@playwright/test'
import {
  MOCK_CHAT_BUSY,
  MOCK_MESSAGES,
  MOCK_SESSION_CLEAR_ALL,
  MOCK_SESSION_CREATE,
  MOCK_SESSION_DELETE,
  MOCK_SESSION_LIST,
  MOCK_SESSION_RENAME,
  MOCK_SSE_EMERGENCY_EVENTS,
  MOCK_SSE_ERROR_EVENTS,
  MOCK_SSE_NORMAL_EVENTS,
} from '../../../src/api/mocks/health-consultation'

export type HealthScenario = 'NORMAL' | 'EMERGENCY' | 'ERROR' | 'BUSY'

export interface HealthMockOptions {
  scenario?: HealthScenario
}

/**
 * 安装 AI健康咨询 Mock（数据与 contracts/mocks.md 同源）。
 *
 * SSE 端点用一次性 body 返回完整帧序列；前端仍按 `\n\n` 逐帧解析，
 * 因此事件顺序（accepted → emergency? → delta* → disclaimer → done）会被真实走一遍。
 */
export async function installHealthMocks(
  page: Page,
  options: HealthMockOptions = {},
): Promise<void> {
  const scenario = options.scenario ?? 'NORMAL'

  await page.route('**/v1/health-consultation/sessions', async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      await route.fulfill({ json: MOCK_SESSION_LIST })
      return
    }
    if (method === 'POST') {
      await route.fulfill({ json: MOCK_SESSION_CREATE })
      return
    }
    await route.fulfill({ json: MOCK_SESSION_CLEAR_ALL })
  })

  await page.route('**/v1/health-consultation/sessions/*', async (route) => {
    const method = route.request().method()
    if (method === 'PATCH') {
      await route.fulfill({ json: MOCK_SESSION_RENAME })
      return
    }
    if (method === 'DELETE') {
      await route.fulfill({ json: MOCK_SESSION_DELETE })
      return
    }
    await route.continue()
  })

  await page.route('**/v1/health-consultation/sessions/*/messages', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: MOCK_MESSAGES })
      return
    }
    if (scenario === 'BUSY') {
      await route.fulfill({ status: 409, json: MOCK_CHAT_BUSY })
      return
    }
    const frames =
      scenario === 'EMERGENCY'
        ? MOCK_SSE_EMERGENCY_EVENTS
        : scenario === 'ERROR'
          ? MOCK_SSE_ERROR_EVENTS
          : MOCK_SSE_NORMAL_EVENTS
    await route.fulfill({
      status: 200,
      headers: { 'content-type': 'text/event-stream;charset=UTF-8' },
      body: frames.join(''),
    })
  })
}
