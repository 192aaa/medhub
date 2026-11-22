import { expect, test } from '@playwright/test'
import {
  APPOINTMENT_STATE,
  installAppointmentMocks,
  signInPatient,
} from './fixtures/appointment-consultation'

/**
 * US2 图文问诊单 Mock E2E（tasks.md T050）：上传成功、提交成功、主诉越界、图片超限、越权访问。
 */
test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

const COMPLAINT = '咳嗽伴低热三天需要复诊'

test('M9 上传图片成功展示缩略图', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page, APPOINTMENT_STATE)

  await page.goto('/appointment/consultation')
  await page.locator('input[type=file]').setInputFiles({
    name: 'report.png',
    mimeType: 'image/png',
    buffer: Buffer.from('fake-png-bytes'),
  })

  await expect(page.locator('img.thumb')).toHaveCount(1)
  await expect(page.locator('p.error')).toHaveCount(0)
})

test('M10 提交问诊单后进入详情页', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page, APPOINTMENT_STATE)

  await page.goto('/appointment/consultation')
  await page.getByPlaceholder('描述症状').fill(COMPLAINT)
  await page.getByRole('button', { name: '提交问诊单' }).click()

  await expect(page).toHaveURL(/\/appointment\/consultation\/701/)
})

test('M11 详情页展示状态、主诉与 AI 摘要', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page)

  await page.goto('/appointment/consultation/701')

  await expect(page.getByText('状态：PENDING')).toBeVisible()
  await expect(page.getByText('主诉：咳嗽伴低热三天')).toBeVisible()
  await expect(
    page.getByText('AI 摘要：患者主诉咳嗽伴低热三天，建议排查呼吸道感染。'),
  ).toBeVisible()
  await expect(page.locator('img.thumb')).toHaveCount(1)
})

test('主诉不足 10 字时本地校验拦截', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page, APPOINTMENT_STATE)

  await page.goto('/appointment/consultation')
  await page.getByPlaceholder('描述症状').fill('咳嗽三天')
  await page.getByRole('button', { name: '提交问诊单' }).click()

  await expect(page.locator('p.error')).toHaveText('主诉须为 10-200 字')
  await expect(page).toHaveURL(/\/appointment\/consultation$/)
})

test('M13 主诉格式越界时展示后端文案', async ({ page }) => {
  await installAppointmentMocks(page, {
    scenarios: { 'POST /v1/consultations': 'INVALID_COMPLAINT_FORMAT' },
  })
  await signInPatient(page, APPOINTMENT_STATE)

  await page.goto('/appointment/consultation')
  await page.getByPlaceholder('描述症状').fill(COMPLAINT)
  await page.getByRole('button', { name: '提交问诊单' }).click()

  await expect(page.locator('p.error')).toHaveText('主诉长度须为 10-200 个字符')
})

test('M14 图片超过 5MB 时展示后端文案', async ({ page }) => {
  await installAppointmentMocks(page, {
    scenarios: { 'POST /v1/consultations/images': 'INVALID_IMAGE_SIZE' },
  })
  await signInPatient(page, APPOINTMENT_STATE)

  await page.goto('/appointment/consultation')
  await page.locator('input[type=file]').setInputFiles({
    name: 'big.png',
    mimeType: 'image/png',
    buffer: Buffer.alloc(1024),
  })

  await expect(page.locator('p.error')).toHaveText('单张图片不得超过 5 MB')
  await expect(page.locator('img.thumb')).toHaveCount(0)
})

test('M15 越权访问他人问诊单时展示禁止文案', async ({ page }) => {
  await installAppointmentMocks(page, {
    scenarios: { 'GET /v1/consultations/701': 'CONSULTATION_FORBIDDEN' },
  })
  await signInPatient(page)

  await page.goto('/appointment/consultation/701')

  await expect(page.locator('p.error')).toHaveText('禁止访问他人问诊资料')
})
