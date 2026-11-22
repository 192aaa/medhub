# 处方取药 Mock 数据

**功能**：`004-prescription-dispensing`
**契约**：`prescription-dispensing-api.md`
**枚举与 `code` 来源**：`../data-model.md`

> Mock 数据必须与契约保持一致（宪法原则 VI）；Mock E2E 用 `page.route()` 拦截，真实后端 E2E 断言响应 `code` 值。键格式 `METHOD /v1/...`，`#SCENARIO` 表示分支场景。

## 固定标识（Mock 内统一）

| 标识 | 取值 |
|------|------|
| 药店账号 | `pharmacy_hp` / `pharmacyId=1` / `nameMask=王*` / `pharmacyName=示例大药房（总部店）` |
| 药店 token | `mock-pharmacy-token` |
| 处方 | `7001`（未取药）、`7002`（已取药）、`7003`（已过期）、`7004`（已作废） |
| 验真码 | `Ab3dE9fG7hJk`（`7001`） |
| 令牌 | `v1.bW9jay1jaXBoZXItcGF5bG9hZA.mock-signature-segment-0000000000000000` |
| 开方医生 | `张*`；水印 `示例医院 电子处方` |
| 二维码图片 | `data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==`（1×1 占位，仅供渲染断言） |
| 时间 | `signedAt=2026-11-08T10:12:00`、`validUntil=2026-11-11T10:12:00`、`dispensedAt=2026-11-08T10:30:00` |

## 药店登录（端点 1）

| 键 | 场景 | 返回 |
|----|------|------|
| `POST /v1/pharmacy/sessions` | M1 登录成功 | 200 `{ accessToken, pharmacyId:1, nameMask:"王*", pharmacyName, expiresIn:7200 }` |
| `POST /v1/pharmacy/sessions#FAIL` | 账号或口令错误 | 400 `AUTHENTICATION_FAILED`「账号或密码错误」 |
| `POST /v1/pharmacy/sessions#CSRF` | 缺 CSRF | 403 `CSRF_TOKEN_MISMATCH` |

## 处方验真（端点 2）

| 键 | 场景 | 返回 |
|----|------|------|
| `POST /v1/pharmacy/prescription-verifications` | M2 验真成功（未取药） | 200 `verificationResult=VALID`、`dispenseStatus=PENDING`、`dispensable=true`、`expired=false`、`items[1]` |
| `POST /v1/pharmacy/prescription-verifications#EXPIRED` | M3 已过期 | 200 `dispenseStatus=EXPIRED`、`dispensable=false`、`expired=true` |
| `POST /v1/pharmacy/prescription-verifications#DISPENSED` | M4 已取药 | 200 `dispenseStatus=DISPENSED`、`dispensable=false`、`dispensedAt`、`dispensedByMask:"王*"`、`dispenseRecordId:31` |
| `POST /v1/pharmacy/prescription-verifications#INVALID` | M5 令牌被篡改 / 验真码不匹配 | 400 `INVALID_VERIFICATION_PROOF`（`data:null`，无任何处方字段） |
| `POST /v1/pharmacy/prescription-verifications#REVOKED` | 处方已作废（验真仍返回状态） | 200 `status=REVOKED`、`dispenseStatus=REVOKED`、`dispensable=false` |
| `POST /v1/pharmacy/prescription-verifications#NOT_SIGNED` | 草稿处方 | 400 `PRESCRIPTION_NOT_SIGNED` |
| `POST /v1/pharmacy/prescription-verifications#NOT_FOUND` | 验签通过但处方不存在 | 404 `PRESCRIPTION_NOT_FOUND` |
| `POST /v1/pharmacy/prescription-verifications#ROLE` | 患者 token 访问 | 403 `PHARMACY_ROLE_REQUIRED` |

> 注：同一键可被**重复调用**且返回一致（验真幂等，M2 可连续调用两次证明「二维码可多次扫描」）。

## 取药核销（端点 3）

| 键 | 场景 | 返回 |
|----|------|------|
| `POST /v1/pharmacy/prescriptions/7001/dispensing` | M6 首次核销成功 | 200 `dispenseResult=DISPENSED`、`idempotent=false`、`dispenseRecordId:31`、`dispenseStatus=DISPENSED`、`dispensedAt`、`dispensedByMask:"王*"` |
| `POST /v1/pharmacy/prescriptions/7001/dispensing#ALREADY` | M7 重复/并发核销 | 200 `dispenseResult=ALREADY_DISPENSED`、`idempotent=true`、`dispenseRecordId:31` |
| `POST /v1/pharmacy/prescriptions/7001/dispensing#EXPIRED` | 已过期 | 400 `PRESCRIPTION_EXPIRED` |
| `POST /v1/pharmacy/prescriptions/7004/dispensing#REVOKED` | 已作废 | 400 `PRESCRIPTION_REVOKED` |
| `POST /v1/pharmacy/prescriptions/7001/dispensing#INVALID` | 凭证缺失或不匹配 | 400 `INVALID_VERIFICATION_PROOF` |

## 患者处方列表（端点 4）

| 键 | 场景 | 返回 |
|----|------|------|
| `GET /v1/prescriptions` | M8 列表（两条，含未取药与已取药） | 200 `{ items:[7001 PENDING, 7002 DISPENSED], page:1, pageSize:10, total:2 }` |
| `GET /v1/prescriptions#EMPTY` | 空列表 | 200 `{ items:[], page:1, pageSize:10, total:0 }` |
| `GET /v1/prescriptions#UNAUTHORIZED` | 药店 token 访问 | 401 `UNAUTHORIZED` |

## 患者处方详情（端点 5）

| 键 | 场景 | 返回 |
|----|------|------|
| `GET /v1/prescriptions/7001` | M9 未取药详情（含二维码） | 200 `status=SIGNED`、`dispenseStatus=PENDING`、`dispensable=true`、`qrCode`、`qrImageDataUrl`、`verifyCode`、`items[1]` |
| `GET /v1/prescriptions/7002` | M10 已取药详情 | 200 `dispenseStatus=DISPENSED`、`dispensable=false`、`dispensedAt`、`dispensedByMask:"王*"` |
| `GET /v1/prescriptions/7003` | M11 已过期详情 | 200 `dispenseStatus=EXPIRED`、`expired=true`、`dispensable=false` |
| `GET /v1/prescriptions/9999#NOT_FOUND` | M12 越权 / 不存在 | 404 `PRESCRIPTION_NOT_FOUND`（文案与越权一致，不泄漏存在性） |

## E2E 场景编排（供 `tests/e2e/fixtures/prescription-dispensing.ts` 使用）

| 用户故事 | 场景序列 |
|----------|----------|
| US1 药店登录 + 验真 | `M1` → `M2`（可重复调用两次，验证多次扫描）→ `M5`（篡改）→ `M3`（过期）→ 作废返回 200 `dispenseStatus=REVOKED` → `/pharmacy/verify?t=...` 直达自动验真 |
| US2 取药核销 | `M1` → `M2` → `M6` → `M7`（重复核销）；失败分支各自断言 400 且写入 `PRESCRIPTION_DISPENSE_FAILED` 审计 |
| US3 患者出示处方 | `M8` → `M9`（二维码可见）→ `M10` → `M12`（越权） |
