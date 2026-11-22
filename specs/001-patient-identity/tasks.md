# 任务清单：患者注册与实名认证

**功能**：`001-patient-identity` | **日期**：2026-11-07
**输入**：`specs/001-patient-identity/` 下的 `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/`、`quickstart.md`

**前置条件**：`plan.md`（必需）、`spec.md`（必需，含 4 个用户故事）、`research.md`、`data-model.md`、`contracts/`、`quickstart.md` — 全部已产出，`contracts/README.md` 第 6 节 10 项一致性验证已通过。

**测试**：依 `.specify/memory/constitution.md`（原则 VI）为强制项——单元测试覆盖率 ≥ 70%、集成测试覆盖 8 个 REST 端点的正向与异常、Playwright 覆盖全部 4 个用户故事；测试用例先写且先失败，通过后方可视为功能完成。测试报告落 `testsdoc/patient-identity/[测试名].md`。

**组织方式**：任务按用户故事分组，保证每个故事可独立实现、独立测试、独立交付。

**唯一来源约束**：枚举与 `ErrorCode` 以 `data-model.md` 为准；请求/响应字段与 `code` 以 `contracts/` 为准；mock 以 `contracts/mocks.md`（M1–M15）为准。任务描述中的类名、字段名不得自行发明。

## 格式说明

- `[ID]`：任务编号，`T001` 起连续编号，实现阶段按序勾选。
- `[P]`：可并行（不同文件、无未完成依赖）。
- `[US1]`–`[US4]`：归属的用户故事，用于追溯与增量交付。
- 每条任务写明精确文件路径与验收要点；禁止出现「实现某功能」这类无路径的模糊任务。

## 路径约定

- 后端：`server/src/main/java/com/sdas/server/identity/`（`controller/`、`service/`、`service/impl/`、`repository/`、`entity/`、`model/`、`dto/`）
- 后端测试：`server/src/test/java/com/sdas/server/identity/`（`contract/`、`integration/`、`unit/`、`support/`）
- 迁移脚本：`server/src/main/resources/db/migration/`（结构脚本，test/prod 共用）；演示种子：`server/src/main/resources/db/seed/`（仅 dev 的 `spring.flyway.locations` 加载）
- 前端：`web/src/`（`api/`、`api/mocks/`、`views/`、`components/`、`composables/`、`stores/`、`router/`）
- 前端测试：`web/tests/unit/`、`web/tests/e2e/`
- 契约与设计：`specs/001-patient-identity/`
- 测试报告：`testsdoc/patient-identity/`

---

## Phase 1: Setup（共享基础设施）

**目的**：目录、依赖、配置与测试基础设施就绪。此阶段不写业务逻辑。

- [x] T001 创建 `server/src/main/java/com/sdas/server/identity/` 下 `controller/`、`service/`、`service/impl/`、`repository/`、`entity/`、`model/`、`dto/` 包（各包先放 `package-info.java` 声明），并创建 `server/src/main/resources/db/migration/`、`server/src/test/java/com/sdas/server/identity/{contract,integration,unit,support}/`、`testsdoc/patient-identity/` 目录
- [x] T002 [P] 在 `server/pom.xml` 新增已评审依赖与插件：`spring-security-crypto`、`jjwt-api`/`jjwt-impl`/`jjwt-jackson` 0.12.x、`flyway-core`+`flyway-mysql`、`spring-boot-testcontainers`+`org.testcontainers:mysql`+`org.testcontainers:junit-jupiter`、`maven-checkstyle-plugin`+`spotbugs-maven-plugin`；版本由 Boot BOM 管理，禁止引入清单外依赖
- [x] T003 [P] 扩展 `web/src/api/types.ts`：按 `contracts/patient-identity-api.md` 定义 `AuthStatus`、`VerificationPurpose` 及 8 端点请求/响应接口，文件头注释标注来源 `specs/001-patient-identity/data-model.md` 与 `contracts/`
- [x] T004 [P] 扩展 `web/src/api/error-codes.ts`：仅在 `API_CODE` 中新增 `data-model.md` 1.9 的 11 个新增常量与对应数值（`INVALID_PHONE_FORMAT` 至 `SERVICE_UNAVAILABLE`）；**不复制 `message`、不新增任何业务文案常量**（既有 `NETWORK_ERROR_MESSAGE` 仅作网络层兜底保留），业务文案一律使用后端响应值；同步更新文件头来源注释，追加 `specs/001-patient-identity/data-model.md` 1.9（原注释放 `specs/health-check/data-model.md` 1.2，按 `contracts/README.md` 第 6 节第 8 项要求标注来源）
- [x] T005 [P] 新建 `web/src/api/mocks/patient-identity.ts`：从 `contracts/mocks.md` 同步 M1–M15，按 `method + path` 组织导出，`message` 逐字复制，禁止出现验证码明文以外的敏感值
- [x] T006 [P] 在 `server/src/main/resources/application.yml`、`application-dev.yml`、`application-test.yml`、`application-prod.yml` 写入 `data-model.md` 第 6 节全部配置项，并新建 `server/src/main/java/com/sdas/server/config/IdentityProperties.java` 承载 `@ConfigurationProperties(prefix = "sdas.identity")`；**注意** `cookie.secure` 不在 `sdas.identity` 前缀下，须用 `@Value("${cookie.secure:false}")` 注入（或另建 `CookieProperties(prefix = "cookie")`），禁止强行并入 `IdentityProperties`；`SDAS_JWT_SECRET`、`SDAS_ID_CARD_HMAC_SECRET`、`SDAS_PHONE_HMAC_SECRET`、`SDAS_AES_KEY` 在 `dev`/`test` 下必须给出可直接运行的占位值，生产仅由环境变量注入（缺失即启动失败属预期）
- [x] T007 [P] 新建 `server/src/main/java/com/sdas/server/config/IdentityStartupValidator.java`：`prod` profile 下 `verification-code.fixed-enabled=true` 时抛 `IllegalStateException` 拒绝启动（D1 fail-fast），并在 `application-prod.yml` 置 `fixed-enabled: false`
- [x] T008 [P] 新建 `server/src/test/java/com/sdas/server/identity/support/IdentityTestSupport.java`：Testcontainers（MySQL 8 + Redis）基类、`@SpringBootTest` + `MockMvc` 骨架、Flyway 自动迁移校验（禁止 `spring.flyway.enabled=false`）；通过 `application-test.yml` 或 `@DynamicPropertySource` 注入 T006 的 4 个密钥测试值，保证 CI 无需预置环境变量即可启动
- [x] T009 [P] 新建 `server/src/test/java/com/sdas/server/identity/support/IdentityFixture.java`：动态生成随机手机号、符合 GB 11643 校验位的身份证号、BCrypt 密码，并提供向 `hospital_identity_whitelist` 插入/回滚的夹具方法（禁止硬编码真实身份信息）
- [x] T010 [P] 新建 `server/config/checkstyle/checkstyle.xml` 与 `server/config/spotbugs/spotbugs-exclude.xml` 最小规则集（函数 ≤ 80 行、圈复杂度 ≤ 10、公有方法 Javadoc、禁 `System.out.println`），并在 `pom.xml` 绑定到 `verify` 阶段
- [x] T011 [P] 新建 `web/tests/e2e/fixtures/patient-identity.ts`：Playwright `page.route('**/api/v1/**')` 统一注入入口，按 `method + path` 匹配 `mocks/patient-identity.ts`，并支持 `E2E_REAL_BACKEND=1` 时关闭拦截

**检查点**：`mvn -B -DskipTests compile` 与 `npm run typecheck` 均可通过；依赖与目录齐备，尚未有业务行为。

---

## Phase 2: Foundational（阻塞性前置，必须全部完成）

**目的**：`ErrorCode`、枚举与 `Converter`、加密摘要、JWT、CSRF、审计、表结构与 Repository、Redis 存储等所有用户故事共用的地基。

**⚠️ 关键**：本阶段未完成前，任何用户故事都不允许开始实现。

- [x] T012 扩展 `server/src/main/java/com/sdas/server/common/ErrorCode.java`：新增 `INVALID_PHONE_FORMAT`、`INVALID_PASSWORD_FORMAT`、`INVALID_VERIFICATION_CODE_FORMAT`、`INVALID_NAME_FORMAT`、`INVALID_ID_CARD_FORMAT`、`AGREEMENT_NOT_ACCEPTED`、`AUTHENTICATION_FAILED`、`IDENTITY_NOT_VERIFIED`、`CSRF_TOKEN_MISMATCH`、`RATE_LIMITED`、`SERVICE_UNAVAILABLE`，`message` 逐字取自 `data-model.md` 1.9，Javadoc 标注来源
- [x] T013 [P] 在 `server/src/main/java/com/sdas/server/identity/model/` 新建 `data-model.md` 1.1–1.8 的 **8 个枚举**：`AuthStatus`、`AccountStatus`、`VerificationPurpose`、`VerificationCodeStatus`、`SessionStatus`、`AgreementConsentStatus`、`AuditEventType`、`IdentityVerificationResult`（`IdentityVerificationResult` 仅内部与审计使用，禁止出现在响应 DTO）
- [x] T014 [P] 在 `server/src/main/java/com/sdas/server/identity/model/converter/` 为 T013 的 8 个枚举各建显式 `AttributeConverter`（`autoApply = false`），处理 `null` 与未知值降级为 `UNVERIFIED`/`ACTIVE` 等安全默认值；禁止 `@Enumerated(EnumType.STRING)`
- [x] T015 [P] 新建 `server/src/main/java/com/sdas/server/identity/service/CryptoService.java` + `impl/CryptoServiceImpl.java`：HMAC-SHA256（手机号、身份证号、验证码摘要）、AES-GCM 加解密（手机号、姓名、证件号）、常量时间比较；密钥只从 `IdentityProperties` 注入
- [x] T016 [P] 新建 `server/src/main/java/com/sdas/server/identity/service/MaskService.java` + `impl/MaskServiceImpl.java`：`maskPhone`（`138****8000`）、`maskName`（`张*`）、`maskIdCard`（`110***********002X`），与 `contracts/` 示例逐字对齐
- [x] T017 [P] 先写 `server/src/test/java/com/sdas/server/identity/unit/JwtTokenServiceTest.java`（过期、签名非法、缺 `sessionId`、TTL 边界），再新建 `server/src/main/java/com/sdas/server/identity/service/JwtTokenService.java` + `impl/JwtTokenServiceImpl.java`：HS256 签发/解析 access token（`patientId`、`sessionId`、TTL 7200 s），解析失败统一抛 `UNAUTHORIZED`
- [x] T018 [P] 新建 `server/src/main/java/com/sdas/server/identity/support/CookieSupport.java`：下发/清除 `sdas_refresh`（`HttpOnly; SameSite=Lax; Path=/api/v1/patient-sessions`）与 `sdas_csrf`（非 HttpOnly，`Path=/api/v1`），`Secure` 取自 `cookie.secure`
- [x] T019 [P] 新建 `server/src/main/java/com/sdas/server/config/CsrfCookieFilter.java`：仅对 `POST/PUT/PATCH/DELETE` 校验 `X-CSRF-Token` 与 `sdas_csrf` 一致，失败返回 `403 CSRF_TOKEN_MISMATCH`；`/api/health` 等既有端点不纳入
- [x] T020 [P] 新建 `server/src/main/java/com/sdas/server/common/log/LogMasker.java`：对手机号、身份证号、验证码做日志脱敏，并在 `server/src/main/resources/logback-spring.xml` 注册为转换器
- [x] T021 [P] 新建 `server/src/main/resources/db/migration/V1__identity_schema.sql`：`patient_account`、`patient_identity`、`patient_agreement_consent`、`patient_session`、`hospital_identity_whitelist`、`security_audit_event` 六表 + `data-model.md` 第 3 节全部唯一索引与普通索引，`InnoDB` / `utf8mb4_general_ci`
- [x] T022 [P] 新建 `server/src/main/resources/db/seed/V2__identity_whitelist_seed.sql`：仅合成演示数据（虚构姓名 + 算法构造占位证件号），文件头标注「禁止用于生产」；**该文件放在 `db/seed/` 而非 `db/migration/`**，仅 `application-dev.yml` 的 `spring.flyway.locations` 追加 `classpath:db/seed`，`test`/`prod` 的 locations 只含 `classpath:db/migration`，确保演示种子在生产与测试均不加载（测试白名单由 T009 夹具动态写入）
- [x] T023 [P] 在 `server/src/main/java/com/sdas/server/identity/entity/` 新建 6 个实体：`PatientAccount`、`PatientIdentity`、`PatientAgreementConsent`、`PatientSession`、`HospitalIdentityWhitelist`、`SecurityAuditEvent`，字段与 `data-model.md` 3.1–3.6 一一对应，均含 `version` 乐观锁字段
- [x] T024 [P] 新建 `server/src/main/java/com/sdas/server/identity/repository/PatientAccountRepository.java`：`JdbcTemplate` 实现按 `phone_hash` 查询、插入（返回自增主键）、更新 `auth_status`/`password_hash`（带 `version` 条件）
- [x] T025 [P] 新建 `server/src/main/java/com/sdas/server/identity/repository/PatientIdentityRepository.java` 与 `PatientAgreementConsentRepository.java`：按 `patient_id` 查询、插入（`uk_id_card_hash` / `uk_patient_version` 冲突映射为业务异常）
- [x] T026 [P] 新建 `server/src/main/java/com/sdas/server/identity/repository/PatientSessionRepository.java`：按 `refresh_token_hash` 查询、插入、`ACTIVE → REVOKED` 条件更新（`version` 乐观锁）、按 `patient_id` 批量撤销
- [x] T027 [P] 新建 `server/src/main/java/com/sdas/server/identity/repository/HospitalIdentityWhitelistRepository.java`：仅 `enabled = 1` 且 `real_name + id_card_hash` 精确匹配的单行查询，只读，无写方法
- [x] T028 [P] 新建 `server/src/main/java/com/sdas/server/identity/repository/SecurityAuditEventRepository.java`：插入审计事件，字段严格限定 `data-model.md` 3.6（禁止密码/验证码/完整手机号/完整证件号/JWT 全文）
- [x] T029 先写 `server/src/test/java/com/sdas/server/identity/unit/AuditEventServiceTest.java`（10 类事件字段完整性、写入异常不影响主流程），再新建 `server/src/main/java/com/sdas/server/identity/service/AuditEventService.java` + `impl/AuditEventServiceImpl.java`：统一落审计事件（10 类 `AuditEventType`），失败不抛出影响主流程（`@Transactional(propagation = REQUIRES_NEW)` 注意事项写入 Javadoc）；其中 `CONSULTATION_GATE_DENIED` 的**写入方为后续问诊模块**（本功能只定义取值并由端点 8 提供 `consultationAllowed` 判定依据），Javadoc 须注明该事件在本功能内无调用点，避免被误当成遗漏
- [x] T030 新建 `server/src/main/java/com/sdas/server/identity/support/RedisKeys.java` 与 `VerificationCodeStore.java`：实现 `sdas:identity:code:{purpose}:{phoneDigest}` 的写入、读取与原子「校验并置 USED」（Lua 或 `SET NX` + 状态机），TTL 300 s
- [x] T031 [P] 先写 `server/src/test/java/com/sdas/server/identity/unit/RateLimitServiceTest.java`（60 s 间隔、单日 10 次、15 分钟窗口、TTL 边界、Redis 异常降级），再新建 `server/src/main/java/com/sdas/server/identity/service/RateLimitService.java` + `impl/RateLimitServiceImpl.java`：发送间隔 60 s + 单日 10 次、登录失败 15 分钟窗口、实名认证 15 分钟窗口；超限抛 `RATE_LIMITED`，不返回剩余次数
- [x] T032 [P] 先写 `server/src/test/java/com/sdas/server/identity/unit/IdempotencyServiceTest.java`（首次写入、重复命中、TTL 过期后重新执行、并发同键只执行一次），再新建 `server/src/main/java/com/sdas/server/identity/service/IdempotencyService.java` + `impl/IdempotencyServiceImpl.java`：基于 `sdas:identity:idem:{idempotencyKey}`（TTL 600 s）实现首次结果快照复用
- [x] T033 新建 `server/src/main/java/com/sdas/server/common/PatientIdExtractor.java`：从 `Authorization: Bearer` 解析 `patientId` + `sessionId`，校验 `session:revoked:{sessionId}`，失败抛 `UNAUTHORIZED`；Controller 禁止自行解析
- [x] T034 扩展 `server/src/main/java/com/sdas/server/exception/GlobalExceptionHandler.java`：新增 `BusinessException` → `ErrorCode` 映射、`MethodArgumentNotValidException` → 对应格式类 `ErrorCode`、限流/CSRF/依赖降级分支，响应统一 `{code, message, data:null}`，禁止泄漏堆栈与内部原因码
- [x] T035 新建 `server/src/test/java/com/sdas/server/identity/integration/FoundationalIT.java`：断言 6 表存在、索引存在、`V1` 可重复迁移、**`test`/`prod` 的 `spring.flyway.locations` 不含 `classpath:db/seed`**（A4 回归断言）、Redis 6 个键模板前缀正确、`ErrorCode` 11 项文案与 `data-model.md` 1.9 逐字一致、`prod` 固定验证码启动失败、CSRF 过滤器对缺头请求返回 403；本任务与 T036 属**地基自检**，不承担用户故事测试先行职责（各 Service 的前置单测已并入 T017/T029/T031/T032）
- [x] T036 新建 `server/src/test/java/com/sdas/server/identity/unit/CryptoServiceTest.java` 与 `MaskServiceTest.java`：摘要一致性、AES-GCM 往返、常量时间比较、脱敏格式与契约示例逐字一致

**检查点**：`mvn -B test -Dtest='FoundationalIT,CryptoServiceTest,MaskServiceTest'` 通过；此时尚无任何端点，但地基可用。

---

## Phase 3: 用户故事 1 - 手机号注册并自动登录（优先级：P1）🎯 MVP

**目标**：未注册手机号 + 验证码 + 合规密码 + 勾选协议 → 创建账户、自动登录、返回 `UNVERIFIED` 且 `consultationAllowed=false`，前端进入个人中心。
**独立测试**：仅实现本阶段，用未注册手机号完成一次注册并进入个人中心即视为交付；不依赖 US2/US3/US4。

**对应需求**：FR-001 ~ FR-006、FR-017、FR-025；端点 1（`POST /api/v1/verification-codes`）、端点 2（`POST /api/v1/patients`）；mock M1–M5。

### 测试先行（必须先失败）⚠️

- [x] T037 [P] [US1] 新建 `server/src/test/java/com/sdas/server/identity/contract/VerificationCodeContractTest.java`：端点 1 正向（`data` 不含验证码字段）+ 异常（`INVALID_PHONE_FORMAT`/`BAD_REQUEST`/`CSRF_TOKEN_MISMATCH`/`RATE_LIMITED`/`SERVICE_UNAVAILABLE`），断言 HTTP 状态与 body `code` 双一致
- [x] T038 [P] [US1] 新建 `server/src/test/java/com/sdas/server/identity/contract/PatientRegisterContractTest.java`：端点 2 正向字段完整性与异常码全覆盖，断言 `authStatus=UNVERIFIED`、`consultationAllowed=false`、`Set-Cookie` 含 `sdas_refresh`（HttpOnly）与 `sdas_csrf`
- [x] T039 [P] [US1] 新建 `server/src/test/java/com/sdas/server/identity/integration/RegisterFlowIT.java`：注册主流程、验证码一次性（重复提交失败）、用途隔离（`RESET_PASSWORD` 码不能注册）、手机号已注册与验证码错误文案逐字一致、并发重复提交仅产生 1 个账户、`X-Idempotency-Key` 重复提交返回首次结果、Redis 不可用时返回 503 且不产生半成品账户
- [x] T040 [P] [US1] 新建 `server/src/test/java/com/sdas/server/identity/unit/VerificationCodeServiceTest.java`：固定验证码只存摘要、TTL 300 s、60 s 间隔、单日 10 次上限、`ACTIVE → USED` 原子性
- [x] T041 [P] [US1] 新建 `server/src/test/java/com/sdas/server/identity/unit/PatientAccountServiceTest.java`：手机号已注册 → `AUTHENTICATION_FAILED`；协议版本不匹配 → `AGREEMENT_NOT_ACCEPTED`；密码规则二次校验 → `INVALID_PASSWORD_FORMAT`
- [x] T042 [P] [US1] 新建 `web/tests/unit/use-verification-code.spec.ts`：60 s 倒计时、超限提示走 `RATE_LIMITED` 文案、不读取 `data.code`（防止契约违规）
- [x] T043 [P] [US1] 新建 `web/tests/unit/patient-store.spec.ts`：store 创建时从 `localStorage` 恢复会话、注册后写入 `authStatus`/`consultationAllowed`
- [x] T044 [P] [US1] 新建 `web/tests/unit/patient-register-view.spec.ts`：密码规则（6-20 位、字母+数字）、协议未勾选禁止提交、字段级错误文案一律来自后端 `message`，请求未发出时只做输入禁用/高亮、不展示文案（网络异常用既有 `NETWORK_ERROR_MESSAGE`），断言前端未硬编码业务文案
- [x] T045 [P] [US1] 新建 `web/tests/e2e/patient-register.mock.spec.ts`：注入 M1–M5，覆盖成功注册、超限、验证码错误、密码不合规四条路径
- [x] T046 [P] [US1] 新建 `web/tests/e2e/patient-register.real.spec.ts`：`E2E_REAL_BACKEND=1` 时走真实后端，随机手机号，断言响应 `code` 与页面状态

### 实现

- [x] T047 [US1] 新建 `server/src/main/java/com/sdas/server/identity/dto/` 下 `VerificationCodeSendRequest`、`VerificationCodeSendResponse`、`PatientRegisterRequest`、`PatientRegisterResponse`：字段与 `contracts/` 端点 1/2 一一对应，Bean Validation 首轮校验，禁止出现 `phoneCipher`/`*_hash` 等内部字段
- [x] T048 [US1] 新建 `service/VerificationCodeService.java` + `impl/VerificationCodeServiceImpl.java`：固定验证码（`fixed-enabled` 时才生效）+ Redis 生命周期 + 频次限制编排；日志与响应均不输出验证码明文
- [x] T049 [US1] 新建 `service/PatientAccountService.java` + `impl/PatientAccountServiceImpl.java`：注册事务（`patient_account` + `patient_agreement_consent` + `patient_session` + `security_audit_event` 同一 `@Transactional`，失败整体回滚），返回 access token 并下发 refresh/CSRF Cookie
- [x] T050 [US1] 新建 `controller/VerificationCodeController.java`：`POST /api/v1/verification-codes`，只做校验 + 调 Service，返回 `Result<VerificationCodeSendResponse>`
- [x] T051 [US1] 新建 `controller/PatientController.java`：`POST /api/v1/patients`（后续 US3 在本类追加端点 8）
- [x] T052 [US1] 扩展 `web/src/api/request/create-request.ts` 的 `RequestConfig` 增加 `withCredentials?: boolean` 并在 `axios.create` 透传，`web/src/api/request/patient-request.ts` 置 `true`（`contracts/README.md` 第 5 节要求，否则跨域部署下 T038 断言的 `Set-Cookie` 无法下发）；同时扩展 `web/src/api/patient-identity.ts`：封装端点 1/2，具体路径写 `/v1/...`（不含 `/api`），复用 `patientRequest`。该配置是 US1 注册自动登录的前置，不得延后到 US2
- [x] T053 [P] [US1] 新建 `web/src/components/verification-code-input.vue` 与 `agreement-consent.vue`：验证码输入 + 获取按钮倒计时；协议勾选与版本展示，`<style scoped>`、≤ 300 行
- [x] T054 [US1] 新建 `web/src/composables/use-verification-code.ts`：调用端点 1、维护倒计时与错误映射，禁止读取响应中的验证码
- [x] T055 [US1] 新建 `web/src/stores/patient.ts`：`authStatus`、`consultationAllowed`、`accessToken`、`maskedPhone`，创建时从 `localStorage` 恢复
- [x] T056 [US1] 新建 `web/src/views/patient-register-view.vue`：手机号、验证码、密码、协议勾选；成功后写 store 并跳转 `/patient/profile`
- [x] T057 [US1] 在 `web/src/router/index.ts` 注册 `/patient/register`（`name: 'patient-register'`，匿名可访问），并扩展 `guards.ts` 支持 `requiresAuth`/`requiresVerified` 元信息（本阶段仅注册 `requiresAuth` 判定逻辑）

**检查点**：`mvn -B test` 中 US1 用例全绿；`npm run test` + `npm run test:e2e -- patient-register` 通过；手测可注册并进入 `/patient/profile`。此时可独立演示 MVP。

---

## Phase 4: 用户故事 2 - 手机号密码登录与安全退出（优先级：P1）

**目标**：登录成功建立会话（access 7200 s + refresh 604800 s）、失败统一模糊提示、安全退出后旧凭证立即失效、续期凭证一次轮换。
**独立测试**：用 US1 或夹具预置的账户验证登录、失败提示一致、退出后旧 token 访问端点 8 返回 401、并发轮换只成功一次；不依赖 US3/US4。

**对应需求**：FR-007 ~ FR-009、FR-026、SC-007；端点 3/4/5；mock M6–M10。

### 测试先行（必须先失败）⚠️

- [x] T058 [P] [US2] 新建 `server/src/test/java/com/sdas/server/identity/contract/PatientLoginContractTest.java`：端点 3 正向与异常（手机号/密码格式、统一 `AUTHENTICATION_FAILED`、CSRF、503），断言响应头下发两个 Cookie
- [x] T059 [P] [US2] 新建 `server/src/test/java/com/sdas/server/identity/contract/PatientLogoutContractTest.java`：端点 4 幂等（重复登出仍 200）、未带凭证 401、CSRF 403、响应头清除 Cookie（`Max-Age=0`）
- [x] T060 [P] [US2] 新建 `server/src/test/java/com/sdas/server/identity/contract/RefreshTokenContractTest.java`：端点 5 轮换成功、旧 refresh 复用返回 `AUTHENTICATION_FAILED`、未带 Cookie 返回 401、CSRF 403
- [x] T061 [P] [US2] 新建 `server/src/test/java/com/sdas/server/identity/integration/SessionFlowIT.java`：账号不存在/密码错误/账户停用三种失败 `message` 逐字相同；登录失败达阈值后仍返回同一文案且不锁号；登出后原 access token 访问端点 8 立即 401；并发轮换仅一个成功；重置密码后全部会话撤销（为 US4 预留断言）
- [x] T062 [P] [US2] 新建 `server/src/test/java/com/sdas/server/identity/unit/PatientSessionServiceTest.java`：refresh token 只存摘要、轮换原子性、乐观锁冲突重试一次后失败
- [x] T063 [P] [US2] 新建 `web/tests/unit/use-patient-session.spec.ts`：登录写入 token、退出清理 store 与 `localStorage`、401 触发续期且并发请求只续期一次
- [x] T064 [P] [US2] 新建 `web/tests/unit/patient-profile-view.spec.ts`：已认证/未认证两种状态卡渲染，退出后跳转登录页
- [x] T065 [P] [US2] 新建 `web/tests/e2e/patient-session.mock.spec.ts`：注入 M6–M10，覆盖登录成功、统一模糊提示、退出、续期成功、旧凭证复用失败
- [x] T066 [P] [US2] 新建 `web/tests/e2e/patient-session.real.spec.ts`：真实后端登录 → 退出 → 用旧 access token 访问端点 8 断言 401；刷新页面后仍保持会话

### 实现

- [x] T067 [US2] 新建 `server/src/main/java/com/sdas/server/identity/dto/` 下 `PatientLoginRequest`、`PatientLoginResponse`、`RefreshTokenResponse`：字段与 `contracts/` 端点 3/5 一致，响应禁止含 `refreshToken` 明文字段
- [x] T068 [US2] 新建 `service/PatientSessionService.java` + `impl/PatientSessionServiceImpl.java`：登录（BCrypt 校验 + 失败计数 + 统一异常）、签发 access/refresh、轮换（旧 `ACTIVE → REVOKED` + 新建，`uk_refresh_token_hash` 冲突即失败）、登出（当前会话撤销 + 写 `session:revoked:{sessionId}`）
- [x] T069 [US2] 新建 `controller/PatientSessionController.java`：`POST /api/v1/patient-sessions`、`DELETE /api/v1/patient-sessions/current`、`PUT /api/v1/patient-sessions/current/refresh-token` 三端点，只做校验 + 调用 Service
- [x] T070 [US2] 扩展 `web/src/api/patient-identity.ts`：封装端点 3/4/5；`DELETE`/`PUT` 自动附带 `X-CSRF-Token`（取自 `sdas_csrf` Cookie）
- [x] T071 [US2] 扩展 `web/src/api/request/create-request.ts`：`RequestConfig` 增加可选 `onUnauthorized?: (response: AxiosResponse) => Promise<unknown>` 回调，并**新建响应拦截器**（当前文件只有请求拦截器），在 `onFulfilled` 中判断 `response.status === 401` 时调用该回调、无回调则原样返回；**`validateStatus` 允许 200–599，401 不会进入 `onRejected`**。`web/src/api/request/patient-request.ts` 注入患者专属实现：复用 T052 的 `withCredentials: true` 调端点 5 续期、重放原请求（加标记防二次触发）、实现单飞（同一时刻只允许一次续期），续期失败清理会话并跳转 `/patient/login`。工厂内禁止写死患者端点、禁止按角色 if-else（宪法 II：不同角色独立实例；`create-request.ts` 文件头：工厂只按传入配置工作，不做角色判断）
- [x] T072 [US2] 新建 `web/src/composables/use-patient-session.ts`：登录、退出、续期、`hasSession` 计算属性
- [x] T073 [US2] 新建 `web/src/views/patient-login-view.vue`：手机号 + 密码，失败统一展示 `AUTHENTICATION_FAILED` 文案，成功后进 `/patient/profile`
- [x] T074 [US2] 新建 `web/src/views/patient-profile-view.vue`：脱敏手机号、认证状态卡、安全退出按钮、「去实名认证」入口（未认证时）
- [x] T075 [US2] 新建 `web/src/components/identity-status-card.vue`：展示 `authStatus`/`consultationAllowed`/`maskedName`/`maskedIdCard`，未认证时显示引导
- [x] T076 [US2] 在 `web/src/router/index.ts` 注册 `/patient/login`（匿名）与 `/patient/profile`（`meta.requiresAuth: true`），`guards.ts` 未登录统一跳 `/patient/login`

**检查点**：US1 与 US2 用例全绿；登录 → 个人中心 → 退出 → 旧 token 401 全链路通过；两故事互不破坏。

---

## Phase 5: 用户故事 3 - 实名认证并解锁线上问诊（优先级：P1）

**目标**：姓名 + 身份证号与白名单精确比对，命中且证件未被占用 → `VERIFIED` + `consultationAllowed=true`；未命中返回统一 `AUTHENTICATION_FAILED`；未认证账户访问门禁入口时由 `requiresVerified` 守卫按端点 8 的 `consultationAllowed=false` 重定向至认证页（后端 `IDENTITY_NOT_VERIFIED(403)` 由后续问诊模块返回，本功能只提供判定依据）。
**独立测试**：夹具动态生成未认证账户与白名单记录，验证命中通过、未命中拒绝、重复提交幂等、并发绑定仅一次成功、门禁拦截；不依赖 US4。

**对应需求**：FR-011 ~ FR-016、FR-019、FR-022；端点 7（`POST /api/v1/identity-verifications`）、端点 8（`GET /api/v1/patients/current/identity-status`）；mock M12–M14。

### 测试先行（必须先失败）⚠️

- [x] T077 [P] [US3] 新建 `server/src/test/java/com/sdas/server/identity/contract/IdentityVerificationContractTest.java`：端点 7 正向字段（`authStatus`/`consultationAllowed`/`verifiedAt`/`maskedName`/`maskedIdCard`）与异常（`INVALID_NAME_FORMAT`/`INVALID_ID_CARD_FORMAT`/`AUTHENTICATION_FAILED`/`UNAUTHORIZED`/`CSRF_TOKEN_MISMATCH`/`RATE_LIMITED`/`SERVICE_UNAVAILABLE`），断言不返回白名单命中信息；**端点 7 不返回 `IDENTITY_NOT_VERIFIED`**（该码由后续问诊模块消费端点 8 结果后返回，不在本功能响应内）
- [x] T078 [P] [US3] 新建 `server/src/test/java/com/sdas/server/identity/contract/IdentityStatusContractTest.java`：端点 8 未认证（三个 `null` 字段仍须存在）与已认证两种响应，401 与 503 异常，`consultationAllowed` 与 `authStatus` 严格联动
- [x] T079 [P] [US3] 新建 `server/src/test/java/com/sdas/server/identity/integration/IdentityVerificationFlowIT.java`：白名单命中、姓名不匹配、证件号未命中、证件号已被他人绑定四种情况的 `code` 与 `message` 完全一致；已认证重复提交返回 200 且不新增 `patient_identity` 记录；同一证件号并发绑定仅一个成功；15 分钟窗口限流生效；MySQL 不可用返回 503 且状态保持 `UNVERIFIED`
- [x] T080 [P] [US3] 新建 `server/src/test/java/com/sdas/server/identity/unit/IdentityVerificationServiceTest.java`：校验位算法（GB 11643）、`uk_id_card_hash` 冲突映射、事务回滚、`authStatus`/`consultationAllowed` 联动判定（门禁 403 由后续问诊模块消费端点 8 结果后返回，本 Service 不产出）
- [x] T081 [P] [US3] 新建 `web/tests/unit/use-identity-verification.spec.ts`：提交成功更新 store、失败仅展示统一文案、不展示可枚举原因
- [x] T082 [P] [US3] 新建 `web/tests/unit/patient-router-guard.spec.ts`：以 `/consultation/entry` 为受保护示例路由，断言 `requiresVerified` 在 `consultationAllowed=false` 或 `authStatus!=='VERIFIED'` 时重定向 `/patient/identity-verification`，已认证放行
- [x] T083 [P] [US3] 新建 `web/tests/e2e/patient-identity.mock.spec.ts`：注入 M12–M14，覆盖认证成功、认证未通过（文案与登录失败一致）、未认证状态卡
- [x] T084 [P] [US3] 新建 `web/tests/e2e/patient-identity.real.spec.ts`：夹具动态插入白名单记录 → 认证成功 → 端点 8 返回 `VERIFIED` → 访问占位门禁入口 `/consultation/entry` 可进入；未认证账户访问该入口被重定向至 `/patient/identity-verification`（SC-003 的「放行次数为 0」由此断言）；未命中用例断言仍为 `UNVERIFIED`

### 实现

- [x] T085 [US3] 新建 `server/src/main/java/com/sdas/server/identity/dto/IdentityVerificationRequest.java`、`IdentityVerificationResponse.java`、`IdentityStatusResponse.java`：字段与 `contracts/` 端点 7/8 一一对应，禁止出现 `idCardHash`/`idCardCipher`/`realNameCipher`
- [x] T086 [US3] 新建 `service/IdentityVerificationService.java` + `impl/IdentityVerificationServiceImpl.java`：格式校验（姓名 1-50 字符、证件号 18 位 + GB 11643 校验位）→ 白名单精确查询 → `patient_identity` 插入 + `patient_account.auth_status` 更新 + 审计，同一 `@Transactional`；未命中只写 `IDENTITY_REJECTED`
- [x] T087 [US3] 新建 `controller/IdentityVerificationController.java`：`POST /api/v1/identity-verifications`，身份取自 `PatientIdExtractor`
- [x] T088 [US3] 在 `controller/PatientController.java` 追加 `GET /api/v1/patients/current/identity-status`，返回 `IdentityStatusResponse`
- [x] T089 [US3] 扩展 `web/src/api/patient-identity.ts`：封装端点 7/8，`POST` 自动附带 CSRF 头
- [x] T090 [US3] 新建 `web/src/composables/use-identity-verification.ts`：提交认证、拉取状态、成功后同步 `patient` store
- [x] T091 [US3] 新建 `web/src/views/patient-identity-verification-view.vue`：姓名 + 身份证号表单、脱敏展示已认证信息、失败统一文案提示，成功后跳 `/patient/profile`
- [x] T092 [US3] 在 `web/src/router/index.ts` 注册 `/patient/identity-verification`（`meta.requiresAuth: true`）与**最小占位门禁入口** `/consultation/entry`（`meta.requiresAuth: true, meta.requiresVerified: true`）；新建 `web/src/views/consultation-entry-view.vue`：仅展示门禁状态与「去实名认证」引导（已认证时展示「线上问诊建设中」占位文案），**不调用任何问诊 API、不实现问诊业务**，用于使 SC-003 可被 E2E 验证；在 `guards.ts` 实现 `requiresVerified`：`authStatus !== 'VERIFIED'` 或 `consultationAllowed === false` 时重定向至认证页，后续问诊页面统一复用该守卫

**检查点**：三个 P1 故事全部可用；未认证账户在任何入口均无法发起问诊（SC-003）；认证状态在个人中心与端点 8 一致。

---

## Phase 6: 用户故事 4 - 忘记密码并通过验证码重置（优先级：P2）

**目标**：手机号 + `RESET_PASSWORD` 用途验证码 + 合规新密码 → 更新密码、撤销该账户全部会话、旧密码立即失效。
**独立测试**：用夹具预置账户验证重置成功、旧密码登录失败、新密码登录成功、原 access token 401、验证码一次性与用途隔离；不依赖 US3。

**对应需求**：FR-010、FR-017、FR-025；端点 6（`POST /api/v1/password-resets`）；mock M11。

### 测试先行（必须先失败）⚠️

- [x] T093 [P] [US4] 新建 `server/src/test/java/com/sdas/server/identity/contract/PasswordResetContractTest.java`：端点 6 正向（`data=null`）与异常（`INVALID_PHONE_FORMAT`/`INVALID_VERIFICATION_CODE_FORMAT`/`INVALID_PASSWORD_FORMAT`/`AUTHENTICATION_FAILED`/`CSRF_TOKEN_MISMATCH`/`SERVICE_UNAVAILABLE`）
- [x] T094 [P] [US4] 新建 `server/src/test/java/com/sdas/server/identity/integration/PasswordResetFlowIT.java`：重置成功后旧密码登录失败、新密码登录成功、该账户全部 `patient_session` 置 `REVOKED`、原有 access token 访问端点 8 返回 401、`RESET_PASSWORD` 验证码不可用于注册、未注册手机号与验证码错误文案一致、MySQL 失败时密码与会话均不变
- [x] T095 [P] [US4] 新建 `server/src/test/java/com/sdas/server/identity/unit/PasswordResetServiceTest.java`：事务边界、密码规则二次校验、验证码用途隔离
- [x] T096 [P] [US4] 新建 `web/tests/unit/patient-password-reset-view.spec.ts`：表单校验、成功提示不暴露账号是否存在
- [x] T097 [P] [US4] 新建 `web/tests/e2e/patient-password-reset.mock.spec.ts`：注入 M1/M2/M11，覆盖发送验证码、超限、重置成功
- [x] T098 [P] [US4] 新建 `web/tests/e2e/patient-password-reset.real.spec.ts`：真实后端重置后旧密码登录失败、新密码登录成功

### 实现

- [x] T099 [US4] 新建 `server/src/main/java/com/sdas/server/identity/dto/PasswordResetRequest.java`：`phone`、`verificationCode`、`newPassword` 三字段与 `contracts/` 端点 6 一致
- [x] T100 [US4] 新建 `service/PasswordResetService.java` + `impl/PasswordResetServiceImpl.java`：验证码消费 → 更新 `password_hash` → 撤销全部会话 → 清理 `RESET_PASSWORD` 验证码 → 写 `PASSWORD_RESET` 审计，同一 `@Transactional`
- [x] T101 [US4] 新建 `controller/PasswordResetController.java`：`POST /api/v1/password-resets`，返回 `Result.success()`
- [x] T102 [US4] 扩展 `web/src/api/patient-identity.ts`：封装端点 6，`POST` 附带 CSRF 头
- [x] T103 [US4] 新建 `web/src/views/patient-password-reset-view.vue`：手机号 + 验证码 + 新密码，复用 `use-verification-code`（`purpose=RESET_PASSWORD`），成功后跳 `/patient/login`
- [x] T104 [US4] 在 `web/src/router/index.ts` 注册 `/patient/password-reset`（匿名可访问）

**检查点**：4 个用户故事全部独立可测；重置后旧凭证链路彻底失效（SC-007）。

---

## Phase 7: 收尾与跨故事事项

**目的**：跨故事的测试、静态检查、契约一致性与交付物收口。

- [x] T105 [P] 补齐后端单测覆盖至 ≥ 70%：`server/src/test/java/com/sdas/server/identity/unit/` 下为 `CryptoService`、`MaskService`、`VerificationCodeService`、`PatientAccountService`、`PatientSessionService`、`IdentityVerificationService`、`PasswordResetService`、`RateLimitService`、`IdempotencyService` 逐项补分支
- [x] T106 [P] 补齐前端单测覆盖：`web/tests/unit/` 下为 3 个 composable（`use-verification-code`/`use-patient-session`/`use-identity-verification`）、1 个 store（`patient`）、6 个页面视图（注册/登录/个人中心/实名认证/重置密码/门禁占位入口）与 3 个公共组件（`verification-code-input`/`agreement-consent`/`identity-status-card`）补充分支用例
- [x] T107 [P] 全量 Playwright Mock E2E：`web/tests/e2e/` 覆盖 4 个用户故事成功/失败路径，`npm run test:e2e` 零失败
- [x] T108 全量真实后端 E2E：`$env:E2E_REAL_BACKEND='1'; npm run test:e2e`，全部断言响应 `code`，且不使用预置演示白名单记录
- [x] T109 [P] 静态检查零错误零警告：`mvn -B verify`（Checkstyle + SpotBugs）、`npm run lint`、`npm run format:check`、`npm run typecheck`
- [x] T110 [P] 按 `quickstart.md` 第 6 节逐条手工验证（注册 / 登录退出 / 实名认证 / 忘记密码 / 6.5 门禁占位入口），记录未认证账户成功进入 `/consultation/entry` 的次数为 0，并覆盖第 8 节常见问题表
- [x] T111 执行 `contracts/README.md` 第 6 节 10 项契约一致性验证：端点、`code` 取值、字段结构、日期格式、敏感字段禁列与真实后端响应逐项比对，结果写入 `testsdoc/patient-identity/contract-conformance.md`
- [x] T112 [P] 测试报告归档：`testsdoc/patient-identity/` 下按 `[测试名].md` 归档单测、集成测试、Mock E2E、真实后端 E2E 报告
- [x] T113 代码审查（宪法原则 V）：检查分层越界、`@Enumerated` 与 `@Converter` 并存、硬编码文案、事务边界、`System.out.println`
- [x] T114 安全检查：日志与响应抽样确认不含密码明文、验证码明文、完整手机号、完整身份证号、JWT 全文；确认 `prod` 下 `fixed-enabled=false` 且启动 fail-fast 生效

---

## Dependencies & Execution Order

### 阶段依赖

- **Phase 1 Setup**：无依赖，可立即开始。
- **Phase 2 Foundational**：依赖 Setup 完成，**阻塞全部用户故事**（T012–T036 未完成前不得开始 T037 之后的任务）。
- **Phase 3 US1（P1）**：依赖 Foundational；无其他故事依赖。
- **Phase 4 US2（P1）**：依赖 Foundational；登录复用 US1 的 `patient_account` 表与 Cookie 基础设施，但不要求 US1 的端点实现完成（夹具可预置账户）。
- **Phase 5 US3（P1）**：依赖 Foundational；需要 US2 的 `PatientIdExtractor` 会话校验生效（T033 已在 Foundational 完成）。
- **Phase 6 US4（P2）**：依赖 Foundational；复用 US1 的验证码存储（T030/T048）与 US2 的会话撤销（T068）。
- **Phase 7 Polish**：依赖所需故事全部完成。

### 故事间依赖

| 故事 | 依赖 | 说明 |
|------|------|------|
| US1 注册 | 无（仅 Foundational） | 端点 1 的验证码服务被 US4 复用 |
| US2 登录/退出/续期 | 无（仅 Foundational） | 会话撤销被 US4 复用 |
| US3 实名认证 | Foundational（含 `PatientIdExtractor`） | 不依赖 US1/US2 的端点，夹具可预置账户与会话 |
| US4 忘记密码 | US1 的验证码存储、US2 的会话撤销 | 属 P2，最后交付 |

### 故事内部顺序

1. 测试任务先行，且必须先运行至失败（宪法原则 VI）。
2. DTO → Service → Controller。
3. 前端 API 封装 → composable/store → 组件 → 页面 → 路由。
4. 单个故事通过检查点后再进入下一优先级。

### 并行机会

- Phase 1 全部 `[P]` 任务可并行（T002–T011）。
- Phase 2 中枚举、加密、脱敏、JWT、Cookie/CSRF、日志、迁移脚本、6 个 Repository 均为不同文件，可并行（T013–T028）。
- Foundational 完成后，US1/US2/US3 可按人力并行开发（不同开发者各领一个故事，测试文件与源码文件不重叠）。
- 每个故事内标记 `[P]` 的测试任务可同时启动。

### 并行示例（US1）

```text
Task: "T037 [P] [US1] 端点 1 契约测试 VerificationCodeContractTest.java"
Task: "T038 [P] [US1] 端点 2 契约测试 PatientRegisterContractTest.java"
Task: "T039 [P] [US1] 注册流程集成测试 RegisterFlowIT.java"
Task: "T045 [P] [US1] Mock E2E patient-register.mock.spec.ts"
Task: "T053 [P] [US1] 表单组件 verification-code-input.vue / agreement-consent.vue"
```

---

## Implementation Strategy

### MVP 优先（仅 US1）

1. Phase 1 Setup → Phase 2 Foundational（阻塞项，不可跳过）。
2. Phase 3 US1 → 通过检查点 → 独立演示「注册并自动登录」。
3. 此时可交付最小可用切片，不依赖后续故事。

### 增量交付

1. Setup + Foundational → 地基就绪。
2. US1 → 独立验证 → 演示（MVP）。
3. US2 → 独立验证 → 演示（账户可进可退）。
4. US3 → 独立验证 → 演示（问诊门禁生效）。
5. US4 → 独立验证 → 演示（自助找回）。
6. Phase 7 收尾，一次性完成静态检查、契约一致性与报告归档。

### 多人力并行

1. 全员共同完成 Setup + Foundational（此阶段不可拆分给单人）。
2. Foundational 完成后：A 负责 US1，B 负责 US2，C 负责 US3，US4 由任一成员在 US1/US2 完成后接手。
3. 每人先写自己故事的全部测试，跑至失败，再实现。

---

## 需求覆盖映射

| 需求 | 对应任务 | 需求 | 对应任务 |
|------|----------|------|----------|
| FR-001 | T047–T051、T054–T057 | FR-014 | T079、T086 |
| FR-002 | T038、T039、T041、T049 | FR-015 | T078、T082、T084、T092 |
| FR-003 | T030、T040、T048 | FR-016 | T078、T088 |
| FR-004 | T041、T044、T049 | FR-017 | T032、T039、T079、T094 |
| FR-005 | T047、T049、T053 | FR-018 | T020、T029、T086、T114 |
| FR-006 | T049、T055、T056 | FR-019 | T077、T079 |
| FR-007 | T058、T061、T068、T071 | FR-020 | T034、T079 |
| FR-008 | T067–T069、T071 | FR-021 | T016、T020、T074、T075、T091、T114 |
| FR-009 | T059、T068、T074 | FR-022 | T013、T086、T088 |
| FR-010 | T099–T101、T103 | FR-023 | T031、T061、T079 |
| FR-011 | T085、T086 | FR-024 | T039、T049、T079、T094 |
| FR-012 | T047、T080、T085、T086 | FR-025 | T047、T080、T086、T099 |
| FR-013 | T027、T086 | FR-026 | T060、T061、T068 |

| 成功标准 | 对应任务 |
|----------|----------|
| SC-001 | T056、T110（度量方式：T110 手工计时记录「注册 → 进入个人中心」10 次，取中位数写入 `testsdoc/patient-identity/`，无中位数则判定未达成） |
| SC-002 | T039、T108 |
| SC-003 | T082、T084、T092、T110（占位入口 `/consultation/entry` 为唯一验证载体） |
| SC-004 | T084、T108 |
| SC-005 | T038、T061、T079、T094 |
| SC-006 | T020、T114 |
| SC-007 | T059、T061、T094 |
| SC-008 | T039、T061、T079 |
| SC-009 | T037、T038、T058、T059、T060、T077、T078、T093 的异常用例、T108 |
| SC-010 | T039、T079、T094、T108 |

**需求缺口检查**：26 条 FR 与 10 条 SC 均有任务覆盖，无遗漏项。

---

## Notes

- `[P]` = 不同文件且无未完成依赖，可并行。
- `[US*]` 标签用于追溯与增量交付，跨故事任务已在「故事间依赖」说明。
- 每个故事必须先让测试失败，再实现，最后通过检查点才可进入下一优先级。
- Java 存在编译期依赖：测试先行时先创建 Service 接口、DTO 与 Controller 方法签名（方法体抛 `UnsupportedOperationException`），保证测试可编译运行并因断言失败而「红」，避免用编译错误代替失败。
- 禁止修改 `specs/001-patient-identity/checklists/requirements.md` 的勾选状态（审查方产物）。
- 禁止在实现阶段引入 `plan.md`「架构评审记录」之外的第三方依赖。
- 禁止把 `data-model.md`/`contracts/` 的枚举、`code`、`message` 复制到代码后再各自演化：代码只允许引用，变更须先改文档。
- 前端只镜像 `code` 数值，`message` 一律展示后端响应值；`error-codes.ts` 不复制业务文案。
- `/consultation/entry` 是**门禁占位入口**，仅用于验证「未认证不得发起问诊」（SC-003），不实现任何问诊功能；后续问诊页面须复用同一 `requiresVerified` 守卫。
- 演示白名单种子位于 `db/seed/`，仅 dev 加载；`test`/`prod` 的 Flyway locations 只含 `db/migration`，测试数据一律由夹具动态写入。
- 每完成一个逻辑分组提交一次，提交信息引用任务编号。

