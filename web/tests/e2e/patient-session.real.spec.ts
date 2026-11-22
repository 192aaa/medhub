import { expect, test, type Page } from '@playwright/test'

const realBackend = process.env.E2E_REAL_BACKEND === '1'
const PASSWORD = 'abc12345'

function randomPhone(): string {
  return `13${Math.floor(Math.random() * 1_000_000_000)
    .toString()
    .padStart(9, '0')}`
}

async function register(page: Page, phone: string): Promise<void> {
  await page.goto('/patient/register')
  await page.fill('#register-phone', phone)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.fill('#verification-code', '888888')
  await page.fill('#register-password', PASSWORD)
  await page.check('#agreement-accepted')
  await page.getByRole('button', { name: '注册并登录' }).click()
  await expect(page).toHaveURL(/\/patient\/profile/)
}

test.describe('真实后端登录与退出（需先启动 MySQL / Redis / Spring Boot / Vite）', () => {
  test.skip(!realBackend, '设置 E2E_REAL_BACKEND=1 后启用')

  test('登录 → 刷新保持会话 → 退出后旧 token 401', async ({ page }) => {
    const phone = randomPhone()
    await register(page, phone)
    const staleToken = await page.evaluate(() => localStorage.getItem('sdas.patient.token'))
    expect(staleToken).toBeTruthy()

    await page.getByTestId('logout-button').click()
    await expect(page).toHaveURL(/\/patient\/login/)

    const staleStatus = await page.evaluate(async (token) => {
      const response = await fetch('/api/v1/patients/current/identity-status', {
        headers: { Authorization: `Bearer ${token}` },
      })
      return response.status
    }, staleToken)
    expect(staleStatus).toBe(401)

    await page.fill('#login-phone', phone)
    await page.fill('#login-password', PASSWORD)
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page).toHaveURL(/\/patient\/profile/)

    await page.reload()
    await expect(page).toHaveURL(/\/patient\/profile/)
    await expect(page.getByTestId('auth-status')).toHaveText('未认证')

    await page.getByTestId('logout-button').click()
    await expect(page).toHaveURL(/\/patient\/login/)
  })
})
