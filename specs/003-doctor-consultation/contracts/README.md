# 医生接诊 契约索引

**功能**：`003-doctor-consultation`
**枚举与 `code` 来源**：`../data-model.md`（1.1–1.7）
**Mock 数据**：`mocks.md`（M1–Mxx）
**通用约定**：本文件
**端点明细**：`doctor-consultation-api.md`

> 本目录是前后端契约的唯一来源（宪法原则 III）。字段名、类型、枚举取值、`code` 与 `message` 不得在前端或后端另行推测。

---

## 1. 统一响应结构

所有响应体统一为：

```json
{ "code": 200, "message": "成功", "data": { } }
```

- 成功：`code=200`，`message="成功"`，`data` 为业务对象或数组。
- 失败：`code` 为业务码（与 HTTP 状态对齐），`message` 取自 `data-model.md` 1.6，`data` 恒为 `null`。
- 时间字段格式统一 `yyyy-MM-dd'T'HH:mm:ss`（宪法原则 III：禁止时间戳数组）。

---

## 2. 认证、CSRF 与 Cookie

- **认证**：医生端点须带 `Authorization: Bearer <doctorAccessToken>`（复用 001 JWT，含「医生」角色）。
- **CSRF**：修改类请求（POST/PUT/DELETE）须带 `X-CSRF-Token`（取值自 `sdas_csrf` Cookie），双重 Cookie 校验失败返回 `CSRF_TOKEN_MISMATCH`(403)。
- **GET 请求**：不需要 CSRF。
- **角色隔离**：患者 token 访问医生端点返回 `DOCTOR_ROLE_REQUIRED`(403)。

---

## 3. 错误码索引（本功能涉及）

| code | 常量 | HTTP | 含义 |
|------|------|------|------|
| 401 | `UNAUTHORIZED` | 401 | 未登录 / 凭证失效 |
| 403 | `CSRF_TOKEN_MISMATCH` | 403 | 双重 Cookie 校验失败 |
| 403 | `DOCTOR_ROLE_REQUIRED` | 403 | 非医生角色访问工作台 |
| 403 | `CONSULTATION_FORBIDDEN` | 403 | 越权访问他人问诊单/处方（复用 002） |
| 404 | `DOCTOR_ACCOUNT_NOT_FOUND` | 404 | 医生账号不存在或已禁用 |
| 404 | `CONSULTATION_NOT_FOUND` | 404 | 问诊单不存在/非本人（复用 002） |
| 404 | `DRUG_NOT_FOUND` | 404 | 药品不存在或已停用 |
| 400 | `AUTHENTICATION_FAILED` | 400 | 通用模糊鉴权失败（登录失败） |
| 400 | `INVALID_REPLY_FORMAT` | 400 | 回复为空/超 1000 字/含控制字符 |
| 400 | `INVALID_CONSULTATION_STATUS` | 400 | 状态不允许当前操作 |
| 400 | `DRUG_NOT_IN_CATALOG` | 400 | 自由输入药品名/不在目录 |
| 400 | `INVALID_PRESCRIPTION_FORMAT` | 400 | 处方字段为空或超长 |
| 400 | `PRESCRIPTION_SIGN_FAILED` | 400 | 签名密钥缺失或签名失败 |
| 400 | `PRESCRIPTION_EXPIRED` | 400 | 处方已过期 |
| 409 | `CONSULTATION_ACCEPT_CONFLICT` | 409 | 并发/重复接诊 |
| 409 | `PRESCRIPTION_ALREADY_EXISTS` | 409 | 一张问诊单重复开方 |
| 429 | `RATE_LIMITED` | 429 | 操作过于频繁 |
| 503 | `SERVICE_UNAVAILABLE` | 503 | 依赖（DB/AI）不可用或超时 |

> 完整定义与 `message` 以 `../data-model.md` 1.6 为唯一权威来源；新增码不得与 001/002 既有码同名不同值。

---

## 4. 敏感信息约束

任何响应体、审计日志、AI 输入输出抽样中**不得**出现：完整手机号、完整身份证号、处方完整签名值明文、医生真实全名。仅可返回脱敏值（如 `张*`、`138****8000`）或内部 ID。处方签名、二维码、水印为服务端生成内容，患者端只读展示。

---

## 5. 契约一致性清单（每迭代核对，SC-012）

- [ ] 端点路径与 `doctor-consultation-api.md` 逐个一致
- [ ] 每个 `code` 取值与 `data-model.md` 1.6 一致
- [ ] 字段名、类型、必填性与契约一致
- [ ] 时间格式均为 `yyyy-MM-dd'T'HH:mm:ss`
- [ ] 认证、CSRF、角色要求与本文第 2 节一致
- [ ] 前端 `web/src/api/types.ts` 与 `web/src/api/mocks/` 已同步且标注本目录路径
- [ ] Playwright 断言均校验 `code` 值

---

## 6. 端点一览（9 个）

| # | 方法 | 路径 | 认证 | CSRF |
|---|------|------|------|------|
| 1 | POST | `/api/v1/workbench/sessions` | 否 | 是 |
| 2 | GET | `/api/v1/workbench/consultations` | 是 | 否 |
| 3 | GET | `/api/v1/workbench/consultations/{consultationId}` | 是 | 否 |
| 4 | POST | `/api/v1/workbench/consultations/{consultationId}/accept` | 是 | 是 |
| 5 | GET | `/api/v1/workbench/consultations/{consultationId}/messages` | 是 | 否 |
| 6 | POST | `/api/v1/workbench/consultations/{consultationId}/messages` | 是 | 是 |
| 7 | POST | `/api/v1/workbench/consultations/{consultationId}/prescriptions` | 是 | 是 |
| 8 | GET | `/api/v1/workbench/prescriptions/{prescriptionId}` | 是 | 否 |
| 9 | POST | `/api/v1/workbench/consultations/{consultationId}/ai-decision` | 是 | 是 |

> 医生侧统一挂 `/api/v1/workbench/*`，与 002 的 `/doctors`、`/consultations` 词干明确错开（原则 II）。
