import { expect, test } from '@playwright/test'
import { realApiFetch } from './fixtures/appointment-consultation'

/**
 * US3 AI 导诊真实后端 E2E（tasks.md T064）。
 *
 * 需真实后端与 E2E_REAL_BACKEND=1；匿名可访问，超时或依赖不可用时必须降级为全量科室且 HTTP 200。
 */
const realBackend = process.env.E2E_REAL_BACKEND === '1'

test.skip(!realBackend, '仅在 E2E_REAL_BACKEND=1 时运行真实后端用例')

test('端点 4 匿名导诊返回 200 且状态合法', async ({ page }) => {
  await page.goto('/')

  const result = await realApiFetch(page, 'POST', '/api/v1/triage-sessions', {
    complaint: '持续咳嗽伴低热三天',
  })

  expect(result.status).toBe(200)
  expect(result.body.code).toBe(200)
  const data = result.body.data as {
    status: string
    recommendedDepartments: unknown[]
    fallbackDepartments: unknown[]
  }
  expect(['SUCCESS', 'TIMEOUT_FALLBACK']).toContain(data.status)
  if (data.status === 'TIMEOUT_FALLBACK') {
    expect(data.recommendedDepartments.length).toBe(0)
    expect(data.fallbackDepartments.length).toBeGreaterThan(0)
  } else {
    expect(data.recommendedDepartments.length).toBeGreaterThan(0)
    expect(data.fallbackDepartments.length).toBe(0)
  }
})

test('端点 4 空主诉返回 400', async ({ page }) => {
  await page.goto('/')

  const result = await realApiFetch(page, 'POST', '/api/v1/triage-sessions', {
    complaint: '',
  })

  expect(result.status).toBe(400)
  expect(result.body.code).toBe(400)
})
