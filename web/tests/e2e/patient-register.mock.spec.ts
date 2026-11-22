import { expect, test, type Page } from '@playwright/test'
import { installPatientIdentityMocks } from './fixtures/patient-identity'

test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

const PHONE = '13800138000'

async function openRegister(page: Page): Promise<void> {
  await page.goto('/patient/register')
  await page.fill('#register-phone', PHONE)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.fill('#verification-code', '888888')
  await page.fill('#register-password', 'abc12345')
  await page.check('#agreement-accepted')
}

test('M1/M3 注册成功并进入个人中心', async ({ page }) => {
  await installPatientIdentityMocks(page)
  await openRegister(page)
  await page.getByRole('button', { name: '注册并登录' }).click()
  await expect(page).toHaveURL(/\/patient\/profile/)
  await expect(page.getByTestId('masked-phone')).toHaveText('138****8000')
  await expect(page.getByTestId('auth-status')).toHaveText('未认证')
})

test('M2 发送超限展示后端文案', async ({ page }) => {
  await installPatientIdentityMocks(page, {
    scenarios: { 'POST /v1/verification-codes': 'M2' },
  })
  await page.goto('/patient/register')
  await page.fill('#register-phone', PHONE)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await expect(page.getByTestId('code-error')).toHaveText('操作过于频繁，请稍后重试')
})

test('M4 验证码错误展示统一文案', async ({ page }) => {
  await installPatientIdentityMocks(page, {
    scenarios: { 'POST /v1/patients': 'M4' },
  })
  await openRegister(page)
  await page.getByRole('button', { name: '注册并登录' }).click()
  await expect(page.getByTestId('form-error')).toHaveText('账号或密码错误，或验证信息无效')
})

test('M5 密码不合规时禁止提交', async ({ page }) => {
  await installPatientIdentityMocks(page, {
    scenarios: { 'POST /v1/patients': 'M5' },
  })
  await page.goto('/patient/register')
  await page.fill('#register-phone', PHONE)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.fill('#verification-code', '888888')
  await page.fill('#register-password', 'abcdefgh')
  await page.check('#agreement-accepted')
  await expect(page.getByRole('button', { name: '注册并登录' })).toBeDisabled()
})
