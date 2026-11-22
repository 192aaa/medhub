/**
 * 006 AI 辅助治疗决策 E2E Mock 统一注入入口（US4）。
 *
 * Mock 唯一来源：web/src/api/mocks/treatment-decision.ts（同步自
 * specs/006-ai-treatment-decision/contracts/mocks.md M1–M9）。
 * E2E_REAL_BACKEND=1 时由 spec 自行跳过。
 */
import type { Page } from '@playwright/test'
import {
  MOCK_ADOPTION,
  MOCK_DECISION_NOT_FOUND,
  MOCK_DECISION_SUCCESS,
} from '../../../src/api/mocks/treatment-decision'

/** E2E 伪造的医生 access token（mock 环境不校验） */
export const DOCTOR_TOKEN = 'e2e-doctor-token'

/** mocks.md 中的问诊单主键 */
export const TREATMENT_CONSULTATION_ID = 501

/** 开方页路由（视图从 query 读取 consultationId） */
export const PRESCRIPTION_PATH = `/workbench/prescriptions/new?consultationId=${TREATMENT_CONSULTATION_ID}`

export interface TreatmentMockOptions {
  /** 端点 2 响应，默认 M4 404 */
  get?: { code: number; message: string; data: unknown }
  /** 端点 1 响应，默认 M1 成功 */
  post?: { code: number; message: string; data: unknown }
  /** 端点 3 响应，默认与 post 相同 */
  regenerate?: { code: number; message: string; data: unknown }
}

/**
 * 写入医生会话（store 创建时从 localStorage 恢复，守卫据此放行）。
 *
 * @param page Playwright 页面
 */
export async function signInDoctor(page: Page): Promise<void> {
  await page.addInitScript((token) => {
    localStorage.setItem('sdas.doctor.token', token)
    localStorage.setItem(
      'sdas.doctor.profile',
      JSON.stringify({ doctorId: 101, nameMask: '张*', title: '主任医师', departmentId: 1 }),
    )
  }, DOCTOR_TOKEN)
}

/**
 * 拦截端点 1–4：按方法 + 路径后缀返回 mock，响应体字段与契约逐字一致。
 *
 * @param page Playwright 页面
 * @param options 各端点覆盖响应
 */
export async function installTreatmentDecisionMocks(
  page: Page,
  options: TreatmentMockOptions = {},
): Promise<void> {
  const getBody = options.get ?? MOCK_DECISION_NOT_FOUND
  const postBody = options.post ?? MOCK_DECISION_SUCCESS
  const regenerateBody = options.regenerate ?? postBody

  await page.route(
    `**/api/v1/workbench/consultations/${TREATMENT_CONSULTATION_ID}/treatment-decision**`,
    async (route) => {
      const request = route.request()
      const path = new URL(request.url()).pathname
      let body: { code: number; message: string; data: unknown } = getBody
      if (path.endsWith('/adoptions')) {
        body = MOCK_ADOPTION
      } else if (path.endsWith('/regenerate')) {
        body = regenerateBody
      } else if (request.method().toUpperCase() === 'POST') {
        body = postBody
      }
      await route.fulfill({
        status: body.code,
        contentType: 'application/json',
        body: JSON.stringify(body),
      })
    },
  )
}
