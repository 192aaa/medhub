import { expect, test, type Page } from '@playwright/test'
import { installPatientIdentityMocks } from './fixtures/patient-identity'

test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

const PHONE = '13800138000'
const PASSWORD = 'abc12345'
const UNIFIED_MESSAGE = '账号或密码错误，或验证信息无效'

async function fillLogin(page: Page): Promise<void> {
  await page.goto('/patient/login')
  await page.fill('#login-phone', PHONE)
  await page.fill('#login-password', PASSWORD)
}

test('M6 登录成功进入个人中心并写入会话', async ({ page }) => {
  await installPatientIdentityMocks(page)
  await fillLogin(page)
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page).toHaveURL(/\/patient\/profile/)
  await expect(page.getByTestId('masked-phone')).toHaveText('138****8000')
  await expect(page.getByTestId('auth-status')).toHaveText('已认证')
  const token = await page.evaluate(() => localStorage.getItem('sdas.patient.token'))
  expect(token).toBe('mock-access-token-verified')
})

test('M7 登录失败展示统一模糊文案', async ({ page }) => {
  await installPatientIdentityMocks(page, {
    scenarios: { 'POST /v1/patient-sessions': 'M7' },
  })
  await fillLogin(page)
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page.getByTestId('login-error')).toHaveText(UNIFIED_MESSAGE)
  await expect(page).toHaveURL(/\/patient\/login/)
})

test('M8 退出后回到登录页并清除本地会话', async ({ page }) => {
  await installPatientIdentityMocks(page)
  await fillLogin(page)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/patient\/profile/)

  await page.getByTestId('logout-button').click()

  await expect(page).toHaveURL(/\/patient\/login/)
  expect(await page.evaluate(() => localStorage.getItem('sdas.patient.token'))).toBeNull()
})

test('M9 续期成功返回新 access token', async ({ page }) => {
  await installPatientIdentityMocks(page)
  await page.goto('/patient/login')

  const result = await page.evaluate(async () => {
    const response = await fetch('/api/v1/patient-sessions/current/refresh-token', {
      method: 'PUT',
      credentials: 'include',
      headers: { 'X-CSRF-Token': 'e2e-csrf' },
    })
    return { status: response.status, body: await response.json() }
  })

  expect(result.status).toBe(200)
  expect(result.body.data.accessToken).toBe('mock-access-token-rotated')
})

test('M10 旧凭证复用返回统一文案', async ({ page }) => {
  await installPatientIdentityMocks(page, {
    scenarios: { 'PUT /v1/patient-sessions/current/refresh-token': 'M10' },
  })
  await page.goto('/patient/login')

  const result = await page.evaluate(async () => {
    const response = await fetch('/api/v1/patient-sessions/current/refresh-token', {
      method: 'PUT',
      credentials: 'include',
      headers: { 'X-CSRF-Token': 'e2e-csrf' },
    })
    return { status: response.status, body: await response.json() }
  })

  expect(result.status).toBe(400)
  expect(result.body.message).toBe('账号或密码错误，或验证信息无效')
})
