import { expect, test } from '@playwright/test'
import { realApiFetch } from './fixtures/appointment-consultation'

/**
 * US2 图文问诊单真实后端 E2E（tasks.md T051）。
 *
 * 需真实后端与 E2E_REAL_BACKEND=1；未认证/无资格场景断言 401 且不产生问诊单。
 */
const realBackend = process.env.E2E_REAL_BACKEND === '1'

test.skip(!realBackend, '仅在 E2E_REAL_BACKEND=1 时运行真实后端用例')

test('端点 8 未认证提交返回 401', async ({ page }) => {
  await page.goto('/')

  const result = await realApiFetch(page, 'POST', '/api/v1/consultations', {
    appointmentId: 1,
    complaint: '咳嗽三天伴有轻微发热',
  })

  expect(result.status).toBe(401)
  expect(result.body.code).toBe(401)
})

test('端点 9 未认证查询返回 401', async ({ page }) => {
  await page.goto('/')

  const result = await realApiFetch(page, 'GET', '/api/v1/consultations/1')

  expect(result.status).toBe(401)
  expect(result.body.code).toBe(401)
})

test('端点 7 未认证上传返回 401', async ({ page }) => {
  await page.goto('/')

  const result = await page.evaluate(async () => {
    const csrf = 'e2e-real-csrf'
    document.cookie = `sdas_csrf=${csrf}; path=/api/v1`
    const form = new FormData()
    form.append('file', new Blob(['fake'], { type: 'image/png' }), 'a.png')
    const response = await fetch('/api/v1/consultations/images', {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-CSRF-Token': csrf },
      body: form,
    })
    return { status: response.status, body: await response.json() }
  })

  expect(result.status).toBe(401)
  expect(result.body.code).toBe(401)
})
