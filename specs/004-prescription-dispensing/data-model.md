# 处方取药 数据模型

**功能**：`004-prescription-dispensing` | **上游**：[spec.md](./spec.md)、[plan.md](./plan.md)、[research.md](./research.md)

**契约**：[contracts/prescription-dispensing-api.md](./contracts/prescription-dispensing-api.md) | **Mock**：[contracts/mocks.md](./contracts/mocks.md) | **通用约定**：[contracts/README.md](./contracts/README.md)

**本文件状态**：枚举、`code` 与表结构的**唯一来源**（宪法原则 II/III）。字段名、类型、枚举取值、`code` 与 `message` 不得在前端或后端另行推测；变更须同步 `data-model.md` → `contracts/` → `web/src/api/types.ts` → `web/src/api/mocks/` → E2E 断言。

---

## 1. 枚举（状态唯一来源）

### 1.1 药店账号状态 `PharmacyAccountStatus`

| 取值 | 说明 | 初始/流转 |
|------|------|-----------|
| `ACTIVE` | 正常可用 | 账号创建时默认 |
| `DISABLED` | 已禁用 | 运维置为禁用；登录失败统一模糊提示 |

> 以 `String` 持久化，显式 `@Converter`（沿用 001/002/003 约定，禁止 `@Enumerated` 自动映射）。

### 1.2 处方取药状态 `DispenseStatus`（**派生，不落库**）

| 取值 | 契约语义 | 说明 |
|------|----------|------|
| `PENDING` | 未取药 | 已签名、未过期、未作废、无核销记录 |
| `DISPENSED` | 已取药 | 存在核销记录（无论是否已过有效期） |
| `EXPIRED` | 已过期 | 无核销记录且 `now > valid_until` |
| `REVOKED` | 已作废 | `prescription.status = REVOKED` |

**判定优先级（自上而下，命中即返回）**：

```text
1. prescription.status = REVOKED            → REVOKED
2. 存在 prescription_dispense_record        → DISPENSED
3. now > prescription.valid_until           → EXPIRED
4. 其他                                      → PENDING
```

`dispensable = (dispenseStatus == PENDING)`。

> 说明：已取药优先于已过期（取药是既成事实，过期只是时间推移）；已作废优先于已取药（作废代表处方失效，需人工介入，审计可查）。
>
> **端点语义差异（有意设计）**：验真端点对 `EXPIRED`/`REVOKED` 返回 **200 + 状态字段**（药店需要看到状态而非错误页）；核销端点对同两种状态返回 **400**（写操作被拒绝）。仅 `DRAFT` 在验真阶段也返回 400（未签名不构成可展示处方）。

### 1.3 核销结果 `DispenseResult`（**派生，不落库**）

| 取值 | 说明 | HTTP | `idempotent` |
|------|------|------|--------------|
| `DISPENSED` | 本次核销成功，写入记录 | 200 | `false` |
| `ALREADY_DISPENSED` | 已存在核销记录（重复或并发），未写入 | 200 | `true` |

> 不使用 409：重复核销是**正常业务分支**（Q2 先到先得），前端按其展示「已取药」而非报错。

### 1.4 处方状态 `PrescriptionStatus`（**复用 003，不新增取值**）

| 取值 | 本功能语义 |
|------|-----------|
| `DRAFT` | 草稿：不可验真成功、不可核销（`PRESCRIPTION_NOT_SIGNED`） |
| `SIGNED` | 已签名：可验真；未过期无核销时为 `PENDING` |
| `EXPIRED` | 已过期：与派生 `EXPIRED` 一致（以 `valid_until` 为准，双保险） |
| `REVOKED` | 已作废：不可核销（`PRESCRIPTION_REVOKED`） |

> **不新增 `DISPENSED`**（Q5）：取药事实只存在于 `prescription_dispense_record`。

### 1.5 统一分页结构（患者处方列表）

```json
{ "items": [], "page": 1, "pageSize": 10, "total": 0 }
```

- `page` 从 1 开始；`pageSize` 默认 10、上限 50；超限按上限截断（不报错）。
- 排序固定 `signed_at DESC, id DESC`（时间相同用主键稳定排序）。

### 1.6 新增 `ErrorCode`（本功能定义项）

继承 001/002/003 全部错误码，无同名不同值。以下 6 项为本功能新增，`code`/`message` 唯一来源即本表：

| code | 常量 | HTTP | message（逐字） | 触发条件 |
|------|------|------|------------------|----------|
| 403 | `PHARMACY_ROLE_REQUIRED` | 403 | 仅药店工作人员可访问该功能 | 非药店角色访问药店端点 |
| 404 | `PHARMACY_ACCOUNT_NOT_FOUND` | 404 | 药店账号不存在或已禁用 | 账号缺失/禁用（登录成功后查库兜底） |
| 400 | `INVALID_VERIFICATION_PROOF` | 400 | 验真凭证无效，请重新扫码 | 令牌结构/签名/解密/令牌有效期/验真码失败 |
| 404 | `PRESCRIPTION_NOT_FOUND` | 404 | 处方不存在或不属于当前患者 | 处方不存在、越权、药店侧处方缺失 |
| 400 | `PRESCRIPTION_NOT_SIGNED` | 400 | 处方未签名，无法取药 | `status = DRAFT` 时验真或核销 |
| 400 | `PRESCRIPTION_REVOKED` | 400 | 处方已作废，无法取药 | `status = REVOKED` 时核销 |

**复用既有码**：`UNAUTHORIZED`(401)、`CSRF_TOKEN_MISMATCH`(403)、`AUTHENTICATION_FAILED`(400)、`BAD_REQUEST`(400，参数校验失败：请求体分支同时为空/同时提供、字段超长或非法)、`PRESCRIPTION_EXPIRED`(400)、`SERVICE_UNAVAILABLE`(503)。

> 同步动作：`web/src/api/error-codes.ts` 增加上述 6 个常量（仅镜像数值）；`ErrorCode.java` 按既有顺序追加。

### 1.7 二维码令牌载荷（`v1`）

| 字段 | 类型 | 说明 |
|------|------|------|
| `v` | int | 令牌版本，固定 `1` |
| `pid` | number | 处方主键（仅存在于密文中） |
| `iat` | number | 签发时间（epoch 秒） |
| `exp` | number | 令牌失效时间（epoch 秒），取 `valid_until + sdas.pharmacy.qr.token-grace-days`（默认 30 天） |

- 编码：JSON（UTF-8，字段顺序固定 `v,pid,iat,exp`）→ `CryptoService.encrypt` → Base64Url（无 padding）。
- 签名：`SHA256withRSA("v1." + cipher)` → Base64Url（无 padding）。
- 完整令牌：`v1.<cipher>.<sig>`，正则 `^v1\.[A-Za-z0-9_-]{16,256}\.[A-Za-z0-9_-]{300,400}$`。
- **长度分层限额**：令牌本身 ≤ **700** 字符；拼接验真链接前缀后的完整 URL ≤ **1024**（与 `prescription.qr_code VARCHAR(1024)` 对齐）；请求字段 `qrContent` 接收 **8–1024**（兼容裸令牌与完整 URL 两种形态）。
- **禁止**进入载荷：患者姓名/手机号/身份证号、诊断、药品明细、医生姓名。

---

## 2. 表结构（`V7__prescription_dispensing_schema.sql`）

### 2.1 `pharmacy_account`

```sql
CREATE TABLE pharmacy_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name_mask VARCHAR(64) NOT NULL,
    pharmacy_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pharmacy_account_username (username),
    INDEX idx_pharmacy_account_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
```

- `password_hash`：BCrypt（cost ≥ 10，沿用 001）。
- `name_mask`：如「王*」，用于响应与审计，**禁止**存真实全名。
- `status`：`PharmacyAccountStatus`。

### 2.2 `prescription_dispense_record`

```sql
CREATE TABLE prescription_dispense_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT NOT NULL,
    pharmacy_id BIGINT NOT NULL,
    dispensed_at DATETIME NOT NULL,
    qr_token_version VARCHAR(8) NOT NULL,
    proof_digest CHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispense_prescription (prescription_id),
    INDEX idx_dispense_pharmacy (pharmacy_id, dispensed_at),
    CONSTRAINT fk_dispense_prescription FOREIGN KEY (prescription_id) REFERENCES prescription (id),
    CONSTRAINT fk_dispense_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
```

- `uk_dispense_prescription`：**一处方最多一条核销记录**，并发幂等的最终保证（R-08）。
- `qr_token_version`：核销时所用令牌版本（`v1`），便于未来审计与轮换排查。
- `proof_digest`：所用凭证（完整令牌或验真码）的 SHA-256 摘要，**不存令牌明文**（FR-016）。**口径分两级**：本列存**完整 64 位十六进制**；审计事件属性仅存**前 16 位十六进制**（便于关联排查、进一步降低可逆风险）。
- `dispensed_at`：服务端时间（`LocalDateTime.now()`），不使用数据库 `NOW()`，保证与业务时钟一致。

### 2.3 既有表变更

```sql
ALTER TABLE prescription MODIFY COLUMN qr_code VARCHAR(1024) NULL;
CREATE INDEX idx_prescription_patient ON prescription (patient_id, signed_at);
```

- `qr_code` 由 `VARCHAR(512)` 扩到 `VARCHAR(1024)`：令牌 URL 实测约 470–520 字符（R-09）。
- `idx_prescription_patient`：支撑端点 4「按患者 + 开方时间倒序」分页。
- `patient_account.id` 为 `BIGINT UNSIGNED`，`prescription.patient_id` 已是同类型，无外键变更。

### 2.4 种子（`V8__prescription_dispensing_seed.sql`，仅 dev 加载）

```sql
INSERT INTO pharmacy_account (username, password_hash, name_mask, pharmacy_name, status)
VALUES ('pharmacy_hp', '<BCrypt(password)>', '王*', '示例大药房（总部店）', 'ACTIVE'),
       ('pharmacy_xh', '<BCrypt(password)>', '李*', '示例大药房（西湖店）', 'ACTIVE');
```

- 口令与 003 医生种子一致（`password`），仅供本地联调；**prod 禁止加载 `db/seed`**。
- 种子账号主键由自增决定，不得硬编码 ID 到测试断言。

---

## 3. 字段映射（契约 ↔ 存储）

| 契约字段 | 来源 | 备注 |
|----------|------|------|
| `prescriptionId` | `prescription.id` | 明文返回（双方已鉴权） |
| `status` | `prescription.status` | 003 原状态字符串 |
| `dispenseStatus` | 派生（1.2） | 不落库 |
| `dispensable` | `dispenseStatus == PENDING` | 布尔 |
| `signedAt` / `validUntil` | `prescription.signed_at/valid_until` | `yyyy-MM-dd'T'HH:mm:ss` |
| `expired` | `now > valid_until` | 服务端时间 |
| `items[]` | `prescription_item` | `drugId/drugName/spec/dosage/caution`（复用 003 字段名） |
| `doctorNameMask` | `doctor.name` | 已是脱敏名（如「张*」） |
| `watermark` | `prescription.watermark` | 003 生成 |
| `qrCode` | `prescription.qr_code` | 完整验真 URL（含令牌） |
| `qrImageDataUrl` | `QrCodeService.generatePng(qrCode)` | `data:image/png;base64,...`，480×480 |
| `verifyCode` | 令牌 `sig` 段前 12 位 | 手工输入用（R-10），服务端计算 |
| `dispensedAt` | `prescription_dispense_record.dispensed_at` | 未取药为 `null` |
| `dispensedByMask` | `pharmacy_account.name_mask` | 未取药为 `null` |
| `dispenseRecordId` | `prescription_dispense_record.id` | 未取药为 `null` |
| `pharmacyName` | `pharmacy_account.pharmacy_name` | 仅药店端响应出现 |

---

## 4. 派生状态组合矩阵（必须单测全覆盖）

| # | `prescription.status` | 核销记录 | `now` vs `valid_until` | `dispenseStatus` | `dispensable` |
|---|----------------------|----------|------------------------|------------------|---------------|
| 1 | `SIGNED` | 无 | now < valid_until | `PENDING` | true |
| 2 | `SIGNED` | 无 | now = valid_until | `PENDING` | true |
| 3 | `SIGNED` | 无 | now > valid_until | `EXPIRED` | false |
| 4 | `SIGNED` | 有 | now < valid_until | `DISPENSED` | false |
| 5 | `SIGNED` | 有 | now > valid_until | `DISPENSED` | false |
| 6 | `EXPIRED` | 无 | now > valid_until | `EXPIRED` | false |
| 7 | `REVOKED` | 无 | 任意 | `REVOKED` | false |
| 8 | `REVOKED` | 有 | 任意 | `REVOKED` | false |

> 边界约定：`now = valid_until` 视为**未过期**（闭区间）；`valid_until` 为 `null` 时按 `EXPIRED` 处理并写审计告警（数据异常）。

---

## 5. 与 003 的兼容关系

| 项 | 003 现状 | 本功能处置 |
|----|----------|-----------|
| `prescription.status` | `DRAFT/SIGNED/EXPIRED/REVOKED` | **不改**，仅读 |
| `prescription.signature` | 内容签名（`SHA256withRSA`） | **不改**，验真时复验 |
| `prescription.qr_code` | 明文 URL `?prescriptionId=&sig=`（32 位前缀） | **替换**为 `?t=v1.<cipher>.<sig>`，同一提交内修订 003 契约说明 |
| `QrCodeService` | 300×300、默认纠错 | 改 480×480 + 纠错 `M`，签名不变 |
| 签名密钥 | 进程内 KeyPair（重启失效） | 改 keystore 加载（R-03） |
| `PrescriptionServiceImpl.detail()` | 误用 `CONSULTATION_NOT_FOUND` | 改 `PRESCRIPTION_NOT_FOUND` |

> 存量数据：003 未上线，`prescription` 仅有 dev/测试数据；V7 后重新签名生成新令牌。无法识别的旧 `qr_code` 一律 `INVALID_VERIFICATION_PROOF` + 审计，不做静默兼容。

---

## 6. 密钥与有效期约定

| 项 | 来源 | 说明 |
|----|------|------|
| RSA 私钥/公钥 | `sdas.doctor.signature.key-store-path` + `key-alias` + `key-store-password`（`SHA256withRSA`） | 私钥不出服务端；`prod` 缺失即启动失败 |
| AES-GCM 密钥 | `sdas.identity.crypto.aes-key` | 轮换需保留旧密钥 ≥ 3 天 |
| 处方有效期 | `sdas.doctor.prescription-valid-days`（默认 3） | 令牌 `exp` 以此为基准并加宽限期 |
| 验真链接模板 | `sdas.pharmacy.qr.verify-url-template` | 默认 `http://localhost:5183/pharmacy/verify?t=%s` |
| 验真码长度 | `sdas.pharmacy.qr.verify-code-length`（默认 12） | 需与前端展示一致 |
| 二维码图片 | `sdas.pharmacy.qr.image-size`（480）、`error-correction`（M） | 需与 R-09 一致 |
| 令牌宽限期 | `sdas.pharmacy.qr.token-grace-days`（默认 30） | `exp = valid_until + 本值`；处方过期后仍须能扫出 `EXPIRED`（否则 1.2「过期 → 200」与端点 2 的凭证校验互斥） |

---

## 7. 变更同步链路（宪法原则 III）

```text
spec.md（语义）
  → data-model.md（枚举/表/错误码，唯一来源）
  → contracts/README.md + prescription-dispensing-api.md + mocks.md
  → server：ErrorCode / model 枚举 / dto / 迁移脚本
  → web：src/api/types.ts / error-codes.ts / api 模块 / mocks
  → 测试：契约测试断言 + Playwright 断言 + testsdoc 报告
```

任一层改动未同步下一层，视为契约一致性失败（SC-009）。
