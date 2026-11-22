# 测试报告：前端骨架（web-skeleton）

**功能**：web-skeleton（前端骨架 + 服务健康检查示例，无业务模块）
**契约**：`specs/health-check/contracts/health-api.md`
**数据模型**：`specs/health-check/data-model.md`
**创建日期**：2026-11-07
**执行状态**：自动化检查全部通过；真实后端 E2E 与覆盖率阈值待环境就绪

## 验证结果

| 检查 | 命令 | 结果 |
|------|------|------|
| 依赖安装 | `npm install`（跳过 Playwright 浏览器下载） | 成功，318 packages |
| 静态检查零错误零警告 | `npm run lint` | 通过，0 error / 0 warning |
| 格式检查 | `npm run format:check` | 通过 |
| 类型检查 | `npm run typecheck` | 通过 |
| 单元测试 | `npm run test` | 3 个文件 / 9 个用例全部通过 |
| 生产构建 | `npm run build` | 通过，110 modules，dist 产物 163.72 kB JS |
| Mock E2E | `$env:PLAYWRIGHT_CHANNEL='msedge'; npm run test:e2e` | 2 passed，1 skipped |

## 测试用例

| 编号 | 类型 | 用例 | 来源 | 结果 |
|------|------|------|------|------|
| T1 | Vitest/composable | M1 `code=200` → 报告写入、isUp=true、无错误 | contracts M1 | 通过 |
| T2 | Vitest/composable | M2 `code=503` → 保留报告、暴露后端 message | contracts M2 | 通过 |
| T3 | Vitest/composable | 网络异常 → UI 兜底文案且结束 loading | 宪法 III/IV | 通过 |
| T4 | Vitest/组件 | M1 展示 UP 与 3 个组件卡片 | contracts M1 | 通过 |
| T5 | Vitest/组件 | M2 展示 DOWN、unavailable、错误提示与 refresh 事件 | contracts M2 | 通过 |
| T6 | Vitest/组件 | 加载中禁用刷新按钮 | 骨架行为 | 通过 |
| T7 | Vitest/store | 模拟刷新：从 `localStorage` 恢复主题/会话，守卫不误拦截 | 宪法 II | 通过 |
| T8 | Vitest/store | 未知 localStorage 值降级为默认值 | 宪法 II | 通过 |
| T9 | Vitest/store | 切换主题后持久化 | 宪法 II | 通过 |
| E1 | Playwright/page.route | M1 全部 UP 的整体状态与 3 个组件 | contracts M1 | 通过 |
| E2 | Playwright/page.route | M2 降级的 DOWN、unavailable、message | contracts M2 | 通过 |
| E3 | Playwright 真实后端 | 断言响应 `code` 值（不能只看 UI） | 宪法 VI | 跳过（见下） |

## 环境说明

- 本机 `5173` 端口已被另一个项目占用（页面为「AI 智能导诊」）。骨架改用独立端口 `5183`（`VITE_DEV_PORT`）并在 Vite 启用 `strictPort`；Playwright 设置 `reuseExistingServer: false`，避免误测其他应用。
- 本机未预装 Playwright 浏览器，但存在系统 Edge，故以 `PLAYWRIGHT_CHANNEL=msedge` 执行。无系统浏览器时需先 `npx playwright install chromium`。
- E3 需启动 Spring Boot + MySQL + Redis 并设置 `E2E_REAL_BACKEND=1`。当前 `root/123456` 连接 MySQL 返回 `ERROR 1045`、Redis `6379` 未开放，故跳过；健康检查不写入用户数据，「随机手机号」类动态数据风险项不适用。

## 宪法符合性

- 已落地：契约先行（类型/mock 从 `contracts/` 同步并标注来源）、`src/api/` 路径不含 `/api`、Vite 代理仅匹配一次、三角色独立 axios 实例且拦截器无角色分支、前端不解析 JWT、Pinia 从 `localStorage` 初始化、views 不直接调 axios、组件名大驼峰/文件名小写横线、`<script setup>` + `<style scoped>`、`as` 别名避免同名冲突、响应式布局（640px 断点）、路由词干错开、Mock E2E 用 `page.route()`、真实后端 E2E 断言 `code`。
- 待补充：Vitest 覆盖率阈值（≥70%）与 CI 契约一致性离线比对尚未接入；前端 `web/` 建立后业务用户故事的 Playwright 用例需按故事补齐；Checkstyle/SpotBugs 属后端门禁。
- 前端组件均 < 300 行，函数 < 80 行；ESLint + Prettier 已配置并通过零告警检查。
