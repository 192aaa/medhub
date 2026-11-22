# SDAS Constitution

<!--
SYNC IMPACT REPORT
==================
版本变更：未批准模板（占位符） → 1.0.0（首次批准）
原则变更：模板 5 个占位原则替换为 6 条已批准原则（I 技术栈与依赖治理、II 前后端分离与严格分层、
          III 契约驱动的 API 规范、IV 安全与隐私、V 代码规范与变更管控、VI 测试先行与质量门禁）
新增章节：Constitution Check 强制门禁、Development Workflow、Governance、解释与待决项
删除章节：无（模板占位符全部被消费）
需同步的下游文件：
  - .specify/templates/plan-template.md      — Constitution Check 门禁具象化；项目结构固定为 server/ + web/
  - .specify/templates/spec-template.md      — 增加契约/枚举来源标注与风险项→测试用例要求
  - .specify/templates/tasks-template.md     — 原「测试为可选」与原则 VI 冲突，改为强制；路径与审查门禁对齐
  - .specify/templates/checklist-template.md — 增加 Spec 阶段风险项须落地为测试用例
后续待办（不阻断本次批准，须在 /speckit.plan 的 research.md 中闭环）：
  - AI 模型调用形态：原文同时出现「DeepSeek 官方 API」与「本地部署」，需确认最终形态
  - 「Redis 3.2.1x」为原文版本表述，需在 plan.md Technical Context 中明确具体版本
  - 原则 III 要求前端 src/api/ 的 TypeScript 类型从 contracts/ 同步，前端脚手架须启用 TypeScript
-->

## Core Principles

### I. 技术栈与依赖治理 (NON-NEGOTIABLE)

- 后端：Java 17 (LTS) + Spring Boot 3.x + Spring AI。
- 前端：Vue 3.4+ + Vite 5+ + Pinia + Vue Router 4.x。
- 数据库：MySQL 8.0（强一致性事务）。
- 缓存：Redis 3.2.1x（会话、验证码、热点数据）。
- AI 模型：DeepSeek 官方 API，模型 `deepseek-v4-flash`，本地部署（原文表述，见「解释与待决项」）。
- 禁止消息队列；禁止反射/动态代理穿透分层；禁止绕过 Service 层调用 Repository。
- 新增第三方依赖须经架构评审；评审结论与理由记录在功能 `plan.md`。

### II. 前后端分离与严格分层 (NON-NEGOTIABLE)

- 前后端分离：后端 RESTful API，前端 HTTP 调用，禁止后端渲染 HTML。
- 后端严格分层 `Controller → Service → Repository`，禁止跨层调用。
- Controller 只做参数校验 + 调用 Service + 返回响应，不写业务逻辑。
- Service 负责核心业务编排，Repository 负责持久化。
- 服务端目录 `server/`，前端目录 `web/`。
- 前端目录结构固定为 `src/api/`、`src/components/`、`src/views/`、`src/stores/`、`src/composables/`、`src/router/`、`src/assets/`。
- 禁止 `views/` 中直接调 axios，API 请求必须通过 `src/api/` 封装。
- 状态管理用 Pinia，禁止组件内直接管理跨组件状态。
- 响应式布局，支持 PC 和手机。
- 路由禁止前缀词根相同的路由放同一层级，如 `/doctors/:id` 与 `/doctor/*` 冲突，须错开词干。
- 不同角色用独立 axios 实例（`patientRequest`、`doctorRequest`、`pharmacyRequest`），禁止共用拦截器做 if-else。
- JWT 身份提取统一用 `XxxIdExtractor`，禁止控制器自行解析或硬编码回退值。
- Pinia store 创建时从 `localStorage` 读初始状态，防刷新后守卫误拦截。
- 多表状态变更在同一 `@Transactional` 内编排，注释中标明关联表和流转路径。
- 状态枚举值在 `specs/[功能名]/data-model.md` 中统一定义，禁止同名不同值。
- Service 层对关键入参做二次校验（密码/手机号/枚举值），防绕过前端直调。

### III. 契约驱动的 API 规范 (NON-NEGOTIABLE)

- 统一响应 `{ code, message, data }`：code=200 成功，4xx 客户端错误，5xx 服务端错误。
- 分页响应：`{ code, message, data: { list, total, page, pageSize } }`。
- 资源路径使用复数名词：`/users`、`/orders`。
- HTTP 语义：GET 查、POST 创、PUT 全量改、PATCH 部分改、DELETE 删。
- JWT 认证：`Authorization: Bearer <token>`。
- 服务端支持跨域。
- `LocalDateTime` 全局序列化为 ISO 8601（`yyyy-MM-dd'T'HH:mm:ss`），配置 `JavaTimeModule` + 关闭 `WRITE_DATES_AS_TIMESTAMPS`，禁止时间戳数组。
- 禁止 `@Enumerated(STRING)` 与 `@Converter(autoApply=true)` 同时作用于同一字段，二选一；`@Converter` 须处理 null/未知值降级。
- REST API 接口须在 `specs/[功能名]/contracts/` 下提前定义请求/响应结构；枚举取值范围在 `data-model.md` 中，contracts 引用标注。
- 契约是前后端唯一参考：前端不得推测，后端不得未同步契约就改接口，审查时校验实现与文档一致。
- 前端 `src/api/` 的 TypeScript 类型须从 `contracts/` 同步并标注来源。
- **API URL 路径前缀规约**：前端 `src/api/` 模块的请求路径禁止重复包含 `/api` 前缀。baseURL（由 `VITE_API_BASE_URL` 配置，通常为 `/api`）与具体路径拼接规则如下：
  - 具体路径写为 `/v1/resource/...`（不含 `/api`）；
  - 运行时最终 URL = baseURL + 具体路径 = `/api/v1/resource/...`；
  - 禁止出现 `/api/api/v1/resource/...` 形式的路径；
  - Vite 代理的 `proxy` 规则仅匹配 `/api` 一次，不再配置重复前缀。
- 枚举值定义在 `data-model.md` 中，前后端代码引用枚举时须标注 `data-model.md` 路径，禁止各自硬编码。

### IV. 安全与隐私 (NON-NEGOTIABLE)

- 密码 BCrypt 加密，强度 ≥ 10。
- JWT 有效期 ≤ 24 小时，支持 Refresh Token。
- 防 XSS：Vue 自动转义，禁止 `v-html` 渲染不受信内容。
- 防 CSRF：修改数据请求实施 `SameSite=Lax` + 双重 Cookie。
- 审计日志禁止记录密码明文、验证码明文、完整手机号/身份证号；开发阶段临时明文须在进入生产前清理。
- 登录/注册失败返回统一模糊提示（如「账号或密码错误」），禁止返回「还剩 3 次机会」等具体信息，防撞库。
- 业务异常 message 统一在 `ErrorCode` 中定义，禁止控制器/Service 硬编码。

### V. 代码规范与变更管控 (NON-NEGOTIABLE)

- 后端按《阿里巴巴 Java 开发手册》：类大驼峰/方法小驼峰/常量全大写下划线；公共方法须 Javadoc；禁止 `System.out.println`，用 SLF4J + Logback；日志级别 ERROR/WARN/INFO/DEBUG；提交前过 Checkstyle 和 SpotBugs。
- 前端按 Vue 官方风格 + ESLint + Prettier：组件大驼峰/文件小写横线/变量小驼峰；`<script setup>` 语法；`<style scoped>`。
- 代码写完后须代码审查。
- 前端 import 的 API 函数名禁止与当前模块函数同名，冲突时用 `as` 别名。
- 禁止同一字段同时标 `@Enumerated` 与 `@Converter`；新增枚举值须全局搜索 switch/if-else 确保无遗漏。
- 当前事务 `save()` 后如需另启事务读取刚写数据，须调 `saveAndFlush()` 或拆为 `REQUIRES_NEW`。
- 前端功能命名（如「健康科普」→「AI健康资讯」→「AI健康咨询」）须在规约阶段确定，禁止反复改。
- 移除代码前须全局搜索被移除函数的调用方。

### VI. 测试先行与质量门禁 (NON-NEGOTIABLE)

- 单元测试覆盖率 ≥ 70%（后端 JUnit5 + Mockito 测 Service，前端 Vitest 测 composables/公共组件）。
- 集成测试覆盖所有 REST API 正向 + 异常（Spring Boot Test + Testcontainers）。
- Playwright 功能测试覆盖全部用户故事；测试用例先写，通过后方可视为功能完成。
- 测试报告存 `testsdoc/[功能名]/[测试名].md`。
- 静态检查零错误零警告；圈复杂度 ≤ 10，函数 ≤ 80 行；前端组件 ≤ 300 行。
- Mock 数据从 `specs/[功能名]/contracts/` 同步，禁止凭空手写；contracts 变更时同步更新 mock。
- Mock E2E 用 `page.route()` 测 UI/异常；真实后端 E2E **必须断言响应 code 值**（不能只看 UI）。
- 真实后端 E2E 用动态数据（随机手机号），禁止硬编码防污染。
- 测试数据库须与生产同套 Flyway 脚本，禁止 `application-test.yml` 禁用 Flyway 绕过。
- Spec 阶段 checklist 风险项须落地为测试用例：API 超时→超时测试、输入长度→超长输入、多 Tab→并发操作、支付恢复→中断恢复等。
- 每迭代执行一次契约一致性验证：对比 `contracts/` 文档与真实后端响应，校验端点暴露/code 值/字段结构/日期格式，由 CI 或测试人员执行。

## Constitution Check 强制门禁

*GATE：必须在 Phase 0 research 前通过，并在 Phase 1 设计后复核；`/speckit.analyze` 与代码审查同样适用。*

- **G1 技术栈**：实现仅使用原则 I 指定技术；新增依赖有架构评审记录。
- **G2 分层与目录**：`server/` + `web/` 布局、三层调用链、角色 axios 实例、`XxxIdExtractor`、Pinia 初始化、路由词干无冲突。
- **G3 契约**：`specs/[功能名]/contracts/` 与 `data-model.md` 先于实现存在；响应结构、枚举、日期格式、URL 前缀与实现一致。
- **G4 安全**：BCrypt ≥ 10、JWT ≤ 24h + Refresh Token、模糊失败提示、`ErrorCode` 统一异常、日志脱敏。
- **G5 代码**：Checkstyle/SpotBugs、ESLint/Prettier 零告警；无 `@Enumerated` 与 `@Converter` 并存；代码审查已完成。
- **G6 测试**：覆盖率 ≥ 70%、所有 REST API 正向/异常集成测试、Playwright 覆盖全部用户故事、风险项测试用例、`testsdoc/` 报告、契约一致性验证。

任何门禁不满足时，须在 `plan.md` 的 Complexity Tracking 表中登记「违规项 / 必要性 / 被否决的更简单方案」；未登记即视为阻断，功能不得视为完成。

## Development Workflow

1. 规约阶段先定功能命名，并在 `specs/[功能名]/` 下产出 `contracts/` 与 `data-model.md`（枚举唯一来源）。
2. 顺序：`specs/[功能名]/spec.md` → `plan.md`（含 Constitution Check）→ `tasks.md` → 测试用例（先写且先失败）→ 实现 → 代码审查 → 静态检查 → 测试报告 → 契约一致性验证。
3. 接口变更必须先改 `contracts/`，同步 mock 与 `src/api/` 类型，再改后端实现；禁止未同步契约就改接口。
4. 代码审查、静态检查零错误零警告、测试报告落盘 `testsdoc/[功能名]/[测试名].md` 均为完成前置条件。
5. 每迭代由 CI 或测试人员执行一次契约一致性验证，结果与差异记录随测试报告归档。

## Governance

- 本宪法优先于模板、口头约定与其他实践；发生冲突时以本宪法为准，并按修订流程同步下游文件。
- 修订须具备：书面提案、影响范围清单（模板/契约/代码/测试）、架构评审批准、迁移计划、版本号与日期更新。
- 版本语义：**MAJOR** = 原则删除/重定义或不兼容的治理变更；**MINOR** = 新增原则/章节或实质性扩展约束；**PATCH** = 措辞澄清、错字、非语义修正。
- 合规校验：每次 `/speckit.plan`、`/speckit.analyze`、代码审查与 CI 必须复核 Constitution Check 门禁；复杂度豁免须在 `plan.md` Complexity Tracking 中说明理由。
- 运行时开发指引以 `.specify/memory/constitution.md` 为唯一准绳。

### 解释与待决项

- AI 模型条款原文同时含「DeepSeek 官方 API」与「本地部署」；在 `/speckit.plan` 的 `research.md` 明确最终调用形态前，不得同时实现两套调用链路。
- 「Redis 3.2.1x」按原文保留，`plan.md` Technical Context 须写明落地具体版本。
- 原则 III 隐含前端使用 TypeScript；脚手架须启用 TypeScript，若改用纯 JavaScript 须先修订本宪法。

**Version**: 1.0.0 | **Ratified**: 2026-11-07 | **Last Amended**: 2026-11-07
