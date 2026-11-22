import { expect, test } from '@playwright/test'
import { signInPatient } from './fixtures/appointment-consultation'
import { installHealthMocks } from './fixtures/health-consultation'

/** US1 Mock E2E：流式问答、免责声明来自服务端事件、失败可重试。 */
test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

test('M7 流式回答逐块渲染并展示免责声明', async ({ page }) => {
  await installHealthMocks(page)
  await signInPatient(page)

  await page.goto('/health-consultation')
  await page.getByLabel('咨询内容').fill('我这两天咳嗽有痰，之前开的阿莫西林还能吃吗')
  await page.getByRole('button', { name: '发送' }).click()

  await expect(page.getByText('从你描述的咳嗽有痰看，')).toBeVisible()
  await expect(page.getByText('建议复诊或咨询开方医生。', { exact: false })).toBeVisible()
  await expect(page.getByText('仅供参考，不构成医疗诊断')).toBeVisible()
})

test('M9 模型失败展示错误提示与重试入口', async ({ page }) => {
  await installHealthMocks(page, { scenario: 'ERROR' })
  await signInPatient(page)

  await page.goto('/health-consultation')
  await page.getByLabel('咨询内容').fill('咳嗽三天了')
  await page.getByRole('button', { name: '发送' }).click()

  await expect(page.getByText('智能助手暂时不可用，请稍后重试')).toBeVisible()
  await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
  // 已生成内容保留（FR-019）
  await expect(page.getByText('从你描述的症状看，')).toBeVisible()
})

test('M10 并发冲突以 toast/错误行展示', async ({ page }) => {
  await installHealthMocks(page, { scenario: 'BUSY' })
  await signInPatient(page)

  await page.goto('/health-consultation')
  await page.getByLabel('咨询内容').fill('还在吗')
  await page.getByRole('button', { name: '发送' }).click()

  await expect(page.getByText('当前会话正在生成回答，请稍候')).toBeVisible()
})
