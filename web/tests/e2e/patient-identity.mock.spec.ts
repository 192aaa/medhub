import { expect, test, type Page } from '@playwright/test'
import { installPatientIdentityMocks } from './fixtures/patient-identity'

test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

const PHONE = '13800138000'
const NAME = '张三'
const ID_CARD = '11010519491231002X'
const UNIFIED_MESSAGE = '账号或密码错误，或验证信息无效'

async function registerAndOpenVerification(page: Page): Promise<void> {
  await page.goto('/patient/register')
  await page.fill('#register-phone', PHONE)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.fill('#verification-code', '888888')
  await page.fill('#register-password', 'abc12345')
  await page.check('#agreement-accepted')
  await page.getByRole('button', { name: '注册并登录' }).click()
  await expect(page).toHaveURL(/\/patient\/profile/)

  await page.getByTestId('verify-entry').click()
  await expect(page).toHaveURL(/\/patient\/identity-verification/)
  // 等待端点 8（M14）完成后再提交，避免迟到的状态响应覆盖认证成功结果
  await expect(page.locator('main[data-status-loaded="true"]')).toBeVisible()
}

async function submitVerification(page: Page): Promise<void> {
  await page.fill('#verify-real-name', NAME)
  await page.fill('#verify-id-card', ID_CARD)
  await page.getByRole('button', { name: '提交认证' }).click()
}

test('M12 认证成功后同步状态并放行占位门禁', async ({ page }) => {
  await installPatientIdentityMocks(page)
  await registerAndOpenVerification(page)
  await submitVerification(page)

  await expect(page).toHaveURL(/\/patient\/profile/)
  await expect(page.getByTestId('auth-status')).toHaveText('已认证')
  await expect(page.getByTestId('consultation-allowed')).toHaveText('已解锁线上问诊')
  await expect(page.getByTestId('masked-name')).toHaveText('张*')
  await expect(page.getByTestId('masked-id-card')).toHaveText('110***********002X')

  await page.goto('/consultation/entry')
  await expect(page).toHaveURL(/\/consultation\/entry/)
  await expect(page.getByTestId('consultation-placeholder')).toHaveText('线上问诊建设中')
})

test('M13 认证未通过仅展示与登录失败一致的统一文案', async ({ page }) => {
  await installPatientIdentityMocks(page, {
    scenarios: { 'POST /v1/identity-verifications': 'M13' },
  })
  await registerAndOpenVerification(page)
  await submitVerification(page)

  await expect(page.getByTestId('verification-error')).toHaveText(UNIFIED_MESSAGE)
  await expect(page).toHaveURL(/\/patient\/identity-verification/)
  await expect(page.getByTestId('auth-status')).toHaveText('未认证')
})

test('M14 未认证状态卡与门禁重定向', async ({ page }) => {
  await installPatientIdentityMocks(page)
  await registerAndOpenVerification(page)

  await expect(page.getByTestId('identity-status-card')).toBeVisible()
  await expect(page.getByTestId('auth-status')).toHaveText('未认证')
  await expect(page.getByTestId('consultation-allowed')).toHaveText('线上问诊未解锁')
  await expect(page.getByTestId('masked-name')).toHaveCount(0)

  await page.goto('/consultation/entry')
  await expect(page).toHaveURL(/\/patient\/identity-verification/)
  await expect(page.getByTestId('consultation-placeholder')).toHaveCount(0)
})

test('M15 服务不可用展示后端文案', async ({ page }) => {
  await installPatientIdentityMocks(page, {
    scenarios: { 'POST /v1/identity-verifications': 'M15' },
  })
  await registerAndOpenVerification(page)
  await submitVerification(page)

  await expect(page.getByTestId('verification-error')).toHaveText('服务暂时不可用，请稍后重试')
})
