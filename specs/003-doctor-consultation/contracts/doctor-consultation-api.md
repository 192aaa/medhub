# 医生接诊 API 契约

**功能**：`003-doctor-consultation`
**枚举与 `code` 来源**：`../data-model.md`（1.1–1.7）
**Mock 数据**：`mocks.md`（M1–Mxx）
**通用约定**：`README.md`（统一响应、认证、CSRF、敏感信息约束、一致性清单）

> 本文件定义 9 个端点的请求 / 响应结构。响应示例中的时间均为 `yyyy-MM-dd'T'HH:mm:ss`。

---

## 端点 1：医生登录

`POST /api/v1/workbench/sessions`

**认证**：否 | **CSRF**：是 | **需求**：FR-001、FR-020

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `username` | string | 是 | 1–64 | 登录名 |
| `password` | string | 是 | 1–100 | 密码 |

```json
{ "username": "doctor_zhang", "password": "********" }
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `accessToken` | string | 医生 JWT（含「医生」角色） |
| `refreshToken` | string | 刷新令牌 |
| `doctorId` | number | 医生主键 |
| `nameMask` | string | 脱敏姓名 |
| `title` | string \| null | 职称 |
| `departmentId` | number | 所属科室 |
| `expiresIn` | number | 过期秒数 |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "accessToken": "eyJ...", "refreshToken": "eyJ...",
    "doctorId": 101, "nameMask": "张*", "title": "主任医师",
    "departmentId": 1, "expiresIn": 86400
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `AUTHENTICATION_FAILED` | 账号或密码错误、账号被禁用（统一模糊提示） |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 2：查询待接诊 / 已接诊列表

`GET /api/v1/workbench/consultations?status={PENDING|IN_PROGRESS}`

**认证**：是 | **CSRF**：否 | **需求**：FR-002

### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `status` | string | 否 | `PENDING`（默认，待接诊）或 `IN_PROGRESS`（已接诊） |

### 成功响应（200）

`data` 为数组，元素字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `consultationId` | number | 问诊单主键 |
| `patientName` | string | 脱敏患者姓名，如 `王*` |
| `complaint` | string | 主诉 |
| `status` | string | `PENDING`/`IN_PROGRESS` |
| `submittedAt` | string | 提交时间 |

```json
{
  "code": 200, "message": "成功",
  "data": [
    { "consultationId": 701, "patientName": "王*", "complaint": "咳嗽伴低热三天", "status": "PENDING", "submittedAt": "2026-11-08T10:30:00" }
  ]
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `DOCTOR_ROLE_REQUIRED` | 非医生角色访问 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 3：查询问诊单详情

`GET /api/v1/workbench/consultations/{consultationId}`

**认证**：是 | **CSRF**：否 | **需求**：FR-003

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `consultationId` | number | 问诊单主键 |

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `consultationId` | number | 问诊单主键 |
| `patientName` | string | 脱敏患者姓名 |
| `complaint` | string | 主诉 |
| `description` | string \| null | 选填描述 |
| `images` | array | 图片列表 `{ imageId, url }` |
| `summary` | string \| null | AI 病情摘要 |
| `status` | string | `ConsultationStatus` |
| `acceptedAt` | string \| null | 接诊时间（未接诊为 `null`） |
| `createdAt` | string | 提交时间 |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "consultationId": 701, "patientName": "王*", "complaint": "咳嗽伴低热三天",
    "description": "夜间加重，无痰",
    "images": [{ "imageId": 601, "url": "/static/consultation-images/abc123.jpg" }],
    "summary": "患者主诉咳嗽伴低热三天，建议排查呼吸道感染。",
    "status": "PENDING", "acceptedAt": null, "createdAt": "2026-11-08T10:30:00"
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `DOCTOR_ROLE_REQUIRED` | 非医生角色访问 |
| 403 | `CONSULTATION_FORBIDDEN` | 越权访问他人问诊单 |
| 404 | `CONSULTATION_NOT_FOUND` | 问诊单不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 4：接诊

`POST /api/v1/workbench/consultations/{consultationId}/accept`

**认证**：是 | **CSRF**：是 | **需求**：FR-004、FR-006、SC-005

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `consultationId` | number | 问诊单主键 |
| `status` | string | `IN_PROGRESS` |
| `acceptedAt` | string | 接诊时间 |
| `doctorId` | number | 接诊医生 |

```json
{
  "code": 200, "message": "成功",
  "data": { "consultationId": 701, "status": "IN_PROGRESS", "acceptedAt": "2026-11-08T11:00:00", "doctorId": 101 }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `DOCTOR_ROLE_REQUIRED` | 非医生角色访问 |
| 403 | `CONSULTATION_FORBIDDEN` | 越权接诊他人问诊单 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 404 | `CONSULTATION_NOT_FOUND` | 问诊单不存在 |
| 400 | `INVALID_CONSULTATION_STATUS` | 已接诊/已完成，不允许再次接诊 |
| 409 | `CONSULTATION_ACCEPT_CONFLICT` | 并发接诊，仅一人成功 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 5：查询对话消息

`GET /api/v1/workbench/consultations/{consultationId}/messages`

**认证**：是 | **CSRF**：否 | **需求**：FR-005

### 成功响应（200）

`data` 为数组，元素字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `messageId` | number | 消息主键 |
| `senderType` | string | `DOCTOR`/`PATIENT`/`SYSTEM` |
| `content` | string | 内容 |
| `createdAt` | string | 发送时间 |

```json
{
  "code": 200, "message": "成功",
  "data": [
    { "messageId": 801, "senderType": "PATIENT", "content": "医生您好", "createdAt": "2026-11-08T11:02:00" },
    { "messageId": 802, "senderType": "DOCTOR", "content": "请描述具体症状", "createdAt": "2026-11-08T11:03:00" }
  ]
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `DOCTOR_ROLE_REQUIRED` | 非医生角色访问 |
| 403 | `CONSULTATION_FORBIDDEN` | 越权访问他人问诊单 |
| 404 | `CONSULTATION_NOT_FOUND` | 问诊单不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 6：发送回复

`POST /api/v1/workbench/consultations/{consultationId}/messages`

**认证**：是 | **CSRF**：是 | **需求**：FR-005、FR-015、SC-004

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `content` | string | 是 | 1–1000 字符，禁控制字符 | 回复内容 |

```json
{ "content": "请先测量体温并多休息" }
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `messageId` | number | 消息主键 |
| `senderType` | string | `DOCTOR` |
| `content` | string | 回复内容（回显） |
| `createdAt` | string | 发送时间 |

```json
{
  "code": 200, "message": "成功",
  "data": { "messageId": 803, "senderType": "DOCTOR", "content": "请先测量体温并多休息", "createdAt": "2026-11-08T11:04:00" }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_REPLY_FORMAT` | 回复为空/超 1000 字/含控制字符 |
| 400 | `INVALID_CONSULTATION_STATUS` | 未接诊（仍 `PENDING`）即回复 |
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `DOCTOR_ROLE_REQUIRED` | 非医生角色访问 |
| 403 | `CONSULTATION_FORBIDDEN` | 越权操作他人问诊单 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 404 | `CONSULTATION_NOT_FOUND` | 问诊单不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 7：开具电子处方

`POST /api/v1/workbench/consultations/{consultationId}/prescriptions`

**认证**：是 | **CSRF**：是 | **需求**：FR-007–FR-013、SC-006、SC-007

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `items` | array | 是 | 1–10 项 | 药品明细 |

`items` 元素：

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `drugId` | number | 是 | 存在于目录且启用 | 药品主键 |
| `spec` | string \| null | 否 | ≤128 | 规格（默认取目录值） |
| `dosage` | string | 是 | 1–255 | 用法用量 |
| `caution` | string \| null | 否 | ≤500 | 注意事项 |

```json
{
  "items": [
    { "drugId": 901, "spec": "0.25g*20", "dosage": "每日三次，每次一片", "caution": "饭后服用" }
  ]
}
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `prescriptionId` | number | 处方主键 |
| `consultationId` | number | 关联问诊单 |
| `status` | string | `SIGNED` |
| `items` | array | 药品明细（含 `drugName`、`spec`、`dosage`、`caution`） |
| `signedAt` | string | 签名时间 |
| `validUntil` | string | 有效期截止（+3 天） |
| `qrCode` | string | 二维码内容：`v1` 令牌 URL（**由 004 修订**，见下注） |
| `watermark` | string | 水印内容 |

> **由 004 修订**：原「明文 `?prescriptionId=&sig=`（32 位签名前缀）」自 `004-prescription-dispensing` 起替换为
> `v1.<cipher>.<sig>` 令牌 URL（模板 `sdas.pharmacy.qr.verify-url-template`，默认
> `http://localhost:5183/pharmacy/verify?t=%s`）。令牌把处方号加密（不可枚举）并附 RSA 签名（可离机验真），
> 唯一来源见 `specs/004-prescription-dispensing/data-model.md` 1.7。字段名 `qrCode` 保持不变。

```json
{
  "code": 200, "message": "成功",
  "data": {
    "prescriptionId": 1001, "consultationId": 701, "status": "SIGNED",
    "items": [{ "drugId": 901, "drugName": "阿莫西林胶囊", "spec": "0.25g*20", "dosage": "每日三次，每次一片", "caution": "饭后服用" }],
    "signedAt": "2026-11-08T11:05:00", "validUntil": "2026-11-11T11:05:00",
    "qrCode": "http://localhost:5183/pharmacy/verify?t=v1.<cipher>.<sig>",
    "watermark": "示例医院 1001 2026-11-08T11:05:00"
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_CONSULTATION_STATUS` | 未接诊（仍 `PENDING`）即开方 |
| 400 | `INVALID_PRESCRIPTION_FORMAT` | 药品明细为空/字段越界 |
| 400 | `DRUG_NOT_IN_CATALOG` | 自由输入药品名 / 不在目录 |
| 404 | `DRUG_NOT_FOUND` | 药品不存在或已停用 |
| 409 | `PRESCRIPTION_ALREADY_EXISTS` | 一张问诊单重复开方 |
| 400 | `PRESCRIPTION_SIGN_FAILED` | 签名密钥缺失或签名失败 |
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `DOCTOR_ROLE_REQUIRED` | 非医生角色访问 |
| 403 | `CONSULTATION_FORBIDDEN` | 越权操作他人问诊单 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 404 | `CONSULTATION_NOT_FOUND` | 问诊单不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 8：查询处方详情

`GET /api/v1/workbench/prescriptions/{prescriptionId}`

**认证**：是 | **CSRF**：否 | **需求**：FR-011、FR-013

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `prescriptionId` | number | 处方主键 |
| `consultationId` | number | 关联问诊单 |
| `status` | string | `PrescriptionStatus` |
| `items` | array | 药品明细 |
| `signedAt` | string \| null | 签名时间 |
| `validUntil` | string \| null | 有效期截止 |
| `expired` | boolean | 是否已过期 |
| `qrCode` | string \| null | 二维码内容：`v1` 令牌 URL（**由 004 修订**） |
| `watermark` | string \| null | 水印内容 |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "prescriptionId": 1001, "consultationId": 701, "status": "SIGNED",
    "items": [{ "drugId": 901, "drugName": "阿莫西林胶囊", "spec": "0.25g*20", "dosage": "每日三次，每次一片", "caution": "饭后服用" }],
    "signedAt": "2026-11-08T11:05:00", "validUntil": "2026-11-11T11:05:00",
    "expired": false, "qrCode": "http://localhost:5183/pharmacy/verify?t=v1.<cipher>.<sig>",
    "watermark": "示例医院 1001 2026-11-08T11:05:00"
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `DOCTOR_ROLE_REQUIRED` | 非医生角色访问 |
| 403 | `CONSULTATION_FORBIDDEN` | 越权访问他人处方 |
| 404 | `PRESCRIPTION_NOT_FOUND` | 处方不存在（**由 004 修订**：原误用 `CONSULTATION_NOT_FOUND`） |
| 400 | `PRESCRIPTION_EXPIRED` | 处方已过期（仅下游核验场景） |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 9：AI 辅助决策

`POST /api/v1/workbench/consultations/{consultationId}/ai-decision`

**认证**：是 | **CSRF**：是 | **需求**：FR-014、FR-017、SC-009

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `query` | string \| null | 否 | ≤500 | 医生附加提问（可选） |

```json
{ "query": "该患者是否有用药禁忌" }
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `decisionId` | number | 决策会话主键 |
| `suggestions` | array | 辅助参考条目（字符串列表） |
| `disclaimer` | string | 固定声明「辅助参考，不作为诊断结论」 |
| `modelUsed` | string \| null | 模型名 |
| `status` | string | `SUCCESS` / `TIMEOUT_FALLBACK` |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "decisionId": 1101,
    "suggestions": ["疑似上呼吸道感染，建议结合查体。", "阿莫西林过敏者禁用。"],
    "disclaimer": "本内容为 AI 辅助参考，不作为诊断结论。",
    "modelUsed": "DeepSeek-Flash-1017", "status": "SUCCESS"
  }
}
```

> AI 超时/异常降级：返回 `status=TIMEOUT_FALLBACK`、`suggestions=[]`，HTTP 200，不阻塞接诊与开方（SC-009）。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_CONSULTATION_STATUS` | 未接诊即请求辅助决策 |
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `DOCTOR_ROLE_REQUIRED` | 非医生角色访问 |
| 403 | `CONSULTATION_FORBIDDEN` | 越权访问他人问诊单 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 404 | `CONSULTATION_NOT_FOUND` | 问诊单不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 通用异常对照（沿用 001/002）

| 场景 | HTTP | `code` | 说明 |
|------|------|--------|------|
| 路径不存在 | 404 | 404 | `NOT_FOUND` |
| 方法不匹配 | 405 | 405 | `METHOD_NOT_ALLOWED` |
| 请求体非 JSON / 字段类型错误 | 400 | 400 | `BAD_REQUEST` |
| 未登录访问受保护端点 | 401 | 401 | `UNAUTHORIZED` |
| 修改类请求缺 `X-CSRF-Token` | 403 | 403 | `CSRF_TOKEN_MISMATCH` |
| 依赖不可用/超时 | 503 | 503 | `SERVICE_UNAVAILABLE` |
| 未捕获异常 | 500 | 500 | `INTERNAL_ERROR`（不返回堆栈） |

**响应字段约束**：任何响应体不得包含 `password`、医生完整真实姓名、处方完整签名值明文、患者完整手机号/身份证号；仅以脱敏值或内部 ID 表达。
