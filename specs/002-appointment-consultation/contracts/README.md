# 预约挂号与图文问诊 契约索引

**功能**：`002-appointment-consultation`
**枚举与 `code` 来源**：`../data-model.md`（1.1–1.10）
**Mock 数据**：`mocks.md`（M1–Mxx）
**通用约定**：本文件
**端点明细**：`appointment-consultation-api.md`

> 本目录是前后端契约的唯一来源（宪法原则 III）。字段名、类型、枚举取值、`code` 与 `message` 不得在前端或后端另行推测。

---

## 1. 统一响应结构

所有响应体统一为：

```json
{ "code": 200, "message": "成功", "data": { } }
```

- 成功：`code=200`，`message="成功"`，`data` 为业务对象或数组（`null` 也在 `data` 中显式表达）。
- 失败：`code` 为业务码（与 HTTP 状态对齐），`message` 取自 `data-model.md` 1.9，`data` 恒为 `null`。
- 时间字段格式统一 `yyyy-MM-dd'T'HH:mm:ss`（宪法原则 III：禁止时间戳数组）。
- 金额字段为字符串化的十进制或数字（前端以数字处理），精度 `DECIMAL(10,2)`。

---

## 2. 认证、CSRF 与 Cookie

- **受保护端点**（创建预约、支付、支付查询、上传图片、提交问诊单、查询问诊单）：须带 `Authorization: Bearer <accessToken>`（复用 001 JWT）。
- **公共端点**（查询科室、查询医生、查询医生详情、AI 导诊）：无需登录，`Authorization` 可缺省。
- **修改类请求**（POST/PUT/DELETE）：须带 `X-CSRF-Token`（取值自 `sdas_csrf` Cookie），双重 Cookie 校验失败返回 `CSRF_TOKEN_MISMATCH`(403)。
- **GET 请求**：不需要 CSRF。

---

## 3. 错误码索引（本功能涉及）

| code | 常量 | HTTP | 含义 |
|------|------|------|------|
| 401 | `UNAUTHORIZED` | 401 | 未登录 / 凭证失效 |
| 403 | `CSRF_TOKEN_MISMATCH` | 403 | 双重 Cookie 校验失败 |
| 403 | `REVISIT_NOT_ELIGIBLE` | 403 | 未实名认证或不具备复诊资格 |
| 403 | `CONSULTATION_FORBIDDEN` | 403 | 越权访问他人问诊单/图片 |
| 404 | `DEPARTMENT_NOT_FOUND` | 404 | 科室不存在/已停用 |
| 404 | `DOCTOR_NOT_FOUND` | 404 | 医生不存在 |
| 404 | `APPOINTMENT_NOT_FOUND` | 404 | 挂号单不存在/非本人 |
| 404 | `CONSULTATION_NOT_FOUND` | 404 | 问诊单不存在/非本人 |
| 400 | `BAD_REQUEST` | 400 | 报文不可解析/字段类型错误 |
| 400 | `AUTHENTICATION_FAILED` | 400 | 通用模糊鉴权失败 |
| 400 | `INVALID_COMPLAINT_FORMAT` | 400 | 主诉为空/超长/含控制字符 |
| 400 | `INVALID_DESCRIPTION_FORMAT` | 400 | 选填描述超长 |
| 400 | `INVALID_IMAGE_FORMAT` | 400 | 图片格式不合法或内容伪装 |
| 400 | `INVALID_IMAGE_SIZE` | 400 | 单张图片超 5 MB |
| 400 | `TOO_MANY_IMAGES` | 400 | 图片超过 9 张 |
| 400 | `INVALID_PAYMENT_AMOUNT` | 400 | 金额不一致/为 0 |
| 400 | `INVALID_APPOINTMENT_STATUS` | 400 | 挂号单状态不允许当前操作 |
| 400 | `INVALID_SCHEDULE` | 400 | 排班不存在/已约满/窗口外 |
| 400 | `PAYMENT_FAILED` | 400 | 模拟支付明确失败 |
| 409 | `PAYMENT_CONFLICT` | 409 | 并发支付竞争 |
| 429 | `RATE_LIMITED` | 429 | 操作过于频繁 |
| 503 | `SERVICE_UNAVAILABLE` | 503 | 依赖（DB/AI）不可用或超时 |

> 完整定义与 `message` 以 `../data-model.md` 1.9 为唯一权威来源；新增码不得与 001 既有码同名不同值。

---

## 4. 敏感信息约束

任何响应体、审计日志、AI 输入输出抽样中**不得**出现：完整手机号、完整身份证号、明文密码/支付敏感信息（卡号、CVV、令牌明文）。仅可返回脱敏值（如 `138****8000`、`张*`、`110***********002X`）或内部 ID。

---

## 5. 契约一致性清单（每迭代核对，SC-012）

- [ ] 端点路径与 `appointment-consultation-api.md` 逐个一致
- [ ] 每个 `code` 取值与 `data-model.md` 1.9 一致
- [ ] 字段名、类型、必填性与契约一致
- [ ] 时间格式均为 `yyyy-MM-dd'T'HH:mm:ss`
- [ ] 公共/受保护、CSRF 要求与本文第 2 节一致
- [ ] 前端 `web/src/api/types.ts` 与 `web/src/api/mocks/` 已同步且标注本目录路径
- [ ] Playwright 断言均校验 `code` 值

---

## 6. 端点一览（9 个）

| # | 方法 | 路径 | 认证 | CSRF |
|---|------|------|------|------|
| 1 | GET | `/api/v1/departments` | 否 | 否 |
| 2 | GET | `/api/v1/doctors` | 否 | 否 |
| 3 | GET | `/api/v1/doctors/{doctorId}` | 否 | 否 |
| 4 | POST | `/api/v1/triage-sessions` | 否 | 是 |
| 5 | POST | `/api/v1/appointments` | 是 | 是 |
| 6a | POST | `/api/v1/appointments/{appointmentId}/payments` | 是 | 是 |
| 6b | GET | `/api/v1/appointments/{appointmentId}/payments` | 是 | 否 |
| 7 | POST | `/api/v1/consultations/images` | 是 | 是 |
| 8 | POST | `/api/v1/consultations` | 是 | 是 |
| 9 | GET | `/api/v1/consultations/{consultationId}` | 是 | 否 |

> 端点 6a/6b 合并计入「挂号费支付与支付状态查询」为一个逻辑端点（共 9 个）。
