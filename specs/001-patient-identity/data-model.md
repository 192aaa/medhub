# 患者注册与实名认证 数据模型（枚举与 ErrorCode 唯一来源）

**功能**：`001-patient-identity`
**依据**：`.specify/memory/constitution.md` v1.0.0（原则 II、III、IV、V、VI）；`specs/001-patient-identity/spec.md`；`plan.md`；`research.md`
**地位**：本文件是**状态枚举与全局 `ErrorCode` 的唯一权威来源**。前后端代码、数据库 DDL、Redis 状态值、`contracts/`、mock 与测试用例引用枚举时须标注本文件路径，禁止各自硬编码、禁止同名不同值。契约字段结构见 `contracts/patient-identity-api.md`，两者不一致即视为缺陷。

> `specs/health-check/data-model.md` 1.2 为全局 `ErrorCode` 的历史基线，其 8 项取值在本文件 1.9 中**原样保留**；`ErrorCode.java` 的 Javadoc 在 `/speckit.implement` 同步改为引用本文件 1.9。

---

## 1. 枚举定义

### 1.1 `AuthStatus`（患者实名认证状态）

| 值 | 含义 | 触发条件 |
|----|------|----------|
| `UNVERIFIED` | 未认证 | 注册成功后的初始值；白名单比对不通过时保持不变 |
| `VERIFIED` | 已认证 | 姓名 + 身份证号与白名单完全一致，且该身份证号未被其他账户绑定 |

单向流转：`UNVERIFIED → VERIFIED`。本功能不提供认证撤销入口。账户表字段、响应字段 `authStatus`、问诊门禁判定共用本枚举（FR-016、FR-022）。

### 1.2 `VerificationPurpose`（验证码用途）

| 值 | 含义 | 使用端点 |
|----|------|----------|
| `REGISTER` | 注册 | `POST /api/v1/patients` |
| `RESET_PASSWORD` | 重置密码 | `POST /api/v1/password-resets` |

用途参与 Redis key 与校验，跨用途复用一律判定失败（FR-003）。

### 1.3 `VerificationCodeStatus`（验证码生命周期状态）

| 值 | 含义 | 触发条件 |
|----|------|----------|
| `ACTIVE` | 已签发未使用 | `POST /api/v1/verification-codes` 成功写入 |
| `USED` | 已消费 | 校验成功后原子置位（一次性，FR-003） |
| `EXPIRED` | 已过期 | TTL 300 s 到期，键被 Redis 清除；仅用于审计语义，不作为可查询状态返回 |
| `REVOKED` | 已作废 | 同一手机号 + 用途重新签发时旧记录作废；重置密码成功后清理 |

状态存于 Redis，**不建 MySQL 表**；验证码值只以 HMAC-SHA256 摘要保存，禁止明文入库与入日志（原则 IV）。`EXPIRED` / `REVOKED` 不得出现在任何 API 响应中。

### 1.4 `AccountStatus`（账户可用状态）

| 值 | 含义 | 触发条件 |
|----|------|----------|
| `ACTIVE` | 正常 | 注册成功 |
| `DISABLED` | 已停用 | 本功能不提供停用入口，仅保留取值供后续运营功能使用 |

`DISABLED` 账户登录失败仍返回 `AUTHENTICATION_FAILED`，不泄漏账户存在性（FR-007、SC-005）。

### 1.5 `SessionStatus`（会话状态）

| 值 | 含义 | 触发条件 |
|----|------|----------|
| `ACTIVE` | 有效 | 登录、注册自动登录、续期凭证轮换后新建 |
| `REVOKED` | 已撤销 | 登出、续期凭证轮换（旧记录）、重置密码成功后全部会话 |
| `EXPIRED` | 已过期 | 超过续期凭证 TTL（604800 s） |

### 1.6 `AgreementConsentStatus`（协议同意状态）

| 值 | 含义 | 触发条件 |
|----|------|----------|
| `AGREED` | 已同意 | 注册时 `agreementAccepted=true` 且 `agreementVersion` 与服务端配置一致 |

协议版本更新后的重新同意流程属范围外（`spec.md` 7.1），本功能只记录注册时的一次同意。

### 1.7 `IdentityVerificationResult`（实名认证结果，仅内部与审计使用）

| 值 | 含义 | 触发条件 |
|----|------|----------|
| `PASSED` | 比对通过 | 姓名与身份证号摘要均在白名单命中，且绑定成功 |
| `REJECTED` | 比对未通过 | 姓名不匹配、身份证号未命中或已被其他账户绑定 |

**禁止返回给客户端**：响应只使用 `AUTHENTICATION_FAILED`，本枚举仅写入 `security_audit_event` 与日志（FR-019、SC-005）。

### 1.8 `AuditEventType`（安全审计事件类型）

| 值 | 含义 | 关联需求 |
|----|------|----------|
| `VERIFICATION_CODE_SENT` | 验证码签发 | FR-003、FR-018 |
| `PATIENT_REGISTERED` | 注册成功 | FR-006 |
| `PATIENT_LOGIN_SUCCEEDED` | 登录成功 | FR-007 |
| `PATIENT_LOGIN_FAILED` | 登录失败（含频次限制触发） | FR-007、FR-023 |
| `PATIENT_LOGGED_OUT` | 安全退出 | FR-009 |
| `PASSWORD_RESET` | 重置密码成功 | FR-010 |
| `IDENTITY_VERIFIED` | 实名认证通过 | FR-013、FR-022 |
| `IDENTITY_REJECTED` | 实名认证未通过 | FR-019 |
| `SESSION_REFRESHED` | 续期凭证轮换 | FR-026 |
| `CONSULTATION_GATE_DENIED` | 未认证访问问诊被拒（**写入方：后续问诊模块**；本功能只提供端点 8 的 `consultationAllowed` 判定与前端 `requiresVerified` 守卫） | FR-015 |

审计记录只允许出现脱敏手机号、脱敏身份证号与 `patientId`，禁止密码明文、验证码明文、证件全文（原则 IV、FR-018、SC-006）。

### 1.9 `ErrorCode`（全局错误码，含本功能新增项）

> 前 8 项为 `specs/health-check/data-model.md` 1.2 的既有取值，**取值与文案不得修改**；其余为本功能新增。`message` 一律由本枚举提供，Controller / Service 禁止硬编码（原则 IV）。HTTP 状态与 `code` 保持一致。

| code | 常量 | message | 用途 | 来源 |
|------|------|---------|------|------|
| 200 | `SUCCESS` | 成功 | 成功响应 | 既有 |
| 400 | `BAD_REQUEST` | 请求参数错误 | Bean Validation 失败、报文不可解析等通用参数异常 | 既有 |
| 401 | `UNAUTHORIZED` | 未授权 | 缺少 / 过期 / 签名非法的访问凭证 | 既有 |
| 403 | `FORBIDDEN` | 禁止访问 | 通用权限不足 | 既有 |
| 404 | `NOT_FOUND` | 资源不存在 | 路径不存在 | 既有 |
| 405 | `METHOD_NOT_ALLOWED` | 请求方法不支持 | HTTP 方法错误 | 既有 |
| 500 | `INTERNAL_ERROR` | 服务端内部错误 | 未预期异常兜底，不泄漏堆栈 | 既有 |
| 503 | `HEALTH_UNAVAILABLE` | 依赖组件不可用 | 健康检查专用（保持原语义） | 既有 |
| 400 | `INVALID_PHONE_FORMAT` | 手机号须为 11 位中国大陆号码 | 手机号格式类失败 | 新增 |
| 400 | `INVALID_PASSWORD_FORMAT` | 密码须为 6-20 位字母数字组合，且至少含一个字母和一个数字 | 密码格式类失败 | 新增 |
| 400 | `INVALID_VERIFICATION_CODE_FORMAT` | 验证码须为 6 位数字 | 验证码格式类失败 | 新增 |
| 400 | `INVALID_NAME_FORMAT` | 姓名长度须为 1-50 个字符 | 姓名格式类失败 | 新增 |
| 400 | `INVALID_ID_CARD_FORMAT` | 身份证号须为 18 位且校验位有效 | 身份证号格式类失败 | 新增 |
| 400 | `AGREEMENT_NOT_ACCEPTED` | 请先阅读并同意协议 | 未勾选协议或版本不匹配 | 新增 |
| 400 | `AUTHENTICATION_FAILED` | 账号或密码错误，或验证信息无效 | **所有鉴权类失败统一文案**（验证码错误 / 过期 / 重放 / 跨用途、手机号已注册、账号不存在、密码错误、白名单未命中、身份证号已绑定、旧续期凭证复用） | 新增 |
| 403 | `IDENTITY_NOT_VERIFIED` | 请先完成实名认证 | 未认证账户发起线上问诊 | 新增 |
| 403 | `CSRF_TOKEN_MISMATCH` | 请求校验失败 | 双重 Cookie 校验未通过 | 新增 |
| 429 | `RATE_LIMITED` | 操作过于频繁，请稍后重试 | 验证码发送、实名认证尝试达频次上限 | 新增 |
| 503 | `SERVICE_UNAVAILABLE` | 服务暂时不可用，请稍后重试 | MySQL / Redis 不可用或超时降级（FR-020、FR-024） | 新增 |

**失败提示分层**（对应 SC-005）：

| 层次 | 使用错误码 | 是否含账户存在性 / 剩余次数 | 说明 |
|------|-----------|------------------------------|------|
| 鉴权类失败 | `AUTHENTICATION_FAILED` | 否 | 文案完全一致，差异率 0；包含登录触发频次上限的情况 |
| 参数格式类失败 | `INVALID_*_FORMAT`、`AGREEMENT_NOT_ACCEPTED` | 否 | 允许给出可操作规则说明（FR-002） |
| 频次类失败 | `RATE_LIMITED` | 否 | 仅用于验证码发送与实名认证尝试 |
| 状态类失败 | `IDENTITY_NOT_VERIFIED` | 否 | 认证状态属用户自身已知信息，不构成枚举泄漏 |
| 依赖降级 | `SERVICE_UNAVAILABLE` | 否 | 不泄漏连接串、堆栈、内部实现 |

---

## 2. 统一响应与通用约定

### 2.1 响应信封

```json
{ "code": 200, "message": "成功", "data": { } }
```

- `code`：整数，取值见 1.9；与 HTTP 状态码一致。
- `message`：只来自 `ErrorCode.message`，禁止自定义文案。
- `data`：成功时为业务数据对象；失败时为 `null`；**字段始终存在**（不得因序列化配置缺失）。
- 分页响应（本功能不涉及，保留约定）：`{ code, message, data: { list, total, page, pageSize } }`。

### 2.2 日期与时间

- 所有时间字段序列化为 `yyyy-MM-dd'T'HH:mm:ss`（ISO 8601，无时区偏移，时区 `Asia/Shanghai`），禁止时间戳数组（原则 III，`JacksonConfig` 已配置）。
- 字段命名：`createdAt` / `updatedAt` / `verifiedAt` / `issuedAt` / `expiresAt` / `occurredAt`，单位秒的时长字段以 `...InSeconds` 结尾。

### 2.3 请求通用要求

| 项 | 约定 |
|----|------|
| 路径前缀 | 前端 `src/api/` 写 `/v1/...`；运行时 `baseURL(/api)` + 具体路径 = `/api/v1/...`，禁止 `/api/api/...`（原则 III） |
| 认证 | 受保护端点要求 `Authorization: Bearer <accessToken>`，身份由 `PatientIdExtractor` 统一提取（原则 II） |
| CSRF | 所有 POST / PUT / PATCH / DELETE 要求 `X-CSRF-Token` 与 `sdas_csrf` Cookie 一致（原则 IV，双重 Cookie） |
| 幂等 | 可选 `X-Idempotency-Key`（UUID v4），同一键重复提交返回首次结果（FR-017、SC-008） |
| 编码 | `Content-Type: application/json; charset=UTF-8`；响应 `application/json` |

---

## 3. MySQL 表结构

**迁移脚本**：`server/src/main/resources/db/migration/V1__identity_schema.sql`（建表与索引）、`V2__identity_whitelist_seed.sql`（白名单预置数据）。测试库与生产共用同一套 `V1`；禁止 `spring.flyway.enabled=false`（原则 VI）。引擎 `InnoDB`、字符集 `utf8mb4`、`COLLATE utf8mb4_general_ci`。

**通用列**：`id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY`、`created_at DATETIME NOT NULL`、`updated_at DATETIME NOT NULL`、`version INT NOT NULL DEFAULT 0`（乐观锁，原则 II 并发绑定需求）。

### 3.1 `patient_account`（患者账户）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 患者主键，JWT `patientId` 来源 |
| `phone_hash` | CHAR(64) | NOT NULL, `uk_phone_hash` UNIQUE | 手机号 HMAC-SHA256 摘要，用于唯一约束与登录查询 |
| `phone_cipher` | VARBINARY(256) | NOT NULL | 手机号 AES-GCM 密文（密钥经环境变量注入） |
| `phone_mask` | VARCHAR(16) | NOT NULL | 展示用脱敏手机号（如 `138****8000`），供界面与日志复用 |
| `password_hash` | VARCHAR(60) | NOT NULL | BCrypt 哈希，strength ≥ 10（原则 IV） |
| `auth_status` | VARCHAR(16) | NOT NULL DEFAULT 'UNVERIFIED' | `AuthStatus`，`@Converter` 显式转换 |
| `account_status` | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE' | `AccountStatus` |
| `registered_at` | DATETIME | NOT NULL | 注册时间 |
| `verified_at` | DATETIME | NULL | 认证通过时间 |
| `created_at` / `updated_at` / `version` | — | NOT NULL | 通用列 |

索引：`uk_phone_hash`（唯一）、`idx_auth_status`（问诊门禁统计用）。

### 3.2 `patient_identity`（实名信息）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | — |
| `patient_id` | BIGINT UNSIGNED | NOT NULL, `uk_patient_id` UNIQUE, FK → `patient_account.id` | 一账户一条实名记录（FR-014） |
| `real_name_cipher` | VARBINARY(512) | NOT NULL | 姓名 AES-GCM 密文 |
| `real_name_mask` | VARCHAR(64) | NOT NULL | 脱敏姓名（如 `张*`） |
| `id_card_hash` | CHAR(64) | NOT NULL, `uk_id_card_hash` UNIQUE | 身份证号 HMAC 摘要，唯一约束保证「一个身份证号仅绑一个账户」 |
| `id_card_cipher` | VARBINARY(512) | NOT NULL | 身份证号 AES-GCM 密文 |
| `id_card_mask` | VARCHAR(32) | NOT NULL | 脱敏证件号（如 `110***********1234`） |
| `verified_at` | DATETIME | NOT NULL | 比对通过时间 |
| `created_at` / `updated_at` / `version` | — | NOT NULL | 通用列 |

索引：`uk_patient_id`、`uk_id_card_hash`。并发绑定靠 `uk_id_card_hash` + `version` 乐观锁，只允许一个成功（FR-014、SC-008）。

### 3.3 `patient_agreement_consent`（协议同意记录）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | — |
| `patient_id` | BIGINT UNSIGNED | NOT NULL, `uk_patient_version` UNIQUE(patient_id, agreement_version) | 同一账户同一版本仅一条 |
| `agreement_version` | VARCHAR(32) | NOT NULL | 与配置 `sdas.identity.agreement.version` 一致 |
| `consent_status` | VARCHAR(16) | NOT NULL | `AgreementConsentStatus` |
| `agreed_at` | DATETIME | NOT NULL | 同意时间（FR-005） |
| `created_at` / `updated_at` / `version` | — | NOT NULL | 通用列 |

### 3.4 `patient_session`（会话与续期凭证）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 会话标识，写入 JWT `sessionId` |
| `patient_id` | BIGINT UNSIGNED | NOT NULL, `idx_patient_id` | 所属账户 |
| `refresh_token_hash` | CHAR(64) | NOT NULL, `uk_refresh_token_hash` UNIQUE | 续期凭证摘要，禁止存明文 |
| `session_status` | VARCHAR(16) | NOT NULL | `SessionStatus` |
| `client_ip_hash` | CHAR(64) | NULL | 客户端 IP 摘要（审计用，不存明文） |
| `expires_at` | DATETIME | NOT NULL | 续期凭证到期（604800 s） |
| `revoked_at` | DATETIME | NULL | 撤销时间 |
| `created_at` / `updated_at` / `version` | — | NOT NULL | 通用列 |

索引：`uk_refresh_token_hash`、`idx_patient_id`。轮换一次成功靠 `uk_refresh_token_hash` + 乐观锁（FR-026）。

### 3.5 `hospital_identity_whitelist`（医院白名单，D2 数据库预置）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | — |
| `real_name` | VARCHAR(64) | NOT NULL | 姓名，精确等值比对（不做模糊匹配） |
| `id_card_hash` | CHAR(64) | NOT NULL, `uk_whitelist_id_card_hash` UNIQUE | 身份证号 HMAC 摘要，比对入口 |
| `id_card_cipher` | VARBINARY(512) | NOT NULL | 身份证号密文，仅在受控运维场景解密 |
| `enabled` | TINYINT(1) | NOT NULL DEFAULT 1 | 是否参与比对 |
| `source` | VARCHAR(32) | NOT NULL DEFAULT 'PRESET' | 数据来源标记（`PRESET` 迁移预置 / `IMPORT` 受控导入） |
| `created_at` / `updated_at` / `version` | — | NOT NULL | 通用列 |

索引：`uk_whitelist_id_card_hash`（唯一）。本功能**只读**该表，不提供维护界面与同步任务（D2）。`V2` 种子仅放合成演示数据（虚构姓名 + 算法构造的占位证件号），生产由 DBA 以 `source='IMPORT'` 受控导入。

### 3.6 `security_audit_event`（安全审计事件）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | — |
| `event_type` | VARCHAR(32) | NOT NULL, `idx_event_type_time(event_type, occurred_at)` | `AuditEventType` |
| `result` | VARCHAR(16) | NOT NULL | `SUCCESS` / `FAILURE` / `REJECTED` |
| `actor_patient_id` | BIGINT UNSIGNED | NULL | 关联患者；注册失败时为空 |
| `masked_phone` | VARCHAR(16) | NULL | 脱敏手机号，禁止明文 |
| `masked_id_card` | VARCHAR(32) | NULL | 脱敏证件号，禁止明文 |
| `reason_code` | VARCHAR(32) | NULL | 内部原因码（如 `CODE_EXPIRED`），**不得**直接返回客户端 |
| `trace_id` | VARCHAR(64) | NULL | 与 `AccessLogFilter` 的 traceId 对齐，便于串联日志 |
| `occurred_at` | DATETIME | NOT NULL | 事件时间 |

禁止列入本表的字段：密码明文 / 密文、验证码明文 / 摘要、完整手机号、完整身份证号、JWT 全文（原则 IV、FR-018、SC-006）。

---

## 4. Redis 键设计

统一前缀 `sdas:identity:`，序列化用 `StringRedisTemplate`，禁止与业务无关的裸 key。

| key 模板 | 类型 / TTL | 值 | 用途 |
|----------|-----------|----|------|
| `sdas:identity:code:{purpose}:{phoneDigest}` | String / 300 s | `{codeDigest, status, issuedAt, attempts, consumedAt}`（JSON） | 验证码生命周期（1.3），`purpose` ∈ `REGISTER` / `RESET_PASSWORD` 实现用途隔离 |
| `sdas:identity:rate:send:{phoneDigest}` | String / 86400 s | 当日发送计数 + 最近发送时间戳 | 60 s 间隔 + 单日 10 次（FR-003） |
| `sdas:identity:rate:verify:{phoneDigest}` | String / 900 s | 实名认证尝试计数 | 15 分钟窗口限流（FR-023） |
| `sdas:identity:rate:login:{phoneDigest}` | String / 900 s | 登录失败计数 | 达阈值后仍返回 `AUTHENTICATION_FAILED`，仅跳过密码校验（FR-007、FR-023） |
| `sdas:identity:idem:{idempotencyKey}` | String / 600 s | 首次处理结果快照 | 幂等去重（FR-017、SC-008） |
| `sdas:identity:session:revoked:{sessionId}` | String / access token 剩余 TTL | `1` | 登出 / 轮换后立即失效旧 access token（FR-009、SC-007） |

约定：`phoneDigest` = HMAC-SHA256(手机号)，禁止用明文手机号或身份证号作 key；`codeDigest` = HMAC-SHA256(验证码 + 手机号摘要)，固定验证码同样只存摘要；不使用 Redis Streams / ACL 等 3.2 之后特性（`research.md` R-03）。

---

## 5. 状态流转

### 5.1 注册（FR-001、FR-003、FR-005、FR-006、FR-017）

```text
[验证码 ACTIVE] --校验通过--> [USED]（原子消费）
[patient_account] 不存在 --插入--> auth_status=UNVERIFIED, account_status=ACTIVE
[patient_agreement_consent] --插入--> consent_status=AGREED, agreement_version=当前配置
[patient_session] --插入--> session_status=ACTIVE（自动登录）
[security_audit_event] --插入--> PATIENT_REGISTERED / SUCCESS
```
同一 `@Transactional`：`patient_account` + `patient_agreement_consent` + `patient_session` + `security_audit_event`；任一失败整体回滚，不产生半成品账户（FR-024、SC-010）。

### 5.2 登录 / 续期 / 登出（FR-007 ~ FR-009、FR-026）

```text
登录成功：新建 session(ACTIVE)，签发 access token(7200 s) + refresh token(604800 s)
续期成功：旧 session ACTIVE → REVOKED，新建 session(ACTIVE)（一次轮换，旧凭证立即失效）
续期失败：旧 session 非 ACTIVE → AUTHENTICATION_FAILED，不新建会话
登出：当前 session ACTIVE → REVOKED，清除 refresh Cookie，写 session:revoked 标记
重置密码成功：该账户全部 session ACTIVE → REVOKED
```

### 5.3 实名认证（FR-011 ~ FR-016、FR-019、FR-022、FR-024）

```text
UNVERIFIED --姓名+证件摘要命中白名单 且 证件未被占用--> VERIFIED（写 patient_identity + 审计 IDENTITY_VERIFIED）
UNVERIFIED --未命中 / 已被占用--> UNVERIFIED 保持不变（审计 IDENTITY_REJECTED，响应统一 AUTHENTICATION_FAILED）
VERIFIED --重复提交--> VERIFIED 不变，返回成功，不写重复认证记录（FR-017）
问诊门禁：VERIFIED → 放行；UNVERIFIED → IDENTITY_NOT_VERIFIED(403) + 审计 CONSULTATION_GATE_DENIED
```

不存在 `VERIFIED → UNVERIFIED` 的路径；本功能无撤销入口。

---

## 6. 配置项

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `sdas.identity.verification-code.fixed-enabled` | `true`（仅 dev/test 允许） | 固定验证码开关；`prod` 为 `true` 时启动失败（D1、`research.md` R-01） |
| `sdas.identity.verification-code.fixed-code` | `${SDAS_FIXED_CODE:888888}` | 固定验证码值，禁止写入日志与响应 |
| `sdas.identity.verification-code.ttl-seconds` | `300` | 验证码有效期 |
| `sdas.identity.verification-code.send-interval-seconds` | `60` | 同一手机号发送间隔 |
| `sdas.identity.verification-code.daily-limit` | `10` | 单日发送上限 |
| `sdas.identity.verification-code.max-attempts` | `5` | 单个验证码校验尝试上限，超限即 `REVOKED` |
| `sdas.identity.agreement.version` | `${SDAS_AGREEMENT_VERSION:v1.0}` | 协议版本，注册时校验 |
| `sdas.identity.jwt.access-token-ttl-seconds` | `7200` | ≤ 24 h（原则 IV） |
| `sdas.identity.jwt.refresh-token-ttl-seconds` | `604800` | 续期凭证有效期 |
| `sdas.identity.jwt.secret` | `${SDAS_JWT_SECRET}` | HS256 密钥，禁止入库 |
| `sdas.identity.crypto.id-card-secret` | `${SDAS_ID_CARD_HMAC_SECRET}` | 身份证号 HMAC 密钥 |
| `sdas.identity.crypto.phone-secret` | `${SDAS_PHONE_HMAC_SECRET}` | 手机号 HMAC 密钥 |
| `sdas.identity.crypto.aes-key` | `${SDAS_AES_KEY}` | AES-GCM 主密钥（32 字节 Base64） |
| `sdas.identity.whitelist.source` | `DATABASE_PRESET` | 白名单来源标记，仅 `DATABASE_PRESET` 合法（D2） |
| `cookie.secure` | `${COOKIE_SECURE:false}` | 生产必须 `true`；CSRF Cookie 为 `httpOnly=false`；**该键不在 `sdas.identity` 前缀下**，不能用 `IdentityProperties` 绑定，须用 `@Value("${cookie.secure:false}")` 或独立 `CookieProperties(prefix = "cookie")` |

---

## 7. 实现约束（原则 V）

- **枚举持久化**：全部使用显式 `@Converter(autoApply = false)`，禁止 `@Enumerated(EnumType.STRING)`，禁止同一字段两者并存；`Converter` 必须处理 `null` 与未知值并降级为安全默认值（`UNVERIFIED` / `ACTIVE`）。
- **新增枚举值**：须全局搜索 `switch` / `if-else` 与前端映射表，确保无遗漏。
- **二次校验**：Controller 用 Bean Validation 做首轮，Service 对手机号、密码、身份证号、枚举值做二次校验（原则 II、FR-025）。
- **敏感数据处理**：手机号 / 身份证号仅在入站时解密一次并立即摘要；出站只使用 `phone_mask` / `id_card_mask`；任何 DTO 不得含 `phone_cipher`、`id_card_cipher`、`*_hash`。
- **事务**：多表变更在同一 `@Transactional` 内编排（5.1、5.2、5.3），Repository 只做持久化，禁止跨层调用。
- **时间**：统一 `LocalDateTime` + `Asia/Shanghai`，禁止 `Date`。

---

## 8. 与契约、前端类型、测试的映射

| 消费方 | 引用位置 | 要求 |
|--------|----------|------|
| REST 契约 | `contracts/patient-identity-api.md` | 字段、`code`、枚举取值须与本文件一致 |
| Mock 数据 | `contracts/mocks.md`（M1–M15） | 前端 `web/src/api/mocks/` 与 Playwright `page.route()` 只从此处同步 |
| 前端类型 | `web/src/api/types.ts` | 注释标注来源 `specs/001-patient-identity/data-model.md` 与 `contracts/` |
| 前端错误码 | `web/src/api/error-codes.ts` | 只镜像 1.9 的 `code` 数值（`API_CODE` 常量）；`message` 一律使用后端响应值，禁止复制或自造业务文案；文件头须标注本文件 1.9 为来源 |
| 后端枚举 | `com.sdas.server.common.ErrorCode`、`identity` 包内枚举类 | Javadoc 标注本文件路径 |
| 测试断言 | 后端集成测试 / 真实后端 E2E | 断言响应 `code` 与 1.9 一致；禁止只断言 HTTP 状态 |

**一致性自检清单**（每次改动后执行）：

1. 本文件新增枚举值已同步到 `contracts/` 与前端类型；无同名不同值。
2. 1.9 中既有 8 项取值未被改动。
3. `contracts/mocks.md` 的每个 `code` 都在 1.9 中存在。
4. 表结构字段与契约响应字段一一对应，无契约中出现但表内不存在的字段。
5. Redis key 中的手机号 / 验证码均为摘要，无明文。
