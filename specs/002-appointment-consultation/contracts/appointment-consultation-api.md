# 预约挂号与图文问诊 API 契约

**功能**：`002-appointment-consultation`
**枚举与 `code` 来源**：`../data-model.md`（1.1–1.10）
**Mock 数据**：`mocks.md`（M1–Mxx）
**通用约定**：`README.md`（统一响应、认证、CSRF、敏感信息约束、一致性清单）

> 本文件定义 9 个端点的请求 / 响应结构。字段名、类型、枚举取值、`code` 与 `message` 不得在前端或后端另行推测。响应示例中的时间均为 `yyyy-MM-dd'T'HH:mm:ss`。

---

## 端点 1：查询科室列表

`GET /api/v1/departments`

**认证**：否 | **CSRF**：否 | **需求**：FR-001、FR-022

### 成功响应（200）

`data` 为科室数组，元素字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `departmentId` | number | 科室主键 |
| `name` | string | 科室名 |
| `description` | string \| null | 简介 |
| `iconUrl` | string \| null | 图标 URL |
| `sortOrder` | number | 展示排序 |
| `enabled` | boolean | 是否启用 |

```json
{
  "code": 200, "message": "成功",
  "data": [
    { "departmentId": 1, "name": "内科", "description": "常见内科疾病", "iconUrl": null, "sortOrder": 1, "enabled": true },
    { "departmentId": 2, "name": "外科", "description": "外科诊疗", "iconUrl": null, "sortOrder": 2, "enabled": true }
  ]
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 2：查询医生列表

`GET /api/v1/doctors?departmentId={id}&online={ONLINE|OFFLINE|BUSY}`

**认证**：否 | **CSRF**：否 | **需求**：FR-002、FR-022

### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `departmentId` | number | 否 | 按科室筛选 |
| `online` | string | 否 | `DoctorOnlineStatus`：`ONLINE`/`OFFLINE`/`BUSY` |

### 成功响应（200）

`data` 为医生数组，元素字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `doctorId` | number | 医生主键 |
| `name` | string | 脱敏姓名，如 `张*` |
| `title` | string \| null | 职称 |
| `departmentId` | number | 所属科室 |
| `hospital` | string \| null | 医院 |
| `yearsOfExperience` | number \| null | 从业年限 |
| `goodAt` | string \| null | 擅长 |
| `consultationFee` | number | 挂号费（服务端定价） |
| `rating` | number \| null | 评分（0–5） |
| `consultationCount` | number | 接诊量 |
| `onlineStatus` | string | `DoctorOnlineStatus` |
| `avatarUrl` | string \| null | 头像 |

```json
{
  "code": 200, "message": "成功",
  "data": [
    {
      "doctorId": 101, "name": "张*", "title": "主任医师", "departmentId": 1,
      "hospital": "示例医院", "yearsOfExperience": 15, "goodAt": "呼吸系统疾病",
      "consultationFee": 30.00, "rating": 4.8, "consultationCount": 1200,
      "onlineStatus": "ONLINE", "avatarUrl": null
    }
  ]
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 404 | `DEPARTMENT_NOT_FOUND` | `departmentId` 对应科室不存在/已停用 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 3：查询医生详情

`GET /api/v1/doctors/{doctorId}`

**认证**：否 | **CSRF**：否 | **需求**：FR-003、FR-004、FR-022

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `doctorId` | number | 医生主键 |

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `doctorId` | number | 医生主键 |
| `name` | string | 脱敏姓名 |
| `title` | string \| null | 职称 |
| `departmentId` | number | 所属科室 |
| `hospital` | string \| null | 医院 |
| `yearsOfExperience` | number \| null | 从业年限 |
| `goodAt` | string \| null | 擅长 |
| `consultationFee` | number | 挂号费 |
| `rating` | number \| null | 评分 |
| `consultationCount` | number | 接诊量 |
| `onlineStatus` | string | `DoctorOnlineStatus` |
| `avatarUrl` | string \| null | 头像 |
| `schedule` | array | 可约排班列表 |

`schedule` 元素：

| 字段 | 类型 | 说明 |
|------|------|------|
| `scheduleId` | number | 排班主键 |
| `workDate` | string | 出诊日期 `yyyy-MM-dd` |
| `period` | string | `SchedulePeriod`：`MORNING`/`AFTERNOON`/`EVENING` |
| `slotsTotal` | number | 总号源 |
| `slotsBooked` | number | 已约号源 |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "doctorId": 101, "name": "张*", "title": "主任医师", "departmentId": 1,
    "hospital": "示例医院", "yearsOfExperience": 15, "goodAt": "呼吸系统疾病",
    "consultationFee": 30.00, "rating": 4.8, "consultationCount": 1200,
    "onlineStatus": "ONLINE", "avatarUrl": null,
    "schedule": [
      { "scheduleId": 201, "workDate": "2026-11-10", "period": "MORNING", "slotsTotal": 20, "slotsBooked": 5 }
    ]
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 404 | `DOCTOR_NOT_FOUND` | 医生不存在 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 4：AI 智能导诊推荐

`POST /api/v1/triage-sessions`

**认证**：否 | **CSRF**：是 | **需求**：FR-010、FR-015、FR-020、FR-022、FR-023

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `complaint` | string | 是 | 1–500 字符，禁止控制字符 | 患者主诉/症状描述 |

```json
{ "complaint": "最近持续咳嗽，伴有低热" }
```

### 成功响应（200）—— AI 推荐成功

| 字段 | 类型 | 说明 |
|------|------|------|
| `triageSessionId` | number | 导诊会话主键 |
| `status` | string | `SUCCESS` |
| `recommendedDepartmentIds` | number[] | 推荐科室 ID 列表 |
| `recommendedDepartments` | array | 推荐科室简项 `{ departmentId, name }` |
| `fallbackDepartments` | array | 空数组（成功分支） |
| `modelUsed` | string | 模型名 |
| `elapsedMs` | number | 调用耗时（含模型推理） |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "triageSessionId": 301, "status": "SUCCESS",
    "recommendedDepartmentIds": [1],
    "recommendedDepartments": [{ "departmentId": 1, "name": "内科" }],
    "fallbackDepartments": [],
    "modelUsed": "deepseek-v4-flash", "elapsedMs": 820
  }
}
```

### 成功响应（200）—— 超时降级（默认 5 秒）

| 字段 | 类型 | 说明 |
|------|------|------|
| `triageSessionId` | number | 导诊会话主键 |
| `status` | string | `TIMEOUT_FALLBACK` |
| `recommendedDepartmentIds` | number[] | 空数组 |
| `recommendedDepartments` | array | 空数组 |
| `fallbackDepartments` | array | **全量科室列表** `{ departmentId, name }` |
| `modelUsed` | string \| null | 可能为空（未返回） |
| `elapsedMs` | number | 超时阈值附近的耗时（如 5000+） |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "triageSessionId": 302, "status": "TIMEOUT_FALLBACK",
    "recommendedDepartmentIds": [], "recommendedDepartments": [],
    "fallbackDepartments": [
      { "departmentId": 1, "name": "内科" },
      { "departmentId": 2, "name": "外科" }
    ],
    "modelUsed": null, "elapsedMs": 5021
  }
}
```

> 超时降级是**正常 200 分支**，不返回错误码、不阻断挂号主流程（用户决策：默认 5 秒超时后展示全量科室列表；满足 SC-006）。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_COMPLAINT_FORMAT` | 主诉为空/超长/含控制字符 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 503 | `SERVICE_UNAVAILABLE` | 知识库/数据库不可用（非 AI 超时） |

---

## 端点 5：创建挂号预约

`POST /api/v1/appointments`

**认证**：是（`Authorization: Bearer`） | **CSRF**：是 | **需求**：FR-005、FR-007、FR-016、FR-024

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `doctorId` | number | 是 | 存在且在线 | 目标医生 |
| `scheduleId` | number | 是 | 存在且未约满 | 目标排班 |
| `patientNote` | string \| null | 否 | ≤2000 | 患者备注 |

```json
{ "doctorId": 101, "scheduleId": 201, "patientNote": "希望能尽快就诊" }
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `appointmentId` | number | 预约主键 |
| `doctorId` | number | 医生 |
| `departmentId` | number | 科室 |
| `appointmentDate` | string | 就诊日期 `yyyy-MM-dd` |
| `period` | string | `SchedulePeriod` |
| `feeAmount` | number | 挂号费（服务端从 `doctor.consultation_fee` 快照） |
| `paymentStatus` | string | `UNPAID` |
| `appointmentStatus` | string | `PENDING_PAYMENT` |
| `createdAt` | string | 创建时间 |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "appointmentId": 401, "doctorId": 101, "departmentId": 1,
    "appointmentDate": "2026-11-10", "period": "MORNING",
    "feeAmount": 30.00, "paymentStatus": "UNPAID",
    "appointmentStatus": "PENDING_PAYMENT", "createdAt": "2026-11-08T10:20:00"
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `REVISIT_NOT_ELIGIBLE` | 未实名认证或不具备历史就诊记录（FR-007 门禁） |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 404 | `DOCTOR_NOT_FOUND` | 医生不存在 |
| 400 | `INVALID_SCHEDULE` | 排班不存在/已约满/窗口外 |
| 400 | `INVALID_APPOINTMENT_STATUS` | 同一 `(patient_id, schedule_id)` 已存在预约 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

> 门禁拒绝时写 `REVISIT_GATE_DENIED` 审计；满足 SC-003（未认证/无资格 0 放行）。

---

## 端点 6a：挂号费支付（模拟）

`POST /api/v1/appointments/{appointmentId}/payments`

**认证**：是 | **CSRF**：是 | **需求**：FR-005、FR-006、FR-021、FR-024、SC-005

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `channel` | string | 是 | 固定 `MOCK` | 支付渠道（MVP 仅模拟） |
| `amount` | number | 是 | 必须等于 `feeAmount` | 客户端传入仅作一致性校验 |

```json
{ "channel": "MOCK", "amount": 30.00 }
```

### 成功响应（200，幂等）

| 字段 | 类型 | 说明 |
|------|------|------|
| `appointmentId` | number | 预约主键 |
| `paymentId` | number | 支付记录主键 |
| `amount` | number | 实际支付金额 |
| `channel` | string | `MOCK` |
| `status` | string | `SUCCESS` |
| `paidAt` | string | 支付时间 |
| `paymentStatus` | string | `PAID` |
| `appointmentStatus` | string | `PAID` |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "appointmentId": 401, "paymentId": 501, "amount": 30.00, "channel": "MOCK",
    "status": "SUCCESS", "paidAt": "2026-11-08T10:25:00",
    "paymentStatus": "PAID", "appointmentStatus": "PAID"
  }
}
```

> 幂等：重复支付返回首次成功结果（200）；并发竞争仅一个成功，另一个 `PAYMENT_CONFLICT`(409)（SC-005/SC-008）。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 404 | `APPOINTMENT_NOT_FOUND` | 预约不存在/非本人 |
| 400 | `INVALID_PAYMENT_AMOUNT` | `amount` 与 `feeAmount` 不一致或 ≤0 |
| 400 | `INVALID_APPOINTMENT_STATUS` | 预约已 `PAID`/`CANCELLED`/`CLOSED` |
| 400 | `PAYMENT_FAILED` | 模拟支付明确失败（可重试） |
| 409 | `PAYMENT_CONFLICT` | 并发支付竞争 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 6b：查询支付状态

`GET /api/v1/appointments/{appointmentId}/payments`

**认证**：是 | **CSRF**：否 | **需求**：FR-006

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `appointmentId` | number | 预约主键 |
| `paymentId` | number \| null | 支付记录主键（未支付为 `null`） |
| `amount` | number \| null | 金额（未支付为 `null`） |
| `channel` | string \| null | 渠道（未支付为 `null`） |
| `status` | string | `UNPAID` \| `PAID` |
| `paidAt` | string \| null | 支付时间 |
| `paymentStatus` | string | `UNPAID` \| `PAID` |
| `appointmentStatus` | string | 当前预约状态 |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "appointmentId": 401, "paymentId": 501, "amount": 30.00, "channel": "MOCK",
    "status": "PAID", "paidAt": "2026-11-08T10:25:00",
    "paymentStatus": "PAID", "appointmentStatus": "PAID"
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 404 | `APPOINTMENT_NOT_FOUND` | 预约不存在/非本人 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 端点 7：上传问诊图片

`POST /api/v1/consultations/images`（`multipart/form-data`，字段名 `file`）

**认证**：是 | **CSRF**：是 | **需求**：FR-008、FR-009、FR-017、SC-004

### 请求

`multipart/form-data` 单文件上传，字段 `file`。服务端二次校验：内容类型白名单 → 内容嗅探 → 大小 ≤5MB → 该问诊单图片数 ≤9。

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `imageId` | number | 图片主键 |
| `url` | string | 访问 URL（`/static/consultation-images/...`） |
| `contentType` | string | 校验后的内容类型 |
| `sizeBytes` | number | 字节大小 |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "imageId": 601, "url": "/static/consultation-images/abc123.jpg",
    "contentType": "image/jpeg", "sizeBytes": 123456
  }
}
```

> 上传后可收集 `url` 列表，在端点 8 提交问诊单时一并传入。同一图片不得被当作可执行内容处理。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_IMAGE_FORMAT` | 内容类型不在白名单或内容伪装 |
| 400 | `INVALID_IMAGE_SIZE` | 单张超过 5 MB |
| 400 | `TOO_MANY_IMAGES` | 已超过 9 张 |
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 503 | `SERVICE_UNAVAILABLE` | 存储/数据库不可用或超时 |

---

## 端点 8：提交图文问诊单

`POST /api/v1/consultations`

**认证**：是 | **CSRF**：是 | **需求**：FR-011、FR-016、FR-019、FR-025、SC-003/SC-008/SC-009

### 请求体

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `appointmentId` | number | 是 | 须为 `PAID` 状态 | 关联挂号单 |
| `complaint` | string | 是 | 1–500 字符，禁控制字符 | 主诉 |
| `description` | string \| null | 否 | ≤2000 | 选填详细描述 |
| `imageUrls` | string[] | 否 | ≤9，URL 来自端点 7 | 图片 URL 列表 |

```json
{
  "appointmentId": 401, "complaint": "咳嗽伴低热三天",
  "description": "夜间加重，无痰", "imageUrls": ["/static/consultation-images/abc123.jpg"]
}
```

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `consultationId` | number | 问诊单主键 |
| `appointmentId` | number | 关联预约 |
| `doctorId` | number | 医生 |
| `departmentId` | number | 科室 |
| `complaint` | string | 主诉（回显） |
| `imageCount` | number | 图片数量 |
| `status` | string | `PENDING`（待接诊） |
| `summary` | string \| null | AI 生成的病情摘要（随单同步医生侧） |
| `createdAt` | string | 提交时间 |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "consultationId": 701, "appointmentId": 401, "doctorId": 101, "departmentId": 1,
    "complaint": "咳嗽伴低热三天", "imageCount": 1, "status": "PENDING",
    "summary": "患者主诉咳嗽伴低热三天，建议排查呼吸道感染。",
    "createdAt": "2026-11-08T10:30:00"
  }
}
```

> 提交瞬间医生由 `ONLINE` 转 `BUSY`/`OFFLINE` 不影响本单状态（仍为 `PENDING`，FR-025）；AI 摘要随单生成，仅作参考不构成诊断。
> 幂等：同一 `appointmentId` 仅一张问诊单（`CONSULTATION_SUBMITTED` 审计一次，SC-008）。

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `INVALID_COMPLAINT_FORMAT` | 主诉为空/超长/含控制字符 |
| 400 | `INVALID_DESCRIPTION_FORMAT` | 选填描述超长 |
| 400 | `TOO_MANY_IMAGES` | `imageUrls` 超过 9 张 |
| 400 | `INVALID_APPOINTMENT_STATUS` | 关联预约未 `PAID`（未支付不可提交） |
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 404 | `APPOINTMENT_NOT_FOUND` | 预约不存在/非本人 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库/AI 不可用或超时 |

---

## 端点 9：查询问诊单状态与详情

`GET /api/v1/consultations/{consultationId}`

**认证**：是 | **CSRF**：否 | **需求**：FR-012、FR-019、SC-010

### 路径参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `consultationId` | number | 问诊单主键 |

### 成功响应（200）

| 字段 | 类型 | 说明 |
|------|------|------|
| `consultationId` | number | 问诊单主键 |
| `appointmentId` | number | 关联预约 |
| `doctorId` | number | 医生 |
| `departmentId` | number | 科室 |
| `complaint` | string | 主诉 |
| `description` | string \| null | 选填描述 |
| `images` | array | 图片列表 `{ imageId, url }` |
| `summary` | string \| null | AI 病情摘要 |
| `status` | string | `ConsultationStatus` |
| `createdAt` | string | 创建时间 |
| `updatedAt` | string | 更新时间 |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "consultationId": 701, "appointmentId": 401, "doctorId": 101, "departmentId": 1,
    "complaint": "咳嗽伴低热三天", "description": "夜间加重，无痰",
    "images": [{ "imageId": 601, "url": "/static/consultation-images/abc123.jpg" }],
    "summary": "患者主诉咳嗽伴低热三天，建议排查呼吸道感染。",
    "status": "PENDING", "createdAt": "2026-11-08T10:30:00", "updatedAt": "2026-11-08T10:30:00"
  }
}
```

### 异常

| code | 常量 | 触发条件 |
|------|------|----------|
| 401 | `UNAUTHORIZED` | 凭证缺失/过期/非法 |
| 403 | `CONSULTATION_FORBIDDEN` | 越权访问他人问诊单 |
| 404 | `CONSULTATION_NOT_FOUND` | 问诊单不存在/非本人 |
| 503 | `SERVICE_UNAVAILABLE` | 数据库不可用或超时 |

---

## 通用异常对照（沿用 001）

| 场景 | HTTP | `code` | 说明 |
|------|------|--------|------|
| 路径不存在 | 404 | 404 | `NOT_FOUND` |
| 方法不匹配 | 405 | 405 | `METHOD_NOT_ALLOWED` |
| 请求体非 JSON / 字段类型错误 | 400 | 400 | `BAD_REQUEST` |
| 未登录访问受保护端点 | 401 | 401 | `UNAUTHORIZED` |
| 修改类请求缺 `X-CSRF-Token` | 403 | 403 | `CSRF_TOKEN_MISMATCH` |
| 依赖不可用/超时 | 503 | 503 | `SERVICE_UNAVAILABLE` |
| 未捕获异常 | 500 | 500 | `INTERNAL_ERROR`（不返回堆栈） |

**响应字段约束**：任何响应体不得包含 `phone`、`idCard` 明文、`password`、`paymentToken`（明文）、`out_trade_no` 以外的支付敏感信息、医生真实姓名等；图片仅以 URL 暴露。
