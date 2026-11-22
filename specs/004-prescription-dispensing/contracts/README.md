# 处方取药 契约索引

**功能**：`004-prescription-dispensing`
**枚举与 `code` 来源**：`../data-model.md`（1.1–1.7）
**Mock 数据**：`mocks.md`（M1–M12）
**通用约定**：本文件
**端点明细**：`prescription-dispensing-api.md`

> 本目录是前后端契约的唯一来源（宪法原则 III）。字段名、类型、枚举取值、`code` 与 `message` 不得在前端或后端另行推测。

---

## 1. 统一响应结构

```json
{ "code": 200, "message": "成功", "data": { } }
```

- 成功：`code=200`，`message="成功"`，`data` 为业务对象、数组或分页对象。
- 失败：`code` 为业务码（与 HTTP 状态对齐），`message` 取自 `data-model.md` 1.6，`data` 恒为 `null`。
- 时间字段格式统一 `yyyy-MM-dd'T'HH:mm:ss`（禁止时间戳数组）。
- 分页对象统一 `{ items, page, pageSize, total }`（见 `data-model.md` 1.5）。

---

## 2. 认证、CSRF 与 Cookie

- **患者端点**：`Authorization: Bearer <patientAccessToken>`（复用 001 JWT）。
- **药店端点**：`Authorization: Bearer <pharmacyAccessToken>`（复用具 001 JWT，`role="PHARMACY"`）。
- **CSRF**：修改类请求（POST/PUT/PATCH/DELETE）须带 `X-CSRF-Token`（取值自 `sdas_csrf` Cookie，路径 `/api/v1`），双重 Cookie 校验失败返回 `CSRF_TOKEN_MISMATCH`(403)。
- **GET 请求**：不需要 CSRF。
- **角色隔离**：患者/医生 token 访问药店端点 → `PHARMACY_ROLE_REQUIRED`(403)；药店/医生 token 访问患者处方端点 → `UNAUTHORIZED`(401)；一律不返回处方数据。
- **前端实例隔离**：患者端 `patientRequest`（`sdas.patient.token`）、药店端 `pharmacyRequest`（`sdas.pharmacy.token`），互不复用 token；`pharmacyRequest` 必须开启 `withCredentials`。

---

## 3. 错误码索引（本功能涉及）

| code | 常量 | HTTP | 含义 |
|------|------|------|------|
| 401 | `UNAUTHORIZED` | 401 | 未登录 / 凭证失效（复用 001） |
| 403 | `CSRF_TOKEN_MISMATCH` | 403 | 双重 Cookie 校验失败（复用 001） |
| 403 | `PHARMACY_ROLE_REQUIRED` | 403 | 非药店角色访问药店端点（**新增**） |
| 400 | `AUTHENTICATION_FAILED` | 400 | 登录失败统一模糊提示（复用 003） |
| 404 | `PHARMACY_ACCOUNT_NOT_FOUND` | 404 | 药店账号不存在或已禁用（**新增**） |
| 400 | `INVALID_VERIFICATION_PROOF` | 400 | 验真凭证无效/被篡改/令牌过期（**新增**） |
| 404 | `PRESCRIPTION_NOT_FOUND` | 404 | 处方不存在或不属于当前患者（**新增**） |
| 400 | `PRESCRIPTION_NOT_SIGNED` | 400 | 处方未签名（DRAFT）不可取药（**新增**） |
| 400 | `PRESCRIPTION_REVOKED` | 400 | 处方已作废，**核销**被拒绝；验真返回 200 + `dispenseStatus=REVOKED`（**新增**） |
| 400 | `PRESCRIPTION_EXPIRED` | 400 | 处方已过期（**核销**时；验真返回 200 + `dispenseStatus=EXPIRED`，复用 003） |
| 400 | `BAD_REQUEST` | 400 | 请求参数错误（分支互斥/超长/非法，复用 001） |
| 503 | `SERVICE_UNAVAILABLE` | 503 | 数据库/依赖不可用或超时（复用 001） |

> 完整定义与 `message` 以 `../data-model.md` 1.6 为唯一权威来源；新增码不得与既有码同名不同值。

---

## 4. 敏感信息约束

任何响应体、审计日志与访问日志中**不得**出现：完整手机号、完整身份证号、患者真实全名、药品之外的诊断明细、二维码令牌明文（仅可存 SHA-256 摘要）。药店端只可见：脱敏姓名（`name_mask`）、处方要素、`dispensedByMask`、药店名。

**验真令牌只经 POST 请求体传输**，禁止放进 URL query（避免写入网关访问日志）。`AccessLogFilter` 不记录请求体。

---

## 5. 契约一致性清单（每迭代核对，SC-009）

- [ ] 端点路径与 `prescription-dispensing-api.md` 逐个一致（5 个）
- [ ] 每个 `code` 取值与 `data-model.md` 1.6 一致
- [ ] 字段名、类型、必填性与契约一致
- [ ] 时间格式均为 `yyyy-MM-dd'T'HH:mm:ss`；分页为 `{items,page,pageSize,total}`
- [ ] 认证、CSRF、角色要求与本文第 2 节一致
- [ ] 令牌格式 `v1.<cipher>.<sig>` 与 `data-model.md` 1.7 一致，二维码内容与 003 已签名处方规则同源（已修订 003 契约说明）
- [ ] 前端 `web/src/api/types.ts`、`error-codes.ts`、`mocks/` 已同步且标注本目录路径
- [ ] Playwright 断言均校验 `code` 值

---

## 6. 端点一览（5 个）

| # | 方法 | 路径 | 认证 | CSRF | 需求 |
|---|------|------|------|------|------|
| 1 | POST | `/api/v1/pharmacy/sessions` | 否 | 是 | FR-001、FR-002 |
| 2 | POST | `/api/v1/pharmacy/prescription-verifications` | 药店 | 是 | FR-006、FR-007、FR-008、FR-009 |
| 3 | POST | `/api/v1/pharmacy/prescriptions/{prescriptionId}/dispensing` | 药店 | 是 | FR-010、FR-011、FR-012、FR-013 |
| 4 | GET | `/api/v1/prescriptions` | 患者 | 否 | FR-003 |
| 5 | GET | `/api/v1/prescriptions/{prescriptionId}` | 患者 | 否 | FR-004、FR-005、FR-014 |

> 药店侧统一挂 `/api/v1/pharmacy/*`（与 003 的 `/workbench/*`、002 的 `/appointment/*` 词干错开）；患者侧处方统一挂 `/api/v1/prescriptions`（与 002 的 `/consultations`、003 的 `/workbench/prescriptions` 不冲突）。

---

## 7. 验真语义约定（重要）

1. **可重复扫描**：端点 2 为只读操作，同一二维码可被任意次调用，返回结果一致（除状态随时间变化）；不消费令牌、不写业务表。
2. **无副作用**：除写审计事件外，不改变任何业务状态；核销是端点 3 的唯一职责。
3. **幂等核销**：端点 3 重复调用返回 `code=200` + `dispenseResult=ALREADY_DISPENSED` + `idempotent=true`，不产生第二条记录，不使用 409。
4. **时间口径**：令牌 `exp`、处方 `validUntil`、核销 `dispensedAt` 一律使用服务端时间。
5. **URL 直达入口**：令牌链接形如 `/pharmacy/verify?t=<token>`。手机相机扫码直接打开该 URL 时，前端必须读取 `t` 自动验真；未登录时先跳登录并在回跳参数中保留 `t`，不得丢失令牌。
6. **状态 vs 错误码**：验真对 `EXPIRED`/`REVOKED` 返回 200 + 状态；核销对同两种状态返回 400。`DRAFT` 在验真阶段即 400 `PRESCRIPTION_NOT_SIGNED`。
