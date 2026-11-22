import { expect, test } from '@playwright/test'
import { realApiFetch } from './fixtures/appointment-consultation'

/**
 * US1 挂号支付真实后端 E2E（tasks.md T034）。
 *
 * 需 MySQL / Redis / Spring Boot / Vite 全部启动并设 E2E_REAL_BACKEND=1；
 * 未实名患者无法通过复诊门禁，故此处断言端点 1 的正向与端点 5 的认证门禁（不写库）。
 */
const realBackend = process.env.E2E_REAL_BACKEND === '1'

test.skip(!realBackend, '仅在 E2E_REAL_BACKEND=1 时运行真实后端用例')

test('端点 1 真实返回启用科室列表', async ({ page }) => {
  await page.goto('/')

  const result = await realApiFetch(page, 'GET', '/api/v1/departments')

  expect(result.status).toBe(200)
  expect(result.body.code).toBe(200)
  expect(Array.isArray(result.body.data)).toBe(true)
})

test('端点 5 未认证访问返回 401 且不建单', async ({ page }) => {
  await page.goto('/')

  const result = await realApiFetch(page, 'POST', '/api/v1/appointments', {
    doctorId: 101,
    scheduleId: 201,
  })

  expect(result.status).toBe(401)
  expect(result.body.code).toBe(401)
  expect(result.body.message).toBe('未授权')
})

test('端点 6b 未认证访问返回 401', async ({ page }) => {
  await page.goto('/')

  const result = await realApiFetch(page, 'GET', '/api/v1/appointments/1/payments')

  expect(result.status).toBe(401)
  expect(result.body.code).toBe(401)
})
