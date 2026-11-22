import { expect, test } from '@playwright/test'
import {
  MOCK_DECISION_FALLBACK,
  MOCK_DECISION_SUCCESS,
} from '../../src/api/mocks/treatment-decision'
import {
  PRESCRIPTION_PATH,
  installTreatmentDecisionMocks,
  signInDoctor,
} from './fixtures/treatment-decision'

/**
 * US4 降级 Mock E2E（tasks.md T030）：
 * M3 降级 → 面板提示「AI 辅助暂不可用」+ 重试；表单可填；无原始 JSON、无空白卡片。
 */
test.skip(process.env.E2E_REAL_BACKEND === '1', 'Mock 套件在真实后端模式下跳过')

const PANEL = 'section[aria-label="AI 辅助治疗决策"]'
const DOSAGE_PLACEHOLDER = '用法用量（如：每日三次，每次一片）'

test('M3 超时降级展示明确提示且表单可填', async ({ page }) => {
  await installTreatmentDecisionMocks(page, { post: MOCK_DECISION_FALLBACK })
  await signInDoctor(page)

  await page.goto(PRESCRIPTION_PATH)

  const panel = page.locator(PANEL)
  await expect(panel).toBeVisible()
  await expect(panel.locator('[data-degraded="TIMEOUT"]')).toBeVisible()
  await expect(panel).toContainText('AI 辅助暂不可用')
  await expect(panel.getByRole('button', { name: '重试' })).toBeVisible()
  await expect(panel).not.toContainText('系统异常')

  // 降级不渲染空卡片、不泄漏原始 JSON
  await expect(panel.locator('[data-state="success"]')).toHaveCount(0)
  await expect(panel).not.toContainText('degradedReason')

  // 表单保持可编辑
  const dosage = page.getByPlaceholder(DOSAGE_PLACEHOLDER)
  await dosage.fill('每日三次，每次一片')
  await expect(dosage).toHaveValue('每日三次，每次一片')
})

test('M4 404 后自动生成并展示结构化摘要', async ({ page }) => {
  await installTreatmentDecisionMocks(page, { post: MOCK_DECISION_SUCCESS })
  await signInDoctor(page)

  await page.goto(PRESCRIPTION_PATH)

  const panel = page.locator(PANEL)
  await expect(panel.locator('[data-state="success"]')).toBeVisible()
  await expect(panel).toContainText('咳嗽伴咳痰 3 天')
  await expect(panel).toContainText('疑似上呼吸道感染')
  await expect(panel).toContainText('阿莫西林胶囊')
  await expect(panel).toContainText('AI 生成，仅供参考，最终诊疗决策由医生负责')
  await expect(panel.locator('[data-degraded]')).toHaveCount(0)
})

test('降级后重试走重新生成端点', async ({ page }) => {
  await installTreatmentDecisionMocks(page, {
    post: MOCK_DECISION_FALLBACK,
    regenerate: MOCK_DECISION_SUCCESS,
  })
  await signInDoctor(page)

  await page.goto(PRESCRIPTION_PATH)
  await page.locator(PANEL).getByRole('button', { name: '重试' }).click()

  await expect(page.locator(`${PANEL} [data-state="success"]`)).toBeVisible()
})
