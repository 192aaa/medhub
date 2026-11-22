import { defineConfig, devices } from '@playwright/test'
import { loadEnv } from 'vite'

// 无预装 Playwright 浏览器时可用系统浏览器：PLAYWRIGHT_CHANNEL=msedge npm run test:e2e
const channel = process.env.PLAYWRIGHT_CHANNEL as 'chrome' | 'msedge' | undefined
const env = loadEnv('development', process.cwd(), '')
const baseURL = `http://127.0.0.1:${Number(env.VITE_DEV_PORT || 5183)}`

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: channel ?? 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        ...(channel ? { channel } : {}),
      },
    },
  ],
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: baseURL,
    // 禁止复用端口上可能存在的其他项目页面
    reuseExistingServer: false,
    timeout: 60_000,
  },
})
