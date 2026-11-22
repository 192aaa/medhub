import { expect, test } from '@playwright/test'

/**
 * US4 真实后端降级 E2E（tasks.md T031）。
 *
 * 运行前提：
 * 1. 启动后端并压超时：`SDAS_AI_TREATMENT_TIMEOUT_MILLIS=1`（或等价配置）；
 * 2. `E2E_REAL_BACKEND=1 npx playwright test tests/e2e/treatment-decision.real.spec.ts`；
 * 3. 测试库存在医生账号 doctor_zhang/password 与 IN_PROGRESS 问诊单 501。
 */
test.skip(process.env.E2E_REAL_BACKEND !== '1', '真实后端套件需 E2E_REAL_BACKEND=1')

const PANEL = 'section[aria-label="AI 辅助治疗决策"]'

test('真实后端超时仍返回 200 降级结构', async ({ page }) => {
  await page.goto('/workbench/login')
  await page.locator('input[autocomplete=username]').fill('doctor_zhang')
  await page.locator('input[autocomplete=current-password]').fill('password')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/workbench\/consultations/)

  const postDecision = page.waitForResponse(
    (response) =>
      response.url().includes('/treatment-decision') &&
      response.request().method() === 'POST' &&
      !response.url().includes('/regenerate'),
  )
  await page.goto('/workbench/prescriptions/new?consultationId=501')
  const response = await postDecision

  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body.code).toBe(200)
  expect(body.data.status).toBe('TIMEOUT_FALLBACK')
  expect(body.data.degradedReason).toBe('TIMEOUT')

  await expect(page.locator(`${PANEL} [data-degraded="TIMEOUT"]`)).toBeVisible()
  await expect(page.locator(PANEL)).toContainText('AI 辅助暂不可用')
})
