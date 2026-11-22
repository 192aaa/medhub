# 任务清单：处方取药

**功能**：`004-prescription-dispensing` | **日期**：2026-11-08
**输入**：`specs/004-prescription-dispensing/` 下的 `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/`、`quickstart.md`
**前置条件**：`spec.md`（3 个用户故事，Q1–Q7 已定稿）、`plan.md`（G1–G6 门禁复核通过）、`research.md`（R-01–R-11）、`data-model.md`、`contracts/`（5 端点 + M1–M12）全部已产出；上游 `001-patient-identity`（JWT/双重 Cookie/`CryptoService`/`AuditEventService`）、`002-appointment-consultation`（`doctor` 档案）、`003-doctor-consultation`（`prescription`/`prescription_item`/`PrescriptionSigningService`/`QrCodeService`）已实现。

**测试**：依 `.specify/memory/constitution.md`（原则 VI）为强制项——单元测试覆盖率 ≥ 70%（jacoco `verify` 强制）、5 个 REST 端点正向/异常契约与集成测试全覆盖、Playwright 覆盖 3 个用户故事（mock + real 成对）；测试用例先写且先失败，通过后方可视为功能完成。测试报告落 `testsdoc/prescription-dispensing/`。

**组织方式**：任务按用户故事分组（US1 药店登录 + 扫码验真 / US2 取药核销 / US3 患者出示处方），每个故事可独立实现、独立测试、独立交付。

**唯一来源约束**：枚举与 `ErrorCode` 以 `data-model.md`（1.1–1.7）为准；请求/响应字段与 `code` 以 `contracts/prescription-dispensing-api.md` 为准；Mock 以 `contracts/mocks.md`（M1–M12）为准。任务中的类名、字段名、路径不得自行发明。

> **进度说明（2026-11-22）**：下方复选框未随实现同步更新，请勿把“未勾选”等同于“未实现”。代码抽查显示处方、药品目录、药店验真相关后端代码已落地；当前仍明确为 Demo/占位的只有沙箱支付渠道（未接真实网关）；问诊入口与药品目录已替换为真实 API 流程。完整状态以实际代码和 `testsdoc/` 为准，复选框待人工逐项复核。

## 格式说明

- `[ID]`：任务编号，`T001` 起连续编号，实现阶段按序勾选。
- `[P]`：可并行（不同文件、无未完成依赖）。
- `[US1]`–`[US3]`：归属用户故事；跨故事地基任务标 `[FOUND]`。
- 每条任务写明精确文件路径与验收要点；禁止出现「实现某功能」这类无路径的模糊任务。

## 路径约定

- 后端药店域：`server/src/main/java/com/sdas/server/pharmacy/`（`controller/`、`service/`、`service/impl/`、`repository/`、`entity/`、`model/`、`model/converter/`、`dto/`、`support/`）
- 后端患者域：`server/src/main/java/com/sdas/server/prescription/`（`controller/`、`service/`、`service/impl/`、`repository/`、`dto/`）
- 后端复用：`server/src/main/java/com/sdas/server/{common,identity,doctor,config,exception}/`
- 后端测试：`server/src/test/java/com/sdas/server/pharmacy/{contract,integration,unit,support}/`；患者域测试放 `.../pharmacy/contract/`、`.../pharmacy/integration/`
- 迁移脚本：`server/src/main/resources/db/migration/V7__prescription_dispensing_schema.sql`；种子 `server/src/main/resources/db/seed/V8__prescription_dispensing_seed.sql`（仅 dev 加载）
- 前端：`web/src/`（`api/`、`api/mocks/`、`api/request/`、`views/`、`stores/`、`router/`）
- 前端测试：`web/tests/unit/`、`web/tests/e2e/`、`web/tests/e2e/fixtures/`
- 契约与设计：`specs/004-prescription-dispensing/`
- 测试报告：`testsdoc/prescription-dispensing/`

---

## Phase 1: Setup（共享基础设施）

**目的**：目录、前端类型/错误码/Mock 骨架、配置绑定类、测试基座就绪；此阶段不写业务逻辑。

- [ ] T001 创建 `server/src/main/java/com/sdas/server/pharmacy/` 下 `controller/`、`service/`、`service/impl/`、`repository/`、`entity/`、`model/`、`model/converter/`、`dto/`、`support/` 与 `server/src/main/java/com/sdas/server/prescription/{controller,service,service/impl,repository,dto}/` 包（各包先放 `package-info.java`），并创建 `server/src/test/java/com/sdas/server/pharmacy/{contract,integration,unit,support}/`、`testsdoc/prescription-dispensing/` 目录
- [ ] T002 [P] 扩展 `web/src/api/types.ts`：按 `contracts/prescription-dispensing-api.md` 定义 `PharmacyAccountStatus`、`DispenseStatus`、`DispenseResult` 常量与 5 端点请求/响应接口（含 `Page<T>`），文件头标注来源 `specs/004-prescription-dispensing/data-model.md` 与 `contracts/`
- [ ] T003 [P] 扩展 `web/src/api/error-codes.ts`：新增 `PHARMACY_ROLE_REQUIRED`(403)、`PHARMACY_ACCOUNT_NOT_FOUND`(404)、`INVALID_VERIFICATION_PROOF`(400)、`PRESCRIPTION_NOT_FOUND`(404)、`PRESCRIPTION_NOT_SIGNED`(400)、`PRESCRIPTION_REVOKED`(400)；**不复制 `message`**，业务文案一律取后端响应值
- [ ] T004 [P] 新建 `web/src/api/mocks/pharmacy.ts` 与 `web/src/api/mocks/prescription.ts`：从 `contracts/mocks.md` 同步 M1–M12（按 `METHOD /v1/path` + `#SCENARIO` 组织），`message` 逐字复制；`qrImageDataUrl` 用 mocks.md 中的 1×1 占位；禁止出现完整手机号/身份证号/令牌明文以外的敏感信息
- [ ] T005 [P] 在 `server/src/main/resources/application.yml` 追加 `data-model.md` §6 的 `sdas.pharmacy.qr.*`（`verify-url-template`/`token-version`/`verify-code-length`/`image-size`/`error-correction`）并新建 `server/src/main/java/com/sdas/server/config/PharmacyProperties.java`（`@ConfigurationProperties(prefix = "sdas.pharmacy")`，嵌套 `Qr` record）；`dev/test` 给可直接运行占位值，生产用 `SDAS_PHARMACY_VERIFY_URL` 注入
- [ ] T006 [P] 新建 `server/src/test/java/com/sdas/server/pharmacy/support/PharmacyTestSupport.java`：`@SpringBootTest` + `MockMvc`，支持 `IDENTITY_TEST_USE_LOCAL_INFRA=true` 复用本机 MySQL/Redis（沿用 002/003 模式），Flyway 自动迁移；提供 `seedPharmacy`、`loginPharmacy`、`verifiedPatient`、`createPaidPrescription` 夹具；**处方夹具主路径固定为**「直接落库 + `PrescriptionSigningService.sign` + `PrescriptionQrTokenService.issue` 写入 `qr_code`」，仅 T061 的集成测试走 002+003 真实链路

**检查点**：`mvn -o -q compile` 与 `npm run typecheck` 通过；目录与配置齐备，尚无业务行为。

---

## Phase 2: Foundational（阻塞性前置，必须全部完成）

**目的**：错误码、枚举、药店身份链路、签名密钥改造、令牌服务、派生状态、表结构与仓储等所有用户故事共用的地基。

**⚠️ 关键**：本阶段未完成前，任何用户故事都不允许开始实现。

- [ ] T007 [FOUND] 扩展 `server/src/main/java/com/sdas/server/common/ErrorCode.java`：新增 `data-model.md` 1.6 的 6 个常量（`PHARMACY_ROLE_REQUIRED`/`PHARMACY_ACCOUNT_NOT_FOUND`/`INVALID_VERIFICATION_PROOF`/`PRESCRIPTION_NOT_FOUND`/`PRESCRIPTION_NOT_SIGNED`/`PRESCRIPTION_REVOKED`），`message` 逐字取自 1.6，Javadoc 标注 `004 data-model.md 1.6`
- [ ] T008 [P] [FOUND] 在 `server/src/main/java/com/sdas/server/pharmacy/model/` 新建枚举 `PharmacyAccountStatus`、`DispenseStatus`、`DispenseResult`；`model/converter/` 为 `PharmacyAccountStatus` 建显式 `AttributeConverter`（`autoApply=false`，`null`/未知值降级），禁止 `@Enumerated(EnumType.STRING)`；`DispenseStatus`/`DispenseResult` 为派生枚举（不落库、无 converter）
- [ ] T009 [P] [FOUND] 扩展 `server/src/main/java/com/sdas/server/identity/model/AuditEventType.java`：追加 `PHARMACY_LOGIN_SUCCESS`、`PHARMACY_LOGIN_FAILED`、`PRESCRIPTION_VERIFICATION_SUCCESS`、`PRESCRIPTION_VERIFICATION_FAILED`、`PRESCRIPTION_DISPENSED`、`PRESCRIPTION_DISPENSE_REPEATED`、`PRESCRIPTION_DISPENSE_FAILED`（覆盖过期/作废/草稿/凭证无效、唯一键以外的失败，满足 FR-016/FR-022）
- [ ] T010 [P] [FOUND] 新建 `server/src/main/resources/db/migration/V7__prescription_dispensing_schema.sql`：按 `data-model.md` §2 建 `pharmacy_account`、`prescription_dispense_record`（含 `uk_dispense_prescription`），并执行 `ALTER TABLE prescription MODIFY COLUMN qr_code VARCHAR(1024) NULL;` 与 `CREATE INDEX idx_prescription_patient ON prescription (patient_id, signed_at);`；InnoDB / utf8mb4_general_ci，FK 指向 `prescription(id)`/`pharmacy_account(id)`
- [ ] T011 [P] [FOUND] 新建 `server/src/main/resources/db/seed/V8__prescription_dispensing_seed.sql`：仅合成演示数据（`pharmacy_hp`/`pharmacy_xh`，BCrypt 口令 `password`，`name_mask` 与药店名），文件头标注「禁止用于生产」；`application-dev.yml` 的 `spring.flyway.locations` 已含 `classpath:db/seed`，无需改动，但需验证 test/prod 不加载
- [ ] T012 [P] [FOUND] 在 `server/src/main/java/com/sdas/server/pharmacy/entity/` 新建 `PharmacyAccount`、`DispenseRecord` 实体，字段与 `data-model.md` §2 一一对应（Javadoc 标注 `@Converter` 关联）
- [ ] T013 [P] [FOUND] 新建 `server/src/main/java/com/sdas/server/pharmacy/repository/PharmacyAccountRepository.java`：`Optional<PharmacyAccount> findById(long)`、`Optional<PharmacyAccount> findByUsername(String)`、`long insert(PharmacyAccount)`（`GeneratedKeyHolder`）
- [ ] T014 [P] [FOUND] 新建 `server/src/main/java/com/sdas/server/pharmacy/repository/DispenseRecordRepository.java`：`long insert(DispenseRecord)`、`Optional<DispenseRecord> findByPrescriptionId(long)`、`List<DispenseRecord> findTopByPharmacyId(...)`（仅供测试/审计，不作为端点）
- [ ] T015 [P] [FOUND] 扩展 `server/src/main/java/com/sdas/server/identity/service/JwtTokenService.java` + `impl/JwtTokenServiceImpl.java`：新增 `String issuePharmacy(long pharmacyId, long sessionId)`（`sub`/`pharmacyId`/`sessionId`/`role="PHARMACY"`）与 `PharmacyTokenClaims parsePharmacy(String)`（角色不符抛 `PHARMACY_ROLE_REQUIRED`），并新建 `identity/service/PharmacyTokenClaims.java`
- [ ] T016 [P] [FOUND] 新建 `server/src/main/java/com/sdas/server/common/PharmacyIdExtractor.java`：镜像 `DoctorIdExtractor`（Bearer 解析 + Redis 吊销校验），失败抛 `PHARMACY_ROLE_REQUIRED`；Controller 禁止自解析 token
- [ ] T017 [P] [FOUND] 先写 `server/src/test/java/com/sdas/server/pharmacy/unit/PrescriptionSigningServiceTest.java`（**须先失败**）：签名可验、篡改内容验签失败、**同一 keystore 跨实例/重建 Bean 后仍可验签**；随后改造 `server/src/main/java/com/sdas/server/doctor/support/PrescriptionSigningService.java` 为 keystore 加载（`sdas.doctor.signature.*`，`prod` 缺失 fail-fast，`dev/test` 自动生成并 WARN），暴露 `verify(String, String)` 与 `publicKey()`
- [ ] T018 [P] [FOUND] 先写 `server/src/test/java/com/sdas/server/pharmacy/unit/QrCodeServiceTest.java`（480×480、可被 ZXing 解码回原文、内容超长抛异常）；随后改 `server/src/main/java/com/sdas/server/doctor/support/QrCodeService.java` 为 480×480 + 纠错 `M`，签名保持 `buildVerifyUrl/generatePng`
- [ ] T019 [P] [FOUND] 先写 `server/src/test/java/com/sdas/server/pharmacy/unit/DispenseStatusResolverTest.java`（**须先失败**）：覆盖 `data-model.md` §4 的 8 行矩阵 + `now == valid_until` 判未过期 + `valid_until=null` 边界
- [ ] T020 [P] [FOUND] 新建 `server/src/main/java/com/sdas/server/pharmacy/service/DispenseStatusResolver.java`（纯函数，无 Spring 依赖）：按 `data-model.md` 1.2 优先级与 §4 矩阵派生 `dispenseStatus`/`dispensable`/`expired`，`valid_until` 为 `null` 时按 `EXPIRED` 处理
- [ ] T021 [P] [FOUND] 先写 `server/src/test/java/com/sdas/server/pharmacy/unit/PrescriptionQrTokenServiceTest.java`（**须先失败**）：签发→校验往返、改 `cipher`/`sig` 任一字符失败、令牌 `exp` 过期失败、非法结构/超长失败、`v2` 前缀拒绝、验真码取签名段前 12 位且校验常量时间
- [ ] T022 [P] [FOUND] 新建 `server/src/main/java/com/sdas/server/pharmacy/support/PrescriptionQrTokenService.java`：`issue(long prescriptionId, LocalDateTime validUntil)` 生成 `v1.<cipher>.<sig>`；`verify(String token)` 校验结构 → RSA 验签 → AES-GCM 解密 → 校验 `v`/`exp` → 返回 `pid`；`verifyCode(String token)` 取签名段前 12 位；`linkOf(String token)` 用 `PharmacyProperties.qr().verifyUrlTemplate()` 拼 URL；令牌不进日志——审计存 SHA-256 **前 16 hex**、`proof_digest` 存**完整 64 hex**；长度分层：令牌 ≤ 700、完整 URL ≤ 1024、`qrContent` 接收 8–1024
- [ ] T023 [P] [FOUND] 扩展 `server/src/main/java/com/sdas/server/doctor/repository/PrescriptionRepository.java`：新增 `List<Prescription> findByPatientId(long patientId, int offset, int limit)`、`long countByPatientId(long patientId)`、`Optional<Prescription> findByIdAndPatientId(long id, long patientId)`（均排除 `status='DRAFT'`，按 `signed_at DESC, id DESC`）
- [ ] T024 [P] [FOUND] 改造 `server/src/main/java/com/sdas/server/doctor/service/impl/PrescriptionServiceImpl.java`：签名成功后调用 `PrescriptionQrTokenService.issue/ linkOf` 写 `qr_code`；`detail()` 改用 `PRESCRIPTION_NOT_FOUND`；同步修订 `specs/003-doctor-consultation/contracts/doctor-consultation-api.md` 中 `qrCode` 字段说明为「`v1` 令牌 URL」，并在该文件标注「由 004 修订」
- [ ] T025 [P] [FOUND] 扩展 `server/src/main/java/com/sdas/server/exception/GlobalExceptionHandler.java`（若既有通用 `BusinessException` 映射已覆盖 `ErrorCode` 则仅补测试）：确认 `PHARMACY_ROLE_REQUIRED`(403)、`INVALID_VERIFICATION_PROOF`(400)、`PRESCRIPTION_NOT_SIGNED`(400)、`PRESCRIPTION_REVOKED`(400)、`PRESCRIPTION_NOT_FOUND`(404) 均返回 `{code,message,data:null}` 且不泄漏堆栈
- [ ] T026 [P] [FOUND] 新建 `server/src/test/java/com/sdas/server/pharmacy/support/PharmacyFixtures.java`：预置启用/禁用药店账号（高位主键 8101/8102 幂等写入）、已签名处方（走 `PrescriptionService` 或直接落库 + `sign()`）、`DispenseRecord` 插入、验真码读取；所有预置语句幂等（`INSERT IGNORE`/先删后插），避免与 001–003 夹具主键冲突
- [ ] T027 [FOUND] 新建 `server/src/test/java/com/sdas/server/pharmacy/integration/FoundationalIT.java`：断言 2 张新表与 `qr_code` 长度/新索引存在、`V7` 可重复迁移、6 个错误码文案与 `data-model.md` 1.6 逐字一致、`PharmacyProperties` 绑定成功、`test/prod` 不加载 `db/seed`

**检查点**：`mvn -o test -Dtest='FoundationalIT,DispenseStatusResolverTest,PrescriptionQrTokenServiceTest,PrescriptionSigningServiceTest,QrCodeServiceTest'` 全绿；此时尚无任何新端点，但地基可用（含跨重启验签能力）。

---

## Phase 3: User Story 1 - 药店登录并扫码验真（Priority: P1）🎯 MVP

**目标**：药店工作人员登录后，通过扫码 / 上传图片 / 手工输入完成处方验真，看到处方要素与取药状态（未取药 / 已取药 / 已过期 / 已作废）。

**独立测试**：用预置药店账号 + 一张已签名处方，完成「登录 → 扫码 → 看到处方要素与状态」；再用篡改令牌、过期处方、患者 token 访问分别验证拒绝逻辑。

### Tests for User Story 1（强制）⚠️

> **说明：先写这些测试并确认失败，再进入实现。**

- [ ] T028 [P] [US1] 契约测试 `server/src/test/java/com/sdas/server/pharmacy/contract/PharmacySessionContractTest.java`：登录成功字段（`accessToken`/`pharmacyId`/`nameMask`/`pharmacyName`/`expiresIn`）、口令错误 `AUTHENTICATION_FAILED`、禁用账号同文案、缺 CSRF 403、`message` 逐字取自契约
- [ ] T029 [P] [US1] 契约测试 `.../pharmacy/contract/PrescriptionVerificationContractTest.java`：`qrContent` 与 `prescriptionId+verifyCode` 两分支各自成功、字段齐备（含 `verifyCode`/`items`）、篡改令牌 400 `INVALID_VERIFICATION_PROOF`（响应 `data:null`）、草稿 400 `PRESCRIPTION_NOT_SIGNED`、作废 **200 + `dispenseStatus=REVOKED` + `dispensable=false`**、过期 **200 + `dispenseStatus=EXPIRED`**、患者 token 403、缺 CSRF 403、互斥参数缺失/同时提供 400 `BAD_REQUEST`
- [ ] T030 [P] [US1] 单测 `.../pharmacy/unit/PharmacyAuthServiceTest.java`：BCrypt 校验、禁用账号、模糊失败、审计事件写入（Mockito）
- [ ] T031 [P] [US1] 集成测试 `.../pharmacy/integration/VerificationFlowIT.java`：**同一二维码连续扫描 3 次均 200 且 `dispenseStatus` 不变**（验真可重复）、篡改令牌后无处方字段泄漏、验真不写业务表（仅审计行数 +1）、`REVOKED` 与 `EXPIRED` 状态区分
- [ ] T032 [P] [US1] E2E `web/tests/e2e/pharmacy-verification.mock.spec.ts`：M1 登录 → M2 验真（连续两次，验证多次扫描）→ M5 篡改失败 → M3 过期展示 → 作废返回 200 状态展示 → `page.goto('/pharmacy/verify?t=<token>')` 直达自动验真（未登录先登录且回跳保留 `t`）
- [ ] T033 [P] [US1] E2E `web/tests/e2e/pharmacy-verification.real.spec.ts`：断言真实后端 `code` 值（未登录 401、药店登录 200、验真 200/400 分支），使用动态数据

### Implementation for User Story 1

- [ ] T034 [US1] 完善 `server/src/main/java/com/sdas/server/pharmacy/service/PharmacyAuthService.java` + `impl/PharmacyAuthServiceImpl.java`：`login(username,password)` 查库 → BCrypt → 禁用判断 → `issuePharmacy` → 组装响应 → 审计（成功/失败，主体为 username 的 SHA-256 前 16 hex）
- [ ] T035 [US1] 新建 `server/src/main/java/com/sdas/server/pharmacy/controller/PharmacySessionController.java`：`POST /api/v1/pharmacy/sessions`，`@Valid` 校验 + 调 Service，禁止解析 token
- [ ] T036 [US1] 新建 `server/src/main/java/com/sdas/server/pharmacy/service/PrescriptionVerificationService.java` + `impl`：按 `plan.md` 校验顺序编排（身份 → 参数互斥 → 令牌结构/签名 → 解密 → 令牌 exp → 处方存在 → 内容签名复验 → 草稿/作废判断 → 派生状态 → 组装），失败写审计且不含处方字段
- [ ] T037 [US1] 新建 `server/src/main/java/com/sdas/server/pharmacy/controller/PharmacyVerificationController.java`：`POST /api/v1/pharmacy/prescription-verifications`，`@Valid @RequestBody`，`proof` 二选一校验
- [ ] T038 [P] [US1] 新建 DTO：`pharmacy/dto/PharmacyLoginRequest.java`、`PharmacyLoginResponse.java`、`VerificationRequest.java`、`VerificationResponse.java`、`PrescriptionItemView.java`（字段名与契约逐字一致）
- [ ] T039 [US1] 审计接入：登录成功/失败、验真成功/失败四类事件落 `security_audit_event`；令牌仅存 SHA-256 前 16 hex，响应/日志零敏感明文（FR-016）
- [ ] T040 [P] [US1] 修复并完善 `web/src/api/request/pharmacy-request.ts`：补 `withCredentials: true` 与 `onUnauthorized`（清理 `sdas.pharmacy.token`/`sdas.pharmacy.profile` 并跳 `/pharmacy/login`）
- [ ] T041 [P] [US1] 新建 `web/src/api/pharmacy.ts`：`pharmacyLogin`、`verifyPrescription`（均标注 contracts 路径；修改类请求附 `csrfHeaders()`）
- [ ] T042 [P] [US1] 新建 `web/src/stores/pharmacy.ts`：`defineStore('pharmacy')` setup 式，`sdas.pharmacy.token`/`sdas.pharmacy.profile`，创建时 `restore()`，`persist()/clear()/hasSession`
- [ ] T043 [US1] 新建 `web/src/views/pharmacy-login-view.vue`：账号/口令表单、加载态、统一模糊错误提示、成功后跳 `/pharmacy/verify`
- [ ] T044 [US1] 新建 `web/src/views/pharmacy-verify-view.vue`：三入口——摄像头（`Html5Qrcode`，动态 `import('html5-qrcode')`，`facingMode: environment`，权限拒绝降级提示）、上传图片（`Html5Qrcode.scanFile`）、手工输入（处方号 + 12 位验真码）；结果区展示处方要素、`dispenseStatus` 徽标、`dispensable` 提示、`verifyCode`、错误分支文案；**URL 直达**：`route.query.t` 存在时自动发起验真并跳过扫码 UI（手机相机扫码落地场景）
- [ ] T045 [US1] 扩展 `web/src/router/index.ts` + `web/src/router/guards.ts`：新增 `/pharmacy/login`、`/pharmacy/verify`（`requiresPharmacy`），`RouteMeta` 增加 `requiresPharmacy`，守卫读取 `pharmacyStore.hasSession` 并跳转 `/pharmacy/login`；**未登录时须携带 `redirect`（保留 `t` 查询参数）**，登录成功后回跳并自动验真
- [ ] T046 [P] [US1] 单元测试 `web/tests/unit/pharmacy-verify-view.spec.ts`：mock `html5-qrcode`（jsdom 无 `mediaDevices`），覆盖三入口的解析容错、错误文案、`dispensable=false` 时核销按钮不可用

**检查点**：US1 可独立演示——药店登录并验真三类处方（未取药/已过期/已取药），篡改令牌被拒且无字段泄漏。

---

## Phase 4: User Story 2 - 未取药处方核销（Priority: P1）

**目标**：药店工作人员将「未取药」处方核销为「已取药」，幂等、并发安全、不可回退、全程审计。

**独立测试**：一张未取药处方完成「验真 → 核销 → 状态已取药」；重复核销返回 `ALREADY_DISPENSED` 且记录仅 1 条；并发核销仅一次 `DISPENSED`。

### Tests for User Story 2（强制）⚠️

- [ ] T047 [P] [US2] 契约测试 `.../pharmacy/contract/DispenseContractTest.java`：首次核销 `DISPENSED/idempotent=false`、重复 `ALREADY_DISPENSED/idempotent=true` 且 `dispensedAt` 为首次时间、凭证缺失 400 `INVALID_VERIFICATION_PROOF`、`pid` 与路径不一致 400、过期 400 `PRESCRIPTION_EXPIRED`、作废 400 `PRESCRIPTION_REVOKED`、草稿 400 `PRESCRIPTION_NOT_SIGNED`、患者 token 403、缺 CSRF 403
- [ ] T048 [P] [US2] 单测 `.../pharmacy/unit/PrescriptionDispenseServiceTest.java`：唯一键冲突 → 读回既有记录 → `ALREADY_DISPENSED`；事务回滚不写半成品；派生状态 `DISPENSED` 优先于 `EXPIRED`
- [ ] T049 [P] [US2] 集成测试 `.../pharmacy/integration/DispenseFlowIT.java`：串行重复核销记录恒 1 条、成功审计 1 条 + 重复审计 1 条、**失败分支（过期/作废/凭证无效）各写 `PRESCRIPTION_DISPENSE_FAILED` 且处方状态不变**、核销后验真返回 `dispensable=false`、`prescription.status` 保持不变（未新增枚举值）
- [ ] T050 [P] [US2] 并发集成测试 `.../pharmacy/integration/DispenseConcurrencyIT.java`：2 线程（`CountDownLatch`）同时核销同一处方 → 恰好 1 个 `DISPENSED` + 1 个 `ALREADY_DISPENSED`，`prescription_dispense_record` 恒 1 行（对应 SC-004）
- [ ] T051 [P] [US2] E2E `web/tests/e2e/pharmacy-dispensing.mock.spec.ts`：M1 → M2 → M6 核销成功 → M7 重复核销提示
- [ ] T052 [P] [US2] E2E `web/tests/e2e/pharmacy-dispensing.real.spec.ts`：断言无凭证核销 400、未登录 401、真实链路核销 `code` 值

### Implementation for User Story 2

- [ ] T053 [US2] 新建 `server/src/main/java/com/sdas/server/pharmacy/service/PrescriptionDispenseService.java` + `impl`：凭证复验（`qrContent`/`verifyCode` 且 `pid` 与路径一致）→ 状态校验 → `@Transactional` 内 `DispenseRecordRepository.insert` → 捕获 `DuplicateKeyException` 读回既有 → 组装 `DispenseResult`；审计三态 `PRESCRIPTION_DISPENSED`/`PRESCRIPTION_DISPENSE_REPEATED`/`PRESCRIPTION_DISPENSE_FAILED`（失败场景不留半成品）
- [ ] T054 [US2] 在 `server/src/main/java/com/sdas/server/pharmacy/controller/PharmacyVerificationController.java` 增加 `POST /api/v1/pharmacy/prescriptions/{prescriptionId}/dispensing`
- [ ] T055 [P] [US2] 新建 DTO `pharmacy/dto/DispenseRequest.java`、`DispenseResponse.java`（字段与契约一致：`dispenseResult`/`idempotent`/`dispenseRecordId`/`dispenseStatus`/`dispensedAt`/`dispensedByMask`/`pharmacyName`）
- [ ] T056 [P] [US2] 扩展 `web/src/api/pharmacy.ts`：`dispensePrescription(prescriptionId, proof)`
- [ ] T057 [US2] 新建 `web/src/views/pharmacy-dispense-view.vue`：二次确认（处方号/药品数/取药提示）、提交中禁用防抖、成功展示「已取药」与核销人；`ALREADY_DISPENSED` 以中性提示展示而非错误；`requiresPharmacy` 路由 `/pharmacy/dispense`
- [ ] T058 [P] [US2] 单元测试 `web/tests/unit/pharmacy-dispense-view.spec.ts`：重复点击只发一次请求、`ALREADY_DISPENSED` 提示、失败回退可重试

**检查点**：US1 + US2 均可独立演示；并发场景记录恒 1 条；处方状态机未被修改。

---

## Phase 5: User Story 3 - 患者查看并出示处方与二维码（Priority: P2）

**目标**：患者查看本人全部已签名处方（倒序，含状态），详情页展示处方要素、水印、二维码图片与验真码。

**独立测试**：预置患者与两张已签名处方（一张未取药、一张已取药），完成「列表 → 详情 → 二维码可见」；他人处方与不存在的处方返回同一 404。

### Tests for User Story 3（强制）⚠️

- [ ] T059 [P] [US3] 契约测试 `.../pharmacy/contract/PatientPrescriptionsContractTest.java`：列表分页结构（`items/page/pageSize/total`）、倒序、排除 `DRAFT`、空列表 `total=0`、详情字段齐备（`qrCode`/`qrImageDataUrl` 以 `data:image/png;base64,` 开头/`verifyCode` 12 位）、他人处方与不存在均 404 `PRESCRIPTION_NOT_FOUND`、药店 token 401
- [ ] T060 [P] [US3] 单测 `.../pharmacy/unit/PatientPrescriptionServiceTest.java`：分页参数越界截断（`pageSize>50`）、`page<1` 归 1、二维码图片仅生成一次（缓存/单次调用）、草稿过滤
- [ ] T061 [P] [US3] 集成测试 `.../pharmacy/integration/PatientPrescriptionFlowIT.java`：真实链路（002 预约 → 支付 → 003 开方）产出的处方在患者列表可见且 `dispenseStatus=PENDING`；核销后变 `DISPENSED`
- [ ] T062 [P] [US3] E2E `web/tests/e2e/prescription.mock.spec.ts`：M8 列表 → M9 详情（二维码 `<img>` 可见、验真码可见）→ M10 已取药展示 → M12 越权 404
- [ ] T063 [P] [US3] E2E `web/tests/e2e/prescription.real.spec.ts`：未登录 401、真实患者列表/详情 `code` 值断言（动态数据）

### Implementation for User Story 3

- [ ] T064 [US3] 新建 `server/src/main/java/com/sdas/server/prescription/repository/PatientPrescriptionQueryRepository.java`：分页列表（`LIMIT/OFFSET`）、详情（含 `prescription_item` 批量查询，避免 N+1）、`countByPatientId`
- [ ] T065 [US3] 新建 `server/src/main/java/com/sdas/server/prescription/service/PatientPrescriptionService.java` + `impl`：组合 `Prescription` + `items` + `DispenseRecord` → 调 `DispenseStatusResolver` 派生状态 → `QrCodeService.generatePng` 生成 `qrImageDataUrl`（480×480）→ 计算 `verifyCode`；草稿过滤；越权与不存在统一 404
- [ ] T066 [US3] 新建 `server/src/main/java/com/sdas/server/prescription/controller/PatientPrescriptionController.java`：`GET /api/v1/prescriptions`、`GET /api/v1/prescriptions/{prescriptionId}`，患者身份由 `PatientIdExtractor` 提供
- [ ] T067 [P] [US3] 新建 DTO `prescription/dto/PatientPrescriptionListItem.java`、`PatientPrescriptionDetailResponse.java`、`PageResponse.java`（与契约字段一致）
- [ ] T068 [P] [US3] 新建 `web/src/api/prescription.ts`：`listMyPrescriptions(page,pageSize)`、`getMyPrescription(prescriptionId)`
- [ ] T069 [US3] 新建 `web/src/views/prescriptions-view.vue`（列表 + 状态徽标 + 空态 + 分页）与 `web/src/views/prescription-detail-view.vue`（处方要素、水印、`<img :src="qrImageDataUrl">`、验真码、已取药信息、返回列表）
- [ ] T070 [US3] 扩展 `web/src/router/index.ts`：`/prescriptions`、`/prescriptions/:id`（`requiresAuth`）
- [ ] T071 [P] [US3] 单元测试 `web/tests/unit/prescription-detail-view.spec.ts`：二维码 `src` 绑定、`dispenseStatus` 徽标映射（4 态）、`dispensable=false` 提示、越权 404 文案

**检查点**：三个用户故事全部可独立演示；患者端二维码可被 US1 扫码验真（端到端闭环）。

---

## Phase 6: Polish & Cross-Cutting Concerns（收口）

**目的**：覆盖率、静态检查、契约一致性、报告归档与交付验证。

- [ ] T072 [P] 后端覆盖率补齐：对 `pharmacy/` 与 `prescription/` 未被覆盖分支补单测（目标行覆盖率 ≥ 70%，`mvn -o verify` 的 jacoco `check` 通过）
- [ ] T073 [P] SpotBugs 豁免口径扩展：把 `com.sdas.server.pharmacy.*` 与 `com.sdas.server.prescription.*` 加入 `server/config/spotbugs/spotbugs-exclude.xml`（`EI_EXPOSE_REP2`/`OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE`，理由同既有包），并在文件注释登记 004
- [ ] T074 [P] 静态检查：`mvn -o checkstyle:check` 零违规（新类全部 Javadoc、圈复杂度 ≤ 10）；`cd web && npm run typecheck && npm run lint && npm run format:check` 零错误零警告
- [ ] T075 [P] 契约一致性验证：逐项比对 `contracts/` 与真实响应（5 端点、`code` 值、字段名/类型、时间格式 `yyyy-MM-dd'T'HH:mm:ss`、分页结构），结果写入 `testsdoc/prescription-dispensing/contract-conformance.md`
- [ ] T076 [P] 安全自查：grep 全量与响应/审计抽样，确认无完整手机号/身份证号/患者全名/令牌明文；确认验真令牌只经 POST body、`AccessLogFilter` 未记录 body
- [ ] T077 [P] 性能抽测：验真接口 P95 ≤ 200 ms（100 次串行/并发混合）对应 SC-001；**核销端到端（验真成功→提交核销→响应）采样 ≥ 20 次、P95 ≤ 30 s** 对应 SC-002；二维码图片 ≤ 16 KB；结果记入 `testsdoc/prescription-dispensing/test-report.md`
- [ ] T078 执行 `quickstart.md` 第 5–6 节全部步骤（含 10 项反向验证），结果记入 `testsdoc/prescription-dispensing/manual-qa-checklist.md`
- [ ] T079 全量回归：`cd server && IDENTITY_TEST_USE_LOCAL_INFRA=true mvn -o verify`；`cd web && npm run test && npx playwright test`（Mock 全绿，real 用例按需跳过）
- [ ] T080 测试报告归档 `testsdoc/prescription-dispensing/test-report.md`（后端/前端/静态检查/覆盖率/相关文档 5 节，参照 `testsdoc/patient-identity/test-report.md` 格式）
- [ ] T081 更新 `specs/004-prescription-dispensing/plan.md` 的「实施阶段」勾选状态与 `analysis.md`（若生成），并在 `contracts/README.md` 第 5 节契约一致性清单全部勾选
- [ ] T082 代码审查（宪法 V）：按 001/002/003 的 `code-review-security.md` 口径评审批次变更（签名密钥处理、令牌校验顺序、幂等实现、脱敏），结论落 `testsdoc/prescription-dispensing/code-review-security.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup（T001–T006）**：无前置，可立即开始。
- **Phase 2 Foundational（T007–T027）**：依赖 Phase 1；**阻塞全部用户故事**。
- **Phase 3 US1（T028–T046）**：依赖 Phase 2；US2/US3 的验真链路也依赖本阶段产出的令牌校验与派生状态。
- **Phase 4 US2（T047–T058）**：依赖 Phase 2 + T036/T037（验真链路）；可与 US3 并行。
- **Phase 5 US3（T059–T071）**：依赖 Phase 2（派生状态、令牌 `verifyCode`）；与 US2 并行无文件冲突。
- **Phase 6 收口（T072–T082）**：依赖 US1–US3 全部完成。

### 关键任务依赖（易错点）

| 任务 | 依赖 | 说明 |
|------|------|------|
| T022 令牌服务 | T017（keystore 改造） | 令牌签发需真实私钥；否则跨重启失效 |
| T024 003 改造 | T022 | 处方签名后写入 `v1` 令牌 URL |
| T036 验真服务 | T022、T023、T020、T013、T015、T016 | 缺任一则校验链不完整 |
| T053 核销服务 | T036（共用凭证复验）、T014（唯一键）、T020 | 幂等依赖 DB 唯一键 |
| T065 患者服务 | T020、T023、T018（二维码 PNG） | `verifyCode` 来自令牌服务 |
| T054 核销端点 | T053 | — |
| T066 患者端点 | T065 | — |

### 用户故事依赖

- **US1（P1）**：Phase 2 完成后即可开始；no 依赖其他故事 → **MVP**。
- **US2（P1）**：复用 US1 的凭证复验与派生状态，但核销端点、服务、页面完全独立；可独立测试（直接插入已签名处方 + 令牌）。
- **US3（P2）**：只读，独立于 US1/US2 的写入路径；可用已落库处方独立测试。

### 并行机会

- Phase 1 的 T002–T006 四路并行（不同文件）。
- Phase 2 的 T008/T009/T010/T011/T012/T013/T014/T015/T016/T017/T018/T019/T021/T023/T025 可并行；T020 紧接 T019、T022 依赖 T017/T021、T024 依赖 T022、T026/T027 收口。
- US1 内 T028–T033（测试）全并行；T038/T040/T041/T042/T046 与 T034–T037 可并行（不同文件）。
- US2 与 US3 在 US1 之后可双人并行（后端文件不重叠；前端 `api/pharmacy.ts` 与 `api/prescription.ts` 分离）。

---

## MVP 与交付策略

1. **MVP（US1）**：完成 Phase 1 + Phase 2 + Phase 3 → 药店可登录并验真，即可演示「辨真伪」价值。
2. **增量 2（US2）**：完成 Phase 4 → 具备「一处方只取一次药」的闭环，业务可用。
3. **增量 3（US3）**：完成 Phase 5 → 患者可出示二维码，真实到店流程完整。
4. **收口**：Phase 6 全绿后交付；`mvn -o verify` 与 `npx playwright test` 必须全绿。

---

## 需求覆盖矩阵（FR → 任务）

| 需求 | 任务 |
|------|------|
| FR-001 药店登录/角色 | T007、T015、T034、T035、T043 |
| FR-002 独立请求实例/token | T003、T040、T041、T042、T045 |
| FR-003 患者列表 | T023、T064、T065、T066、T069 |
| FR-004 患者详情要素+水印 | T065、T066、T069 |
| FR-005 二维码与 003 规则同源 | T018、T022、T024、T065 |
| FR-006 扫码/上传/手工 | T044、T046 |
| FR-007 验签失败不泄漏 | T021、T029、T036、T039 |
| FR-008 返回要素与状态 | T029、T036、T038 |
| FR-009 服务端时间判定 | T019、T020、T065 |
| FR-010 核销 | T053、T054、T057 |
| FR-011 幂等 | T048、T053 |
| FR-012 并发仅一次 | T010（唯一键）、T050、T053 |
| FR-013 整单核销不可回退 | T053、T054（无撤销端点） |
| FR-014 患者越权 404 | T059、T065 |
| FR-015 药店端脱敏 | T034、T038、T039 |
| FR-016 审计与零明文 | T009、T039、T053、T076 |
| FR-017 服务端二次校验 | T036、T053 |
| FR-018 事务编排 | T053 |
| FR-019 依赖降级 503 | T025、T079 |
| FR-020 派生状态唯一来源 | T008、T019、T020、T065 |
| FR-021 角色隔离 | T016、T029、T059 |
| FR-022 失败可统计 | T009、T039、T077 |
| FR-023 药店端仅验真+核销 | T045、T057（不建记录查询页） |

## 风险项 → 测试任务（对应 spec 8.1）

| 风险项 | 测试任务 |
|--------|----------|
| 二维码篡改/伪造 | T021、T029、T031、T032 |
| 并发核销 | T050 |
| 重复核销 | T048、T049、T051 |
| 过期/作废/草稿 | T020、T029、T047 |
| 派生状态组合 | T019、T020、T027 |
| 越权访问 | T029、T059、T062 |
| 敏感信息泄漏 | T039、T059、T076 |
| 依赖不可用 | T025、T079 |
| 手工输入枚举 | T021、T029、T031 |
| 二维码规则漂移 | T022、T024、T075 |
| 状态枚举漂移 | T008、T075 |

---

## Notes

- `[P]` 表示不同文件、无未完成依赖；同文件任务必须串行（例如 T036/T037 与 T053/T054 分属不同文件可并行，但同文件内新增方法需串行）。
- 测试任务必须**先写并确认失败**，实现后再转绿；禁止先写实现再补测试。
- 每完成一个用户故事即执行其「独立测试」，通过后才进入下一优先级。
- 契约或数据模型如需变更，先改 `specs/004-prescription-dispensing/{data-model.md,contracts/}`，再改代码与测试，禁止反向同步。
- Mock E2E 一律用 `page.route()`，真实后端 E2E 用 `E2E_REAL_BACKEND=1` 且断言 `code` 值。
