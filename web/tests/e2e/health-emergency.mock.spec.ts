import { expect, test } from '@playwright/test'
import { signInPatient } from './fixtures/appointment-consultation'
import { installHealthMocks } from './fixtures/health-consultation'

/** US2 Mock E2E：紧急提示条、挂号弹窗预填、关闭后提示条保留。 */
test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

test('M8 紧急症状展示提示条并自动弹出挂号引导', async ({ page }) => {
  await installHealthMocks(page, { scenario: 'EMERGENCY' })
  await signInPatient(page)

  await page.goto('/health-consultation')
  await page.getByLabel('咨询内容').fill('胸口疼得厉害，喘不上气')
  await page.getByRole('button', { name: '发送' }).click()

  const banner = page.getByRole('alert')
  await expect(banner).toBeVisible()
  await expect(banner).toContainText('请立即拨打 120 或前往最近急诊')
  await expect(banner).toContainText('症状可能存在紧急风险')

  const dialog = page.getByRole('dialog', { name: '挂号引导' })
  await expect(dialog).toBeVisible()
  await expect(dialog).toContainText('心血管内科')
})

test('关闭弹窗后提示条保留且可从提示条重开', async ({ page }) => {
  await installHealthMocks(page, { scenario: 'EMERGENCY' })
  await signInPatient(page)

  await page.goto('/health-consultation')
  await page.getByLabel('咨询内容').fill('胸口疼得厉害，喘不上气')
  await page.getByRole('button', { name: '发送' }).click()

  const dialog = page.getByRole('dialog', { name: '挂号引导' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '稍后再说' }).click()
  await expect(dialog).toBeHidden()

  const banner = page.getByRole('alert')
  await expect(banner).toBeVisible()
  await banner.getByRole('button', { name: '去挂号' }).click()
  await expect(dialog).toBeVisible()
})

test('普通症状不触发提示条与弹窗', async ({ page }) => {
  await installHealthMocks(page)
  await signInPatient(page)

  await page.goto('/health-consultation')
  await page.getByLabel('咨询内容').fill('有点困，想早点睡')
  await page.getByRole('button', { name: '发送' }).click()

  await expect(page.getByText('从你描述的咳嗽有痰看，')).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.getByRole('dialog', { name: '挂号引导' })).toHaveCount(0)
})
