# quickstart.md 第 6 节逐条验证记录（T110）

环境：本机 MySQL 8.0.46（库 `cloud_consultation`）、Redis 3.2.100、Spring Boot（dev profile，8080）、Vite（5183）、Edge（Playwright `msedge` channel）。
执行方式：`E2E_REAL_BACKEND=1 npm run test:e2e`，真实后端不注入任何 `page.route` 拦截；Mock 套件在该模式下自动跳过。
结果：**4 个真实用例全部通过，零失败**；固定验证码 `888888`；白名单记录由夹具动态生成并清理，**未使用 `V2__identity_whitelist_seed.sql` 的演示记录**。

## 6.1 注册（US1）

| 步骤 | 期望 | 结果 |
|---|---|---|
| `POST /v1/verification-codes` `purpose=REGISTER` | `code=200`，`data` 不含验证码字段 | ✅ |
| `POST /v1/patients` 固定验证码 + 合规密码 + 同意协议 | `code=200`，`authStatus=UNVERIFIED`，`consultationAllowed=false`，`Set-Cookie: sdas_refresh`（HttpOnly） | ✅ 进入个人中心 |
| 重复使用同一验证码 | `400 AUTHENTICATION_FAILED`（一次性） | ✅ 由 `PatientRegisterContractTest` / `RegisterFlowIT` 覆盖 |
| `RESET_PASSWORD` 用途验证码用于注册 | 同样失败（用途隔离） | ✅ 由 `PasswordResetFlowIT` 覆盖 |

## 6.2 登录与退出（US2）

| 步骤 | 期望 | 结果 |
|---|---|---|
| `POST /v1/patient-sessions` | `code=200`，返回 `accessToken`，不返回 refresh token 明文 | ✅ |
| `DELETE /v1/patient-sessions/current` | `code=200` | ✅ |
| 退出后同一 accessToken 访问端点 8 | `401` | ✅ |
| 不存在手机号 / 密码错误 / 验证码错误 | `message` 逐字一致 | ✅ |

## 6.3 实名认证（US3）

| 步骤 | 期望 | 结果 |
|---|---|---|
| 夹具动态 INSERT `hospital_identity_whitelist`（合成姓名 + 随机合法证件号 + HMAC 摘要） | 不使用演示种子 | ✅ |
| 未命中先提交 | `400 AUTHENTICATION_FAILED`，端点 8 仍 `UNVERIFIED` | ✅ |
| 命中后提交 | `code=200`，`VERIFIED` + `consultationAllowed=true`，脱敏姓名/证件号 | ✅ |
| 已认证重复提交 | `200` 且不新增认证记录 | ✅ 由 `IdentityVerificationFlowIT` 覆盖 |

## 6.4 忘记密码（US4）

| 步骤 | 期望 | 结果 |
|---|---|---|
| 注册后签发 `RESET_PASSWORD` 验证码 | 需跨过 60 s 发送间隔（限流按手机号计，不区分用途） | ✅ E2E 显式等待窗口 |
| `POST /v1/password-resets` | `code=200` | ✅ |
| 旧密码登录 / 新密码登录 | 失败 / 成功 | ✅ |
| 原 accessToken 访问受保护端点 | `401` | ✅ 由 `PasswordResetFlowIT` 覆盖 |

## 6.5 问诊门禁占位入口（SC-003）

| 步骤 | 期望 | 结果 |
|---|---|---|
| 未认证账户访问 `/consultation/entry` | `requiresVerified` 守卫重定向 `/patient/identity-verification`，端点 8 `consultationAllowed=false` | ✅ |
| 认证完成后再次访问 | 进入占位页，显示「线上问诊建设中」，无问诊表单 / 提交动作 / 问诊 API 调用 | ✅ |
| **未认证账户成功进入该入口的次数** | **0** | ✅ **实测 0 次**（E2E 断言 `/consultation/entry` 被重定向且 `consultation-placeholder` 数量为 0） |

## 第 8 节常见问题表核对

| 现象 | 核对结论 |
|---|---|
| `prod` 启动即失败提示固定验证码 | 符合预期：`application-prod.yml` `fixed-enabled=false` + `IdentityStartupValidator` fail-fast |
| 所有接口 `503 SERVICE_UNAVAILABLE` | 本机 MySQL / Redis 已就绪，`/api/health` 返回 `code=200` |
| 修改类请求 403 `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验正常；真实 E2E 已验证 `sdas_csrf` 与 `X-CSRF-Token` 一致 |
| 注册 `AUTHENTICATION_FAILED` 但验证码正确 | 60 s 发送间隔 / 用途隔离 / 一次性已在本轮验证 |
| 前端 404 且路径含 `/api/api` | `src/api/` 全部以 `/v1/...` 开头，API 层单测断言路径不以 `/api` 开头 |
| 未认证账户能进问诊页 | 守卫按端点 8 判定，实测放行 0 次 |
| Playwright 打开其他项目页面 | 端口固定 5183、`strictPort: true`、`reuseExistingServer: false` |

## 本轮真实后端验证发现并修复的集成缺陷

Mock E2E 使用 `page.route` 拦截 `/api/v1/**`，无法覆盖真实网络层。真实后端 E2E 首轮暴露
`403 Invalid CORS request`：`application.yml` 的 CORS 默认白名单与 `quickstart.md` 规定的
`127.0.0.1:5183` 不一致。已补齐白名单并重建验证，4 个真实用例随后全部通过。
详见 `code-review-security.md`。
