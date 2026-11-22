/**
 * Playwright Mock E2E 统一注入入口。
 *
 * Mock 唯一来源：web/src/api/mocks/patient-identity.ts（同步自 contracts/mocks.md M1–M15）。
 * 真实后端 E2E 使用 E2E_REAL_BACKEND=1 关闭拦截。
 */
import type { Page } from '@playwright/test'
import { defaultMockFor, findMock } from '../../../src/api/mocks/patient-identity'

/** method + path → 场景编号（M1–M15），未指定的路由使用默认场景 */
export type ScenarioMap = Record<string, string>

export interface MockInstallOptions {
  scenarios?: ScenarioMap
  realBackend?: boolean
}

/**
 * 安装 /api/v1/** 的 mock 拦截。
 *
 * @param page Playwright 页面
 * @param options 场景覆盖与真实后端开关
 */
export async function installPatientIdentityMocks(
  page: Page,
  options: MockInstallOptions = {},
): Promise<void> {
  const realBackend = options.realBackend ?? process.env.E2E_REAL_BACKEND === '1'
  if (realBackend) {
    return
  }

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/api/, '')
    const key = `${request.method().toUpperCase()} ${path}`
    const scenarioId = options.scenarios?.[key]
    const mock =
      (scenarioId ? findMock(scenarioId) : undefined) ?? defaultMockFor(request.method(), path)

    if (!mock) {
      await route.abort()
      return
    }

    await route.fulfill({
      status: mock.status,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(mock.body),
    })
  })
}
