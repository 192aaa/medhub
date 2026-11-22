import { expect, test, type Page } from '@playwright/test'
import { installPatientIdentityMocks } from './fixtures/patient-identity'

test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

const UNIFIED_MESSAGE = '账号或密码错误，或验证信息无效'

async function fillResetForm(page: Page): Promise<void> {
  await page.fill('#reset-phone', '13800138000')
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.fill('#verification-code', '888888')
  await page.fill('#reset-password', 'abc12345')
}

test('M11 重置成功跳转登录页', async ({ page }) => {
  await installPatientIdentityMocks(page)
  await page.goto('/patient/password-reset')
  await fillResetForm(page)

  await page.getByRole('button', { name: '重置密码' }).click()
  await expect(page).toHaveURL(/\/patient\/login/)
})

test('失败展示统一文案且不跳转', async ({ page }) => {
  await installPatientIdentityMocks(page)
  await page.route('**/api/v1/password-resets', (route) =>
    route.fulfill({
      status: 400,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify({ code: 400, message: UNIFIED_MESSAGE, data: null }),
    }),
  )

  await page.goto('/patient/password-reset')
  await fillResetForm(page)
  await page.getByRole('button', { name: '重置密码' }).click()

  await expect(page.getByTestId('form-error')).toHaveText(UNIFIED_MESSAGE)
  await expect(page).toHaveURL(/\/patient\/password-reset/)
})
