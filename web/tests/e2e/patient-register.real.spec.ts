import { expect, test } from '@playwright/test'

const realBackend = process.env.E2E_REAL_BACKEND === '1'

test.describe('真实后端注册（需先启动 MySQL / Redis / Spring Boot / Vite）', () => {
  test.skip(!realBackend, '设置 E2E_REAL_BACKEND=1 后启用')

  test('随机手机号注册成功并进入个人中心', async ({ page }) => {
    const phone = `13${Math.floor(Math.random() * 1_000_000_000)
      .toString()
      .padStart(9, '0')}`
    await page.goto('/patient/register')
    await page.fill('#register-phone', phone)
    await page.getByRole('button', { name: '获取验证码' }).click()
    await page.fill('#verification-code', '888888')
    await page.fill('#register-password', 'abc12345')
    await page.check('#agreement-accepted')
    await page.getByRole('button', { name: '注册并登录' }).click()
    await expect(page).toHaveURL(/\/patient\/profile/)
    await expect(page.getByTestId('auth-status')).toHaveText('未认证')
  })
})
