# 医生接诊 Mock 数据

**功能**：`003-doctor-consultation`
**契约**：`doctor-consultation-api.md`
**枚举与 `code` 来源**：`../data-model.md`

> Mock 数据必须与契约保持一致（宪法原则 VI）；Mock E2E 用 `page.route()` 拦截，真实后端 E2E 断言响应 `code` 值。键格式 `METHOD /v1/workbench/...`，`#SCENARIO` 表示分支场景。

## 医生登录（端点 1）

| 键 | 场景 | 返回 |
|----|------|------|
| `POST /v1/workbench/sessions` | M1 成功 | 200 `{ accessToken, refreshToken, doctorId, nameMask, title, departmentId, expiresIn }` |
| `POST /v1/workbench/sessions#FAIL` | 账号或密码错误 | 400 `AUTHENTICATION_FAILED` |
| `POST /v1/workbench/sessions#CSRF` | 缺 CSRF | 403 `CSRF_TOKEN_MISMATCH` |

## 待接诊 / 已接诊列表（端点 2）

| 键 | 场景 | 返回 |
|----|------|------|
| `GET /v1/workbench/consultations` | M2 待接诊列表 | 200 数组（含 `consultationId`、`patientName`、`complaint`、`status=PENDING`、`submittedAt`） |
| `GET /v1/workbench/consultations#IN_PROGRESS` | 已接诊列表 | 200 数组（`status=IN_PROGRESS`） |
| `GET /v1/workbench/consultations#EMPTY` | 空列表 | 200 `[]` |
| `GET /v1/workbench/consultations#ROLE` | 患者 token 访问 | 403 `DOCTOR_ROLE_REQUIRED` |

## 问诊单详情（端点 3）

| 键 | 场景 | 返回 |
|----|------|------|
| `GET /v1/workbench/consultations/701` | M3 成功 | 200 详情（含图片、摘要、`status`、`acceptedAt`） |
| `GET /v1/workbench/consultations/999#FORBIDDEN` | 越权访问他人单 | 403 `CONSULTATION_FORBIDDEN` |
| `GET /v1/workbench/consultations/998#NOT_FOUND` | 不存在 | 404 `CONSULTATION_NOT_FOUND` |

## 接诊（端点 4）

| 键 | 场景 | 返回 |
|----|------|------|
| `POST /v1/workbench/consultations/701/accept` | M4 成功 | 200 `{ status=IN_PROGRESS, acceptedAt, doctorId }` |
| `POST /v1/workbench/consultations/701/accept#CONFLICT` | 并发接诊 | 409 `CONSULTATION_ACCEPT_CONFLICT` |
| `POST /v1/workbench/consultations/701/accept#STATUS` | 已接诊再点 | 400 `INVALID_CONSULTATION_STATUS` |

## 对话（端点 5/6）

| 键 | 场景 | 返回 |
|----|------|------|
| `GET /v1/workbench/consultations/701/messages` | M5 对话列表 | 200 数组（`senderType`、`content`、`createdAt`） |
| `POST /v1/workbench/consultations/701/messages` | M6 回复成功 | 200 `{ messageId, senderType=DOCTOR, content, createdAt }` |
| `POST /v1/workbench/consultations/701/messages#TOO_LONG` | 超 1000 字 | 400 `INVALID_REPLY_FORMAT` |
| `POST /v1/workbench/consultations/701/messages#NOT_ACCEPTED` | 未接诊即回复 | 400 `INVALID_CONSULTATION_STATUS` |

## 电子处方（端点 7/8）

| 键 | 场景 | 返回 |
|----|------|------|
| `POST /v1/workbench/consultations/701/prescriptions` | M7 开方成功 | 200 `{ prescriptionId, status=SIGNED, items, signedAt, validUntil, qrCode, watermark }` |
| `POST /v1/workbench/consultations/701/prescriptions#DUP` | 一张单多张处方 | 409 `PRESCRIPTION_ALREADY_EXISTS` |
| `POST /v1/workbench/consultations/701/prescriptions#DRUG` | 药品不在目录 | 400 `DRUG_NOT_IN_CATALOG` / 404 `DRUG_NOT_FOUND` |
| `POST /v1/workbench/consultations/701/prescriptions#SIGN_FAIL` | 签名失败 | 400 `PRESCRIPTION_SIGN_FAILED` |
| `GET /v1/workbench/prescriptions/1001` | M8 查询处方 | 200 详情（含 `expired`） |
| `GET /v1/workbench/prescriptions/1001#EXPIRED` | 已过期 | 200（`expired=true`）或 400 `PRESCRIPTION_EXPIRED` |

## AI 辅助决策（端点 9）

| 键 | 场景 | 返回 |
|----|------|------|
| `POST /v1/workbench/consultations/701/ai-decision` | M9 成功 | 200 `{ decisionId, suggestions, disclaimer, modelUsed, status=SUCCESS }` |
| `POST /v1/workbench/consultations/701/ai-decision#TIMEOUT` | AI 超时 | 200 `{ status=TIMEOUT_FALLBACK, suggestions=[] }` |
| `POST /v1/workbench/consultations/701/ai-decision#NOT_ACCEPTED` | 未接诊 | 400 `INVALID_CONSULTATION_STATUS` |
