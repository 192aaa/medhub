# 契约索引与通用约定（患者注册与实名认证）

**功能**：`001-patient-identity`
**契约地位**：本目录是前后端**唯一参考**。前端不得推测字段，后端不得未同步契约就改接口；实现与文档不一致即视为缺陷（原则 III）。

| 文件 | 内容 |
|------|------|
| `README.md` | 通用约定、端点索引、错误码索引、契约一致性验证清单 |
| `patient-identity-api.md` | 8 个端点的请求 / 响应结构、状态码、异常场景 |
| `mocks.md` | M1–M15 mock 数据，前端 `web/src/api/mocks/` 与 Playwright `page.route()` 的唯一来源 |

**枚举与 `code` 唯一来源**：`specs/001-patient-identity/data-model.md`（1.1–1.9）。本目录只引用，不重复定义取值。

---

## 1. 端点索引

| # | 方法 | 端点 | 认证 | CSRF | 说明 | 关联需求 |
|---|------|------|------|------|------|----------|
| 1 | POST | `/api/v1/verification-codes` | 否 | 是 | 签发验证码（dev/test 为固定值，不发送短信，不回传验证码） | FR-001、FR-003 |
| 2 | POST | `/api/v1/patients` | 否 | 是 | 注册并自动登录 | FR-001 ~ FR-006 |
| 3 | POST | `/api/v1/patient-sessions` | 否 | 是 | 登录 | FR-007、FR-008 |
| 4 | DELETE | `/api/v1/patient-sessions/current` | 是 | 是 | 安全退出（仅当前会话） | FR-009 |
| 5 | PUT | `/api/v1/patient-sessions/current/refresh-token` | 否（凭 Cookie） | 是 | 续期凭证一次轮换 | FR-008、FR-026 |
| 6 | POST | `/api/v1/password-resets` | 否 | 是 | 通过验证码重置密码（成功后全端会话撤销） | FR-010 |
| 7 | POST | `/api/v1/identity-verifications` | 是 | 是 | 提交实名认证 | FR-011 ~ FR-014、FR-019 |
| 8 | GET | `/api/v1/patients/current/identity-status` | 是 | 否 | 查询认证状态与问诊门禁判定 | FR-015、FR-016、FR-022 |

- 资源路径均为复数名词；方法语义遵循 GET 查 / POST 创 / PUT 全量改 / DELETE 删（原则 III）。
- **前端路径**：`web/src/api/` 内写 `/v1/...`（不含 `/api`），运行时拼接为 `/api/v1/...`，禁止出现 `/api/api/...`。
- 除端点 1、2、3、5、6 外均需 `Authorization: Bearer <accessToken>`；所有修改类请求（POST / PUT / PATCH / DELETE）需 `X-CSRF-Token`。

## 2. 统一响应

```json
{ "code": 200, "message": "成功", "data": { } }
```

- `code` 取值与 HTTP 状态一致，清单见 `data-model.md` 1.9；失败时 `data` 为 `null`，字段始终存在。
- `message` 只来自 `ErrorCode`，禁止 Controller / Service 自定义文案（原则 IV）。
- 时间格式 `yyyy-MM-dd'T'HH:mm:ss`；时长字段以 `InSeconds` 结尾。

## 3. 错误码索引（引用 `data-model.md` 1.9）

| code | 常量 | 使用端点 | 说明 |
|------|------|----------|------|
| 200 | `SUCCESS` | 全部 | 成功 |
| 400 | `BAD_REQUEST` | 全部 | Bean Validation 失败 / 报文不可解析 |
| 400 | `INVALID_PHONE_FORMAT` | 1、2、3、6 | 手机号格式非法 |
| 400 | `INVALID_PASSWORD_FORMAT` | 2、6 | 密码格式非法 |
| 400 | `INVALID_VERIFICATION_CODE_FORMAT` | 2、6 | 验证码非 6 位数字 |
| 400 | `INVALID_NAME_FORMAT` | 7 | 姓名格式非法 |
| 400 | `INVALID_ID_CARD_FORMAT` | 7 | 身份证号格式 / 校验位非法 |
| 400 | `AGREEMENT_NOT_ACCEPTED` | 2 | 未勾选协议或版本不匹配 |
| 400 | `AUTHENTICATION_FAILED` | 2、3、5、6、7 | 鉴权类失败统一文案 |
| 401 | `UNAUTHORIZED` | 4、7、8 | 缺少 / 过期 / 非法访问凭证 |
| 403 | `FORBIDDEN` | —（本功能未使用） | 通用权限不足，保留既有取值 |
| 403 | `IDENTITY_NOT_VERIFIED` | 端点 8 判定后由问诊模块返回 | 未认证发起问诊被拒 |
| 403 | `CSRF_TOKEN_MISMATCH` | 全部修改类 | 双重 Cookie 校验失败 |
| 404 | `NOT_FOUND` | 全部 | 路径不存在 |
| 405 | `METHOD_NOT_ALLOWED` | 全部 | 方法不支持 |
| 429 | `RATE_LIMITED` | 1、7 | 频次达上限 |
| 500 | `INTERNAL_ERROR` | 全部 | 未预期异常 |
| 503 | `SERVICE_UNAVAILABLE` | 全部 | MySQL / Redis 不可用或超时 |

## 4. 通用请求头

| 头 | 必需 | 说明 |
|----|------|------|
| `Content-Type: application/json` | 有请求体时 | UTF-8 |
| `Authorization: Bearer <accessToken>` | 受保护端点 | 身份由 `PatientIdExtractor` 提取，Controller 禁止自行解析（原则 II） |
| `X-CSRF-Token` | 所有修改类请求 | 值取自非 HttpOnly 的 `sdas_csrf` Cookie，服务端常量时间比对 |
| `X-Idempotency-Key` | 可选 | UUID v4；同样键 10 分钟内重复提交返回首次结果（FR-017） |
| `Cookie: sdas_refresh=...` | 端点 4、5 | `HttpOnly; SameSite=Lax; Path=/api/v1/patient-sessions` |

## 5. Cookie 约定

| Cookie | HttpOnly | SameSite | Secure | Path | 用途 |
|--------|----------|----------|--------|------|------|
| `sdas_refresh` | 是 | Lax | 生产 `true` | `/api/v1/patient-sessions` | 续期凭证，前端不读取 |
| `sdas_csrf` | **否** | Lax | 生产 `true` | `/api/v1` | 双重 Cookie 校验，前端读取后放入 `X-CSRF-Token` |

access token 由响应体 `data.accessToken` 返回，前端存 `localStorage`（键 `sdas.patient.token`）并注入 `Authorization`；患者端 axios 实例须启用 `withCredentials: true`。

## 6. 契约一致性验证清单（每迭代执行一次，原则 VI）

1. 8 个端点均已暴露，路径、方法、认证要求与本文件第 1 节一致。
2. 每个成功响应含 `code` / `message` / `data` 三个字段；失败响应 `data` 为 `null`。
3. 响应 `code` 取值全部存在于 `data-model.md` 1.9；HTTP 状态与 `code` 一致。
4. 鉴权类失败（`AUTHENTICATION_FAILED`）在端点 2、3、5、6、7 的 `message` 完全一致。
5. 参数格式类失败的 `message` 不含账户存在性、剩余次数、白名单命中信息。
6. 时间字段格式为 `yyyy-MM-dd'T'HH:mm:ss`，与 `JacksonConfig` 一致。
7. 响应体不含 `password`、`verificationCode`、`phone_cipher`、`id_card_cipher`、`*_hash` 等敏感字段。
8. 前端 `web/src/api/types.ts`、`error-codes.ts`、`mocks/` 与 `contracts/mocks.md` 逐字段一致。
9. 未出现 `/api/api/...` 形式的请求路径。
10. `contracts/mocks.md` 中每个 mock 的 `code` 与真实后端同场景响应一致。