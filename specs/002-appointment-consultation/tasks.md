# 任务清单：预约挂号与图文问诊

**功能**：`002-appointment-consultation` | **日期**：2026-11-08
**输入**：`specs/002-appointment-consultation/` 下的 `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/`、`quickstart.md`
**前置条件**：`plan.md`（必需）、`spec.md`（必需，含 3 个用户故事）、`research.md`（R-01–R-10 定稿）、`data-model.md`、`contracts/`、`quickstart.md` 全部已产出；上游 `001-patient-identity` 已实现（提供 JWT、`PatientIdExtractor`、双重 Cookie、`AuditEventService`、`security_audit_event` 表、`GlobalExceptionHandler`）。

**测试**：依 `.specify/memory/constitution.md`（原则 VI）为强制项——单元测试覆盖率 ≥ 70%、集成测试覆盖 9 个 REST 端点的正向与异常、Playwright 覆盖全部 3 个用户故事；测试用例先写且先失败，通过后方可视为功能完成。测试报告落 `testsdoc/appointment-consultation/`。

**组织方式**：任务按用户故事分组（US1 挂号支付 / US2 图文问诊单 / US3 AI 导诊），保证每个故事可独立实现、独立测试、独立交付。

**唯一来源约束**：枚举与 `ErrorCode` 以 `data-model.md` 为准；请求/响应字段与 `code` 以 `contracts/` 为准；mock 以 `contracts/mocks.md`（M1–M16）为准。任务描述中的类名、字段名不得自行发明。

> **进度说明（2026-11-22）**：下方复选框未随实现同步更新，请勿把“未勾选”等同于“未实现”。代码抽查显示 `server/`、`web/` 下对应的控制器、服务、仓储、页面与测试文件已大量落地；当前仍明确为 Demo/占位的只有 MOCK 支付、前端“线上问诊建设中”页面与演示药品目录。完整状态以实际代码和 `testsdoc/` 为准，复选框待人工逐项复核。

## 格式说明

- `[ID]`：任务编号，`T001` 起连续编号，实现阶段按序勾选。
- `[P]`：可并行（不同文件、无未完成依赖）。
- `[US1]`–`[US3]`：归属的用户故事；跨故事地基任务标 `[FOUND]`。
- 每条任务写明精确文件路径与验收要点；禁止出现「实现某功能」这类无路径的模糊任务。

## 路径约定

- 后端：`server/src/main/java/com/sdas/server/appointment/`（`controller/`、`service/`、`service/impl/`、`repository/`、`entity/`、`model/`、`model/converter/`、`dto/`、`support/`）
- 后端复用（001）：`server/src/main/java/com/sdas/server/{common,identity,exception,config}/`
- 后端测试：`server/src/test/java/com/sdas/server/appointment/`（`contract/`、`integration/`、`unit/`、`support/`）
- 迁移脚本：`server/src/main/resources/db/migration/V2__appointment_consultation_schema.sql`；演示种子：`server/src/main/resources/db/seed/V2__appointment_consultation_seed.sql`（仅 dev 加载）
- 前端：`web/src/`（`api/`、`api/mocks/`、`views/`、`stores/`、`composables/`、`router/`）
- 前端测试：`web/tests/unit/`、`web/tests/e2e/`
- 契约与设计：`specs/002-appointment-consultation/`
- 测试报告：`testsdoc/appointment-consultation/`

---

## Phase 1: Setup（共享基础设施）

**目的**：目录、配置、配置绑定类、前端类型与 Mock 骨架、测试基础设施就绪；此阶段不写业务逻辑。

- [ ] T001 创建 `server/src/main/java/com/sdas/server/appointment/` 下 `controller/`、`service/`、`service/impl/`、`repository/`、`entity/`、`model/`、`model/converter/`、`dto/`、`support/` 包（各包先放 `package-info.java`），并创建 `server/src/test/java/com/sdas/server/appointment/{contract,integration,unit,support}/`、`testsdoc/appointment-consultation/` 目录
- [ ] T002 [P] 扩展 `web/src/api/types.ts`：按 `contracts/appointment-consultation-api.md` 定义 `AppointmentStatus`、`PaymentStatus`、`PaymentRecordStatus`、`PaymentChannel`、`ConsultationStatus`、`DoctorOnlineStatus`、`SchedulePeriod`、`TriageResultStatus` 枚举常量与 9 端点请求/响应接口，文件头注释标注来源 `specs/002-appointment-consultation/data-model.md` 与 `contracts/`
- [ ] T003 [P] 扩展 `web/src/api/error-codes.ts`：在 `API_CODE` 中新增 `data-model.md` 1.9 的 16 个新增常量与对应数值（`DEPARTMENT_NOT_FOUND` 至 `PAYMENT_FAILED`）；**不复制 `message`、不新增业务文案常量**，业务文案一律使用后端响应值；同步文件头来源注释，追加 `specs/002-appointment-consultation/data-model.md` 1.9
- [ ] T004 [P] 新建 `web/src/api/mocks/appointment-consultation.ts`：从 `contracts/mocks.md` 同步 M1–M16，按 `method + path` 组织导出，`message` 逐字复制，禁止出现完整手机号/身份证号/支付敏感信息明文
- [ ] T005 [P] 在 `server/src/main/resources/application.yml`、`application-dev.yml`、`application-test.yml`、`application-prod.yml` 写入 `data-model.md` §3 全部配置项（`sdas.appointment.*` / `sdas.ai.triage.*` / `sdas.storage.images.*`），并新建 `server/src/main/java/com/sdas/server/config/AppointmentProperties.java`（`@ConfigurationProperties(prefix = "sdas.appointment")`）、`TriageProperties.java`（`prefix = "sdas.ai.triage"`）、`ImageStorageProperties.java`（`prefix = "sdas.storage.images"`）；`dev`/`test` 下给出可直接运行占位值，生产由环境变量注入（`APP_UPLOAD_DIR`）
- [ ] T006 [P] 新建 `server/src/test/java/com/sdas/server/appointment/support/AppointmentTestSupport.java`：Testcontainers（MySQL 8 + Redis，复用 001 同版本）基类、`@SpringBootTest` + `MockMvc` 骨架、Flyway 自动迁移校验；通过 `@DynamicPropertySource` 注入 001 的 4 个密钥与 `DEEPSEEK_API_KEY` 测试值，保证 CI 无需预置环境变量即可启动；复用 001 的 `IdentityFixture` 生成随机患者、`patient_history_visit` 预置夹具
- [ ] T007 [P] 新建 `web/tests/e2e/fixtures/appointment-consultation.ts`：Playwright `page.route('**/api/v1/**')` 统一注入入口，按 `method + path` 匹配 `mocks/appointment-consultation.ts`，并支持 `E2E_REAL_BACKEND=1` 时关闭拦截

**检查点**：`mvn -B -DskipTests compile` 与 `npm run typecheck` 均可通过；依赖与目录齐备，尚未有业务行为。

---

## Phase 2: Foundational（阻塞性前置，必须全部完成）

**目的**：`ErrorCode` 扩展、枚举与 `Converter`、配置绑定、表结构与 Repository、复诊资格判定、图片存储与 AI 调用底座、审计事件扩展等所有用户故事共用的地基。

**⚠️ 关键**：本阶段未完成前，任何用户故事都不允许开始实现。

- [ ] T008 扩展 `server/src/main/java/com/sdas/server/common/ErrorCode.java`：新增 `data-model.md` 1.9 的 16 个常量（`DEPARTMENT_NOT_FOUND`/`DOCTOR_NOT_FOUND`/`APPOINTMENT_NOT_FOUND`/`CONSULTATION_NOT_FOUND`/`INVALID_COMPLAINT_FORMAT`/`INVALID_DESCRIPTION_FORMAT`/`INVALID_IMAGE_FORMAT`/`INVALID_IMAGE_SIZE`/`TOO_MANY_IMAGES`/`INVALID_PAYMENT_AMOUNT`/`INVALID_APPOINTMENT_STATUS`/`INVALID_SCHEDULE`/`REVISIT_NOT_ELIGIBLE`/`CONSULTATION_FORBIDDEN`/`PAYMENT_CONFLICT`/`PAYMENT_FAILED`），`message` 逐字取自 `data-model.md` 1.9，Javadoc 标注来源
- [ ] T009 [P] 在 `server/src/main/java/com/sdas/server/appointment/model/` 新建 `data-model.md` 1.1–1.8 的 **8 个枚举**：`AppointmentStatus`、`PaymentStatus`、`PaymentRecordStatus`、`PaymentChannel`、`ConsultationStatus`、`DoctorOnlineStatus`、`SchedulePeriod`、`TriageResultStatus`
- [ ] T010 [P] 在 `server/src/main/java/com/sdas/server/appointment/model/converter/` 为 T009 的 8 个枚举各建显式 `AttributeConverter`（`autoApply = false`），处理 `null` 与未知值降级为安全默认值；禁止 `@Enumerated(EnumType.STRING)`
- [ ] T011 [P] 扩展 `server/src/main/java/com/sdas/server/identity/model/AuditEventType.java`：追加 002 新增事件类型 `APPOINTMENT_CREATED`、`APPOINTMENT_PAID`、`PAYMENT_FAILED`、`CONSULTATION_SUBMITTED`、`IMAGE_UPLOADED`、`TRIAGE_RECOMMENDED`、`TRIAGE_TIMEOUT_FALLBACK`、`REVISIT_GATE_DENIED`、`CONSULTATION_GATE_DENIED`（复用 001 的 `AuditEventService`，无需新建）
- [ ] T012 [P] 新建 `server/src/main/resources/db/migration/V2__appointment_consultation_schema.sql`：`department`、`doctor`、`doctor_schedule`、`department_disease_kb`、`patient_history_visit`、`appointment`、`payment_record`、`consultation`、`consultation_image`、`ai_triage_session` 十表 + `data-model.md` §2 全部唯一/普通索引，InnoDB / utf8mb4_general_ci；`FK` 指向 `patient_account(id)` 与 `department(id)`/`doctor(id)`
- [ ] T013 [P] 新建 `server/src/main/resources/db/seed/V2__appointment_consultation_seed.sql`：仅合成演示数据（虚构科室/医生/排班/知识库/历史就诊），文件头标注「禁止用于生产」；该文件放在 `db/seed/`，仅 `application-dev.yml` 的 `spring.flyway.locations` 追加 `classpath:db/seed`，`test`/`prod` 的 locations 只含 `classpath:db/migration`
- [ ] T014 [P] 在 `server/src/main/java/com/sdas/server/appointment/entity/` 新建 10 个实体：`Department`、`Doctor`、`DoctorSchedule`、`DepartmentDiseaseKb`、`PatientHistoryVisit`、`Appointment`、`PaymentRecord`、`Consultation`、`ConsultationImage`、`AiTriageSession`，字段与 `data-model.md` §2 一一对应，均含 `version` 乐观锁
- [ ] T015 [P] 新建 `server/src/main/java/com/sdas/server/appointment/repository/` 下 10 个 Repository（JdbcTemplate，镜像 001 模式）：`DepartmentRepository`、`DoctorRepository`、`DoctorScheduleRepository`、`DepartmentDiseaseKbRepository`、`PatientHistoryVisitRepository`、`AppointmentRepository`、`PaymentRecordRepository`、`ConsultationRepository`、`ConsultationImageRepository`、`AiTriageSessionRepository`，含按 ID/唯一键查询、插入（返回自增主键）、状态更新（带 `version` 条件）
- [ ] T016 [P] 新建 `server/src/main/java/com/sdas/server/appointment/support/RevisitEligibilityService.java`：判定「`patient_account.auth_status=VERIFIED` **且** `patient_history_visit` 存在该 `patient_id`」，不满足返回 `REVISIT_NOT_ELIGIBLE` 并写 `REVISIT_GATE_DENIED` 审计；复用 001 的 `PatientAccountRepository`；门禁失败不泄漏账号存在性或资格细节
- [ ] T017 [P] 新建 `server/src/main/java/com/sdas/server/appointment/support/ScheduleValidator.java`：校验 `doctor_schedule` 存在、未约满（`slots_booked < slots_total`）、在可预约窗口；不满足映射 `INVALID_SCHEDULE`
- [ ] T018 [P] 新建 `server/src/main/java/com/sdas/server/appointment/service/ImageStorageService.java` + `impl/ImageStorageServiceImpl.java`：本地磁盘写入（`ImageStorageProperties.location`）、内容类型白名单 + `javax.imageio.ImageIO.read` 内容嗅探（防伪装）、单张 ≤`max-size-bytes`、单问诊单 ≤`max-count`；越界映射 `INVALID_IMAGE_FORMAT`/`INVALID_IMAGE_SIZE`/`TOO_MANY_IMAGES`；静态资源映射由 `ResourceHandler` 经 `serve-path` 只读暴露
- [ ] T019 [P] 新建 `server/src/main/java/com/sdas/server/appointment/service/TriageClient.java` + `impl/TriageClientImpl.java`：封装 Spring AI `ChatClient`（复用 001 `spring.ai.openai` 配置）调用 DeepSeek，带 `TriageProperties.timeout-millis` 超时（`CompletableFuture` + `orTimeout`/`get` 超时）与异常捕获；超时/异常返回 `TIMEOUT_FALLBACK` 信号，由上层组装全量科室列表
- [ ] T020 [P] 新建 `server/src/main/java/com/sdas/server/appointment/service/SummaryClient.java` + `impl/SummaryClientImpl.java`：封装 AI 调用生成病情摘要（提交问诊单时调用），异常时返回 `null`（不阻断主流程，FR-014/FR-020）
- [ ] T021 [P] 扩展 `server/src/main/java/com/sdas/server/exception/GlobalExceptionHandler.java`：新增 002 业务异常 → `ErrorCode` 映射分支（含 `PAYMENT_CONFLICT` 409、`REVISIT_NOT_ELIGIBLE`/`CONSULTATION_FORBIDDEN` 403），响应统一 `{code, message, data:null}`，禁止泄漏堆栈与内部原因码
- [ ] T022 [P] 先写 `server/src/test/java/com/sdas/server/appointment/unit/RevisitEligibilityServiceTest.java`（已认证+有记录通过、未认证拒绝、无记录拒绝、审计事件类型正确），再实现 T016
- [ ] T023 [P] 先写 `server/src/test/java/com/sdas/server/appointment/unit/ImageStorageServiceTest.java`（合法 jpg/png/webp 往返、伪装后缀拒绝、超 5MB 拒绝、>9 张拒绝、磁盘写入与 URL 映射），再实现 T018
- [ ] T024 [P] 新建 `server/src/test/java/com/sdas/server/appointment/integration/FoundationalIT.java`：断言 10 表存在、索引存在、`V2` 可重复迁移、`test`/`prod` 的 `spring.flyway.locations` 不含 `classpath:db/seed`（A4 回归）、`ErrorCode` 16 项新增文案与 `data-model.md` 1.9 逐字一致、`AppointmentProperties`/`TriageProperties`/`ImageStorageProperties` 绑定成功

**检查点**：`mvn -B test -Dtest='FoundationalIT,RevisitEligibilityServiceTest,ImageStorageServiceTest'` 通过；此时尚无任何端点，但地基可用。

---

## Phase 3: 用户故事 1 - 浏览科室选择医生并完成挂号支付（优先级：P1）🎯 MVP

**目标**：已认证复诊患者按科室浏览医生列表、查看医生详情、选定医生与时段发起预约、完成模拟支付，挂号单状态变已支付可查询；无在线医生时提示「暂无医生在线」并禁止预约。
**独立测试**：用预置的科室/医生/排班数据（不依赖 AI 导诊故事）完成「浏览 → 选医生 → 支付 → 查询状态」即视为交付；不依赖 US2/US3。

**对应需求**：FR-001、FR-002、FR-003、FR-004、FR-005、FR-006、FR-007、FR-016、FR-021、FR-022、FR-024；端点 1/2/3/5/6a/6b；mock M1–M8、M12、M16。

### 测试先行（必须先失败）⚠️

- [ ] T025 [P] [US1] 新建 `server/src/test/java/com/sdas/server/appointment/contract/DepartmentControllerContractTest.java`：端点 1 正向数组结构与异常（`SERVICE_UNAVAILABLE`）；断言 `code` 与字段
- [ ] T026 [P] [US1] 新建 `server/src/test/java/com/sdas/server/appointment/contract/DoctorControllerContractTest.java`：端点 2（`departmentId`/`online` 筛选、空科室返回空数组不报错）、端点 3（`DOCTOR_NOT_FOUND` / `DEPARTMENT_NOT_FOUND`）正向与异常
- [ ] T027 [P] [US1] 新建 `server/src/test/java/com/sdas/server/appointment/contract/AppointmentPaymentContractTest.java`：端点 5（`REVISIT_NOT_ELIGIBLE`/`DOCTOR_NOT_FOUND`/`INVALID_SCHEDULE`/`UNAUTHORIZED`/`CSRF_TOKEN_MISMATCH`）、端点 6a（`INVALID_PAYMENT_AMOUNT`/`INVALID_APPOINTMENT_STATUS`/`PAYMENT_FAILED`/`PAYMENT_CONFLICT`/`APPOINTMENT_NOT_FOUND`）、端点 6b（已支付/未支付两种状态），断言 `data` 字段与契约逐字一致
- [ ] T028 [P] [US1] 新建 `server/src/test/java/com/sdas/server/appointment/integration/AppointmentPaymentFlowIT.java`：挂号主流程（金额快照=医生 `consultation_fee`）、支付幂等（重复支付返回首次结果）、并发支付仅一个成功（`PAYMENT_CONFLICT`）、无在线医生时 `INVALID_SCHEDULE` 不建单、未认证/无资格 0 放行（`REVISIT_NOT_ELIGIBLE`）、中途 DB 不可用返回 503 且无半成品、刷新后状态一致（SC-003/SC-005）
- [ ] T029 [P] [US1] 新建 `server/src/test/java/com/sdas/server/appointment/unit/AppointmentServiceTest.java`：复诊门禁装配、`UNIQUE(patient_id, schedule_id)` 重复预约、`fee_amount` 快照正确、状态流转 `PENDING_PAYMENT→PAID`
- [ ] T030 [P] [US1] 新建 `server/src/test/java/com/sdas/server/appointment/unit/PaymentServiceTest.java`：模拟支付事务内「先查后写」、`UNIQUE(appointment_id)` 幂等、并发竞争、金额校验（拒绝 `amount≠fee` 与 `≤0`）
- [ ] T031 [P] [US1] 新建 `web/tests/unit/appointment-store.spec.ts`：store 创建时从 `localStorage` 恢复、挂号/支付后写入 `appointmentId`/`paymentStatus`
- [ ] T032 [P] [US1] 新建 `web/tests/unit/appointment-flow.spec.ts`：科室→医生→创建预约→支付的状态机 UI 联动，失败文案一律来自后端 `message`
- [ ] T033 [P] [US1] 新建 `web/tests/e2e/appointment.mock.spec.ts`：注入 M1–M8、M12、M16，覆盖浏览、选医生、创建预约成功、支付成功、门禁拒绝、依赖降级
- [ ] T034 [P] [US1] 新建 `web/tests/e2e/appointment.real.spec.ts`：`E2E_REAL_BACKEND=1` 时走真实后端，随机复诊患者，断言响应 `code` 与页面状态

### 实现

- [ ] T035 [US1] 新建 `server/src/main/java/com/sdas/server/appointment/dto/`：`DepartmentResponse`、`DoctorResponse`、`DoctorDetailResponse`、`AppointmentCreateRequest`、`AppointmentCreateResponse`、`PaymentRequest`、`PaymentResponse`、`PaymentStatusResponse`：字段与 `contracts/` 端点 1/2/3/5/6 一一对应，Bean Validation 首轮校验，禁止出现内部字段
- [ ] T036 [US1] 新建 `service/DepartmentReadService.java` + `impl/DepartmentReadServiceImpl.java`：科室列表、医生列表（筛选）、医生详情（含排班）；复用 `DoctorOnlineStatus` 判定「暂无医生在线」展示
- [ ] T037 [US1] 新建 `service/AppointmentService.java` + `impl/AppointmentServiceImpl.java`：创建预约（`@Transactional`：复诊门禁 → 排班校验 → 写 `appointment` + 审计 `APPOINTMENT_CREATED`），金额从 `doctor.consultation_fee` 快照，绝不以客户端值计
- [ ] T038 [US1] 新建 `service/PaymentService.java` + `impl/PaymentServiceImpl.java`：`mockPaymentEnabled=true` 时生成 `out_trade_no`、同事务写 `payment_record(SUCCESS)` + 更新 `appointment(payment_status=PAID, appointment_status=PAID)` + 审计 `APPOINTMENT_PAID`；幂等与并发由 `UNIQUE(appointment_id)` 保证（SC-005/SC-008）
- [ ] T039 [US1] 新建 `controller/DepartmentController.java`（`GET /api/v1/departments`）、`DoctorController.java`（`GET /api/v1/doctors`、`GET /api/v1/doctors/{doctorId}`）、`AppointmentController.java`（`POST /api/v1/appointments`）、`PaymentController.java`（`POST/GET /api/v1/appointments/{appointmentId}/payments`），只做校验 + 调 Service，身份取自 `PatientIdExtractor`
- [ ] T040 [US1] 扩展 `web/src/api/appointment-consultation.ts`：封装端点 1/2/3/5/6a/6b，具体路径写 `/v1/...`（不含 `/api`），`POST` 自动附带 CSRF 头，复用 `patientRequest`
- [ ] T041 [P] [US1] 新建 `web/src/views/appointment-departments-view.vue`、`appointment-doctors-view.vue`、`appointment-create-view.vue`、`appointment-pay-view.vue`：浏览/详情/创建预约/支付，`<style scoped>`、≤ 400 行，无 `v-html` 渲染
- [ ] T042 [P] [US1] 新建 `web/src/stores/appointment.ts`：`appointmentId`、`paymentStatus`、`selectedDoctor`，创建时从 `localStorage` 恢复
- [ ] T043 [US1] 在 `web/src/router/index.ts` 注册 `/appointment/departments`、`/appointment/doctors`、`/appointment/create`、`/appointment/pay`（词干与 `/consultation/entry`、`/patient/*` 错开），`meta.requiresAuth: true`

**检查点**：`mvn -B test` 中 US1 用例全绿；`npm run test` + `npm run test:e2e -- appointment` 通过；手测可浏览→选医生→支付→查询状态。此时可独立演示 MVP。

---

## Phase 4: 用户故事 2 - 提交图文问诊单（优先级：P1）

**目标**：已认证复诊患者填写主诉（10–200 字）、选填描述、上传最多 9 张图（≤5MB），提交后问诊单状态「待接诊」，可在问诊记录查询；AI 摘要随单生成给医生。
**独立测试**：用预置已认证复诊账户与已 `PAID` 的挂号单（不依赖手动浏览或 AI 导诊），提交一份问诊单，验证状态、图片校验、摘要生成即视为交付；不依赖 US1/US3。

**对应需求**：FR-008、FR-009、FR-010、FR-011、FR-016、FR-017、FR-018、FR-019、FR-021、FR-025；端点 7/8/9；mock M9–M11、M13–M15、M16。

### 测试先行（必须先失败）⚠️

- [ ] T044 [P] [US2] 新建 `server/src/test/java/com/sdas/server/appointment/contract/ImageUploadContractTest.java`：端点 7 正向（返回 `imageId`/`url`/`contentType`/`sizeBytes`）与异常（`INVALID_IMAGE_FORMAT`/`INVALID_IMAGE_SIZE`/`TOO_MANY_IMAGES`/`UNAUTHORIZED`/`CSRF_TOKEN_MISMATCH`）
- [ ] T045 [P] [US2] 新建 `server/src/test/java/com/sdas/server/appointment/contract/ConsultationContractTest.java`：端点 8（`INVALID_COMPLAINT_FORMAT`/`INVALID_DESCRIPTION_FORMAT`/`TOO_MANY_IMAGES`/`INVALID_APPOINTMENT_STATUS` 未支付/`UNAUTHORIZED`/`CONSULTATION_FORBIDDEN`/`APPOINTMENT_NOT_FOUND`）、端点 9（`CONSULTATION_NOT_FOUND`/`CONSULTATION_FORBIDDEN`/`UNAUTHORIZED`），断言 `data.status=PENDING`、`summary` 字段存在
- [ ] T046 [P] [US2] 新建 `server/src/test/java/com/sdas/server/appointment/integration/ConsultationFlowIT.java`：提交成功状态 `PENDING`、主诉 9/10/200/201 字边界、10 张图/单张>5MB/伪装格式拒绝且无脏数据、同一 `appointment_id` 并发提交仅一张（`CONSULTATION_SUBMITTED` 一次）、提交瞬间医生转离线仍 `PENDING`（FR-025）、越权访问他人问诊单 403（SC-010）、AI 摘要生成或异常降级为 `null`
- [ ] T047 [P] [US2] 新建 `server/src/test/java/com/sdas/server/appointment/unit/ConsultationServiceTest.java`：主诉/描述长度二次校验、图片数量/大小/格式校验、关联 `appointment` 须 `PAID`、`UNIQUE(appointment_id)` 幂等、`summary` 生成（或 null 降级）
- [ ] T048 [P] [US2] 新建 `web/tests/unit/consultation-submit.spec.ts`：主诉 10–200 字规则、图片张数/大小前端预校验（仅禁用提交与高亮，文案来自后端）、成功更新 store
- [ ] T049 [P] [US2] 新建 `web/tests/unit/consultation-detail.spec.ts`：状态 `PENDING` 渲染、图片列表渲染、摘要只读展示
- [ ] T050 [P] [US2] 新建 `web/tests/e2e/consultation.mock.spec.ts`：注入 M9–M11、M13–M15、M16，覆盖上传成功、提交成功、主诉越界、图片超 5MB、越权访问
- [ ] T051 [P] [US2] 新建 `web/tests/e2e/consultation.real.spec.ts`：真实后端提交后查询状态 `PENDING`、越权访问他人单 403

### 实现

- [ ] T052 [US2] 新建 `server/src/main/java/com/sdas/server/appointment/dto/`：`ImageUploadResponse`、`ConsultationSubmitRequest`、`ConsultationSubmitResponse`、`ConsultationDetailResponse`：字段与 `contracts/` 端点 7/8/9 一一对应，禁止出现内部存储路径字段暴露
- [ ] T053 [US2] 新建 `service/ConsultationService.java` + `impl/ConsultationServiceImpl.java`：`@Transactional`：校验关联 `appointment` 状态=`PAID` → 写 `consultation`（`status=PENDING`）+ `consultation_image` 绑定 + 调 `SummaryClient` 生成摘要（异常置 null，不回滚）+ 审计 `CONSULTATION_SUBMITTED`；越权由 `patient_id` 归属校验（`CONSULTATION_FORBIDDEN`）
- [ ] T054 [US2] 新建 `controller/ImageController.java`（`POST /api/v1/consultations/images`）、`ConsultationController.java`（`POST /api/v1/consultations`、`GET /api/v1/consultations/{consultationId}`），调 `ImageStorageService` 与 `ConsultationService`，归属校验
- [ ] T055 [US2] 扩展 `web/src/api/appointment-consultation.ts`：封装端点 7/8/9，`POST` 附带 CSRF 头
- [ ] T056 [P] [US2] 新建 `web/src/views/consultation-submit-view.vue`、`consultation-detail-view.vue`：主诉/描述/图片上传（`<input type=file>` 走 `multipart`）、提交、详情展示，图片渲染用 `<img>` 受控 `src`（不 `v-html`）
- [ ] T057 [P] [US2] 新建 `web/src/stores/consultation.ts`：`consultationId`、`complaint`、`imageUrls`、`status`，创建时从 `localStorage` 恢复
- [ ] T058 [US2] 在 `web/src/router/index.ts` 注册 `/appointment/consultation`（提交，`requiresAuth`）与 `/appointment/consultation/:id`（详情，`requiresAuth`）；复用 `/consultation/entry` 作为问诊总入口（`requiresAuth+requiresVerified`）并挂载 US1/US2 入口按钮

**检查点**：US1 与 US2 用例全绿；挂号支付→提交问诊单→查询状态全链路通过；两故事互不破坏（SC-003/SC-004/SC-008/SC-010）。

---

## Phase 5: 用户故事 3 - 通过 AI 智能导诊获得科室与医生推荐（优先级：P2）

**目标**：患者文字描述症状，系统基于知识库推荐科室与医生列表，标注辅助参考，患者可自由改选；AI 超时/不可用时降级手动路径（默认 5 秒返回全量科室列表，HTTP 200），不阻塞挂号与问诊单提交；提交时病情摘要同步医生侧。
**独立测试**：输入症状，验证返回推荐、可自由改选、AI 超时后手动路径仍可用、摘要随单生成即视为交付；不依赖 US1/US2。

**对应需求**：FR-012、FR-013、FR-014、FR-015、FR-020、FR-022、FR-023；端点 4；mock M4、M5、M16。

### 测试先行（必须先失败）⚠️

- [ ] T059 [P] [US3] 新建 `server/src/test/java/com/sdas/server/appointment/contract/TriageContractTest.java`：端点 4 成功分支（`status=SUCCESS` + `recommendedDepartments` 非空 + `fallbackDepartments` 空）与降级分支（`status=TIMEOUT_FALLBACK` + 全量科室列表 + `recommendedDepartments` 空），均 HTTP 200；异常 `INVALID_COMPLAINT_FORMAT`/`CSRF_TOKEN_MISMATCH`
- [ ] T060 [P] [US3] 新建 `server/src/test/java/com/sdas/server/appointment/integration/TriageFlowIT.java`：注入 >5s 延迟断言返回 `TIMEOUT_FALLBACK`+全量科室、HTTP 200、写 `TRIAGE_TIMEOUT_FALLBACK` 审计；知识库命中返回正确科室；空推荐/未知科室降级；前端可自由改选不强制（契约不约束选择）
- [ ] T061 [P] [US3] 新建 `server/src/test/java/com/sdas/server/appointment/unit/TriageClientTest.java`：`timeout-millis=5000` 超时触发降级、AI 异常触发降级、正常返回科室 ID 列表；`kb_version` 可追溯
- [ ] T062 [P] [US3] 新建 `web/tests/unit/appointment-triage.spec.ts`：提交症状→展示推荐、超时降级后展示全量科室列表、可改选任意科室
- [ ] T063 [P] [US3] 新建 `web/tests/e2e/appointment-triage.mock.spec.ts`：注入 M4、M5、M16，覆盖推荐成功、超时降级、依赖降级
- [ ] T064 [P] [US3] 新建 `web/tests/e2e/appointment-triage.real.spec.ts`：真实后端（或 Mock AI 延迟）验证 5 秒降级分支与状态

### 实现

- [ ] T065 [US3] 新建 `server/src/main/java/com/sdas/server/appointment/dto/TriageRequest.java`、`TriageResponse.java`：字段与 `contracts/` 端点 4 一致（`triageSessionId`/`status`/`recommendedDepartmentIds`/`recommendedDepartments`/`fallbackDepartments`/`modelUsed`/`elapsedMs`）
- [ ] T066 [US3] 新建 `service/TriageService.java` + `impl/TriageServiceImpl.java`：调 `TriageClient` → 命中知识库组装 `recommendedDepartments`（`status=SUCCESS`）或降级 `fallbackDepartments=全量科室`（`status=TIMEOUT_FALLBACK`，HTTP 200）；写 `ai_triage_session`（输入/输出/`model_used`/`elapsed_ms`/`kb_version`）供审计，不向患者暴露推理细节；标注「辅助参考」由前端负责
- [ ] T067 [US3] 新建 `controller/TriageController.java`：`POST /api/v1/triage-sessions`，身份可不登录，`CSRF` 必带
- [ ] T068 [US3] 扩展 `web/src/api/appointment-consultation.ts`：封装端点 4，`POST` 附带 CSRF 头
- [ ] T069 [P] [US3] 新建 `web/src/views/appointment-triage-view.vue`：症状输入、推荐列表（标注「辅助参考」）、可改选，跳转 `/appointment/doctors`；`<style scoped>`、≤ 350 行
- [ ] T070 [US3] 在 `web/src/router/index.ts` 注册 `/appointment/triage`（`requiresAuth` 可选，本端点允许匿名），并在 `/consultation/entry` 增加「AI 智能导诊」入口按钮

**检查点**：三个故事全部可用；AI 超时不阻塞主流程（SC-006），推荐仅为辅助、可自由改选（SC-007）。

---

## Phase 6: 收尾与跨故事事项（Polish）

**目的**：跨故事的测试、静态检查、契约一致性与交付物收口。

- [ ] T071 [P] 补齐后端单测覆盖至 ≥ 70%：`server/src/test/java/com/sdas/server/appointment/unit/` 下为 `AppointmentService`、`PaymentService`、`ConsultationService`、`TriageService`、`TriageClient`、`SummaryClient`、`ImageStorageService`、`RevisitEligibilityService`、`ScheduleValidator`、`DoctorRepository` 等逐项补分支
- [ ] T072 [P] 补齐前端单测：`web/tests/unit/` 下为 3 个 store（`appointment`/`consultation`/`patient` 复用）、视图与 composable 补充分支用例
- [ ] T073 [P] 全量 Playwright Mock E2E：`web/tests/e2e/` 覆盖 3 个用户故事成功/失败路径，`npm run test:e2e` 零失败
- [ ] T074 全量真实后端 E2E：`$env:E2E_REAL_BACKEND='1'; npm run test:e2e`，全部断言响应 `code`，且不使用预置演示种子记录
- [ ] T075 [P] 静态检查零错误零警告：`mvn -B verify`（Checkstyle + SpotBugs）、`npm run lint`、`npm run format:check`、`npm run typecheck`
- [ ] T076 [P] 按 `quickstart.md` 第 6–7 节逐条手工验证（科室浏览/选医生/导诊/创建预约/支付/上传图片/提交问诊单/查询），记录未认证账户成功发起预约/支付/问诊单次数为 0、AI 超时降级为 200、越权访问 0 返回病情资料
- [ ] T077 执行 `contracts/README.md` 第 5 节一致性清单：端点、`code` 取值、字段结构、日期格式、敏感字段禁列与真实后端响应逐项比对，结果写入 `testsdoc/appointment-consultation/contract-conformance.md`
- [ ] T078 [P] 测试报告归档：`testsdoc/appointment-consultation/` 下按 `[测试名].md` 归档单测、集成测试、Mock E2E、真实后端 E2E 报告
- [ ] T079 代码审查（宪法原则 V）：检查分层越界、枚举 `@Enumerated` 与 `@Converter` 并存、硬编码文案、事务边界（`@Transactional` 多表编排）、`System.out.println`、新增依赖是否超出 `plan.md` 架构评审
- [ ] T080 安全检查：日志与响应抽样确认不含完整手机号/身份证号/支付敏感信息/AI 输入敏感身份；确认 `mock-payment-enabled` 仅 dev/test，`prod` 须显式关闭或走真实渠道（A1 回归）

---

## Dependencies & Execution Order

### 阶段依赖

- **Phase 1 Setup**：无依赖，可立即开始。
- **Phase 2 Foundational**：依赖 Setup 完成，**阻塞全部用户故事**（T008–T024 未完成前不得开始 T025 之后的任务）。
- **Phase 3 US1（P1）**：依赖 Foundational。
- **Phase 4 US2（P1）**：依赖 Foundational；复用 US1 的 `appointment` 表与支付状态，但夹具可预置已支付挂号单，不要求 US1 端点完成。
- **Phase 5 US3（P2）**：依赖 Foundational；AI 导诊为辅助入口，不阻塞 US1/US2。
- **Phase 6 Polish**：依赖所需故事全部完成。

### 故事间依赖

| 故事 | 依赖 | 说明 |
|------|------|------|
| US1 挂号支付 | Foundational | 端点 1–3 公共浏览，5/6 受保护 |
| US2 图文问诊单 | Foundational + US1 的 `appointment`/`payment` 表 | 提交须关联 `PAID` 挂号单；夹具可预置 |
| US3 AI 导诊 | Foundational | 独立辅助入口，推荐结果可自由改选 |

### 故事内部顺序

1. 测试任务先行，且必须先运行至失败（宪法原则 VI）。
2. DTO → Service → Controller。
3. 前端 API 封装 → store/composable → 组件 → 页面 → 路由。
4. 单个故事通过检查点后再进入下一优先级。

### 并行机会

- Phase 1 全部 `[P]` 任务可并行（T002–T007）。
- Phase 2 中枚举、转换器、迁移脚本、10 个 Repository、配置类均为不同文件，可并行（T009–T020）。
- Foundational 完成后，US1/US2/US3 可按人力并行开发（不同开发者各领一个故事，测试与源码文件不重叠）。
- 每个故事内标记 `[P]` 的测试任务可同时启动。

### 并行示例（US1）

```text
Task: "T025 [P] [US1] 端点 1 契约测试 DepartmentControllerContractTest.java"
Task: "T026 [P] [US1] 端点 2/3 契约测试 DoctorControllerContractTest.java"
Task: "T028 [P] [US1] 挂号支付集成测试 AppointmentPaymentFlowIT.java"
Task: "T033 [P] [US1] Mock E2E appointment.mock.spec.ts"
Task: "T041 [P] [US1] 视图 appointment-departments/doctors/create/pay-view.vue"
```

---

## 需求覆盖映射

| 需求 | 对应任务 | 需求 | 对应任务 |
|------|----------|------|----------|
| FR-001 | T035–T039、T041–T043 | FR-014 | T020、T053、T058 |
| FR-002 | T035–T036、T041 | FR-015 | T019、T066、T070 |
| FR-003 | T036、T041（「暂无医生在线」展示） | FR-016 | T029、T037、T047、T053 |
| FR-004 | T035、T037、T039、T043 | FR-017 | T018、T041、T056 |
| FR-005 | T038–T039、T043 | FR-018 | T053–T054、T058 |
| FR-006 | T027、T038–T039、T043 | FR-019 | T011、T037–T038、T053 |
| FR-007 | T016、T022、T037、T039 | FR-020 | T019–T020、T053、T066 |
| FR-008 | T052–T053、T056–T058 | FR-021 | T037–T038、T053 |
| FR-009 | T018、T023、T052–T054 | FR-022 | T012–T013、T036、T066 |
| FR-010 | T052–T053、T056–T058 | FR-023 | T011、T066 |
| FR-011 | T030、T047、T053 | FR-024 | T005、T037–T038 |
| FR-012 | T019、T066–T067 | FR-025 | T053 |
| FR-013 | T066、T069–T070 | | |

| 成功标准 | 对应任务 |
|----------|----------|
| SC-001 | T041、T076（手工计时「浏览→支付」10 次取中位数写入报告） |
| SC-002 | T028、T046、T074 |
| SC-003 | T016、T022、T028、T037、T039、T076 |
| SC-004 | T018、T023、T044–T047、T053、T076 |
| SC-005 | T027–T028、T030、T038、T076 |
| SC-006 | T019、T059–T060、T066、T070、T076 |
| SC-007 | T066、T069–T070 |
| SC-008 | T028、T046、T053 |
| SC-009 | T011、T025–T027、T044–T045、T074、T080 |
| SC-010 | T045、T053–T054、T058、T076 |
| SC-011 | T028、T046、T074、T080 |
| SC-012 | T077 |

**需求缺口检查**：25 条 FR 与 12 条 SC 均有任务覆盖，无遗漏项。

---

## Notes

- `[P]` = 不同文件且无未完成依赖，可并行。
- `[US*]` 标签用于追溯与增量交付，跨故事地基任务标 `[FOUND]`。
- 每个故事必须先让测试失败，再实现，最后通过检查点才可进入下一优先级。
- Java 存在编译期依赖：测试先行时先创建 Service 接口、DTO 与 Controller 方法签名（方法体抛 `UnsupportedOperationException`），保证测试可编译运行并因断言失败而「红」。
- 禁止修改 `specs/002-appointment-consultation/checklists/requirements.md` 的勾选状态（审查方产物）。
- 禁止在实现阶段引入 `plan.md`「架构评审记录」之外的第三方依赖（本次零新增）。
- 禁止把 `data-model.md`/`contracts/` 的枚举、`code`、`message` 复制到代码后再各自演化：代码只允许引用，变更须先改文档。
- 前端只镜像 `code` 数值，`message` 一律展示后端响应值；`error-codes.ts` 不复制业务文案。
- 模拟支付仅 `dev`/`test` 启用（`mock-payment-enabled=true`）；`prod` 须显式关闭或接入真实渠道后再启用支付（A1 回归）。
- 演示种子位于 `db/seed/`，仅 dev 加载；`test`/`prod` 的 Flyway locations 只含 `db/migration`，测试数据由 `AppointmentTestSupport` 动态写入。
- 复用 001 的 `patientRequest`、`PatientIdExtractor`、`AuditEventService`、`GlobalExceptionHandler`、`PatientAccountRepository`，不复制重复实现。
- 每完成一个逻辑分组提交一次，提交信息引用任务编号。
