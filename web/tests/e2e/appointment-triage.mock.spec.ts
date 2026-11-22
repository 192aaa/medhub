import { expect, test } from '@playwright/test'
import { installAppointmentMocks, signInPatient } from './fixtures/appointment-consultation'

/**
 * US3 AI 智能导诊 Mock E2E（tasks.md T063）：推荐成功、超时降级、本地校验与手动选择入口。
 */
test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

test('M4 导诊推荐科室并可跳转医生列表', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page)

  await page.goto('/appointment/triage')
  await page.getByPlaceholder('例如：持续咳嗽伴低热').fill('持续咳嗽伴低热')
  await page.getByRole('button', { name: '开始导诊' }).click()

  await expect(page.getByText('为您推荐以下科室：')).toBeVisible()
  await expect(page.locator('.result li a')).toHaveCount(1)
  await expect(page.locator('.result li a').first()).toHaveText('内科')

  await page.locator('.result li a').first().click()
  await expect(page).toHaveURL(/\/appointment\/doctors\?departmentId=1/)
})

test('M5 超时降级展示全部科室并可手动选择', async ({ page }) => {
  await installAppointmentMocks(page, {
    scenarios: { 'POST /v1/triage-sessions': 'TIMEOUT_FALLBACK' },
  })
  await signInPatient(page)

  await page.goto('/appointment/triage')
  await page.getByPlaceholder('例如：持续咳嗽伴低热').fill('不明原因持续乏力')
  await page.getByRole('button', { name: '开始导诊' }).click()

  await expect(page.getByText('AI 推荐超时，已为您展示全部科室：')).toBeVisible()
  await expect(page.locator('.result li a')).toHaveCount(2)
  await expect(page.locator('.result li a').nth(0)).toHaveText('内科')
  await expect(page.locator('.result li a').nth(1)).toHaveText('外科')
})

test('空主诉时本地校验拦截', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page)

  await page.goto('/appointment/triage')
  await page.getByRole('button', { name: '开始导诊' }).click()

  await expect(page.locator('p.error')).toHaveText('请输入症状描述')
})

test('导诊结果页提供浏览全部科室入口', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page)

  await page.goto('/appointment/triage')
  await page.getByPlaceholder('例如：持续咳嗽伴低热').fill('持续咳嗽伴低热')
  await page.getByRole('button', { name: '开始导诊' }).click()
  await expect(page.getByText('为您推荐以下科室：')).toBeVisible()

  await page.locator('.result .hint a').click()

  await expect(page).toHaveURL(/\/appointment\/doctors$/)
})
