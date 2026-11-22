# Mock 数据（M1–M15）

**功能**：`001-patient-identity`
**唯一来源**：本文件。前端 `web/src/api/mocks/` 与 Playwright `page.route()` 只能从此处同步，禁止凭空手写（原则 VI）。
**同步要求**：契约变更时同步本文件 → `web/src/api/mocks/` → `web/src/api/types.ts` → E2E 用例断言。

## 通用约定

- 所有响应均为 `{ code, message, data }`；`message` 取自 `data-model.md` 1.9，不得改写。
- `data` 为 `null` 时字段仍须存在。
- 时间统一 `yyyy-MM-dd'T'HH:mm:ss`。
- 手机号统一用 `138****8000` 脱敏展示；mock 中不出现明文密码、验证码明文以外的敏感值。
- 固定验证码在 mock 请求体中写 `888888`（仅 dev/test 契约演示，生产禁用）。

| 编号 | 场景 | 端点 | HTTP | `code` | 使用位置 |
|------|------|------|------|--------|----------|
| M1 | 签发验证码成功 | `POST /v1/verification-codes` | 200 | 200 | 注册页、忘记密码页 |
| M2 | 签发验证码超限 | 同上 | 429 | 429 | 注册页异常用例 |
| M3 | 注册成功并自动登录 | `POST /v1/patients` | 200 | 200 | 用户故事 1 主流程 |
| M4 | 注册失败：验证码错误 | 同上 | 400 | 400 | 用户故事 1 异常 |
| M5 | 注册失败：密码格式不合规 | 同上 | 400 | 400 | 前端规则提示 |
| M6 | 登录成功（已认证） | `POST /v1/patient-sessions` | 200 | 200 | 用户故事 2 主流程 |
| M7 | 登录失败：统一模糊提示 | 同上 | 400 | 400 | 用户故事 2 异常 |
| M8 | 安全退出成功 | `DELETE /v1/patient-sessions/current` | 200 | 200 | 个人中心 |
| M9 | 续期成功 | `PUT /v1/patient-sessions/current/refresh-token` | 200 | 200 | 会话保持 |
| M10 | 续期失败：旧凭证复用 | 同上 | 400 | 400 | 多 Tab 并发用例 |
| M11 | 重置密码成功 | `POST /v1/password-resets` | 200 | 200 | 用户故事 4 |
| M12 | 实名认证成功 | `POST /v1/identity-verifications` | 200 | 200 | 用户故事 3 主流程 |
| M13 | 实名认证未通过 | 同上 | 400 | 400 | 用户故事 3 异常 |
| M14 | 查询认证状态（未认证） | `GET /v1/patients/current/identity-status` | 200 | 200 | 个人中心、门禁 |
| M15 | 依赖降级 | 任意端点 | 503 | 503 | 超时 / 降级用例 |

> 前端具体路径为 `/v1/...`（不含 `/api`），运行时拼为 `/api/v1/...`。

---

## M1 签发验证码成功

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

`data` 中**不含** `code` / `verificationCode` 字段；任何出现验证码明文的 mock 均视为契约违规。

## M2 签发验证码超限

```json
{ "code": 429, "message": "操作过于频繁，请稍后重试", "data": null }
```

## M3 注册成功并自动登录

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "patientId": 10001,
    "maskedPhone": "138****8000",
    "authStatus": "UNVERIFIED",
    "consultationAllowed": false,
    "accessToken": "mock-access-token-new-patient",
    "accessTokenExpiresInSeconds": 7200,
    "registeredAt": "2026-11-07T10:15:30",
    "agreementVersion": "v1.0"
  }
}
```

## M4 注册失败：验证码错误

```json
{ "code": 400, "message": "账号或密码错误，或验证信息无效", "data": null }
```

与 M7、M10、M13 的 `message` 必须逐字一致（SC-005）。

## M5 注册失败：密码格式不合规

```json
{ "code": 400, "message": "密码须为 6-20 位字母数字组合，且至少含一个字母和一个数字", "data": null }
```

## M6 登录成功（已认证）

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "patientId": 10001,
    "maskedPhone": "138****8000",
    "authStatus": "VERIFIED",
    "consultationAllowed": true,
    "accessToken": "mock-access-token-verified",
    "accessTokenExpiresInSeconds": 7200
  }
}
```

## M7 登录失败：统一模糊提示

```json
{ "code": 400, "message": "账号或密码错误，或验证信息无效", "data": null }
```

## M8 安全退出成功

```json
{ "code": 200, "message": "成功", "data": null }
```

## M9 续期成功

```json
{
  "code": 200,
  "message": "成功",
  "data": { "accessToken": "mock-access-token-rotated", "accessTokenExpiresInSeconds": 7200 }
}
```

## M10 续期失败：旧凭证复用

```json
{ "code": 400, "message": "账号或密码错误，或验证信息无效", "data": null }
```

## M11 重置密码成功

```json
{ "code": 200, "message": "成功", "data": null }
```

## M12 实名认证成功

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

> `maskedName` / `maskedIdCard` 为脱敏展示值，mock 中不得放入完整真实身份信息。

## M13 实名认证未通过

```json
{ "code": 400, "message": "账号或密码错误，或验证信息无效", "data": null }
```

与白名单未命中、姓名不匹配、证件号已被绑定三种情况共用同一响应，禁止差异化文案或附加原因字段。

## M14 查询认证状态（未认证）

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

## M15 依赖降级

```json
{ "code": 503, "message": "服务暂时不可用，请稍后重试", "data": null }
```

---

## 使用约束

1. **Mock E2E**：Playwright 用 `page.route('**/api/v1/**')` 注入本文件 mock，按 `method + path` 匹配；每个用例只覆盖被测端点的成功与失败各一条。
2. **真实后端 E2E**：不使用本文件，改由测试夹具动态生成随机手机号与白名单记录，且**必须断言响应 `code`**（原则 VI）。
3. **前端单测**：Vitest 的 mock 从 `web/src/api/mocks/` 引入，不得在用例内联自造 `code` / `message`。
4. **禁止事项**：在 mock 中添加契约中不存在的字段；改写 `message` 文案；返回验证码明文；使用真实姓名与身份证号。
5. **一致性**：本文件每个 `code` 必须存在于 `data-model.md` 1.9；新增 mock 须先在 `data-model.md` 定义取值。