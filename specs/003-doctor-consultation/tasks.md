# 任务清单：医生接诊

**功能**：`003-doctor-consultation` | **日期**：2026-11-08
**输入**：`specs/003-doctor-consultation/` 下的 `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/`
**前置条件**：`plan.md`、`spec.md`（含 5 个用户故事）、`research.md`（R-01–R-10 定稿）、`data-model.md`、`contracts/` 全部已产出；上游 `001-patient-identity`（JWT/双重 Cookie/`AuditEventService`/`security_audit_event`）与 `002-appointment-consultation`（图文问诊单、`doctor` 档案、`spring.ai.openai` 配置）已实现。

**测试**：依 `.specify/memory/constitution.md`（原则 VI）为强制项——单元测试覆盖率 ≥ 70%、集成测试覆盖 9 个 REST 端点的正向与异常、Playwright 覆盖全部 5 个用户故事；测试用例先写且先失败，通过后方可视为功能完成。测试报告落 `testsdoc/doctor-consultation/`。

**组织方式**：任务按用户故事分组（US1 登录看待接诊 / US2 接诊回复 / US3 电子处方 / US4 AI 辅助决策 / US5 超时转诊），保证每个故事可独立实现、独立测试、独立交付。

**唯一来源约束**：枚举与 `ErrorCode` 以 `data-model.md` 为准；请求/响应字段与 `code` 以 `contracts/` 为准；mock 以 `contracts/mocks.md`（M1–M9）为准。任务描述中的类名、字段名不得自行发明。

> **进度说明（2026-11-22）**：下方复选框未随实现同步更新，请勿把“未勾选”等同于“未实现”。代码抽查显示医生接诊、处方、AI 治疗决策相关控制器与服务已落地；当前仍明确为 Demo/占位的只有 MOCK 支付、前端“线上问诊建设中”页面与演示药品目录。完整状态以实际代码和 `testsdoc/` 为准，复选框待人工逐项复核。

## 格式说明

- `[ID]`：任务编号，`T001` 起连续编号，实现阶段按序勾选。
- `[P]`：可并行（不同文件、无未完成依赖）。
- `[US1]`–`[US5]`：归属的用户故事；跨故事地基任务标 `[FOUND]`。
- 每条任务写明精确文件路径与验收要点；禁止出现「实现某功能」这类无路径的模糊任务。

## 路径约定

- 后端：`server/src/main/java/com/sdas/server/doctor/`（`controller/`、`service/`、`service/impl/`、`repository/`、`entity/`、`model/`、`model/converter/`、`dto/`、`support/`）
- 后端复用：`server/src/main/java/com/sdas/server/{common,identity,appointment,config,exception}/`
- 后端测试：`server/src/test/java/com/sdas/server/doctor/`（`contract/`、`integration/`、`unit/`、`support/`）
- 迁移脚本：`server/src/main/resources/db/migration/V5__doctor_consultation_schema.sql`；种子：`server/src/main/resources/db/seed/V6__doctor_consultation_seed.sql`（仅 dev 加载）
- 前端：`web/src/`（`api/`、`api/mocks/`、`api/request/`、`views/`、`stores/`、`router/`）
- 前端测试：`web/tests/unit/`、`web/tests/e2e/`
- 契约与设计：`specs/003-doctor-consultation/`
- 测试报告：`testsdoc/doctor-consultation/`

---

## Phase 1: Setup（共享基础设施）

**目的**：目录、配置绑定类、前端类型与 Mock 骨架、测试基础设施就绪；此阶段不写业务逻辑。

- [ ] T001 创建 `server/src/main/java/com/sdas/server/doctor/` 下 `controller/`、`service/`、`service/impl/`、`repository/`、`entity/`、`model/`、`model/converter/`、`dto/`、`support/` 包（各包先放 `package-info.java`），并创建 `server/src/test/java/com/sdas/server/doctor/{contract,integration,unit,support}/`、`testsdoc/doctor-consultation/` 目录
- [ ] T002 [P] 扩展 `web/src/api/types.ts`：按 `contracts/doctor-consultation-api.md` 定义 `PrescriptionStatus`、`MessageType`、`ConsultationTimeoutStatus`、`DoctorAccountStatus` 枚举常量与 9 端点请求/响应接口，文件头注释标注来源 `specs/003-doctor-consultation/data-model.md` 与 `contracts/`
- [ ] T003 [P] 扩展 `web/src/api/error-codes.ts`：在 `API_CODE` 中新增 `data-model.md` 1.6 的 11 个新增常量与数值（`DOCTOR_ROLE_REQUIRED` 至 `PRESCRIPTION_EXPIRED`）；**不复制 `message`**，业务文案一律使用后端响应值；文件头追加来源注释
- [ ] T004 [P] 新建 `web/src/api/mocks/doctor-consultation.ts`：从 `contracts/mocks.md` 同步 M1–M9，按 `method + path` 组织导出，`message` 逐字复制，禁止出现完整手机号/身份证号/签名值明文
- [ ] T005 [P] 在 `server/src/main/resources/application.yml` 写入 `data-model.md` §3 全部配置项（`sdas.doctor.*` / `sdas.ai.decision.*`），并新建 `server/src/main/java/com/sdas/server/config/DoctorProperties.java`（`@ConfigurationProperties(prefix = "sdas.doctor")`）、`DecisionProperties.java`（`prefix = "sdas.ai.decision"`）；`dev`/`test` 给可直接运行占位值，生产由环境变量注入（`SDAS_DOCTOR_KEYSTORE`）
- [ ] T006 [P] 新建 `server/src/test/java/com/sdas/server/doctor/support/DoctorTestSupport.java`：测试基类（`@SpringBootTest` + `MockMvc`），支持 `IDENTITY_TEST_USE_LOCAL_INFRA=true` 复用本机 MySQL/Redis（沿用 002 已落地模式），Flyway 自动迁移；`@DynamicPropertySource` 注入测试密钥与 `DEEPSEEK_API_KEY` 测试值；复用 `IdentityFixture` 生成随机医生/患者/问诊单夹具
- [ ] T007 [P] 新建 `web/tests/e2e/fixtures/doctor-consultation.ts`：Playwright `page.route('**/api/v1/workbench/**')` 统一注入入口，按 `method + path` 匹配 `mocks/doctor-consultation.ts`，支持 `E2E_REAL_BACKEND=1` 关闭拦截

**检查点**：`mvn -B -DskipTests compile` 与 `npm run typecheck` 均可通过；依赖与目录齐备，尚未有业务行为。

---

## Phase 2: Foundational（阻塞性前置，必须全部完成）

**目的**：`ErrorCode` 扩展、枚举与 `Converter`、`DoctorIdExtractor`、签名/二维码/水印底座、表结构与 Repository、AI 辅助决策底座等所有用户故事共用的地基。

**⚠️ 关键**：本阶段未完成前，任何用户故事都不允许开始实现。

- [ ] T008 扩展 `server/src/main/java/com/sdas/server/common/ErrorCode.java`：新增 `data-model.md` 1.6 的 11 个常量（`DOCTOR_ROLE_REQUIRED`/`DOCTOR_ACCOUNT_NOT_FOUND`/`INVALID_REPLY_FORMAT`/`INVALID_CONSULTATION_STATUS`/`CONSULTATION_ACCEPT_CONFLICT`/`DRUG_NOT_FOUND`/`DRUG_NOT_IN_CATALOG`/`INVALID_PRESCRIPTION_FORMAT`/`PRESCRIPTION_ALREADY_EXISTS`/`PRESCRIPTION_SIGN_FAILED`/`PRESCRIPTION_EXPIRED`），`message` 逐字取自 `data-model.md` 1.6，Javadoc 标注来源
- [ ] T009 [P] 在 `server/src/main/java/com/sdas/server/doctor/model/` 新建 `data-model.md` 1.2–1.5 的 **4 个枚举**：`PrescriptionStatus`、`MessageType`、`ConsultationTimeoutStatus`、`DoctorAccountStatus`
- [ ] T010 [P] 在 `server/src/main/java/com/sdas/server/doctor/model/converter/` 为 T009 的 4 个枚举各建显式 `AttributeConverter`（`autoApply = false`），处理 `null` 与未知值降级；禁止 `@Enumerated(EnumType.STRING)`
- [ ] T011 [P] 扩展 `server/src/main/java/com/sdas/server/identity/model/AuditEventType.java`：追加 `DOCTOR_LOGIN`、`CONSULTATION_ACCEPTED`、`CONSULTATION_REPLIED`、`PRESCRIPTION_CREATED`、`PRESCRIPTION_SIGN_FAILED`、`CONSULTATION_TIMEOUT`、`AI_DECISION_REQUESTED`
- [ ] T012 [P] 新建 `server/src/main/resources/db/migration/V5__doctor_consultation_schema.sql`：`doctor_account`、`consultation_message`、`drug_catalog`、`prescription`、`prescription_item`、`ai_decision_session` 六表 + `ALTER TABLE consultation` 增加 `accepted_at`/`accepted_by_doctor_id`/`timeout_status`/`timeout_handled_at`/`transferred_to_doctor_id`，含 `data-model.md` §2 全部唯一/普通索引，InnoDB / utf8mb4_general_ci；`FK` 指向 `doctor(id)`/`consultation(id)`/`patient_account(id)`/`drug_catalog(id)`
- [ ] T013 [P] 新建 `server/src/main/resources/db/seed/V6__doctor_consultation_seed.sql`：仅合成演示数据（虚构医生账号 BCrypt 哈希、药品目录、演示接诊单），文件头标注「禁止用于生产」；仅 `application-dev.yml` 的 `spring.flyway.locations` 追加 `classpath:db/seed`
- [ ] T014 [P] 在 `server/src/main/java/com/sdas/server/doctor/entity/` 新建 6 个实体：`DoctorAccount`、`ConsultationMessage`、`DrugCatalog`、`Prescription`、`PrescriptionItem`、`AiDecisionSession`，字段与 `data-model.md` §2 一一对应；`consultation` 扩展字段在 002 实体上追加
- [ ] T015 [P] 新建 `server/src/main/java/com/sdas/server/doctor/repository/` 下 6 个 Repository（JdbcTemplate）：`DoctorAccountRepository`、`ConsultationMessageRepository`、`DrugCatalogRepository`、`PrescriptionRepository`、`PrescriptionItemRepository`、`AiDecisionSessionRepository`，含按 ID/唯一键查询、插入（返回自增主键）、状态更新（带 `version` 条件）；并扩展 002 `ConsultationRepository` 增加 `accept()`（`accepted_at` CAS 更新）与 `markTimeout()` 方法
- [ ] T016 [P] 新建 `server/src/main/java/com/sdas/server/common/DoctorIdExtractor.java`：镜像 `PatientIdExtractor`，解析 JWT + Redis 吊销校验返回 `DoctorTokenClaims`，非医生角色抛 `DOCTOR_ROLE_REQUIRED`
- [ ] T017 [P] 新建 `server/src/main/java/com/sdas/server/doctor/support/PrescriptionSigningService.java`：JDK `java.security.Signature` 的 `SHA256withRSA`，对处方规范化摘要签名；密钥从加密密钥库加载（`DoctorProperties.signature.*`）；失败抛 `PRESCRIPTION_SIGN_FAILED`
- [ ] T018 [P] 新建 `server/src/main/java/com/sdas/server/doctor/support/QrCodeService.java` + `WatermarkService.java`：`QrCodeService` 用 zxing 生成验真链接二维码；`WatermarkService` 用 `java.awt` 生成「院名 + 处方单号 + 生成时间」水印
- [ ] T019 [P] 新建 `server/src/main/java/com/sdas/server/doctor/service/AiDecisionClient.java` + `impl/AiDecisionClientImpl.java`：复用 002 `spring.ai.openai` 配置调用 DeepSeek/SophNet，`DecisionProperties.timeout-millis` 超时降级返回空建议（`status=TIMEOUT_FALLBACK`），输出标注「辅助参考」
- [ ] T020 [P] 扩展 `server/src/main/java/com/sdas/server/exception/GlobalExceptionHandler.java`：新增 003 业务异常 → `ErrorCode` 映射（含 `CONSULTATION_ACCEPT_CONFLICT`/`PRESCRIPTION_ALREADY_EXISTS` 409、`DOCTOR_ROLE_REQUIRED`/`CONSULTATION_FORBIDDEN` 403），响应统一 `{code, message, data:null}`，禁止泄漏堆栈
- [ ] T021 [P] 先写 `server/src/test/java/com/sdas/server/doctor/unit/DoctorAccountConverterTest.java`（null/未知值降级、往返一致），再实现 T010
- [ ] T022 [P] 先写 `server/src/test/java/com/sdas/server/doctor/unit/PrescriptionSigningServiceTest.java`（签名可验、密钥缺失/篡改失败），再实现 T017
- [ ] T023 [P] 先写 `server/src/test/java/com/sdas/server/doctor/unit/DoctorIdExtractorTest.java`（合法医生 token 解析、患者 token 拒绝、吊销拒绝），再实现 T016
- [ ] T024 [P] 新建 `server/src/test/java/com/sdas/server/doctor/integration/FoundationalIT.java`：断言 6 表存在、`consultation` 扩展列存在、`V5` 可重复迁移、`ErrorCode` 11 项文案与 `data-model.md` 1.6 逐字一致、`DoctorProperties`/`DecisionProperties` 绑定成功

**检查点**：`mvn -B test -Dtest='FoundationalIT,DoctorAccountConverterTest,PrescriptionSigningServiceTest,DoctorIdExtractorTest'` 通过；此时尚无任何端点，但地基可用。

---

## Phase 3: 用户故事 1 - 医生登录并查看待接诊列表（优先级：P1）🎯 MVP

**目标**：医生用管理员分配的账号登录工作台，看到待接诊列表（患者姓名、主诉、提交时间），点击可查看问诊单详情与图片，未接诊前不得回复或开药。
**独立测试**：用预置医生账号与已存在的「待接诊」问诊单，完成「登录 → 查看待接诊列表 → 查看详情」即视为交付；不依赖 US2–US5。

**对应需求**：FR-001、FR-002、FR-003、FR-016、FR-020；端点 1/2/3；mock M1–M3。

### 测试先行（必须先失败）⚠️

- [ ] T025 [P] [US1] 新建 `server/src/test/java/com/sdas/server/doctor/contract/WorkbenchSessionsContractTest.java`：端点 1 正向（`accessToken`/`doctorId`/`nameMask`/`title`/`departmentId`/`expiresIn`）与异常（`AUTHENTICATION_FAILED`/`CSRF_TOKEN_MISMATCH`），断言 `code` 与字段
- [ ] T026 [P] [US1] 新建 `server/src/test/java/com/sdas/server/doctor/contract/WorkbenchConsultationsContractTest.java`：端点 2（`status=PENDING`/`IN_PROGRESS` 筛选、空列表返回空数组）、端点 3（详情含 `images`/`summary`/`acceptedAt`、`CONSULTATION_NOT_FOUND`/`CONSULTATION_FORBIDDEN`/`DOCTOR_ROLE_REQUIRED`），断言字段逐字一致
- [ ] T027 [P] [US1] 新建 `server/src/test/java/com/sdas/server/doctor/integration/WorkbenchFlowIT.java`：登录成功后待接诊列表正确、非医生 token 访问 403（`DOCTOR_ROLE_REQUIRED`）、越权访问他人问诊单 403（`CONSULTATION_FORBIDDEN`）、登录失败不泄漏账号存在性（SC-003）
- [ ] T028 [P] [US1] 新建 `server/src/test/java/com/sdas/server/doctor/unit/DoctorAuthServiceTest.java`：BCrypt 校验、禁用账号拒绝、JWT 含「医生」角色
- [ ] T029 [P] [US1] 新建 `web/tests/unit/doctor-store.spec.ts`：store 创建时从 `localStorage` 恢复 `doctorId`/`accessToken`
- [ ] T030 [P] [US1] 新建 `web/tests/unit/workbench-login.spec.ts`：登录表单、失败文案来自后端 `message`、成功后跳转待接诊列表
- [ ] T031 [P] [US1] 新建 `web/tests/e2e/workbench.mock.spec.ts`：注入 M1–M3，覆盖登录成功、登录失败、待接诊列表、详情查看、越权 403
- [ ] T032 [P] [US1] 新建 `web/tests/e2e/workbench.real.spec.ts`：`E2E_REAL_BACKEND=1` 时真实后端，随机医生，断言响应 `code` 与页面状态

### 实现

- [ ] T033 [US1] 新建 `server/src/main/java/com/sdas/server/doctor/dto/`：`LoginRequest`、`LoginResponse`、`ConsultationListItem`、`ConsultationDetailResponse`：字段与 `contracts/` 端点 1/2/3 一一对应，Bean Validation 首轮校验，禁止出现内部字段与明文手机号/身份证号
- [ ] T034 [US1] 新建 `service/DoctorAuthService.java` + `impl/DoctorAuthServiceImpl.java`：校验 `doctor_account`（BCrypt + `status=ACTIVE`），签发含「医生」角色的 JWT，失败统一 `AUTHENTICATION_FAILED`，写审计 `DOCTOR_LOGIN`
- [ ] T035 [US1] 新建 `service/WorkbenchService.java` + `impl/WorkbenchServiceImpl.java`：待接诊/已接诊列表（按 `status` 过滤，患者姓名脱敏）、问诊单详情（含图片、摘要），`doctor_id` 归属校验（`CONSULTATION_FORBIDDEN`）
- [ ] T036 [US1] 新建 `controller/WorkbenchController.java`：`POST /api/v1/workbench/sessions`、`GET /api/v1/workbench/consultations`、`GET /api/v1/workbench/consultations/{consultationId}`，只做校验 + 调 Service，身份取自 `DoctorIdExtractor`
- [ ] T037 [US1] 新建 `web/src/api/request/doctor-request.ts`（独立医生 axios 实例，注入 doctor JWT，`withCredentials: true`）并扩展 `web/src/api/doctor-consultation.ts` 封装端点 1/2/3，`POST` 附带 CSRF 头
- [ ] T038 [P] [US1] 新建 `web/src/views/workbench-login-view.vue`、`workbench-consultations-view.vue`：登录、待接诊/已接诊列表，`<style scoped>`、≤ 400 行、无 `v-html`
- [ ] T039 [P] [US1] 新建 `web/src/stores/doctor.ts`：`doctorId`、`accessToken`、`currentConsultation`，创建时从 `localStorage` 恢复
- [ ] T040 [US1] 在 `web/src/router/index.ts` 注册 `/workbench/login`、`/workbench/consultations`（词干与 `/appointment/*`、`/consultation/entry`、`/patient/*` 错开），`meta.requiresDoctor: true`

**检查点**：`mvn -B test` 中 US1 用例全绿；`npm run test` + `npm run test:e2e -- workbench` 通过；手测登录 → 看待接诊列表 → 查看详情。

---

## Phase 4: 用户故事 2 - 接诊并与患者文字回复（优先级：P1）

**目标**：医生点击「接诊」将问诊单状态从「待接诊」变「接诊中」，进入对话界面发送文字回复（单条 ≤1000 字）；并发接诊仅一人成功。
**独立测试**：用待接诊问诊单，完成「接诊 → 状态变接诊中 → 发送回复」，验证 1000 字上限与并发接诊即视为交付；不依赖 US1/US3/US4/US5（夹具可预置问诊单）。

**对应需求**：FR-004、FR-005、FR-015、FR-016、FR-019；端点 4/5/6；mock M4–M6。

### 测试先行（必须先失败）⚠️

- [ ] T041 [P] [US2] 新建 `server/src/test/java/com/sdas/server/doctor/contract/AcceptContractTest.java`：端点 4 正向（`status=IN_PROGRESS`/`acceptedAt`/`doctorId`）与异常（`CONSULTATION_ACCEPT_CONFLICT`/`INVALID_CONSULTATION_STATUS`/`CONSULTATION_FORBIDDEN`/`CONSULTATION_NOT_FOUND`）
- [ ] T042 [P] [US2] 新建 `server/src/test/java/com/sdas/server/doctor/contract/MessageContractTest.java`：端点 5（对话列表）、端点 6 正向（`messageId`/`senderType=DOCTOR`/`content`/`createdAt`）与异常（`INVALID_REPLY_FORMAT` 空/超 1000 字/控制字符、`INVALID_CONSULTATION_STATUS` 未接诊即回复）
- [ ] T043 [P] [US2] 新建 `server/src/test/java/com/sdas/server/doctor/integration/AcceptReplyFlowIT.java`：接诊状态 CAS、并发接诊仅一人成功（`CONSULTATION_ACCEPT_CONFLICT`）、回复 999/1000/1001 字边界、未接诊不可回复、越权回复 403（SC-004/SC-005）
- [ ] T044 [P] [US2] 新建 `server/src/test/java/com/sdas/server/doctor/unit/WorkbenchServiceTest.java`：接诊幂等（`accepted_at IS NULL` CAS）、回复字数二次校验、消息落库
- [ ] T045 [P] [US2] 新建 `web/tests/unit/workbench-chat.spec.ts`：对话列表渲染、发送回复（≤1000 字）、超长禁用与后端文案展示
- [ ] T046 [P] [US2] 新建 `web/tests/e2e/accept-reply.mock.spec.ts`：注入 M4–M6，覆盖接诊成功、并发接诊 409、回复成功、超长 400
- [ ] T047 [P] [US2] 新建 `web/tests/e2e/accept-reply.real.spec.ts`：真实后端接诊 → 回复 → 状态查询

### 实现

- [ ] T048 [US2] 新建 `server/src/main/java/com/sdas/server/doctor/dto/`：`AcceptResponse`、`MessageRequest`、`MessageResponse`、`MessageItem`：字段与 `contracts/` 端点 4/5/6 一致
- [ ] T049 [US2] 在 `service/WorkbenchService.java` + `impl` 增加 `accept()`（`@Transactional`：`accepted_at` CAS + 状态 `PENDING→IN_PROGRESS` + 审计 `CONSULTATION_ACCEPTED`）与 `reply()`（字数校验 + 写 `consultation_message` + 审计 `CONSULTATION_REPLIED`）
- [ ] T050 [US2] 在 `controller/WorkbenchController.java` 增加 `POST /api/v1/workbench/consultations/{consultationId}/accept`、`GET/POST /api/v1/workbench/consultations/{consultationId}/messages`
- [ ] T051 [US2] 扩展 `web/src/api/doctor-consultation.ts` 封装端点 4/5/6，`POST` 附带 CSRF 头
- [ ] T052 [P] [US2] 新建 `web/src/views/workbench-chat-view.vue`：接诊按钮、对话列表、输入框（1000 字上限）、发送，`<style scoped>`、≤ 400 行
- [ ] T053 [US2] 在 `web/src/router/index.ts` 注册 `/workbench/consultations/:id`（接诊对话，`meta.requiresDoctor: true`）

**检查点**：US1 与 US2 用例全绿；接诊 → 回复全链路通过（SC-004/SC-005）。

---

## Phase 5: 用户故事 3 - 开具电子处方（优先级：P1）

**目标**：医生在已接诊问诊单中从药品目录选药、填规格/用法用量/注意事项，预览后私钥签名，生成带水印与二维码的电子处方；一张问诊单仅一张处方，默认有效期 3 天。
**独立测试**：用已接诊问诊单与预置药品目录，完成「选药 → 填字段 → 签名 → 生成处方」，验证一张单一张处方与签名失败不落库即视为交付。

**对应需求**：FR-007、FR-008、FR-009、FR-010、FR-011、FR-012、FR-013、FR-015、FR-016、FR-019；端点 7/8；mock M7–M8。

### 测试先行（必须先失败）⚠️

- [ ] T054 [P] [US3] 新建 `server/src/test/java/com/sdas/server/doctor/contract/PrescriptionContractTest.java`：端点 7 正向（`prescriptionId`/`status=SIGNED`/`items`/`signedAt`/`validUntil`/`qrCode`/`watermark`）与异常（`DRUG_NOT_IN_CATALOG`/`DRUG_NOT_FOUND`/`INVALID_PRESCRIPTION_FORMAT`/`PRESCRIPTION_ALREADY_EXISTS`/`PRESCRIPTION_SIGN_FAILED`/`INVALID_CONSULTATION_STATUS`）、端点 8（详情 + `expired` 判定 + `CONSULTATION_FORBIDDEN`）
- [ ] T055 [P] [US3] 新建 `server/src/test/java/com/sdas/server/doctor/integration/PrescriptionFlowIT.java`：一张单一张处方（`UNIQUE(consultation_id)`）、签名后 `valid_until=signed_at+3d`、签名失败不落库（SC-006/SC-007）、未接诊不可开方、越权开方 403
- [ ] T056 [P] [US3] 新建 `server/src/test/java/com/sdas/server/doctor/unit/PrescriptionServiceTest.java`：药品目录校验、字段二次校验、签名调用、状态流转 `DRAFT→SIGNED`
- [ ] T057 [P] [US3] 新建 `server/src/test/java/com/sdas/server/doctor/unit/QrCodeServiceTest.java` + `WatermarkServiceTest.java`：二维码内容、水印内容格式
- [ ] T058 [P] [US3] 新建 `web/tests/unit/prescription.spec.ts`：选药、填字段、预览、成功展示处方、重复开方提示
- [ ] T059 [P] [US3] 新建 `web/tests/e2e/prescription.mock.spec.ts`：注入 M7–M8，覆盖开方成功、重复开方 409、药品不在目录 400、签名失败 400
- [ ] T060 [P] [US3] 新建 `web/tests/e2e/prescription.real.spec.ts`：真实后端开方 → 查询处方 → 验真链接

### 实现

- [ ] T061 [US3] 新建 `server/src/main/java/com/sdas/server/doctor/dto/`：`PrescriptionCreateRequest`、`PrescriptionItemRequest`、`PrescriptionResponse`、`PrescriptionDetailResponse`：字段与 `contracts/` 端点 7/8 一致，禁止暴露完整签名值
- [ ] T062 [US3] 新建 `service/PrescriptionService.java` + `impl/PrescriptionServiceImpl.java`：`@Transactional`：校验已接诊 → 药品目录校验 → 写 `prescription`（`DRAFT`）+ `prescription_item` → `PrescriptionSigningService` 签名 → `SIGNED` + `qr_code`/`watermark`/`valid_until` + 审计 `PRESCRIPTION_CREATED`；签名失败审计 `PRESCRIPTION_SIGN_FAILED` 并回滚
- [ ] T063 [US3] 新建 `controller/PrescriptionController.java`：`POST /api/v1/workbench/consultations/{consultationId}/prescriptions`、`GET /api/v1/workbench/prescriptions/{prescriptionId}`，归属校验
- [ ] T064 [US3] 扩展 `web/src/api/doctor-consultation.ts` 封装端点 7/8，`POST` 附带 CSRF 头
- [ ] T065 [P] [US3] 新建 `web/src/views/workbench-prescription-view.vue`、`workbench-prescription-detail-view.vue`：选药/填字段/预览/生成、处方详情（二维码/水印/有效期），`<style scoped>`、≤ 400 行
- [ ] T066 [US3] 在 `web/src/router/index.ts` 注册 `/workbench/prescriptions/new`、`/workbench/prescriptions/:id`（`meta.requiresDoctor: true`）

**检查点**：US3 用例全绿；选药 → 签名 → 生成处方 → 查询全链路通过（SC-006/SC-007）。

---

## Phase 6: 用户故事 4 - AI 辅助决策（优先级：P2）

**目标**：医生接诊/开药时基于问诊资料与药品目录获得 AI 辅助参考（疑似疾病/用药配伍禁忌），明确标注辅助参考，AI 不可用时降级不阻塞。
**独立测试**：输入问诊资料，验证返回辅助参考且明确标注、AI 超时后仍可正常接诊开方即视为交付。

**对应需求**：FR-014、FR-017、FR-018；端点 9；mock M9。

### 测试先行（必须先失败）⚠️

- [ ] T067 [P] [US4] 新建 `server/src/test/java/com/sdas/server/doctor/contract/AiDecisionContractTest.java`：端点 9 正向（`decisionId`/`suggestions`/`disclaimer`/`modelUsed`/`status=SUCCESS`）与降级（`status=TIMEOUT_FALLBACK` + `suggestions=[]`）、异常（`INVALID_CONSULTATION_STATUS`/`CONSULTATION_FORBIDDEN`）
- [ ] T068 [P] [US4] 新建 `server/src/test/java/com/sdas/server/doctor/integration/AiDecisionFlowIT.java`：注入 >5s 延迟断言 `TIMEOUT_FALLBACK` + HTTP 200、输出不含内部推理、AI 不可用不阻塞接诊开方（SC-009）
- [ ] T069 [P] [US4] 新建 `server/src/test/java/com/sdas/server/doctor/unit/AiDecisionServiceTest.java`：超时降级、异常降级、`ai_decision_session` 落库
- [ ] T070 [P] [US4] 新建 `web/tests/unit/ai-decision.spec.ts`：辅助参考渲染、disclaimer 展示、降级空态
- [ ] T071 [P] [US4] 新建 `web/tests/e2e/ai-decision.mock.spec.ts`：注入 M9，覆盖成功与超时降级
- [ ] T072 [P] [US4] 新建 `web/tests/e2e/ai-decision.real.spec.ts`：真实后端（或 Mock AI 延迟）验证降级分支

### 实现

- [ ] T073 [US4] 新建 `server/src/main/java/com/sdas/server/doctor/dto/AiDecisionRequest.java`、`AiDecisionResponse.java`：字段与 `contracts/` 端点 9 一致（含固定 `disclaimer`）
- [ ] T074 [US4] 新建 `service/AiDecisionService.java` + `impl/AiDecisionServiceImpl.java`：调 `AiDecisionClient` → 组装 `suggestions` + 固定 `disclaimer`，写 `ai_decision_session`（输入脱敏、输出、`model_used`、`elapsed_ms`），审计 `AI_DECISION_REQUESTED`；超时降级 `suggestions=[]`
- [ ] T075 [US4] 新建 `controller/AiDecisionController.java`：`POST /api/v1/workbench/consultations/{consultationId}/ai-decision`
- [ ] T076 [US4] 扩展 `web/src/api/doctor-consultation.ts` 封装端点 9，`POST` 附带 CSRF 头
- [ ] T077 [P] [US4] 在 `web/src/views/workbench-chat-view.vue` 增加「AI 辅助决策」面板（展示 `suggestions` + `disclaimer`）

**检查点**：US4 用例全绿；AI 超时不阻塞接诊开方（SC-009）。

---

## Phase 7: 用户故事 5 - 接诊超时自动转诊（优先级：P2）

**目标**：医生接诊后超 24 小时未回复/开药，系统自动标记超时并通知管理员，流转可追溯；自动分配医生列后续增强。
**独立测试**：构造超时接诊单，触发超时判定，验证状态迁移与审计可追溯即视为交付。

**对应需求**：FR-006、FR-019；`data-model.md` 1.4 `ConsultationTimeoutStatus`。

### 测试先行（必须先失败）⚠️

- [ ] T078 [P] [US5] 新建 `server/src/test/java/com/sdas/server/doctor/unit/ConsultationTimeoutSchedulerTest.java`：超 24h 未回复/开药触发、恰好 24h 边界、`timeout_status=NONE` 幂等去重、已回复不触发
- [ ] T079 [P] [US5] 新建 `server/src/test/java/com/sdas/server/doctor/integration/TimeoutFlowIT.java`：超时置 `ADMIN_NOTIFIED` + 审计 `CONSULTATION_TIMEOUT`、重复扫描不重复通知、已开方不触发（SC-008）

### 实现

- [ ] T080 [US5] 新建 `server/src/main/java/com/sdas/server/doctor/support/ConsultationTimeoutScheduler.java`：`@Scheduled` 扫描 `accepted_at` 超 `timeout-hours` 的 `IN_PROGRESS` 单，`@Transactional` 置 `timeout_status=ADMIN_NOTIFIED` + `timeout_handled_at` + 审计 `CONSULTATION_TIMEOUT`；`transferred_to_doctor_id` 字段预留

**检查点**：US5 用例全绿；超时兜底流转可追溯（SC-008）。

---

## Phase 8: 收尾与跨故事事项（Polish）

**目的**：跨故事的测试、静态检查、契约一致性与交付物收口。

- [ ] T081 [P] 补齐后端单测覆盖至 ≥ 70%：`server/src/test/java/com/sdas/server/doctor/unit/` 下为 `DoctorAuthService`、`WorkbenchService`、`PrescriptionService`、`AiDecisionService`、`PrescriptionSigningService`、`QrCodeService`、`WatermarkService`、`ConsultationTimeoutScheduler`、各 `Repository` 逐项补分支
- [ ] T082 [P] 补齐前端单测：`web/tests/unit/` 下为 `doctor` store、各视图与 composable 补充分支用例
- [ ] T083 [P] 全量 Playwright Mock E2E：`web/tests/e2e/` 覆盖 5 个用户故事成功/失败路径，`npm run test:e2e` 零失败
- [ ] T084 全量真实后端 E2E：`$env:E2E_REAL_BACKEND='1'; npm run test:e2e`，全部断言响应 `code`，且不使用预置演示种子记录
- [ ] T085 [P] 静态检查零错误零警告：`mvn -B verify`（Checkstyle + SpotBugs）、`npm run lint`、`npm run format:check`、`npm run typecheck`
- [ ] T086 [P] 手工验证（登录 → 待接诊 → 接诊 → 回复 → 开方 → 查询处方 → AI 辅助决策），记录越权访问 0 返回病情资料、未签名处方 0 张、AI 超时降级 200
- [ ] T087 执行 `contracts/README.md` 第 5 节一致性清单：端点、`code` 取值、字段结构、日期格式、敏感字段禁列与真实后端响应逐项比对，结果写入 `testsdoc/doctor-consultation/contract-conformance.md`
- [ ] T088 [P] 测试报告归档：`testsdoc/doctor-consultation/` 下按 `[测试名].md` 归档单测、集成测试、Mock E2E、真实后端 E2E 报告
- [ ] T089 代码审查（宪法原则 V）：检查分层越界、枚举 `@Enumerated` 与 `@Converter` 并存、硬编码文案、事务边界、`System.out.println`、新增依赖是否超出 `plan.md` 架构评审（仅 zxing）
- [ ] T090 安全检查：日志与响应抽样确认不含完整手机号/身份证号/处方完整签名值/AI 输入敏感身份；确认签名密钥经 `aes-key` 加密、`zxing` 已登记架构评审

---

## Dependencies & Execution Order

### 阶段依赖

- **Phase 1 Setup**：无依赖，可立即开始。
- **Phase 2 Foundational**：依赖 Setup，**阻塞全部用户故事**（T008–T024 未完成前不得开始 T025 之后任务）。
- **Phase 3 US1（P1）**：依赖 Foundational。
- **Phase 4 US2（P1）**：依赖 Foundational；复用 US1 的 `WorkbenchService` 与 `DoctorIdExtractor`，但夹具可预置问诊单。
- **Phase 5 US3（P1）**：依赖 Foundational + US2 的「已接诊」状态。
- **Phase 6 US4（P2）**：依赖 Foundational；复用 002 AI 基础设施。
- **Phase 7 US5（P2）**：依赖 Foundational + US2 的接诊状态。
- **Phase 8 Polish**：依赖所需故事全部完成。

### 故事间依赖

| 故事 | 依赖 | 说明 |
|------|------|------|
| US1 登录看待接诊 | Foundational | 端点 1/2/3 |
| US2 接诊回复 | Foundational + US1 的 `WorkbenchService` | 端点 4/5/6 |
| US3 电子处方 | Foundational + US2 的「已接诊」 | 端点 7/8，须 `IN_PROGRESS` |
| US4 AI 辅助决策 | Foundational + 002 AI | 端点 9 |
| US5 超时转诊 | Foundational + US2 的 `accepted_at` | 调度任务 |

### 故事内部顺序

1. 测试任务先行，且必须先运行至失败（宪法原则 VI）。
2. DTO → Service → Controller。
3. 前端 API 封装 → store → 组件 → 页面 → 路由。
4. 单个故事通过检查点后再进入下一优先级。

### 并行机会

- Phase 1 全部 `[P]` 任务可并行（T002–T007）。
- Phase 2 中枚举、转换器、迁移、Repository、签名/二维码、AI 客户端、`DoctorIdExtractor` 均为不同文件，可并行（T009–T020）。
- Foundational 完成后，US1/US2 可先行；US3 依赖 US2 的接诊状态；US4/US5 可独立并行开发。
- 每个故事内标记 `[P]` 的测试任务可同时启动。

---

## 需求覆盖映射

| 需求 | 对应任务 | 需求 | 对应任务 |
|------|----------|------|----------|
| FR-001 | T025、T033–T036、T038–T040 | FR-011 | T017、T054–T056、T062、T065 |
| FR-002 | T026、T035、T038 | FR-012 | T055–T056、T062 |
| FR-003 | T026、T035 | FR-013 | T055、T062、T065 |
| FR-004 | T041、T049–T050、T052 | FR-014 | T019、T067–T074、T077 |
| FR-005 | T042、T049–T050、T052 | FR-015 | T008、T042、T049、T056、T062 |
| FR-006 | T078–T080 | FR-016 | T016、T023、T035、T049 |
| FR-007 | T054–T056、T062–T063 | FR-017 | T011、T034、T049、T062、T074 |
| FR-008 | T055–T056、T062 | FR-018 | T019、T074 |
| FR-009 | T054、T056、T061–T062 | FR-019 | T049、T062、T080 |
| FR-010 | T017、T022、T054–T056、T062 | FR-020 | T016、T034 |

| 成功标准 | 对应任务 |
|----------|----------|
| SC-001 | T086（手工计时「登录→接诊回复」取中位数写入报告） |
| SC-002 | T055、T060、T086 |
| SC-003 | T016、T027、T035、T086 |
| SC-004 | T042–T044、T049、T086 |
| SC-005 | T041、T043、T049 |
| SC-006 | T055–T056、T062 |
| SC-007 | T017、T054–T055、T062 |
| SC-008 | T078–T080 |
| SC-009 | T067–T068、T074 |
| SC-010 | T090 |
| SC-011 | T043、T055、T068 |
| SC-012 | T087 |

**需求缺口检查**：20 条 FR 与 12 条 SC 均有任务覆盖，无遗漏项。

---

## Notes

- `[P]` = 不同文件且无未完成依赖，可并行。
- `[US*]` 标签用于追溯与增量交付，跨故事地基任务标 `[FOUND]`。
- 每个故事必须先让测试失败，再实现，最后通过检查点才可进入下一优先级。
- Java 存在编译期依赖：测试先行时先创建 Service 接口、DTO 与 Controller 方法签名（方法体抛 `UnsupportedOperationException`），保证测试可编译运行并因断言失败而「红」。
- 禁止修改 `specs/003-doctor-consultation/checklists/requirements.md` 的勾选状态（审查方产物）。
- 禁止在实现阶段引入 `plan.md`「架构评审记录」之外的第三方依赖（本次仅 zxing）。
- 禁止把 `data-model.md`/`contracts/` 的枚举、`code`、`message` 复制到代码后再各自演化：代码只允许引用，变更须先改文档。
- 前端只镜像 `code` 数值，`message` 一律展示后端响应值；`error-codes.ts` 不复制业务文案。
- 医生侧 `doctorRequest` 与患者侧 `patientRequest` 实例相互独立；身份提取统一用 `DoctorIdExtractor`。
- 处方签名密钥经 `aes-key` 加密存储，禁止明文落库；演示种子仅 dev 加载，测试数据由 `DoctorTestSupport` 动态写入。
- 复用 002 的 `ConsultationStatus`、`TriageResultStatus`、`spring.ai.openai` 配置、`PatientIdExtractor`（模式），不复制重复实现。
- 每完成一个逻辑分组提交一次，提交信息引用任务编号。
