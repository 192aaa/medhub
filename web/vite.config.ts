/// <reference types="vitest" />
import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiPrefix = env.VITE_API_BASE_URL || '/api'
  // 独立端口避免与本机其他 Vite 项目冲突；strictPort 防止静默切换端口
  const devPort = Number(env.VITE_DEV_PORT || 5183)

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      host: '0.0.0.0',
      port: devPort,
      strictPort: true,
      proxy: {
        // 宪法 III：proxy 只匹配 /api 一次，具体业务路径不得再带 /api 前缀
        [apiPrefix]: {
          target: env.VITE_API_TARGET || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      include: ['tests/unit/**/*.spec.ts'],
      globals: true,
      restoreMocks: true,
      coverage: {
        provider: 'v8',
        reporter: ['text', 'json-summary', 'html'],
        include: ['src/**/*.{ts,vue}'],
        // mock 场景表是 Playwright E2E 的测试替身数据，非生产逻辑，不计入单测覆盖率
        exclude: ['src/main.ts', 'src/**/*.d.ts', 'src/api/mocks/**'],
        thresholds: {
          lines: 70,
          statements: 70,
          branches: 70,
          functions: 70,
        },
      },
    },
  }
})
