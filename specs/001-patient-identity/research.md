# Phase 0 研究：患者注册与实名认证

**功能**：`001-patient-identity` | **日期**：2026-11-07
**输入**：`spec.md`、`plan.md`、`.specify/memory/constitution.md` v1.0.0、`server/pom.xml`、`web/package.json`
**目标**：在 Phase 1 设计前闭环所有技术未知项，特别是 `/speckit.plan` 的两项用户决策（D1 固定验证码、D2 数据库预置白名单），以及宪法「解释与待决项」要求在本阶段明确的两项（AI 调用形态、Redis 具体版本）。

**结论**：无 `NEEDS CLARIFICATION` 残留；G1–G6 门禁在 Phase 0 前全部通过（见 `plan.md`）。

---

## R-01 验证码方案：固定值，不接第三方短信服务（D1）

**决策**：验证码**不真正发送**。dev/test 使用固定验证码（默认 `888888`，`SDAS_FIXED_CODE` 可覆盖），由 `POST /api/v1/verification-codes` 写入 Redis 生命周期记录后返回；响应与日志均不回传验证码明文。

**关键边界（不可简化）**：固定值只替换「生成 + 发送」两个环节，**不替换**校验链路。以下能力全部保留，否则 `spec.md` 的 FR-003 / FR-017 / FR-023 / FR-026、SC-005 / SC-008 将不可测：

| 保留能力 | 实现位置 | 对应需求 |
|----------|----------|----------|
| 5 分钟有效期（300 s） | Redis TTL | FR-003 |
| 一次性使用（`ACTIVE → USED`） | Redis 状态 + 原子消费 | FR-003、FR-017 |
| 用途隔离（`REGISTER` / `RESET_PASSWORD`） | key 前缀 + 请求 `purpose` 参与校验 | FR-003 |
| 60 s 发送间隔、单日 10 次 | Redis 计数器 | FR-003、FR-023 |
| 验证码摘要存储，禁止明文入库/入日志 | HMAC-SHA256 摘要 + `LogMasker` | 原则 IV、FR-018 |
| 失败统一模糊提示 | `AUTHENTICATION_FAILED` | FR-002、FR-007、SC-005 |

**存储**：Redis key `sdas:identity:code:{purpose}:{HMAC(phone)}`（不对完整手机号建 key），value 为 `{ codeDigest, status, issuedAt, attempts, consumedAt }`，TTL = 300 s。校验时以常量时间比较摘要，成功后原子置为 `USED`；并发消费只允许一个成功。

**生产边界（fail-fast）**：`sdas.identity.verification-code.fixed-enabled` 仅在 `dev` / `test` profile 允许为 `true`；`prod` profile 若为 `true`，应用启动时抛 `IllegalStateException` 拒绝启动。由于本阶段按 D1 **未实现任何短信发送通道**，`prod` 目前没有可用的验证码下发实现 —— 这是明确的后续迭代前置条件，不视为本功能的隐藏缺陷：后续接入短信时只需替换「生成 + 发送」实现，校验链路、契约、错误码、前端页面与测试用例均不变。

**备选与否决**：

| 备选 | 否决理由 |
|------|----------|
| 全局无条件固定验证码（含 prod） | 任何人可用 `888888` 注册 / 重置任意账户，直接破坏账户安全，违反原则 IV |
| 把固定值直接返回给前端（`data.code`） | 等同把认证凭据暴露在响应与浏览器日志，违反原则 IV 与「审计日志禁止记录验证码明文」 |
| 取消 Redis，直接比对常量 | 失去一次性、有效期、用途隔离、频次限制，FR-003 / FR-017 / SC-008 不可测 |
| 接入第三方短信 SDK | 与 D1「暂不接入第三方验证码服务」冲突，且引入须架构评审的新依赖 |

**验证方式**：Vitest 校验前端不读取 `data.code`；后端集成测试覆盖「过期 / 错误 / 重放 / 跨用途 / 超频」；Playwright Mock E2E 使用 `contracts/mocks.md` 的 M1/M2/M4；真实后端 E2E 从 `SDAS_FIXED_CODE` 读取固定值并断言响应 `code`。

---

## R-02 医院白名单方案：数据库预置，不接外部同步（D2）

**决策**：白名单为 MySQL 表 `hospital_identity_whitelist`，由 Flyway 迁移建表并提供受控预置数据；本功能以 `real_name + id_card_hash` 精确查询，只读使用。不实现同步接口、定时任务、HTTP 客户端或外部系统适配器。

**数据结构要点**：身份证号不以明文存储，保存 `id_card_cipher`（AES-GCM 密文）与 `id_card_hash`（HMAC-SHA256 摘要）；`uk_id_card_hash` 唯一索引既保证比对性能，也保证「一个身份证号只能有一份白名单记录」。`real_name` 精确等值匹配（不做模糊匹配，避免误放行）。详细字段见 `data-model.md` 3.6。

**「预置数据」与「动态测试数据」的冲突消解**：宪法原则 VI 要求真实后端 E2E 使用动态数据、禁止硬编码真实身份信息；D2 要求使用数据库预置数据。结论：

1. Flyway `V1__identity_schema.sql` 建表；`V2__identity_whitelist_seed.sql` 只提供**合成演示数据**（虚构姓名 + 按校验位算法构造的占位身份证号，且明确标注禁止用于生产），供 dev 环境手工演示。
2. 生产白名单由 DBA 通过受控 SQL 导入同一张表，属运维动作，不属于本功能代码范围；`prod` 启动时不自动写入演示种子（`V2` 通过 `spring.flyway.locations` 的 dev/test 专属位置或种子脚本内的显式标注区分）。
3. 集成测试 / 真实后端 E2E 通过**测试夹具动态生成**随机手机号与符合校验位算法的身份证号，向同一张表 `INSERT` 后使用，用例结束回滚。测试库与生产共用 `V1` 结构脚本，禁止在 `application-test.yml` 设置 `spring.flyway.enabled=false` 绕过迁移。

**备选与否决**：

| 备选 | 否决理由 |
|------|----------|
| 调用医院侧接口实时同步 / 定时拉取 | 与 D2 冲突；且会重新引入 FR-020 / SC-010 原本针对「外部依赖超时」的复杂度 |
| E2E 直接复用预置演示记录 | 静态数据被并发用例污染，且存在硬编码身份信息的合规风险，违反原则 VI |
| 白名单放 Redis 或配置文件 | 失去事务一致性与唯一约束，且无法与账户表的实名认证事务编排 |
| 明文存储身份证号用于比对 | 违反原则 IV 脱敏要求 |

**失败语义变化**：原 `spec.md` 中的「白名单接口超时」「外部短信通道不可用」不再是外部依赖故障，收敛为**内部依赖**（MySQL / Redis）不可用或超时。FR-020 / FR-024、SC-010 的断言不变（不创建半成品账户、不误标记「已认证」、不写入失败认证记录、可重试），统一降级错误码改为 `SERVICE_UNAVAILABLE(503)`。`spec.md` 7.1、8.1、9.3 已同步修订。

---

## R-03 宪法待决项闭环：Redis 具体版本

宪法原则 I 原文为「Redis 3.2.1x」，要求 `plan.md` Technical Context 写明落地版本。**结论**：本项目落地 `Redis 3.2.100`（3.2 协议族），由 `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` 注入；服务端使用 Spring Boot 3.4.5 `spring-boot-starter-data-redis` + Lettuce + `commons-pool2`（`RedisConfig` 已存在），测试环境固定 `database: 1`（`application-test.yml` 已配置）。不使用 3.2 之后才引入的特性（如 Streams、Redis 6 ACL），确保与宪法版本表述一致。若后续升级 Redis 主版本，须走宪法修订流程。

---

## R-04 宪法待决项闭环：AI 模型调用形态

宪法「解释与待决项」指出原文同时含「DeepSeek 官方 API」与「本地部署」，要求在本阶段明确最终形态，且不得同时实现两套调用链。**结论**：采用**DeepSeek 官方 API（OpenAI 协议兼容）**，不实现本地部署链路。依据现有 `server/pom.xml` 的 `spring-ai-starter-model-openai` 与 `application.yml` 的 `base-url: https://api.deepseek.com`、`api-key: ${DEEPSEEK_API_KEY:xxx}`、`model: deepseek-v4-flash`；密钥只经环境变量注入，禁止入库。本功能（患者身份域）**不发起任何 AI 调用**，故不新增 Spring AI 依赖、不新增配置；该结论仅用于关闭宪法待决项，供后续问诊功能沿用。

---

## R-05 新增第三方依赖架构评审

`plan.md`「架构评审记录」已逐项评审并批准：`spring-security-crypto`（BCrypt ≥ 10）、`jjwt-* 0.12.x`（JWT）、`flyway-core` + `flyway-mysql`（迁移与预置数据）、`spring-boot-testcontainers` + Testcontainers MySQL/JUnit（集成测试）、`maven-checkstyle-plugin` + `spotbugs-maven-plugin`（静态检查）。均为宪法原则 I / IV / V / VI 的显式要求，非本功能自行扩张。D1 / D2 使短信 SDK 与白名单同步客户端的依赖数量保持为 0，且**明确不引入消息队列**（原则 I 禁止）。实现阶段若需表外依赖，须先回到 `plan.md` 补评审记录。

---

## R-06 全局 `ErrorCode` 扩展与唯一来源

现有 `server/src/main/java/com/sdas/server/common/ErrorCode.java` 是全局唯一枚举，注释指向 `specs/health-check/data-model.md` 1.2。本功能需新增错误码，但不能造成「同名不同值」或两份互相冲突的枚举表。

**结论**：
1. `specs/001-patient-identity/data-model.md` 1.9 重新列出**完整全局 `ErrorCode` 清单**（既有 8 项取值原样保留，新增 11 项），声明为**最新唯一权威来源**；`specs/health-check/data-model.md` 1.2 作为历史基线保持不变，`ErrorCode.java` 的 Javadoc 在 `/speckit.implement` 同步改为引用本功能 `data-model.md`。
2. 所有 `message` 仍是 `ErrorCode` 枚举常量，`GlobalExceptionHandler` 继续用 `Result.failure(errorCode)`，Controller / Service 禁止硬编码文案。
3. 失败分类（对应 SC-005「文案差异率为 0」）：
   - **鉴权类失败**（验证码错误/过期/重放、手机号已注册、账号不存在、密码错误、白名单未命中、旧凭证复用）→ 全部 `AUTHENTICATION_FAILED(400)`，同一文案「账号或密码错误，或验证信息无效」，不泄漏账户存在性、白名单命中情况、剩余次数。
   - **参数格式类失败**（手机号/密码/验证码/姓名/身份证号/协议未勾选）→ 各自 `INVALID_*_FORMAT` 或 `AGREEMENT_NOT_ACCEPTED`，文案固定且可操作，但不含账户存在性与剩余次数。
   - **频次类失败**（验证码发送、实名认证尝试达上限）→ `RATE_LIMITED(429)`，统一文案，无剩余次数；登录触发频次上限时仍返回 `AUTHENTICATION_FAILED`，避免与「任一失败原因均返回统一模糊提示」（FR-007）冲突。
   - **状态类**（未认证访问问诊门禁）→ `IDENTITY_NOT_VERIFIED(403)`，明确引导至「实名认证」；认证状态属用户自身已知信息，不构成枚举泄漏。
   - **依赖降级**（MySQL / Redis 不可用或超时）→ `SERVICE_UNAVAILABLE(503)`，不泄漏连接串、堆栈与内部实现。

---

## R-07 会话、双重 Cookie 与身份提取

- **JWT**：access token TTL 7200 s（≤ 24 h，原则 IV），载荷仅含 `patientId`、`sessionId`、`iat`、`exp`，不含手机号 / 身份证号；签名算法 HS256，密钥由 `SDAS_JWT_SECRET` 注入。refresh token TTL 604800 s（7 天），以摘要存 `patient_session` 表 + Redis 撤销状态，**一次轮换**（FR-026）：轮换时旧记录置 `REVOKED`、新记录写入，并发轮换靠 `uk_refresh_token_hash` 与乐观锁只允许一个成功。
- **存放**：access token 由响应体 `data.accessToken` 返回，前端 `patientRequest` 从 `localStorage`（`sdas.patient.token`）注入 `Authorization: Bearer <token>`（与现有 `create-request.ts` 一致，符合原则 III）；refresh token 放 `HttpOnly; SameSite=Lax; Path=/api/v1/patient-sessions` Cookie，前端不读取。患者端请求实例须启用 `withCredentials: true`（实现阶段调整 `create-request.ts`）。
- **防 CSRF**：所有 POST / PUT / PATCH / DELETE 请求要求 `SameSite=Lax` + 双重 Cookie —— 服务端下发非 HttpOnly 的 `sdas_csrf` Cookie，前端读值放入 `X-CSRF-Token` 头，服务端常量时间比对；`application.yml` 已设 `server.servlet.session.cookie.same-site: lax`，自定义 CSRF Cookie 需显式 `httpOnly=false`、`secure=${COOKIE_SECURE}`。GET 不需 CSRF 头。
- **身份提取**：统一 `PatientIdExtractor`，从已校验的 JWT 取 `patientId`；Controller 禁止自行解析 token 或硬编码回退值（原则 II）。`GET /api/v1/health` 继续在安全白名单中放行，无需 JWT。
- **登出**：`DELETE /api/v1/patient-sessions/current` 撤销当前会话与续期凭证（Q2 结论：不踢出其他设备），清除 refresh Cookie，Redis 写撤销标记直至 access token 自然过期，避免「登出后旧凭证仍可访问」（SC-007）。
- **重置密码**：成功后撤销该账户全部会话与续期凭证，旧密码立即失效（用户故事 4）。

---

## R-08 测试策略（原则 VI）与动态数据

| 层次 | 工具 | 覆盖范围 |
|------|------|----------|
| 后端单测 | JUnit 5 + Mockito | 各 Service 的正向 / 异常分支，验证码消费、白名单比对、轮换并发、频次限制，覆盖率 ≥ 70% |
| 后端集成测试 | Spring Boot Test + Testcontainers（MySQL 8 + Redis） | 8 个端点全部正向 + 异常；同套 Flyway `V1` 结构脚本；断言 HTTP 状态与 body `code` 一致 |
| 前端单测 | Vitest + @vue/test-utils + jsdom | composables、公共组件、Pinia store 的 `localStorage` 初始化与守卫 |
| Mock E2E | Playwright `page.route()` | 4 个用户故事 + 异常路径；注入 `contracts/mocks.md` 的 M1–M15，禁止手写 mock |
| 真实后端 E2E | `E2E_REAL_BACKEND=1` | 必须断言响应 `code` 值；随机手机号 + 测试夹具动态生成并向 `hospital_identity_whitelist` 写入配对身份；禁止硬编码真实身份信息 |
| 契约一致性验证 | CI / 测试人员，每迭代一次 | 端点暴露、`code` 值、字段结构、日期格式、枚举与 `contracts/` 逐项比对 |

风险项 → 测试用例映射沿用 `spec.md` 8.1（本阶段把「白名单接口超时」改为「白名单查询（MySQL）超时」）。测试报告落 `testsdoc/patient-identity/[测试名].md`。当前环境的 MySQL `root/123456` 实测 `ERROR 1045`、Redis 6379 未开放，真实后端 E2E 需先准备环境；`quickstart.md` 给出前置检查。

---

## R-09 入参格式与服务端二次校验

`spec.md` FR-025 给出语义规则，`contracts/patient-identity-api.md` 给出精确字符集与字段约束，两者必须一致：

| 字段 | 规则 | 失败码 |
|------|------|--------|
| `phone` | 11 位中国大陆手机号，`^1[3-9]\d{9}$`，不含空格 | `INVALID_PHONE_FORMAT` |
| `password` / `newPassword` | 6–20 位，`^[A-Za-z0-9]{6,20}$` 且至少一个字母 + 至少一个数字，不做 trim 后静默修改 | `INVALID_PASSWORD_FORMAT` |
| `verificationCode` | 6 位数字 `^\d{6}$` | `INVALID_VERIFICATION_CODE_FORMAT` |
| `realName` | 1–50 个字符，禁止控制字符 | `INVALID_NAME_FORMAT` |
| `idCardNumber` | 18 位，前 17 位数字 + 校验位（数字或 `X`），校验位按 GB 11643 加权算法验证 | `INVALID_ID_CARD_FORMAT` |
| `agreementAccepted` / `agreementVersion` | 必须为 `true` 且版本与服务端配置一致 | `AGREEMENT_NOT_ACCEPTED` |

Controller 用 Bean Validation 做首轮校验（异常统一映射 `BAD_REQUEST`），Service 对密码、手机号、身份证号、枚举值做二次校验（原则 II），防止绕过前端直调。Service 抛出 `BusinessException(INVALID_*_FORMAT)`，文案仍全部来自 `ErrorCode`。

---

## R-10 幂等、并发与事务

- 注册、实名认证、重置密码、登出支持可选请求头 `X-Idempotency-Key`（UUID）；Redis 以 `sdas:identity:idem:{key}` 记录处理结果，重复提交返回首次结果，不产生重复账户 / 认证记录 / 凭证（FR-017、SC-008）。
- 注册事务：创建 `patient_account` + `patient_agreement_consent` + `security_audit_event` + 建立 `patient_session`，同一 `@Transactional`，注释标明关联表与流转路径（原则 II）。
- 实名认证事务：查询白名单 → 校验 `uk_id_card_hash` 未被占用 → 写 `patient_identity` → 更新 `patient_account.auth_status` / `verified_at` → 写审计事件，同一事务；并发绑定靠唯一约束 + 乐观锁 `version`，只允许一个成功（FR-014）。
- 已认证账户重复提交返回成功且状态不变，不写重复认证记录（用户故事 3 场景 5）。
- 所有多表写入在 Service 层编排，Repository 只做持久化；禁止跨层调用与反射 / 动态代理穿透分层（原则 I、II）。

---

## R-11 枚举持久化方式

原则 V 禁止同一字段同时标 `@Enumerated` 与 `@Converter`。**结论**：本功能所有 MySQL 枚举字段（`auth_status`、`account_status`、`session_status`、`consent_status`、`verification_result`、`event_type`）统一使用显式 `@Converter` 实现类（`@Converter(autoApply = false)`，字段上单独标注），转换器必须处理 `null` 与未知值并降级为安全默认值；不使用 `@Enumerated(EnumType.STRING)`。新增枚举值前须全局搜索 `switch` / `if-else` 确保无遗漏（原则 V）。Redis 中的状态同样按 `data-model.md` 的字符串取值序列化，禁止另起一套数值编码。

---

## 宪法「解释与待决项」闭环表

| 待决项 | 结论 | 依据章节 |
|--------|------|----------|
| AI 模型调用形态（官方 API vs 本地部署） | DeepSeek 官方 API（OpenAI 协议兼容），不实现本地链路；本功能不发起 AI 调用 | R-04 |
| Redis「3.2.1x」具体版本 | Redis 3.2.100，Lettuce + commons-pool2，测试库 database=1 | R-03 |
| 原则 III 隐含前端 TypeScript | 骨架已启用 TypeScript 5.7 + `vue-tsc`，无需修订宪法 | `web/package.json`、`web/env.d.ts` |

## 未决项

无。若后续产品要覆盖 Q1 ~ Q3（`spec.md` 9.1）或接入真实短信 / 外部白名单同步，须走 `/speckit.clarify` 或宪法修订流程，并同步 `plan.md`、本文件、`data-model.md`、`contracts/`、mock 与测试用例。
