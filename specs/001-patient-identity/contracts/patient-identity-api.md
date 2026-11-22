# 患者注册与实名认证 API 契约

**功能**：`001-patient-identity`
**枚举与 `code` 来源**：`specs/001-patient-identity/data-model.md`（1.1–1.9）
**mock 数据**：`contracts/mocks.md`（M1–M15）
**通用约定**：`contracts/README.md`（统一响应、请求头、Cookie、一致性清单）

> 本文件定义 8 个端点的请求 / 响应结构。字段名、类型、枚举取值、`code` 与 `message` 不得在前端或后端另行推测。响应示例中的时间均为 `yyyy-MM-dd'T'HH:mm:ss`。

---

## 端点 1：签发验证码

`POST /api/v1/verification-codes`

**认证**：否 | **CSRF**：是 | **需求**：FR-001、FR-003（D1 固定验证码，不发送短信）

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `phone` | string | 是 | `^1[3-9]\d{9}$` | 11 位中国大陆手机号 |
| `purpose` | string | 是 | `REGISTER` \| `RESET_PASSWORD` | 用途，参与隔离（`VerificationPurpose`） |

```json
{ "phone": "13800138000", "purpose": "REGISTER" }
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `purpose` | string | 回显用途 |
| `maskedPhone` | string | 脱敏手机号，如 `138****8000` |
| `expiresInSeconds` | number | 验证码有效期，300 |
| `nextSendAllowedInSeconds` | number | 下次可发送间隔，60 |

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "purpose": "REGISTER",
    "maskedPhone": "138****8000",
    "expiresInSeconds": 300,
    "nextSendAllowedInSeconds": 60
  }
}
```

**禁止返回验证码本身**（含固定验证码），即使 `data` 中也不得出现 `code` / `verificationCode` 字段。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_PHONE_FORMAT` | 手机号格式非法 |
| 400 | `BAD_REQUEST` | `purpose` 缺失 / 非枚举值、报文不可解析 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 429 | `RATE_LIMITED` | 未满 60 s 重复请求或当日超过 10 次（不返回剩余次数） |
| 503 | `SERVICE_UNAVAILABLE` | Redis 不可用或超时 |

---

## 端点 2：患者注册（自动登录）

`POST /api/v1/patients`

**认证**：否 | **CSRF**：是 | **需求**：FR-001 ~ FR-006、FR-017、FR-025

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `phone` | string | 是 | `^1[3-9]\d{9}$` | 登录手机号，全局唯一 |
| `verificationCode` | string | 是 | `^\d{6}$` | 用途必须为 `REGISTER`，一次性 |
| `password` | string | 是 | `^[A-Za-z0-9]{6,20}$` 且至少一个字母 + 一个数字 | 不做 trim 静默修正 |
| `agreementAccepted` | boolean | 是 | 必须为 `true` | 未勾选即拒绝 |
| `agreementVersion` | string | 是 | 与服务端 `sdas.identity.agreement.version` 一致 | 不匹配视为未同意 |

```json
{
  "phone": "13800138000",
  "verificationCode": "888888",
  "password": "abc12345",
  "agreementAccepted": true,
  "agreementVersion": "v1.0"
}
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `patientId` | number | 患者主键 |
| `maskedPhone` | string | 脱敏手机号 |
| `authStatus` | string | `UNVERIFIED`（新注册固定值） |
| `consultationAllowed` | boolean | `false` |
| `accessToken` | string | JWT，TTL 7200 s |
| `accessTokenExpiresInSeconds` | number | 7200 |
| `registeredAt` | string | 注册时间 |
| `agreementVersion` | string | 已记录的协议版本 |

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "patientId": 10001,
    "maskedPhone": "138****8000",
    "authStatus": "UNVERIFIED",
    "consultationAllowed": false,
    "accessToken": "<jwt>",
    "accessTokenExpiresInSeconds": 7200,
    "registeredAt": "2026-11-07T10:15:30",
    "agreementVersion": "v1.0"
  }
}
```

**响应头**：`Set-Cookie: sdas_refresh=<token>; HttpOnly; SameSite=Lax; Path=/api/v1/patient-sessions`、`Set-Cookie: sdas_csrf=<random>; SameSite=Lax; Path=/api/v1`（非 HttpOnly）。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_PHONE_FORMAT` | 手机号格式非法 |
| 400 | `INVALID_PASSWORD_FORMAT` | 密码长度 / 字符集 / 组合不合规 |
| 400 | `INVALID_VERIFICATION_CODE_FORMAT` | 验证码非 6 位数字 |
| 400 | `AGREEMENT_NOT_ACCEPTED` | `agreementAccepted=false` 或版本不匹配 |
| 400 | `AUTHENTICATION_FAILED` | 验证码错误 / 过期 / 已使用 / 跨用途、手机号已注册（**统一文案**） |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 503 | `SERVICE_UNAVAILABLE` | MySQL / Redis 不可用或超时（不产生半成品账户） |

---

## 端点 3：登录

`POST /api/v1/patient-sessions`

**认证**：否 | **CSRF**：是 | **需求**：FR-007、FR-008、FR-023

### 请求体

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `phone` | string | 是 | `^1[3-9]\d{9}$` |
| `password` | string | 是 | 6-20 位字母数字组合；服务端按同一规则二次校验 |

```json
{ "phone": "13800138000", "password": "abc12345" }
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `patientId` | number | 患者主键 |
| `maskedPhone` | string | 脱敏手机号 |
| `authStatus` | string | `UNVERIFIED` \| `VERIFIED` |
| `consultationAllowed` | boolean | 是否已解锁线上问诊 |
| `accessToken` | string | JWT |
| `accessTokenExpiresInSeconds` | number | 7200 |

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "patientId": 10001,
    "maskedPhone": "138****8000",
    "authStatus": "VERIFIED",
    "consultationAllowed": true,
    "accessToken": "<jwt>",
    "accessTokenExpiresInSeconds": 7200
  }
}
```

响应头同端点 2（下发 `sdas_refresh` 与 `sdas_csrf`）。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_PHONE_FORMAT` | 手机号格式非法 |
| 400 | `INVALID_PASSWORD_FORMAT` | 密码格式非法（与注册同一规则） |
| 400 | `AUTHENTICATION_FAILED` | 账号不存在 / 密码错误 / 账户停用 / 失败次数达上限（**统一文案，不返回剩余次数**） |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 503 | `SERVICE_UNAVAILABLE` | MySQL / Redis 不可用或超时 |

> 频次达上限时仍返回 `AUTHENTICATION_FAILED`（FR-007 要求「任一失败原因」统一），限流只影响服务端是否执行密码校验。

---

## 端点 4：安全退出

`DELETE /api/v1/patient-sessions/current`

**认证**：是（`Authorization: Bearer`） | **CSRF**：是 | **需求**：FR-009、SC-007

### 请求

无请求体。身份由 `PatientIdExtractor` + JWT `sessionId` 确定。

### 成功响应（200）

```json
{ "code": 200, "message": "成功", "data": null }
```

**响应头**：清除 `sdas_refresh` 与 `sdas_csrf`（`Max-Age=0`）。服务端同时写 `sdas:identity:session:revoked:{sessionId}`，使当前 access token 立即不可用。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失 / 过期 / 非法 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 503 | `SERVICE_UNAVAILABLE` | Redis / MySQL 不可用或超时 |

> 幂等：重复登出返回 200，不报错（FR-017）。仅撤销当前会话，不影响该账户其他设备（`spec.md` 9.1 Q2）。

---

## 端点 5：续期凭证轮换

`PUT /api/v1/patient-sessions/current/refresh-token`

**认证**：否（凭 `sdas_refresh` Cookie） | **CSRF**：是 | **需求**：FR-008、FR-026、SC-007

### 请求

无请求体。必须携带 `Cookie: sdas_refresh=<token>` 与 `X-CSRF-Token`（取自 `sdas_csrf`）。语义为「用新凭证全量替换当前续期凭证」。

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `accessToken` | string | 新 JWT |
| `accessTokenExpiresInSeconds` | number | 7200 |

```json
{
  "code": 200,
  "message": "成功",
  "data": { "accessToken": "<new-jwt>", "accessTokenExpiresInSeconds": 7200 }
}
```

**响应头**：`Set-Cookie: sdas_refresh=<new-token>; HttpOnly; SameSite=Lax; Path=/api/v1/patient-sessions`（轮换）。旧 refresh token 立即置 `REVOKED`，再次使用即失败。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 未携带 `sdas_refresh` Cookie |
| 400 | `AUTHENTICATION_FAILED` | 旧凭证已被轮换 / 已撤销 / 已过期 / 并发轮换失败（**统一文案**） |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 503 | `SERVICE_UNAVAILABLE` | MySQL / Redis 不可用或超时 |

> 并发轮换只允许一个成功，另一个返回 `AUTHENTICATION_FAILED`；禁止出现两个同时可用的续期凭证（FR-026、SC-008）。

---

## 端点 6：重置密码

`POST /api/v1/password-resets`

**认证**：否 | **CSRF**：是 | **需求**：FR-010、FR-017、FR-025

### 请求体

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `phone` | string | 是 | `^1[3-9]\d{9}$` |
| `verificationCode` | string | 是 | `^\d{6}$`，用途必须为 `RESET_PASSWORD` |
| `newPassword` | string | 是 | 与注册同一密码规则 |

```json
{ "phone": "13800138000", "verificationCode": "888888", "newPassword": "xyz98765" }
```

### 成功响应（200）

```json
{ "code": 200, "message": "成功", "data": null }
```

服务端行为：更新 `password_hash`、撤销该账户**全部**会话与续期凭证（`SessionStatus=REVOKED`）、清理该手机号 `RESET_PASSWORD` 用途的验证码、写 `PASSWORD_RESET` 审计事件。旧密码与旧凭证立即失效。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_PHONE_FORMAT` | 手机号格式非法 |
| 400 | `INVALID_VERIFICATION_CODE_FORMAT` | 验证码非 6 位数字 |
| 400 | `INVALID_PASSWORD_FORMAT` | 新密码不合规 |
| 400 | `AUTHENTICATION_FAILED` | 验证码错误 / 过期 / 已使用 / 跨用途、手机号未注册（**统一文案**） |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 503 | `SERVICE_UNAVAILABLE` | MySQL / Redis 不可用或超时（不产生部分更新） |

---

## 端点 7：提交实名认证

`POST /api/v1/identity-verifications`

**认证**：是（`Authorization: Bearer`） | **CSRF**：是 | **需求**：FR-011 ~ FR-014、FR-019、FR-025

### 请求体

| 字段 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `realName` | string | 是 | 1-50 个字符，禁止控制字符，不做 trim 后静默修正 |
| `idCardNumber` | string | 是 | 18 位，前 17 位数字 + 校验位（数字或 `X`），校验位按 GB 11643 加权算法有效 |

```json
{ "realName": "张三", "idCardNumber": "11010519491231002X" }
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `authStatus` | string | `VERIFIED` |
| `consultationAllowed` | boolean | `true` |
| `verifiedAt` | string | 认证通过时间 |
| `maskedName` | string | 脱敏姓名，如 `张*` |
| `maskedIdCard` | string | 脱敏证件号，如 `110***********002X` |

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "authStatus": "VERIFIED",
    "consultationAllowed": true,
    "verifiedAt": "2026-11-07T11:02:10",
    "maskedName": "张*",
    "maskedIdCard": "110***********002X"
  }
}
```

**幂等**：已认证账户重复提交返回 200 且状态不变，不写重复认证记录（FR-017）。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_NAME_FORMAT` | 姓名为空 / 超长 / 含控制字符 |
| 400 | `INVALID_ID_CARD_FORMAT` | 非 18 位 / 校验位错误 / 含非法字符 |
| 400 | `AUTHENTICATION_FAILED` | 姓名与证件不匹配、证件号未在白名单、证件号已被其他账户绑定（**任何情况均同一文案**） |
| 401 | `UNAUTHORIZED` | 凭证缺失 / 过期 / 非法 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 429 | `RATE_LIMITED` | 15 分钟窗口内尝试次数达上限（不返回剩余次数、不永久锁定） |
| 503 | `SERVICE_UNAVAILABLE` | MySQL 不可用或白名单查询超时（认证状态保持 `UNVERIFIED`） |

> 认证未通过时**不得**返回 `IdentityVerificationResult` 枚举，不得返回「白名单中是否存在」「还差几次」等信息（FR-019、SC-005）。

---

## 端点 8：查询认证状态

`GET /api/v1/patients/current/identity-status`

**认证**：是 | **CSRF**：否 | **需求**：FR-015、FR-016、FR-022

### 请求

无请求体、无查询参数；身份由 `PatientIdExtractor` 确定。

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `authStatus` | string | `UNVERIFIED` \| `VERIFIED` |
| `consultationAllowed` | boolean | `authStatus == VERIFIED` 时为 `true` |
| `verifiedAt` | string \| null | 未认证时为 `null` |
| `maskedName` | string \| null | 未认证时为 `null` |
| `maskedIdCard` | string \| null | 未认证时为 `null` |

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "authStatus": "UNVERIFIED",
    "consultationAllowed": false,
    "verifiedAt": null,
    "maskedName": null,
    "maskedIdCard": null
  }
}
```

**问诊门禁**：本端点只提供判定结果；线上问诊模块调用时，`consultationAllowed=false` 须以 `IDENTITY_NOT_VERIFIED(403)` 拒绝并引导至「实名认证」，同时写 `CONSULTATION_GATE_DENIED` 审计事件（FR-015、SC-003）。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失 / 过期 / 非法 |
| 503 | `SERVICE_UNAVAILABLE` | MySQL 不可用或超时 |

---

## 通用异常对照

| 场景 | HTTP | `code` | 说明 |
|------|------|--------|------|
| 路径不存在 | 404 | 404 | `NOT_FOUND` |
| 方法不匹配 | 405 | 405 | `METHOD_NOT_ALLOWED` |
| 请求体非 JSON / 字段类型错误 | 400 | 400 | `BAD_REQUEST` |
| 未登录访问受保护端点 | 401 | 401 | `UNAUTHORIZED` |
| 修改类请求缺 `X-CSRF-Token` 或值不一致 | 403 | 403 | `CSRF_TOKEN_MISMATCH` |
| MySQL / Redis 不可用或超时 | 503 | 503 | `SERVICE_UNAVAILABLE` |
| 未捕获异常 | 500 | 500 | `INTERNAL_ERROR`（不返回堆栈） |

**响应字段约束**：任何响应体不得包含 `password`、`passwordHash`、`verificationCode`、`phoneCipher`、`idCardCipher`、`phoneHash`、`idCardHash`、`refreshToken`（明文）等字段。
