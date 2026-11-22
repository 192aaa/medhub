# 代码审查与安全检查报告（T113 / T114）

范围：`server/src/main/java`、`web/src`、`server/src/main/resources`、`web/tests/e2e`。
方式：静态检索 + 真实后端 HTTP 抽样 + 契约测试断言复核。日期：2026-11-07。

## 1. 宪法原则 V：分层与代码规范（T113）

| 检查项 | 结论 | 依据 |
|---|---|---|
| `System.out.println` / `printStackTrace` | ✅ 0 处 | 全量检索 main 源码无命中；日志统一 SLF4J + MDC `traceId` |
| `@Enumerated` 与 `@Converter` 并存 | ✅ 不存在 | 项目使用 JdbcTemplate 而非 JPA；枚举统一经 `model/converter/SafeEnumConverter`（null / 未知值安全降级），源码中仅有「禁止 @Enumerated」的注释说明 |
| `JdbcTemplate` 越界 | ✅ 仅在 `repository` 包 | `identity/repository/*`、`health/repository/*`；Controller / Service / DTO 无 JDBC 依赖 |
| Controller 越界 | ✅ 只做 `@Valid` + 调 Service | `PasswordResetController` 等 8 个端点均为薄控制器，返回 `Result.success()` / Service 结果 |
| 硬编码业务文案 | ✅ 0 处 | 后端异常全部 `throw new BusinessException(ErrorCode.*)`；响应 `message` 取自 `ErrorCode`；前端无硬编码业务错误文案（仅 UI 标签与 `src/api/mocks/**` 的测试替身场景数据），失败一律展示 `result.message` |
| 事务边界 | ✅ 正确 | `PatientAccountServiceImpl`、`PatientSessionServiceImpl`、`IdentityVerificationServiceImpl`、`PasswordResetServiceImpl` 的写操作均 `@Transactional`；端点 8 为 `@Transactional(readOnly = true)`；`AuditEventServiceImpl` 的 `REQUIRES_NEW` 由跨 Bean 注入调用，代理生效 |
| 已知设计权衡 | ⚠️ 登记不改 | `PatientSessionService` / `IdentityVerificationService` 接口签名携带 `HttpServletRequest`，用于把身份提取交给 `PatientIdExtractor`（Foundational T033 的既有契约）。属既有设计，改动会波及全部端点与测试，本期不做重构 |

### 本轮审查实际修复的代码缺陷

1. `config/CsrfCookieFilter.java`：`toUpperCase()` → `toUpperCase(Locale.ROOT)`，消除 `DM_CONVERT_CASE`（土耳其语等区域大小写差异）。
2. `exception/GlobalExceptionHandler.java`：`getFieldError()` 由重复调用改为提取 `FieldError` 局部变量，消除 `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`。
3. `common/PageResult.java`：构造器 / setter 防御性拷贝 `new ArrayList<>(...)`，getter 返回 `Collections.unmodifiableList(...)`，消除 `EI_EXPOSE_REP` / `EI_EXPOSE_REP2`。
4. `config/spotbugs/spotbugs-exclude.xml`：仅对两类框架误报精准豁免 —— Spring 构造器注入 Bean 的 `EI_EXPOSE_REP2`（34 项，按已知组件包限定）、`PreparedStatementCreator` lambda 的 `OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE`（4 项，限定 `identity.repository` 的 `insert` lambda，资源由 `JdbcTemplate` 关闭）。`EI_EXPOSE_REP`（getter 暴露内部可变对象）**不在豁免范围**，确保未来真实缺陷仍会被拦截。
5. `resources/application.yml`：**真实后端 E2E 暴露的集成缺陷** —— CORS 默认白名单只有 `localhost:5173/5174`，而 `quickstart.md` 规定前端在 `127.0.0.1:5183`，导致所有修改类请求返回 `403 Invalid CORS request`。已补齐 `localhost:5183` 与 `127.0.0.1:5183`，并注明生产必须由 `CORS_ALLOWED_ORIGINS` 显式注入。Mock E2E 因 `page.route` 拦截未覆盖此路径，真实后端 E2E 是唯一能发现它的验证层。

## 2. 敏感信息抽样（T114）

| 检查项 | 结论 | 依据 |
|---|---|---|
| 日志中的密码 / 验证码 / 完整手机号 / 完整证件号 / JWT 全文 | ✅ 无 | `common/log/LogMasker` 以正则掩盖 `(password\|passwd\|pwd\|token\|secret\|api-key\|verifycode\|code)=...`、18 位证件号 `1[3-9]\d{9}` 之外再屏蔽 11 位手机号；`AccessLogFilter` 仅记录 `method / LogMasker.mask(uri) / status / elapsedMs`；`GlobalExceptionHandler` 仅记录 `code / traceId / exception class` |
| 响应体中的内部字段 | ✅ 无 | DTO 包检索无 `idCardHash` / `idCardCipher` / `realNameCipher` / `phoneCipher` / `passwordHash` / `refreshTokenHash`；契约测试显式断言：`VerificationCodeContractTest` → `$.data.code` / `$.data.verificationCode` `doesNotExist()`；`PatientLoginContractTest` → `$.data.refreshToken` `doesNotExist()`；`IdentityVerificationContractTest` → `identityVerificationResult` / `whitelistHit` / `idCardHash` / `idCardCipher` / `realNameCipher` `doesNotExist()`；`IdentityStatusContractTest` → `$.data.idCardHash` `doesNotExist()` |
| 响应中的 `accessToken` | ✅ 符合契约 | 端点 2/3/5 按契约返回 JWT；日志与审计不记录其全文，`patientSession` Cookie 为 HttpOnly |
| 统一模糊文案 | ✅ 一致 | 登录失败 / 验证码错误 / 白名单未命中 / 重置密码失败均 `code=400` + 「账号或密码错误，或验证信息无效」，不暴露账号存在性、可枚举原因与剩余次数 |
| `prod` 固定验证码关闭 | ✅ | `application-prod.yml` → `fixed-enabled: false`；`config/IdentityStartupValidator` 在 prod profile 且开关为 true 时抛 `IllegalStateException` 使启动 fail-fast |
| 生产密钥注入 | ✅ | `application-prod.yml` 的 `SDAS_JWT_SECRET` / `SDAS_ID_CARD_HMAC_SECRET` / `SDAS_PHONE_HMAC_SECRET` / `SDAS_AES_KEY` 由环境变量注入，缺失即启动失败；dev/test 占位值不进入生产 |

## 3. 已闭环的改进与后续建议

- ✅ 已修复：`PasswordResetServiceImpl` 的 Redis 会话撤销标记 TTL 由硬编码 `Duration.ofSeconds(7200)` 改为 `jwtTokenService.accessTokenTtlSeconds()`，与 `PatientSessionServiceImpl` 退出逻辑使用同一有效期来源；`PasswordResetServiceTest` 新增 `revokedMarkerUsesAccessTokenTtl` 断言防止回归。
- 建议在 CI 中固化 `E2E_REAL_BACKEND=1 npm run test:e2e`（需 MySQL / Redis / 后端 / Vite），因为 Mock 层无法覆盖 CORS、Cookie Path、真实限流窗口等集成问题。
- 运行本机真实 E2E / 集成测试前须确认 Redis 常驻（`redis-cli PING` 返回 `PONG`）；Redis 掉线会让依赖缓存的契约与集成测试统一降级为 503，与代码改动无关。
