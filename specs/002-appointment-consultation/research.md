# 预约挂号与图文问诊 研究报告（Phase 0）

**功能**：`002-appointment-consultation`
**关联规约**：`spec.md`（Q1–Q4 已于 2026-11-08 `/speckit.clarify` 确认定稿）
**目标**：在编码前关闭所有架构与契约层面的未知项，输出可执行结论供 `plan.md` 与 `contracts/` 引用。

> 宪法原则 I：新增第三方依赖须经架构评审。本报告对每个潜在新增依赖给出「采用 / 不采用」结论。

---

## R-01 挂号费支付：MVP 模拟支付，不接入真实网关（用户决策，定稿 Q2）

**问题**：`dev`/`test` 与生产是否接入真实支付渠道？

**决策**：MVP 阶段使用**模拟支付闭环**，不接入任何真实第三方支付网关（微信/支付宝等）。
- `PaymentChannel` 仅含 `MOCK`；`payment_record.channel` 恒为 `MOCK`。
- 模拟支付由 `MockPaymentService` 在应用内完成：成功即写 `payment_record(status=SUCCESS)` + 同事务更新 `appointment.payment_status=PAID` + 审计 `APPOINTMENT_PAID`。
- 金额由服务端从 `doctor.consultation_fee` 快照到 `appointment.fee_amount`；客户端传入 `amount` 仅作一致性校验，不一致返回 `INVALID_PAYMENT_AMOUNT`，**绝不**以客户端金额作扣费依据（FR-024）。
- **生产待确认**：若后续接入真实网关，须按原则 I 补充架构评审记录、新增依赖与回调幂等设计；当前契约以状态一致性、幂等、可恢复性为唯一可测约束。

**结论**：不新增支付依赖；不新增消息队列；退款/对账/补偿属范围外。

---

## R-02 AI 智能导诊：超时降级，默认 5 秒（用户决策）

**问题**：AI 推荐不可用时如何保障主流程不阻塞？

**决策**：设计超时降级机制，**默认 `timeout-millis: 5000`（5 秒）**。
- 调用链：`TriageService.recommend()` 使用带超时的 `CompletableFuture`/`CallsionTemplate` 包装 Spring AI 调用；超时或异常捕获后返回 `TriageResultStatus.TIMEOUT_FALLBACK`。
- 降级响应（HTTP 200）携带 `fallbackDepartments` = **全量科室列表**，前端据此展示手动选择路径；不返回错误码、不阻断挂号（满足 `SC-006` ≥ 99% 成功率）。
- 全量科室列表复用端点 1 的数据访问层，避免导诊会话耦合。
- 每次调用记录 `ai_triage_session`（输入、输出、`model_used`、`elapsed_ms`、`status`），用于审计与超时统计（FR-023），但**不向患者暴露内部推理细节**。

**结论**：超时阈值配置化（默认 5000ms），降级为正常 200 分支，不新增错误码。

---

## R-03 科室-疾病知识库：数据库预置，不开放管理（用户决策）

**问题**：知识库如何提供、是否需管理界面？

**决策**：知识库存于 `department_disease_kb` 表，**数据库预置**（`source='PRESET'`），由 Flyway 种子脚本装载演示数据；**本功能不提供任何管理/写界面**（FR-022）。
- AI 导诊只读此表做症状→科室匹配（关键词命中 + 模型语义增强），命中结果可追溯 `kb_version`。
- 运维如需更新知识库，走数据库变更，不在应用内实现 CRUD（明确范围外）。

**结论**：不新增管理模块；数据来源单一（`DATABASE_PRESET`），无外部同步依赖。

---

## R-04 AI 模型调用形态：沿用 001 结论，禁止双链路（定稿 Q3）

**问题**：官方 API 还是本地部署？

**决策**：沿用 `001-patient-identity` `research.md` R-04 结论——**Spring AI + DeepSeek 官方 API**（已在 `application.yml` 的 `spring.ai.openai` 配置）。本功能通过 `sdas.ai.triage.model` 复用同一 chat client，**禁止同时实现两套调用链路**（原则 I / 宪法「解释与待决项」）。
- 不新增 AI 依赖；不新增本地模型服务。
- 若未来切换本地部署，须走架构评审并删除另一链路，保持单一形态。

**结论**：零新增 AI 依赖。

---

## R-05 新增第三方依赖评审（宪法原则 I）

| 候选依赖 | 是否引入 | 结论与依据 |
|----------|----------|------------|
| 微信/支付宝支付 SDK | 否 | R-01：MVP 模拟支付，不接入真实网关 |
| 消息队列（Kafka/RabbitMQ） | 否 | 规约已声明不引入 MQ，沿用既有缓存策略 |
| 对象存储 SDK（OSS/S3） | 否 | R-07：图片存本地文件系统 |
| 本地 AI 模型服务 | 否 | R-04：沿用官方 API，禁止双链路 |
| 新增 AI starter | 否 | `spring-ai-starter-model-openai` 已存在 |
| 文件上传组件 | 否 | Spring MVC `MultipartFile` 内置，无新依赖 |
| 图片内容校验库（如 `javax.imageio` / Apache Tika） | 可选 | 用 `javax.imageio` + 内容类型嗅探，无需新依赖；若需更强校验可经评审引入 Tika（非必须） |

**结论**：本次**不新增任何第三方依赖**，符合原则 I；`pom.xml` 无需变更。

---

## R-06 图片上传：本地存储 + 格式/内容校验（定稿 Q4 的部分）

**问题**：图片存何处、允许哪些格式、如何防伪装？

**决策**：
- **存储位置**：本地文件系统（`storage.images.location`，默认 `./uploads/consultation`），通过 Spring `ResourceHandler` 以 `serve-path: /static/consultation-images` 对外只读服务。
- **格式白名单**：`image/jpeg`、`image/png`、`image/webp`（与 `allowed-content-types` 一致）。
- **校验顺序**（服务端二次校验，原则 II）：
  1. 内容类型白名单（`MultipartFile.getContentType()` 仅初筛）；
  2. **内容嗅探**：用 `javax.imageio.ImageIO.read()` 解析实际像素，确认确为图片且格式匹配，拒绝「改后缀伪装」（防可执行内容）；
  3. 单张大小 ≤ `max-size-bytes`（5 MB）；
  4. 单问诊单图片数 ≤ `max-count`（9 张）。
- 越界返回 `INVALID_IMAGE_FORMAT` / `INVALID_IMAGE_SIZE` / `TOO_MANY_IMAGES`（SC-004 拦截率 100%）。

**结论**：无存储 SDK 依赖；本地磁盘 + 内容嗅探。

---

## R-07 支付与问诊单的状态一致性与幂等（SC-005 / SC-008）

**问题**：支付中断、重复点击、并发、刷新如何保证无重复扣费与无重复单？

**决策**：
- **支付幂等**：`payment_record.appointment_id` `UNIQUE`；支付在事务内「先查后写」——已存在 `SUCCESS` 则直接返回首次结果（200）；并发竞争通过数据库唯一约束 + 事务隔离，仅一个成功，另一个 `PAYMENT_CONFLICT`(409)。
- **创建挂号单幂等**：`appointment UNIQUE(patient_id, schedule_id)`，重复预约返回既有单或 `INVALID_SCHEDULE`。
- **提交问诊单幂等**：`consultation UNIQUE(appointment_id)`，同一挂号单仅一张问诊单；并发提交仅一个成功（SC-008）。
- **事务编排**：挂号/支付/问诊单/审计事件的多表变更在 `@Transactional` 内完成，注明关联表与流转路径，避免半成品（SC-011）。
- **待支付过期**：定时任务/懒清理将超 `payment-timeout-seconds` 未支付的 `PENDING_PAYMENT` 单置 `CLOSED` + `appointment_status=CANCELLED`。

---

## R-08 门禁与鉴权（SC-003 / SC-010）

**问题**：如何复用 001 认证并叠加复诊资格门禁？

**决策**：
- 复用 001 的 `PatientIdExtractor` + JWT `Authorization: Bearer`；受保护端点（创建预约、支付、上传图片、提交问诊单、查询问诊单）统一前置 `UNAUTHORIZED`(401) 校验。
- **复诊门禁（FR-007 / Q1）**：创建预约前校验「已认证（`patient_account.auth_status=VERIFIED`）**且** `patient_history_visit` 存在该 `patient_id` 记录」；任一不满足返回 `REVISIT_NOT_ELIGIBLE`(403)，并写 `REVISIT_GATE_DENIED` 审计。
- **越权防护（SC-010）**：查询/操作问诊单与图片时强制 `patient_id` 归属校验，非本人返回 `CONSULTATION_FORBIDDEN`(403)。
- 修改类请求沿用 001 双重 Cookie + `X-CSRF-Token`（`CSRF_TOKEN_MISMATCH` 403）。

---

## R-09 测试与契约一致性策略（SC-012 / 原则 VI）

**问题**：如何保证集成/E2E 与契约一致、可验证？

**决策**（与 001 对齐）：
- **契约先行**：`contracts/appointment-consultation-api.md` 定义 9 端点请求/响应；`web/src/api/types.ts` 与 `web/src/api/mocks/` 必须标注该路径并同步。
- **测试先写**：单元（Service 状态机/幂等/校验）+ 集成（Testcontainers MySQL + 内存 Redis）+ Playwright（3 个用户故事，`page.route()` 用 `mocks.md`）。
- **契约一致性验证**：每迭代执行，比对端点、`code` 值、字段结构、`yyyy-MM-dd'T'HH:mm:ss` 日期格式，差异项归零。
- **动态数据**：真实后端 E2E 用随机患者 + 预置动态医生/科室夹具，禁止硬编码真实身份信息（原则 VI）。
- **降级测试**：AI 超时（注入 5s+ 延迟）断言返回 `TIMEOUT_FALLBACK` + 全量科室列表、HTTP 200。
- **门禁测试**：未认证 / 无复诊资格 / 越权直调服务端，断言拒绝次数 0 放行（SC-003/SC-010）。

---

## R-10 路由词干与前端结构（原则 II/III，避免与 001 冲突）

**问题**：新增页面/路由/请求实例如何不冲突？

**决策**：
- 路由词干：复用 001 的 `/consultation/entry` 作为问诊入口；本功能新增 `/appointment/departments`、`/appointment/doctors`、`/appointment/triage`、`/appointment/create`、`/appointment/pay`、`/appointment/consultation` 等，与既有 `/patient/*`、`/system/*`、`/consultation/entry` **词干明确错开**（原则 II：禁止前缀词根近似的复数/单数变体）。
- 请求实例：复用 001 的 `patientRequest`（`Authorization` 注入由拦截器统一）；医生侧若后续需要独立 `doctorRequest`（本功能暂不实现医生端）。
- 状态管理：跨组件状态用 Pinia；store 从 `localStorage` 初始化（沿用 001 约定）。
- `views/` 禁止直接调 axios，统一经 `src/api/*` 封装。

---

## 研究结论汇总

| 编号 | 议题 | 结论 | 状态 |
|------|------|------|------|
| R-01 | 支付渠道 | MVP 模拟支付，不接真实网关 | 用户决策定稿 |
| R-02 | AI 超时降级 | 默认 5 秒，超时返回全量科室列表（200） | 用户决策定稿 |
| R-03 | 知识库 | 数据库预置，不开放管理 | 用户决策定稿 |
| R-04 | AI 形态 | 沿用 001 DeepSeek 官方 API，禁双链路 | 定稿 Q3 |
| R-05 | 新增依赖 | 零新增第三方依赖 | 架构评审通过 |
| R-06 | 图片存储/校验 | 本地文件系统 + 格式白名单 + 内容嗅探 | 定稿 Q4（部分） |
| R-07 | 状态一致/幂等 | 唯一约束 + 事务编排 + 过期清理 | 设计定稿 |
| R-08 | 门禁/鉴权 | 复用 001 + 复诊资格 + 越权防护 | 设计定稿 |
| R-09 | 测试/契约 | 契约先行 + 测试先写 + 一致性验证 | 策略定稿 |
| R-10 | 路由/前端 | 词干错开 + 复用 patientRequest + Pinia | 设计定稿 |

**无 `NEEDS CLARIFICATION` 残留**：Q1–Q4 与全部技术未知项均已定稿，可进入 `plan.md` 与编码。
