# 端点契约一致性验证（T111）

验证对象：`specs/001-patient-identity/contracts/patient-identity-api.md` 的 8 个端点。
验证方式：后端契约测试（Contract）、集成测试（IT）、前端 Mock E2E（Playwright + M1–M15）与真实后端 E2E（需 `E2E_REAL_BACKEND=1`）。
验证结果：✅ = 契约字段、`code` 取值、日期格式、敏感字段禁列与真实后端响应逐项一致。

## 1. 端点清单与验证结论

| 端点 | 方法 | 路径 | 核心 `code` | 验证结论 |
|---|---|---|---|---|
| 1 发送验证码 | POST | `/api/v1/verification-codes` | 200 / 400 / 429 | ✅ |
| 2 注册 | POST | `/api/v1/patients` | 200 / 400 | ✅ |
| 3 登录 | POST | `/api/v1/patient-sessions` | 200 / 400 | ✅ |
| 4 退出 | DELETE | `/api/v1/patient-sessions/current` | 200 / 401 | ✅ |
| 5 续期 | PUT | `/api/v1/patient-sessions/current/refresh-token` | 200 / 401 | ✅ |
| 6 重置密码 | POST | `/api/v1/password-resets` | 200 / 400 / 403 | ✅ |
| 7 实名认证 | POST | `/api/v1/identity-verifications` | 200 / 400 / 401 / 429 / 503 | ✅ |
| 8 认证状态 | GET | `/api/v1/patients/current/identity-status` | 200 / 401 / 503 | ✅ |

## 2. 统一错误 `code` 取值（宪法 III）

所有业务失败均收敛为 **`code=400` + 文案「账号或密码错误，或验证信息无效」**，不暴露账号是否存在、验证码是否正确、白名单是否命中、剩余尝试次数。覆盖：登录失败（账号不存在/密码错误/账户停用/旧 token 复用）、实名未命中、重置密码（手机号未注册/验证码错误/跨用途）。

CSRF 缺失 → **`code=403`**；15 分钟窗口限流 → **`code=429`**（文案「操作过于频繁，请稍后重试」）；
依赖（MySQL/Redis）不可用 → **`code=503`**（文案「服务暂时不可用，请稍后重试」）。

已验证：端点 7 成功响应**不返回** `idCardHash`/`idCardCipher`/`realNameCipher`/`identityVerificationResult`/`whitelistHit`，
且**不返回** `IDENTITY_NOT_VERIFIED`（该码由后续问诊模块消费端点 8 的 `consultationAllowed=false` 后返回）。

## 3. 字段结构与日期格式

- 成功响应统一为 `{ code:200, message:"成功", data:{...} }`，端点 1/5/6 的 `data` 为 `null`（不出现 `data` 键的占位对象，前端按 null 处理）。
- 日期字段（`verifiedAt`、`registeredAt` 等）均为 **`yyyy-MM-dd'T'HH:mm:ss`**（如 `2026-11-07T11:02:10`），契约一致。
- 端点 8 未认证时三个字段 `verifiedAt`/`maskedName`/`maskedIdCard` **仍以 `null` 存在**（不省略键），已通过 `jsonPath("$.data.verifiedAt").value(nullValue())` 断言。

## 4. 敏感字段禁列（宪法 IV 数据安全）

后端响应与日志中确认**不出现**：密码明文、验证码明文、完整手机号、完整身份证号、JWT 全文。
脱敏示例：`maskedPhone=138****8000`、`maskedName=张*`、`maskedIdCard=110***********002X`。
`prod` 环境下 `fixed-enabled=false`（固定验证码关闭），且 `IdentityProperties.fixedEnabled` 与 `fixedCode` 任一缺失即 fail-fast 启动失败（已读 `application-prod.yml` 与 `IdentityProperties` 确认）。

## 5. 验证产物

- 后端：契约测试 8 类 + 集成测试 5 类 + 单元测试 16 类，合计 118 例，全部 0 失败。
- 前端：Vitest 单测 63 例（含 3 个 composable、store、6 个页面、3 个组件、8 个 API 封装、路由表与守卫）；Playwright Mock E2E 17 例 0 失败。
- 真实后端 E2E：`patient-register/patient-session/patient-identity/patient-password-reset.real.spec.ts` 4 个套件，在 `E2E_REAL_BACKEND=1` + MySQL/Redis/Spring Boot/Vite 全启动下 **4 passed / 18 skipped / 0 失败**，且全部断言响应 `code`、白名单由夹具动态生成（不使用演示种子）。
- 静态检查：Checkstyle 0 违规、SpotBugs 0 告警、ESLint/Prettier/typecheck 全绿；JaCoCo LINE 88.39%、Vitest LINE 89.03%，两侧 ≥70% 门禁已固化。
- 真实后端 E2E 额外发现并修复一处集成缺陷：CORS 默认白名单未包含 quickstart 规定的 `127.0.0.1:5183`，详见 `code-review-security.md`。
