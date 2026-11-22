import { expect, test } from '@playwright/test'
import {
  APPOINTMENT_STATE,
  installAppointmentMocks,
  signInPatient,
} from './fixtures/appointment-consultation'

/**
 * US1 挂号支付 Mock E2E（tasks.md T033）：浏览科室 → 选医生 → 创建预约 → 支付 → 依赖降级。
 */
test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

test('M1 科室列表展示启用科室并可进入医生列表', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page)

  await page.goto('/appointment/departments')

  await expect(page.getByRole('heading', { name: '选择科室' })).toBeVisible()
  await expect(page.locator('.list li')).toHaveCount(2)
  await expect(page.locator('.list li').filter({ hasText: '内科' })).toBeVisible()

  await page.locator('.list li').filter({ hasText: '内科' }).click()
  await expect(page).toHaveURL(/\/appointment\/doctors\?departmentId=1/)
})

test('M2/M3 选择医生后进入确认预约页并展示费用', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page)

  await page.goto('/appointment/doctors?departmentId=1')

  await expect(page.getByRole('heading', { name: '选择医生' })).toBeVisible()
  await expect(page.locator('.list li')).toHaveCount(1)
  await expect(page.locator('.list li').filter({ hasText: '张*' })).toBeVisible()
  await expect(page.locator('.list li').filter({ hasText: '在线' })).toBeVisible()

  await page.locator('.list li').first().click()

  await expect(page).toHaveURL(/\/appointment\/create/)
  await expect(page.getByText('挂号费：¥30')).toBeVisible()
})

test('M6 提交预约写入本地状态并进入支付页', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page, {
    selectedDoctorId: 101,
    selectedScheduleId: 201,
    feeAmount: 30,
  })

  await page.goto('/appointment/create')
  await expect(page.getByText('医生：张*（主任医师）')).toBeVisible()

  await page.getByRole('button', { name: '提交预约' }).click()

  await expect(page).toHaveURL(/\/appointment\/pay/)
  const stored = await page.evaluate(() => localStorage.getItem('sdas-appointment'))
  expect(stored).toContain('"appointmentId":401')
})

test('M7 支付成功后进入问诊单提交页', async ({ page }) => {
  await installAppointmentMocks(page, {
    responses: {
      'GET /v1/appointments/401/payments': {
        code: 200,
        message: '成功',
        data: {
          appointmentId: 401,
          paymentId: null,
          amount: null,
          channel: null,
          status: 'UNPAID',
          paidAt: null,
          paymentStatus: 'UNPAID',
          appointmentStatus: 'PENDING_PAYMENT',
        },
      },
    },
  })
  await signInPatient(page, APPOINTMENT_STATE)

  await page.goto('/appointment/pay')
  await expect(page.getByText('金额：¥30')).toBeVisible()

  await page.getByRole('button', { name: '确认支付' }).click()
  await expect(page.getByRole('button', { name: '确认支付成功' })).toBeVisible()
  await page.getByRole('button', { name: '确认支付成功' }).click()

  await expect(page).toHaveURL(/\/appointment\/consultation$/)
})

test('M8 已支付挂号单展示已支付状态', async ({ page }) => {
  await installAppointmentMocks(page)
  await signInPatient(page, APPOINTMENT_STATE)

  await page.goto('/appointment/pay')

  await expect(page.getByText('已支付，正在进入问诊...')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认支付' })).toHaveCount(0)
})

test('M12 复诊门禁拒绝时展示后端文案', async ({ page }) => {
  await installAppointmentMocks(page, {
    scenarios: { 'POST /v1/appointments': 'REVISIT_NOT_ELIGIBLE' },
  })
  await signInPatient(page, {
    selectedDoctorId: 101,
    selectedScheduleId: 201,
    feeAmount: 30,
  })

  await page.goto('/appointment/create')
  await page.getByRole('button', { name: '提交预约' }).click()

  await expect(page.locator('p.error')).toHaveText('请先完成实名认证或确认复诊资格')
  await expect(page).toHaveURL(/\/appointment\/create/)
})

test('M16 科室依赖降级时展示 503 文案', async ({ page }) => {
  await installAppointmentMocks(page, {
    scenarios: { 'GET /v1/departments': 'SERVICE_UNAVAILABLE' },
  })
  await signInPatient(page)

  await page.goto('/appointment/departments')

  await expect(page.locator('p.error')).toHaveText('服务暂时不可用，请稍后重试')
  await expect(page.locator('.list li')).toHaveCount(0)
})
