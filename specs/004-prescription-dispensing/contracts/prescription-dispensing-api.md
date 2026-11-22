# 处方取药 API 契约

**功能**：`004-prescription-dispensing`
**枚举与 `code` 来源**：`../data-model.md`（1.1–1.7）
**Mock 数据**：`mocks.md`（M1–M12）
**通用约定**：`README.md`（统一响应、认证、CSRF、敏感信息、验真语义、一致性清单）

> 本文件定义 5 个端点的请求 / 响应结构。示例时间均为 `yyyy-MM-dd'T'HH:mm:ss`。

---

## 端点 1：药店登录

`POST /api/v1/pharmacy/sessions`

**认证**：否 | **CSRF**：是 | **需求**：FR-001、FR-002

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `username` | string | 是 | 1–64 | 药店登录名 |
| `password` | string | 是 | 1–100 | 口令 |

```json
{ "username": "pharmacy_hp", "password": "********" }
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `accessToken` | string | 药店 JWT（`role="PHARMACY"`），HS256，有效期 7200 s |
| `pharmacyId` | number | 药店账号主键 |
| `nameMask` | string | 脱敏操作人姓名，如「王*」 |
| `pharmacyName` | string | 药店名称 |
| `expiresIn` | number | 过期秒数 |

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "pharmacyId": 1,
    "nameMask": "王*",
    "pharmacyName": "示例大药房（总部店）",
    "expiresIn": 7200
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `AUTHENTICATION_FAILED` | 账号不存在 / 口令错误 / 账号禁用（统一模糊提示，不区分原因） |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

### 约定

- 不签发 Refresh Token（与 003 医生端一致）；过期后重新登录。
- 登录成功与失败均写审计（`PHARMACY_LOGIN_SUCCESS` / `PHARMACY_LOGIN_FAILED`），主体为 `username` 的 SHA-256 前 16 hex（不存明文）。

---

## 端点 2：处方验真

`POST /api/v1/pharmacy/prescription-verifications`

**认证**：药店（`role="PHARMACY"`） | **CSRF**：是 | **需求**：FR-006、FR-007、FR-008、FR-009

### 请求体（`qrContent` 与「`prescriptionId` + `verifyCode`」二选一，互斥）

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `qrContent` | string | 条件 | 8–1024 | 扫码所得原始内容，可为完整 URL 或裸令牌 |
| `prescriptionId` | number | 条件 | > 0 | 手工输入处方号 |
| `verifyCode` | string | 条件 | 12 位 `[A-Za-z0-9_-]` | 手工输入验真码（二维码下方展示） |

```json
{ "qrContent": "http://localhost:5183/pharmacy/verify?t=v1.xxxxx.yyyyy" }
```

```json
{ "prescriptionId": 7001, "verifyCode": "Ab3dE9fG7hJk" }
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `verificationResult` | string | 固定 `VALID`（失败走异常分支） |
| `prescriptionId` | number | 处方主键 |
| `doctorNameMask` | string | 开方医生脱敏名 |
| `signedAt` | string | 签名时间 |
| `validUntil` | string | 有效期截止 |
| `expired` | boolean | 服务端时间是否已过期 |
| `status` | string | 003 处方状态（`SIGNED`/`EXPIRED`/`REVOKED`） |
| `dispenseStatus` | string | `PENDING` / `DISPENSED` / `EXPIRED` / `REVOKED` |
| `dispensable` | boolean | 是否可取药（等价 `dispenseStatus == PENDING`） |
| `dispensedAt` | string \| null | 已取药时间 |
| `dispensedByMask` | string \| null | 核销人脱敏名 |
| `dispenseRecordId` | number \| null | 核销记录主键 |
| `watermark` | string | 处方水印（如「示例医院 电子处方」） |
| `verifyCode` | string | 12 位验真码（回显，便于店员手工核对） |
| `items` | array | 药品明细，字段同下 |

`items[]`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `drugId` | number | 药品主键 |
| `drugName` | string | 药品名 |
| `spec` | string \| null | 规格 |
| `dosage` | string | 用法用量 |
| `caution` | string \| null | 注意事项 |

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "verificationResult": "VALID",
    "prescriptionId": 7001,
    "doctorNameMask": "张*",
    "signedAt": "2026-11-08T10:12:00",
    "validUntil": "2026-11-11T10:12:00",
    "expired": false,
    "status": "SIGNED",
    "dispenseStatus": "PENDING",
    "dispensable": true,
    "dispensedAt": null,
    "dispensedByMask": null,
    "dispenseRecordId": null,
    "watermark": "示例医院 电子处方",
    "verifyCode": "Ab3dE9fG7hJk",
    "items": [
      {
        "drugId": 901,
        "drugName": "阿莫西林胶囊",
        "spec": "0.25g*20",
        "dosage": "口服，一次 2 粒，一日 3 次",
        "caution": "青霉素过敏者禁用"
      }
    ]
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_VERIFICATION_PROOF` | 令牌结构不合法 / RSA 验签失败 / GCM 解密失败 / 令牌 `exp` 过期 / 验真码不匹配 / 旧格式二维码 |
| 400 | `PRESCRIPTION_NOT_SIGNED` | 处方为 `DRAFT`（未签名，不构成可展示处方） |
| 400 | `BAD_REQUEST` | 请求体两个分支同时为空或同时提供、字段超长或非法 |
| 403 | `PHARMACY_ROLE_REQUIRED` | 非药店角色 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 401 | `UNAUTHORIZED` | 未登录 / token 失效 |
| 404 | `PRESCRIPTION_NOT_FOUND` | 令牌验签通过但处方不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

### 约定（重要）

1. **可重复调用**：本端点为只读操作，同一二维码/验真码可被任意次调用；不消费令牌、不改变状态。
2. `PRESCRIPTION_EXPIRED` **不在此端点抛出**：处方过期属于可展示的业务状态，用 `200 + dispenseStatus=EXPIRED + dispensable=false` 表达，便于店员看到「已过期」而非错误页。
3. `REVOKED` 也返回 **200 + `dispenseStatus=REVOKED` + `dispensable=false`**（与 `EXPIRED` 对称）：药店必须看到「已作废」状态，而不是错误页；只有核销端点对同两种状态返回 400（写操作被拒绝）。
4. `DRAFT`（未签名）例外：返回 400 `PRESCRIPTION_NOT_SIGNED`，且响应不含任何处方字段。
5. 每次调用写审计 `PRESCRIPTION_VERIFICATION_SUCCESS` / `PRESCRIPTION_VERIFICATION_FAILED`；失败时响应不含任何处方字段（FR-007）。
6. 服务端不信任前端解析结果：`qrContent` 若为 URL，自行提取 `t` 参数后校验。
7. **URL 直达入口**：手机相机扫到 `/pharmacy/verify?t=...` 时，前端须读取 `t` 自动发起一次验真（未登录则先登录并在回跳时保留 `t`），等价于扫码入口。

---

## 端点 3：取药核销

`POST /api/v1/pharmacy/prescriptions/{prescriptionId}/dispensing`

**认证**：药店 | **CSRF**：是 | **需求**：FR-010、FR-011、FR-012、FR-013

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `prescriptionId` | number | 处方主键（须与凭证内 `pid` 一致） |

### 请求体（同样二选一，必须提供凭证）

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `qrContent` | string | 条件 | 8–1024 | 完整二维码内容（推荐） |
| `verifyCode` | string | 条件 | 12 位 | 手工输入验真码 |

```json
{ "qrContent": "http://localhost:5183/pharmacy/verify?t=v1.xxxxx.yyyyy" }
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `dispenseResult` | string | `DISPENSED`（首次）/ `ALREADY_DISPENSED`（重复或并发） |
| `idempotent` | boolean | `false`（首次）/ `true`（重复） |
| `prescriptionId` | number | 处方主键 |
| `dispenseRecordId` | number | 核销记录主键（重复时为既有记录） |
| `dispenseStatus` | string | 固定 `DISPENSED` |
| `dispensedAt` | string | 核销时间（重复时为首次时间） |
| `dispensedByMask` | string | 核销人脱敏名（重复时为首次操作人） |
| `pharmacyName` | string | 当前药店名 |

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "dispenseResult": "DISPENSED",
    "idempotent": false,
    "prescriptionId": 7001,
    "dispenseRecordId": 31,
    "dispenseStatus": "DISPENSED",
    "dispensedAt": "2026-11-08T10:30:00",
    "dispensedByMask": "王*",
    "pharmacyName": "示例大药房（总部店）"
  }
}
```

重复调用：

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "dispenseResult": "ALREADY_DISPENSED",
    "idempotent": true,
    "prescriptionId": 7001,
    "dispenseRecordId": 31,
    "dispenseStatus": "DISPENSED",
    "dispensedAt": "2026-11-08T10:30:00",
    "dispensedByMask": "王*",
    "pharmacyName": "示例大药房（西湖店）"
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_VERIFICATION_PROOF` | 凭证无效 / 凭证内 `pid` 与路径参数不一致 / 未提供凭证 |
| 400 | `PRESCRIPTION_NOT_SIGNED` | 处方为 `DRAFT` |
| 400 | `PRESCRIPTION_REVOKED` | 处方为 `REVOKED` |
| 400 | `PRESCRIPTION_EXPIRED` | 无核销记录且 `now > valid_until` |
| 403 | `PHARMACY_ROLE_REQUIRED` / `CSRF_TOKEN_MISMATCH` | 角色不符 / CSRF 失败 |
| 401 | `UNAUTHORIZED` | 未登录 |
| 404 | `PRESCRIPTION_NOT_FOUND` | 处方不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时（事务回滚，不产生半成品） |

### 约定（重要）

1. **幂等**：重复/并发核销统一 `200 + ALREADY_DISPENSED`，**不使用 409**；`uk_dispense_prescription` 唯一键兜底。
2. **不可回退**：无撤销/退药端点（Q3 范围外）。
3. **凭证必填**：防止仅凭自增 ID 越权核销；服务端重新验签，`pid` 必须与路径一致。
4. **事务**：核销记录插入与审计写入在同一事务内；唯一键冲突时回滚并读回既有记录。
5. 审计事件：`PRESCRIPTION_DISPENSED`（首次成功）/ `PRESCRIPTION_DISPENSE_REPEATED`（重复或并发）/ `PRESCRIPTION_DISPENSE_FAILED`（凭证无效、过期、作废、草稿等任一失败）。

---

## 端点 4：患者处方列表

`GET /api/v1/prescriptions?page=1&pageSize=10`

**认证**：患者 | **CSRF**：否 | **需求**：FR-003

### 查询参数

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `page` | number | 否 | 1 | 页码，≥1 |
| `pageSize` | number | 否 | 10 | 每页条数，1–50，超限按 50 截断 |

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `items[].prescriptionId` | number | 处方主键 |
| `items[].doctorNameMask` | string | 开方医生脱敏名 |
| `items[].signedAt` | string | 签名时间 |
| `items[].validUntil` | string | 有效期截止 |
| `items[].status` | string | 003 处方状态 |
| `items[].dispenseStatus` | string | 派生取药状态 |
| `page` / `pageSize` / `total` | number | 分页信息 |

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "items": [
      {
        "prescriptionId": 7001,
        "doctorNameMask": "张*",
        "signedAt": "2026-11-08T10:12:00",
        "validUntil": "2026-11-11T10:12:00",
        "status": "SIGNED",
        "dispenseStatus": "PENDING"
      }
    ],
    "page": 1,
    "pageSize": 10,
    "total": 1
  }
}
```

### 约定

- 仅返回**本人**且 `status <> 'DRAFT'` 的处方（FR-003）；按 `signed_at DESC, id DESC` 排序。
- 空列表返回 `items: []`、`total: 0`，不返回 404。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 未登录 / 药店或医生 token 访问 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用 |

---

## 端点 5：患者处方详情（含验真二维码）

`GET /api/v1/prescriptions/{prescriptionId}`

**认证**：患者 | **CSRF**：否 | **需求**：FR-004、FR-005、FR-014

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `prescriptionId` | number | 处方主键 |
| `status` | string | 003 处方状态 |
| `dispenseStatus` | string | 派生取药状态 |
| `dispensable` | boolean | 是否可取药 |
| `signedAt` / `validUntil` | string | 签名时间 / 有效期 |
| `expired` | boolean | 服务端时间是否已过期 |
| `doctorNameMask` | string | 开方医生脱敏名 |
| `watermark` | string | 水印 |
| `qrCode` | string | 完整验真 URL（含 `v1` 令牌），兼容 003 同名字段 |
| `qrImageDataUrl` | string | `data:image/png;base64,...`，480×480，纠错 M |
| `verifyCode` | string | 12 位验真码（供无摄像头手工输入） |
| `dispensedAt` | string \| null | 已取药时间 |
| `dispensedByMask` | string \| null | 核销人脱敏名 |
| `items` | array | 药品明细，字段同端点 2 |

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "prescriptionId": 7001,
    "status": "SIGNED",
    "dispenseStatus": "PENDING",
    "dispensable": true,
    "signedAt": "2026-11-08T10:12:00",
    "validUntil": "2026-11-11T10:12:00",
    "expired": false,
    "doctorNameMask": "张*",
    "watermark": "示例医院 电子处方",
    "qrCode": "http://localhost:5183/pharmacy/verify?t=v1.xxxxx.yyyyy",
    "qrImageDataUrl": "data:image/png;base64,iVBORw0KGgo...",
    "verifyCode": "Ab3dE9fG7hJk",
    "dispensedAt": null,
    "dispensedByMask": null,
    "items": [
      {
        "drugId": 901,
        "drugName": "阿莫西林胶囊",
        "spec": "0.25g*20",
        "dosage": "口服，一次 2 粒，一日 3 次",
        "caution": "青霉素过敏者禁用"
      }
    ]
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 未登录 / 非患者 token |
| 404 | `PRESCRIPTION_NOT_FOUND` | 处方不存在 / 非本人 / `DRAFT`（统一不泄漏存在性） |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用 |

### 约定

- 越权与不存在**返回同一错误码与文案**（FR-014）。
- 草稿处方患者端一律不可见（列表排除 + 详情 404）。
- `qrImageDataUrl` 由服务端生成，前端直接 `<img :src>`，不引入二维码生成库。
