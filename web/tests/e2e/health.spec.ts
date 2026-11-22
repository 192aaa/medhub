import { expect, test } from '@playwright/test'
import { MOCK_HEALTH_DOWN, MOCK_HEALTH_UP } from '../../src/api/mocks/health'

test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

/**
 * 宪法 VI：Mock E2E 用 page.route() 测 UI/异常；mock 数据来自
 * specs/health-check/contracts/health-api.md（经 src/api/mocks/health.ts 同步）。
 */
test('M1：全部 UP 时展示整体 UP 与 3 个组件', async ({ page }) => {
  await page.route('**/api/health', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(MOCK_HEALTH_UP),
    }),
  )

  await page.goto('/system/health')

  await expect(page.locator('.health-panel__overall')).toContainText('UP')
  await expect(page.locator('.health-card')).toHaveCount(3)
  await expect(page.locator('.health-card__detail').first()).toContainText('running')
})

test('M2：降级时展示 DOWN、unavailable 与后端 message', async ({ page }) => {
  await page.route('**/api/health', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/json',
      body: JSON.stringify(MOCK_HEALTH_DOWN),
    }),
  )

  await page.goto('/system/health')

  await expect(page.locator('.health-panel__overall')).toContainText('DOWN')
  await expect(page.locator('[role="alert"]')).toContainText(MOCK_HEALTH_DOWN.message)
  await expect(page.locator('.health-card__detail')).toContainText([
    'running',
    'unavailable',
    'PING = PONG',
  ])
})

test('真实后端 E2E：必须断言响应 code 值（不能只看 UI）', async ({ page, request }) => {
  test.skip(
    !process.env.E2E_REAL_BACKEND,
    '需启动 Spring Boot + MySQL + Redis 并设置 E2E_REAL_BACKEND=1',
  )

  const response = await request.get('/api/health')
  const body = (await response.json()) as { code: number }
  expect([200, 503]).toContain(body.code)
  expect(response.status()).toBe(body.code)

  await page.goto('/system/health')
  await expect(page.locator('.health-panel__overall')).toContainText(
    body.code === 200 ? 'UP' : 'DOWN',
  )
})
