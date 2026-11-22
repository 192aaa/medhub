import { expect, test, type Page } from '@playwright/test'

/**
 * T098：真实后端重置密码 E2E（需 MySQL / Redis / Spring Boot / Vite 全部启动）。
 * 固定验证码 888888（application-dev.yml 的 fixed-enabled）。
 */
const realBackend = process.env.E2E_REAL_BACKEND === '1'
const FIXED_CODE = '888888'
const OLD_PASSWORD = 'abc12345'
const NEW_PASSWORD = 'xyz98765'

async function register(page: Page): Promise<string> {
  const phone = `13${Math.floor(Math.random() * 1_000_000_000)
    .toString()
    .padStart(9, '0')}`
  await page.goto('/patient/register')
  await page.fill('#register-phone', phone)
  await page.getByRole('button', { name: '获取验证码' }).click()
  await page.fill('#verification-code', FIXED_CODE)
  await page.fill('#register-password', OLD_PASSWORD)
  await page.check('#agreement-accepted')
  await page.getByRole('button', { name: '注册并登录' }).click()
  await expect(page).toHaveURL(/\/patient\/profile/)
  return phone
}

async function login(page: Page, phone: string, password: string): Promise<void> {
  await page.goto('/patient/login')
  await page.fill('#login-phone', phone)
  await page.fill('#login-password', password)
  await page.getByRole('button', { name: '登录' }).click()
}

test.describe('真实后端重置密码（SC-001）', () => {
  test.skip(!realBackend, '设置 E2E_REAL_BACKEND=1 后启用')
  // 需跨过端点 1 的 60 s 发送间隔窗口
  test.setTimeout(150_000)

  test('重置后新密码可登录、旧密码不可登录', async ({ page }) => {
    const phone = await register(page)

    // 发送间隔限流按手机号计且不区分用途：注册刚签发过 REGISTER 验证码，
    // 必须等 60 s 窗口过期，才能为同一手机号签发 RESET_PASSWORD 验证码
    await page.waitForTimeout(61_000)

    await page.goto('/patient/password-reset')
    await page.fill('#reset-phone', phone)
    await page.getByRole('button', { name: '获取验证码' }).click()
    await expect(page.getByTestId('code-error')).toHaveCount(0)
    await page.fill('#verification-code', FIXED_CODE)
    await page.fill('#reset-password', NEW_PASSWORD)
    await page.getByRole('button', { name: '重置密码' }).click()

    await expect(page).toHaveURL(/\/patient\/login/)

    await login(page, phone, NEW_PASSWORD)
    await expect(page).toHaveURL(/\/patient\/profile/)

    await login(page, phone, OLD_PASSWORD)
    await expect(page).toHaveURL(/\/patient\/login/)
  })
})
