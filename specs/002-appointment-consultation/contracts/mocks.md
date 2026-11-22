# Mock 数据（M1–M16）

**功能**：`002-appointment-consultation`
**唯一来源**：本文件。前端 `web/src/api/mocks/` 与 Playwright `page.route()` 只能从此处同步，禁止凭空手写（原则 VI）。
**同步要求**：契约变更时同步本文件 → `web/src/api/mocks/` → `web/src/api/types.ts` → E2E 用例断言。
**契约结构**：`appointment-consultation-api.md`；**枚举/错误码**：`../data-model.md` 1.1–1.10。

## 通用约定

- 所有响应均为 `{ code, message, data }`；`message` 取自 `data-model.md` 1.9，不得改写。
- `data` 为 `null` 时字段仍须存在。
- 时间统一 `yyyy-MM-dd'T'HH:mm:ss`；日期 `yyyy-MM-dd`。
- 不出现完整手机号/身份证号/支付敏感信息明文；姓名用脱敏（如 `张*`）。
- 前端具体路径为 `/v1/...`（不含 `/api`），运行时拼为 `/api/v1/...`。
- 模拟支付固定成功（除非专门测试 `PAYMENT_FAILED`）。

| 编号 | 场景 | 端点 | HTTP | `code` | 使用位置 |
|------|------|------|------|--------|----------|
| M1 | 科室列表成功 | `GET /v1/departments` | 200 | 200 | 科室浏览页 |
| M2 | 医生列表成功 | `GET /v1/doctors` | 200 | 200 | 医生列表页 |
| M3 | 医生详情成功 | `GET /v1/doctors/{id}` | 200 | 200 | 医生详情页 |
| M4 | AI 导诊推荐成功 | `POST /v1/triage-sessions` | 200 | 200 | 智能导诊 |
| M5 | AI 导诊超时降级 | 同上 | 200 | 200 | 降级手动选科（`TIMEOUT_FALLBACK`） |
| M6 | 创建挂号预约成功 | `POST /v1/appointments` | 200 | 200 | 用户故事 1 主流程 |
| M7 | 支付成功（幂等） | `POST /v1/appointments/{id}/payments` | 200 | 200 | 用户故事 1 主流程 |
| M8 | 支付状态查询（已支付） | `GET /v1/appointments/{id}/payments` | 200 | 200 | 支付结果页 |
| M9 | 上传图片成功 | `POST /v1/consultations/images` | 200 | 200 | 用户故事 2 主流程 |
| M10 | 提交图文问诊单成功 | `POST /v1/consultations` | 200 | 200 | 用户故事 2 主流程 |
| M11 | 查询问诊单详情 | `GET /v1/consultations/{id}` | 200 | 200 | 问诊单详情页 |
| M12 | 复诊门禁拒绝 | `POST /v1/appointments` | 403 | 403 | 未认证/无资格（SC-003） |
| M13 | 主诉格式越界 | `POST /v1/consultations` | 400 | 400 | 输入校验（SC-004） |
| M14 | 图片超 5MB | `POST /v1/consultations/images` | 400 | 400 | 上传校验（SC-004） |
| M15 | 越权访问他人问诊单 | `GET /v1/consultations/{id}` | 403 | 403 | 越权防护（SC-010） |
| M16 | 依赖降级 | 任意端点 | 503 | 503 | 超时/降级用例 |

---

## M1 科室列表成功

```json
{ "code": 200, "message": "成功", "data": [
  { "departmentId": 1, "name": "内科", "description": "常见内科疾病", "iconUrl": null, "sortOrder": 1, "enabled": true },
  { "departmentId": 2, "name": "外科", "description": "外科诊疗", "iconUrl": null, "sortOrder": 2, "enabled": true }
] }
```

## M2 医生列表成功

```json
{ "code": 200, "message": "成功", "data": [
  { "doctorId": 101, "name": "张*", "title": "主任医师", "departmentId": 1, "hospital": "示例医院",
    "yearsOfExperience": 15, "goodAt": "呼吸系统疾病", "consultationFee": 30.00, "rating": 4.8,
    "consultationCount": 1200, "onlineStatus": "ONLINE", "avatarUrl": null }
] }
```

## M3 医生详情成功

```json
{ "code": 200, "message": "成功", "data": {
  "doctorId": 101, "name": "张*", "title": "主任医师", "departmentId": 1, "hospital": "示例医院",
  "yearsOfExperience": 15, "goodAt": "呼吸系统疾病", "consultationFee": 30.00, "rating": 4.8,
  "consultationCount": 1200, "onlineStatus": "ONLINE", "avatarUrl": null,
  "schedule": [ { "scheduleId": 201, "workDate": "2026-11-10", "period": "MORNING", "slotsTotal": 20, "slotsBooked": 5 } ]
} }
```

## M4 AI 导诊推荐成功

```json
{ "code": 200, "message": "成功", "data": {
  "triageSessionId": 301, "status": "SUCCESS",
  "recommendedDepartmentIds": [1], "recommendedDepartments": [ { "departmentId": 1, "name": "内科" } ],
  "fallbackDepartments": [], "modelUsed": "deepseek-v4-flash", "elapsedMs": 820
} }
```

## M5 AI 导诊超时降级（默认 5 秒后）

```json
{ "code": 200, "message": "成功", "data": {
  "triageSessionId": 302, "status": "TIMEOUT_FALLBACK",
  "recommendedDepartmentIds": [], "recommendedDepartments": [],
  "fallbackDepartments": [ { "departmentId": 1, "name": "内科" }, { "departmentId": 2, "name": "外科" } ],
  "modelUsed": null, "elapsedMs": 5021
} }
```

> 超时降级为正常 200 分支，返回全量科室列表，前端展示手动选择路径（用户决策）。

## M6 创建挂号预约成功

```json
{ "code": 200, "message": "成功", "data": {
  "appointmentId": 401, "doctorId": 101, "departmentId": 1, "appointmentDate": "2026-11-10",
  "period": "MORNING", "feeAmount": 30.00, "paymentStatus": "UNPAID",
  "appointmentStatus": "PENDING_PAYMENT", "createdAt": "2026-11-08T10:20:00"
} }
```

## M7 支付成功（幂等）

```json
{ "code": 200, "message": "成功", "data": {
  "appointmentId": 401, "paymentId": 501, "amount": 30.00, "channel": "MOCK",
  "status": "SUCCESS", "paidAt": "2026-11-08T10:25:00",
  "paymentStatus": "PAID", "appointmentStatus": "PAID"
} }
```

## M8 支付状态查询（已支付）

```json
{ "code": 200, "message": "成功", "data": {
  "appointmentId": 401, "paymentId": 501, "amount": 30.00, "channel": "MOCK",
  "status": "PAID", "paidAt": "2026-11-08T10:25:00",
  "paymentStatus": "PAID", "appointmentStatus": "PAID"
} }
```

## M9 上传图片成功

```json
{ "code": 200, "message": "成功", "data": {
  "imageId": 601, "url": "/static/consultation-images/abc123.jpg",
  "contentType": "image/jpeg", "sizeBytes": 123456
} }
```

## M10 提交图文问诊单成功

```json
{ "code": 200, "message": "成功", "data": {
  "consultationId": 701, "appointmentId": 401, "doctorId": 101, "departmentId": 1,
  "complaint": "咳嗽伴低热三天", "imageCount": 1, "status": "PENDING",
  "summary": "患者主诉咳嗽伴低热三天，建议排查呼吸道感染。", "createdAt": "2026-11-08T10:30:00"
} }
```

## M11 查询问诊单详情

```json
{ "code": 200, "message": "成功", "data": {
  "consultationId": 701, "appointmentId": 401, "doctorId": 101, "departmentId": 1,
  "complaint": "咳嗽伴低热三天", "description": "夜间加重，无痰",
  "images": [ { "imageId": 601, "url": "/static/consultation-images/abc123.jpg" } ],
  "summary": "患者主诉咳嗽伴低热三天，建议排查呼吸道感染。",
  "status": "PENDING", "createdAt": "2026-11-08T10:30:00", "updatedAt": "2026-11-08T10:30:00"
} }
```

## M12 复诊门禁拒绝

```json
{ "code": 403, "message": "请先完成实名认证或确认复诊资格", "data": null }
```

> 对应 `REVISIT_NOT_ELIGIBLE`；前端未认证先跳登录/实名，无资格则引导至实名认证与历史就诊说明。

## M13 主诉格式越界

```json
{ "code": 400, "message": "主诉长度须为 1-500 个字符", "data": null }
```

> 对应 `INVALID_COMPLAINT_FORMAT`。

## M14 图片超 5MB

```json
{ "code": 400, "message": "单张图片不得超过 5 MB", "data": null }
```

> 对应 `INVALID_IMAGE_SIZE`。

## M15 越权访问他人问诊单

```json
{ "code": 403, "message": "禁止访问他人问诊资料", "data": null }
```

> 对应 `CONSULTATION_FORBIDDEN`。

## M16 依赖降级

```json
{ "code": 503, "message": "服务暂时不可用，请稍后重试", "data": null }
```

> 对应 `SERVICE_UNAVAILABLE`；用于数据库/AI/存储不可用的超时测试。
