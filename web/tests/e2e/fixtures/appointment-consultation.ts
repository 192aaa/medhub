/**
 * 002 预约挂号 / 图文问诊 / AI 导诊 的 Playwright Mock 统一注入入口。
 *
 * Mock 唯一来源：web/src/api/mocks/appointment-consultation.ts（同步自
 * specs/002-appointment-consultation/contracts/mocks.md M1–M16）。
 * E2E_REAL_BACKEND=1 时关闭拦截，改走真实后端。
 */
import type { Page } from '@playwright/test'
import { appointmentConsultationMockResponses } from '../../../src/api/mocks/appointment-consultation'

/** `METHOD /v1/path` → 场景后缀（对应 mock 表的 `#SCENARIO` 键） */
export type ScenarioMap = Record<string, string>

/** 单个接口的响应体（覆盖默认 mock 时使用） */
export interface MockBody {
  code: number
  message: string
  data: unknown
}

export interface MockInstallOptions {
  scenarios?: ScenarioMap
  responses?: Record<string, MockBody>
  realBackend?: boolean
}

/** E2E 伪造的患者 access token（mock 环境不校验） */
export const PATIENT_TOKEN = 'e2e-patient-token'

/** mock 中的挂号单主键 */
export const APPOINTMENT_ID = 401

/** mock 中的问诊单主键 */
export const CONSULTATION_ID = 701

/** 预约链路依赖的本地状态（store 创建时从 localStorage 恢复） */
export const APPOINTMENT_STATE = {
  appointmentId: APPOINTMENT_ID,
  paymentStatus: 'UNPAID',
  selectedDoctorId: 101,
  selectedScheduleId: 201,
  feeAmount: 30,
}

/**
 * 安装 /api/v1/** 拦截：按 `method + path` 匹配 mock 表，支持场景后缀与整体覆盖。
 *
 * @param page Playwright 页面
 * @param options 场景 / 覆盖 / 真实后端开关
 */
export async function installAppointmentMocks(
  page: Page,
  options: MockInstallOptions = {},
): Promise<void> {
  const realBackend = options.realBackend ?? process.env.E2E_REAL_BACKEND === '1'
  if (realBackend) {
    return
  }

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace(/^\/api/, '')
    const key = `${request.method().toUpperCase()} ${path}`
    const scenario = options.scenarios?.[key]
    const mock =
      options.responses?.[key] ??
      (scenario ? appointmentConsultationMockResponses[`${key}#${scenario}`] : undefined) ??
      appointmentConsultationMockResponses[key]

    if (!mock) {
      await route.abort()
      return
    }

    await route.fulfill({
      status: mock.code,
      contentType: 'application/json; charset=utf-8',
      body: JSON.stringify(mock),
    })
  })
}

/**
 * 注入已登录患者会话（可选同时注入预约链路本地状态）。
 *
 * @param page Playwright 页面
 * @param appointment 预约 store 初始值
 */
export async function signInPatient(
  page: Page,
  appointment?: Record<string, unknown>,
): Promise<void> {
  await page.addInitScript(
    (state: { token: string; profile: unknown; appointment: unknown }) => {
      localStorage.setItem('sdas.patient.token', state.token)
      localStorage.setItem('sdas.patient.profile', JSON.stringify(state.profile))
      if (state.appointment) {
        localStorage.setItem('sdas-appointment', JSON.stringify(state.appointment))
      }
    },
    {
      token: PATIENT_TOKEN,
      profile: {
        patientId: 1,
        authStatus: 'VERIFIED',
        consultationAllowed: true,
        maskedPhone: '138****8000',
        maskedName: '张*',
        maskedIdCard: '110***********002X',
        verifiedAt: '2026-01-01T00:00:00',
      },
      appointment: appointment ?? null,
    },
  )
}

/**
 * 真实后端模式下直接调用接口（自动附带双重 Cookie CSRF）。
 *
 * @param page Playwright 页面
 * @param method HTTP 方法
 * @param path 以 /api 开头的路径
 * @param body JSON 请求体
 * @returns HTTP 状态与响应体
 */
export async function realApiFetch(
  page: Page,
  method: string,
  path: string,
  body?: unknown,
): Promise<{ status: number; body: { code: number; message: string; data: unknown } }> {
  return page.evaluate(
    async (request: { method: string; path: string; body?: unknown }) => {
      const csrf = 'e2e-real-csrf'
      document.cookie = `sdas_csrf=${csrf}; path=/api/v1`
      const response = await fetch(request.path, {
        method: request.method,
        credentials: 'include',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf },
        body: request.body ? JSON.stringify(request.body) : undefined,
      })
      return { status: response.status, body: await response.json() }
    },
    { method, path, body },
  )
}
