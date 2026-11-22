# 预约挂号与图文问诊 复杂度与可行性分析

**功能**：`002-appointment-consultation` | **日期**：2026-11-08
**输入**：`spec.md`、`research.md`（R-01–R-10 定稿）、`data-model.md`、`contracts/`、`plan.md`、`tasks.md`、`quickstart.md`
**分析目的**：在 `/speckit.implement` 之前，依据 `.specify/memory/constitution.md` 的 **Constitution Check（G1–G6）** 复核设计，识别复杂度、风险与可行性缺口，给出放行/修订建议。
**上游**：`001-patient-identity` 已实现（认证/实名/门禁/双重 Cookie/审计/`GlobalExceptionHandler`/`PatientIdExtractor`/`PatientAccountRepository` 可复用）。

---

## 1. 分析结论（TL;DR）

| 维度 | 结论 |
|------|------|
| 门禁 G1–G6 | **全部通过**，无违规登记项（见 §3） |
| 技术可行性 | **高**：复用 001 全套技术栈与基础设施，零新增第三方依赖（R-04/R-05 已锁定） |
| 复杂度 | **中**：5 类状态机 + 3 处幂等/事务编排 + AI 不确定性；已在 `plan.md` Complexity Tracking 受控 |
| 关键风险 | 4 项（见 §4）：AI 超时线程占用、本地图片存储不可水平扩展、匿名导诊的 CSRF 获取、Mock 支付生产误开 |
| 待决项 | 1 项（Redis 具体版本，非阻断）；AI 形态与 TS 两项待决已在本功能闭环 |
| 放行建议 | **可进入 `/speckit.implement`**；4 项风险列入实现期监控，不阻塞启动 |

---

## 2. 复杂度评估

### 2.1 维度评分

| 维度 | 等级 | 依据 |
|------|------|------|
| 业务规则 | 中 | 复诊资格门禁（已认证+历史就诊）、支付/预约/问诊单状态机、5 秒 AI 降级 |
| 数据模型 | 中 | 10 张新表，与 001 通过 `patient_account.id`/`department` FK 关联 |
| 外部依赖 | 低 | 仅 MySQL + Redis（复用）+ DeepSeek 官方 API；无 MQ/对象存储/支付 SDK（架构评审零新增） |
| 并发与一致性 | 中 | 支付、预约、问诊单各一处幂等（唯一约束）+ 多表变更同 `@Transactional`（R-07） |
| 安全面 | 中 | 复用 001 门禁 + 复诊资格（REVISIT_NOT_ELIGIBLE）+ 越权防护（CONSULTATION_FORBIDDEN）；图片上传内容嗅探（SC-004） |
| AI 不确定性 | 中 | 统一收敛为「超时/异常 → TIMEOUT_FALLBACK 全量科室列表（HTTP 200）」，不阻断主流程（D2/R-02/SC-006） |
| 测试面 | 中-高 | 9 端点契约 + 集成 + 单测 + Playwright 3 故事；覆盖率 ≥70%（原则 VI） |

**综合**：复杂度受控，无需触发架构升级或模块分拆。若实现期任一门禁或指标越界，须回写 `plan.md` Complexity Tracking 并标注「违规项/必要性/被否决的更简单方案」（宪法 §Governance）。

### 2.2 复杂度热点与对应任务

| 热点 | 影响 | 已规划控制（tasks.md） |
|------|------|------------------------|
| 支付幂等与并发 | 重复扣费/重复记录 | T027/T030/T038：`UNIQUE(appointment_id)` + 事务「先查后写」+ 并发竞争 409 |
| 预约-支付状态双列一致 | 状态发散 | T037/T038：同事务内 `appointment` 双状态列原子更新 |
| 问诊单重复提交 | 多张单 | T047/T053：`UNIQUE(appointment_id)` + `CONSULTATION_SUBMITTED` 审计一次 |
| AI 超时 | 主流程卡顿 | T019/T060：5s `orTimeout` + 降级分支，集成测试注入延迟验证 |
| 图片伪装/超量 | 存储滥用/脏数据 | T018/T023：白名单 + `ImageIO.read` 嗅探 + 大小/数量上限 |

---

## 3. Constitution Check 门禁复核（强制）

> 来源：宪法「Constitution Check 强制门禁」。每次 `/speckit.analyze` 须复核 G1–G6。

| 门禁 | 要求 | 复核证据 | 结论 |
|------|------|----------|------|
| **G1 技术栈** | 仅原则 I 技术；新增依赖有评审 | `plan.md` 架构评审记录：明确不引入支付 SDK/MQ/对象存储/第二 AI 链路/图片校验库；`server/pom.xml` 无需变更；AI 复用 001 `spring-ai-starter-model-openai` | ✅ PASS |
| **G2 分层与目录** | server/+web/ 布局；三层链；角色 axios；XxxIdExtractor；Pinia 初始化；路由词干无冲突 | `plan.md` §5：`controller→service→repository`；复用 `PatientIdExtractor`；`/appointment/*` 词干错开 `/patient/*`、`/system/*`、`/consultation/entry`；store 自 `localStorage` 初始化 | ✅ PASS |
| **G3 契约** | contracts/ + data-model.md 先于实现；结构/枚举/日期/前缀一致 | 9 端点契约与 mocks(M1–M16) 先于代码；`data-model.md` 1.1–1.10 为唯一来源；日期 `yyyy-MM-dd'T'HH:mm:ss`；前端路径 `/v1/...` 不含 `/api` | ✅ PASS |
| **G4 安全** | BCrypt≥10；JWT≤24h+Refresh；模糊失败；ErrorCode 统一；日志脱敏 | 复用 001（BCrypt/JWT/Refresh）；模糊失败沿用 001；新增 16 个 `ErrorCode` 统一文案（data-model 1.9）；审计禁明文手机号/身份证号；响应禁出支付敏感信息 | ✅ PASS |
| **G5 代码** | Checkstyle/SpotBugs/ESLint/Prettier 零告警；无 `@Enumerated`+`@Converter` 并存；代码审查 | `tasks.md` T075/T079 强制静态检查与审查；枚举统一用显式 `@Converter(autoApply=false)`（T009/T010），规避并存冲突 | ✅ PASS（实现期验证） |
| **G6 测试** | 覆盖≥70%；所有 REST 正向/异常集成；Playwright 覆盖全故事；风险项用例；testsdoc；契约一致性 | `tasks.md` T025–T064 测试先行；T071 覆盖率≥70%；T073–T074 Mock+真实后端 E2E；T077 契约一致性(SC-012)；T078 报告归档 | ✅ PASS（实现期验证） |

**结论**：6 门禁全部满足，无违规项需登记。`G5/G6` 的「实现期验证」类结论在 `/speckit.implement` 完成时由静态检查与测试报告最终确认。

---

## 4. 风险登记（Risk Register）

| 编号 | 风险 | 类别 | 可能性 | 影响 | 缓解（已在设计/任务中） |
|------|------|------|--------|------|--------------------------|
| RK-01 | AI 导诊在请求线程同步等待（默认 5s），高并发下占用 servlet 线程池 | 性能/可扩展 | 中 | 中 | MVP 可接受；`tasks.md` T019 用 `CompletableFuture.orTimeout`；**建议**：量产前改为 `@Async`/响应式，分离超时与业务线程（非阻断） |
| RK-02 | 图片存本地文件系统，多实例部署时文件不共享 | 架构/生产就绪 | 高（生产） | 中 | `plan.md` D5/quickstart 明确本地；**建议**：生产前置对象存储（OSS/S3）前不得多实例水平扩展；当前 dev/test 单实例无碍 |
| RK-03 | 匿名 AI 导诊端点受 CSRF 双重 Cookie 保护，匿名用户须先取得 CSRF Cookie 才能 POST | 功能/集成 | 高 | 中 | 需在 001 既有 CSRF 暴露端点确保匿名可获取 Cookie；**建议**：实现期确认存在公开 `/csrf` 或等价机制，前端 `/appointment/triage` 进入即取 Cookie（T067/T070 补充验证） |
| RK-04 | `mock-payment-enabled` 误在 prod 开启，暴露模拟支付通道 | 安全/合规 | 低 | 高 | `data-model.md` §3 + `plan.md` 明确仅 dev/test；`tasks.md` T080 安全审查回归；**建议**：prod 默认 `false`，CI 拦截 prod 配置该项为 true |
| RK-05 | 支付并发竞争返回 409 给后到方，前端若未引导查看首次结果会误报失败 | 体验/一致性 | 中 | 低 | `tasks.md` T038 幂等返回首次成功；前端支付结果页以查询态为准（T043） |
| RK-06 | 全新已实名患者无历史就诊记录 → 003 门禁 403，首诊被拒 | 业务/体验 | 高 | 低 | 符合 spec「复诊」定位（FR-007）；**建议**：入口清晰提示「本功能面向复诊患者」，并提供首诊引导至其他渠道 |
| RK-07 | 知识库仅预置、无管理界面，科室/疾病映射需 DBA 变更 | 运维 | 中 | 低 | D3 已定；`V2` 种子受控；**建议**：建立知识库变更评审流程（不在本功能范围） |
| RK-08 | Redis 具体版本未显式确认（宪法待决「3.2.1x」表述异常） | 合规/配置 | 低 | 低 | 复用 001 配置；**建议**：实现期从 `application.yml`/`pom` 查清实际版本并在 `plan.md` Technical Context 显式登记（见 §5 待决） |

---

## 5. 待决项与宪法闭环

| 宪法待决项 | 状态 | 说明 |
|------------|------|------|
| AI 模型调用形态（官方 API vs 本地部署） | **已闭环** | R-04/D4 锁定 DeepSeek 官方 API（spring-ai + openai 兼容），禁止双链路；与宪法解释一致 |
| 前端 TypeScript | **已闭环** | `web` 已启用 TS，`src/api/types.ts` 从 contracts 同步（原则 III） |
| Redis 具体版本 | **待确认（非阻断）** | 计划复用 001 配置；实现期在 `plan.md` Technical Context 显式登记实际版本号，消解「3.2.1x」歧义 |

---

## 6. 可行性评估

| 能力 | 是否就绪 | 证据 |
|------|----------|------|
| 后端技术栈（Spring Boot/JdbcTemplate/AI） | 是 | 001 已落地，002 仅新增 `appointment` 包，复用 `identity/common` |
| 前端技术栈（Vue3/Pinia/Router/TS） | 是 | 001 已落地 `src/api`/`stores`/`views`/`router` 模式 |
| 测试基础设施 | 是 | 001 的 Testcontainers/Vitest/Playwright 模式可复刻；`AppointmentTestSupport`(T006) 继承 |
| 认证/门禁/审计 | 是 | 复用 `PatientIdExtractor`/`AuditEventService`/`GlobalExceptionHandler` |
| AI 调用 | 是 | 复用 001 `spring.ai.openai` 配置 + DeepSeek Key |
| 部署/CI | 是 | Maven/Checkstyle/SpotBugs/Jacoco + ESLint/Prettier 已配置 |

**结论**：技术可行性高，无能力缺口；唯一外部约束为 DeepSeek API Key（env 注入，已有 001 机制）。

---

## 7. 建议与放行

### 7.1 放行判断
**建议放行进入 `/speckit.implement`**。6 门禁通过，复杂度受控，风险已规划缓解，无阻断性待决项。

### 7.2 实现期必须落实的 5 项
1. **RK-03**：确认匿名用户可获取 CSRF Cookie（或在 `TriageController` 放开 CSRF 仅对该端点——但宪法要求修改类请求须双重 Cookie，故优先「公开端点暴露 Cookie」方案）。
2. **RK-04**：`prod` 的 `mock-payment-enabled` 默认 `false`，CI 拦截误开。
3. **RK-01**：若压测显示线程池吃紧，将导诊 AI 调用移至异步线程（不阻塞主流程，仍守 5s 降级）。
4. **RK-08**：实现期初清 Redis 实际版本并登记 `plan.md` Technical Context。
5. **契约一致性（SC-012）**：实现完一轮即跑 `tasks.md` T077，差异归零方可视为功能完成。

### 7.3 不建议在本功能范围的事（避免范围蔓延）
- 不接入真实支付网关（D1：MVP 模拟）。
- 不实现知识库管理后台（D3：预置只读）。
- 不引入对象存储/消息队列/第二 AI 链路（架构评审零新增）。
- 不改动 001 既有认证逻辑（仅复用与扩展 `AuditEventType`/`ErrorCode`）。

---

## 8. 与 tasks.md 的衔接

- 风险缓解已映射到任务：RK-01→T019/T060；RK-03→T067/T070（补验证）；RK-04→T080；RK-05→T038/T043；RK-06→前端入口文案（T041/T070）；RK-08→实现期初登记。
- 复杂度热点见 §2.2，与 `tasks.md` Phase 2–5 一一对应。
- 门禁实现期验证：G5→T075/T079；G6→T071–T078。

**分析产出物**：本 `analysis.md` 与 `plan.md`/`tasks.md`/`contracts/` 共同构成实施依据；进入 implement 后若设计变更，须回写相应文档并保持「契约唯一来源」原则。
