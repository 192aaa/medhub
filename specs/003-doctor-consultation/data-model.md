# 医生接诊 数据模型

**功能**：`003-doctor-consultation`
**契约**：`contracts/doctor-consultation-api.md`
**Mock**：`contracts/mocks.md`
**通用约定**：`contracts/README.md`
**本文件状态**：枚举与 `code` 的唯一来源（宪法原则 II/III）。字段名、类型、枚举取值、`code` 与 `message` 不得在前端或后端另行推测；如需变更须同步 `contracts/` → `web/src/api/types.ts` → `web/src/api/mocks/` → E2E 断言。

---

## 1. 枚举（状态机唯一来源）

> 全部枚举以 `String` 持久化，沿用 `001/002` 的显式 `@Converter` 模式（禁止 JPA 自动 `EnumType.STRING` 隐式约定）。

### 1.1 问诊单状态 `ConsultationStatus`（**复用 002**）

002 已定义，本功能**复用，不新增同名不同值**。医生接诊语义映射：

| 取值 | 002 语义 | 003 医生侧语义 |
|------|----------|----------------|
| `PENDING` | 待接诊 | 医生「待接诊」列表展示；点击「接诊」的起始态 |
| `IN_PROGRESS` | 沟通中 | 医生「接诊」后进入；可回复与开方 |
| `REPLIED` | 已回复 | 医生回复后（可选中间态） |
| `COMPLETED` | 已完成 | 开具处方或诊疗结束 |
| `CANCELLED` | 已取消 | 范围外（预留） |

> 接诊动作 = `PENDING → IN_PROGRESS`；是否推进到 `REPLIED/COMPLETED` 由后续诊疗流程决定，本功能只约束「接诊」这一跳转。

### 1.2 处方状态 `PrescriptionStatus`

| 取值 | 说明 | 初始/流转 |
|------|------|-----------|
| `DRAFT` | 草稿（预览，未签名） | 生成预览时 |
| `SIGNED` | 已签名（生效） | `DRAFT → SIGNED`（医生确认签名后） |
| `EXPIRED` | 已过期 | `SIGNED → EXPIRED`（超过 `valid_until`） |
| `REVOKED` | 已撤销 | 预留（范围外） |

### 1.3 消息发送方 `MessageType`

| 取值 | 说明 |
|------|------|
| `DOCTOR` | 医生发送 |
| `PATIENT` | 患者发送 |
| `SYSTEM` | 系统消息（如接诊/超时提示） |

### 1.4 接诊超时状态 `ConsultationTimeoutStatus`

| 取值 | 说明 |
|------|------|
| `NONE` | 未超时 |
| `ADMIN_NOTIFIED` | 已超时并通知管理员 |
| `TRANSFERRED` | 已转给其他医生（预留，后续增强） |

### 1.5 医生账号状态 `DoctorAccountStatus`

| 取值 | 说明 |
|------|------|
| `ACTIVE` | 正常可用 |
| `DISABLED` | 已禁用 |

### 1.6 新增 `ErrorCode`（本功能定义项）

继承自 `001/002` 的全部错误码（无同名不同值）。以下为 `003` 新增项；`code`/`message` 唯一来源即本表。

| code | 常量 | HTTP | 触发条件 |
|------|------|------|----------|
| 403 | `DOCTOR_ROLE_REQUIRED` | 403 | 非医生角色访问医生工作台 |
| 404 | `DOCTOR_ACCOUNT_NOT_FOUND` | 404 | 医生账号不存在或已禁用 |
| 400 | `INVALID_REPLY_FORMAT` | 400 | 回复为空 / 超 1000 字 / 含控制字符 |
| 400 | `INVALID_CONSULTATION_STATUS` | 400 | 状态不允许当前操作（未接诊即回复/开方、已完成单再操作） |
| 409 | `CONSULTATION_ACCEPT_CONFLICT` | 409 | 并发/重复接诊（仅一人成功） |
| 404 | `DRUG_NOT_FOUND` | 404 | 药品不存在或已停用 |
| 400 | `DRUG_NOT_IN_CATALOG` | 400 | 自由输入药品名 / 不在目录 |
| 400 | `INVALID_PRESCRIPTION_FORMAT` | 400 | 处方字段（规格/用法用量/注意事项）为空或超长 |
| 409 | `PRESCRIPTION_ALREADY_EXISTS` | 409 | 一张问诊单重复开具处方 |
| 400 | `PRESCRIPTION_SIGN_FAILED` | 400 | 签名密钥缺失或签名失败 |
| 400 | `PRESCRIPTION_EXPIRED` | 400 | 处方已过期 |

复用 002 既有：`CONSULTATION_NOT_FOUND`(404)、`CONSULTATION_FORBIDDEN`(403)、`DOCTOR_NOT_FOUND`(404)、`UNAUTHORIZED`(401)、`CSRF_TOKEN_MISMATCH`(403)、`AUTHENTICATION_FAILED`(400)、`SERVICE_UNAVAILABLE`(503) 等，不重复定义。

### 1.7 审计事件类型 `AuditEventType`（003 新增）

| 取值 | 说明 |
|------|------|
| `DOCTOR_LOGIN` | 医生登录 |
| `CONSULTATION_ACCEPTED` | 接诊 |
| `CONSULTATION_REPLIED` | 医生回复 |
| `PRESCRIPTION_CREATED` | 开具处方（签名成功） |
| `PRESCRIPTION_SIGN_FAILED` | 处方签名失败 |
| `CONSULTATION_TIMEOUT` | 接诊超时兜底 |
| `AI_DECISION_REQUESTED` | AI 辅助决策调用 |

---

## 2. 数据表结构

**迁移脚本**：`server/src/main/resources/db/migration/V5__doctor_consultation_schema.sql`（在 002 的 `V3` 之后执行）。
**约定**：InnoDB / utf8mb4 / `utf8mb4_general_ci`；时间列 `DATETIME`；`created_at`/`updated_at` 写入时填充；`version INT` 乐观锁；金额 `DECIMAL(10,2)`。

### 2.1 `doctor_account` 医生账号（新增，对应 `doctor` 表）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK AUTO_INCREMENT | 账号主键 |
| `username` | VARCHAR(64) | NOT NULL UNIQUE | 登录名 |
| `password_hash` | VARCHAR(100) | NOT NULL | BCrypt 哈希（强度 ≥ 10，原则 IV） |
| `doctor_id` | BIGINT UNSIGNED | NOT NULL UNIQUE, FK→doctor(id) | 关联医生档案 |
| `name_mask` | VARCHAR(32) | NOT NULL | 脱敏姓名 |
| `status` | VARCHAR(16) | NOT NULL | `DoctorAccountStatus`（`ACTIVE`/`DISABLED`） |
| `created_at`/`updated_at`/`version` | — | — | 同约定 |

### 2.2 `consultation` 扩展列（002 表上 ALTER，V5 新增）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `accepted_at` | DATETIME | NULL | 接诊时间（`NULL`=未接诊） |
| `accepted_by_doctor_id` | BIGINT UNSIGNED | NULL | 接诊医生（冗余，便于归属校验） |
| `timeout_status` | VARCHAR(16) | NOT NULL DEFAULT 'NONE' | `ConsultationTimeoutStatus` |
| `timeout_handled_at` | DATETIME | NULL | 超时兜底执行时间 |
| `transferred_to_doctor_id` | BIGINT UNSIGNED | NULL | 转诊目标医生（预留，后续增强） |

> `accepted_at IS NULL` 作为接诊幂等条件（状态 CAS：`PENDING → IN_PROGRESS`）。

### 2.3 `consultation_message` 对话消息（新增）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 消息主键 |
| `consultation_id` | BIGINT UNSIGNED | FK→consultation(id) | 关联问诊单 |
| `sender_type` | VARCHAR(16) | NOT NULL | `MessageType`（`DOCTOR`/`PATIENT`/`SYSTEM`） |
| `sender_id` | BIGINT UNSIGNED | NULL | 发送方主键（医生或患者） |
| `content` | VARCHAR(1000) | NOT NULL | 内容（医生回复 ≤ 1000 字） |
| `created_at` | DATETIME | NOT NULL | — |

`KEY idx_consultation(consultation_id, created_at)`。

### 2.4 `drug_catalog` 药品目录（新增，只读）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 药品主键 |
| `name` | VARCHAR(128) | NOT NULL | 药品名称 |
| `spec` | VARCHAR(128) | NULL | 规格（如 `0.25g*20`） |
| `manufacturer` | VARCHAR(128) | NULL | 厂商 |
| `dosage_form` | VARCHAR(32) | NULL | 剂型（片剂/胶囊/注射液等） |
| `unit` | VARCHAR(16) | NULL | 单位（盒/瓶/支） |
| `prescription_only` | TINYINT(1) | NOT NULL DEFAULT 1 | 是否处方药 |
| `enabled` | TINYINT(1) | NOT NULL DEFAULT 1 | 是否启用 |
| `source` | VARCHAR(32) | NOT NULL DEFAULT 'PRESET' | 来源（仅 `PRESET`） |
| `created_at`/`updated_at`/`version` | — | — | 同约定 |

> 只读、无维护界面；开方时校验 `enabled=1`。

### 2.5 `prescription` 电子处方（新增）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 处方主键 |
| `consultation_id` | BIGINT UNSIGNED | NOT NULL UNIQUE, FK→consultation(id) | 关联问诊单（一张单仅一张处方） |
| `doctor_id` | BIGINT UNSIGNED | FK→doctor(id) | 开具医生 |
| `patient_id` | BIGINT UNSIGNED | FK→patient_account(id) | 患者 |
| `status` | VARCHAR(16) | NOT NULL | `PrescriptionStatus` |
| `signature` | VARCHAR(512) | NULL | 签名摘要（`SIGNED` 后必填） |
| `qr_code` | VARCHAR(512) | NULL | 二维码内容（验真链接） |
| `watermark` | VARCHAR(512) | NULL | 水印内容 |
| `signed_at` | DATETIME | NULL | 签名时间 |
| `valid_until` | DATETIME | NULL | 有效期截止（`signed_at + 3d`） |
| `created_at`/`updated_at`/`version` | — | — | 同约定 |

### 2.6 `prescription_item` 处方药品明细（新增）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 明细主键 |
| `prescription_id` | BIGINT UNSIGNED | FK→prescription(id) | 关联处方 |
| `drug_id` | BIGINT UNSIGNED | FK→drug_catalog(id) | 关联药品目录 |
| `spec` | VARCHAR(128) | NULL | 规格快照 |
| `dosage` | VARCHAR(255) | NOT NULL | 用法用量 |
| `caution` | VARCHAR(500) | NULL | 注意事项 |
| `created_at` | DATETIME | NOT NULL | — |

### 2.7 `ai_decision_session` AI 辅助决策会话（可追溯，FR-017）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 会话主键 |
| `consultation_id` | BIGINT UNSIGNED | FK→consultation(id) | 关联问诊单 |
| `doctor_id` | BIGINT UNSIGNED | FK→doctor(id) | 请求医生 |
| `input_summary` | VARCHAR(2000) | NULL | 输入摘要（脱敏） |
| `output` | VARCHAR(2000) | NULL | AI 输出 |
| `model_used` | VARCHAR(64) | NULL | 模型名 |
| `status` | VARCHAR(16) | NULL | `TriageResultStatus` 语义（`SUCCESS`/`TIMEOUT_FALLBACK`，复用 002） |
| `elapsed_ms` | INT | NULL | 耗时 |
| `created_at` | DATETIME | NOT NULL | — |

### 2.8 `security_audit_event`（复用 001 表）

沿用既有表，本功能仅新增事件类型（见 1.7），不改表结构。

---

## 3. 配置项（唯一来源）

`server/src/main/resources/application.yml` 在 `sdas:` 下新增：

```yaml
sdas:
  doctor:
    # 接诊超时：超过未回复/开药则兜底（用户决策 Q2）
    timeout-hours: 24
    # 医生回复单条上限（字符）
    reply-max-chars: 1000
    # 处方默认有效期（天），从签名时间起算（用户决策 Q4）
    prescription-valid-days: 3
    signature:
      # 服务端托管签名密钥库路径（密钥经 aes-key 加密，禁止明文落库）
      key-store-path: ${SDAS_DOCTOR_KEYSTORE:./keystore/doctor-signing.jks}
      key-alias: doctor-signing
      algorithm: SHA256withRSA
    prescription:
      # 二维码验真链接模板
      verify-url-template: ${SDAS_PRESCRIPTION_VERIFY_URL:http://localhost:5183/verify-prescription?prescriptionId=%s&sig=%s}
      watermark-prefix: 示例医院
  ai:
    decision:
      timeout-millis: ${SDAS_DECISION_TIMEOUT_MILLIS:5000}
      model: ${SDAS_DECISION_MODEL:DeepSeek-Flash-1017}
```

> AI 辅助决策复用 002 的 `spring.ai.openai.*` 全局配置（`base-url: https://api.sophnet.com`、`api-key` 环境变量注入），本功能仅新增 `sdas.ai.decision.*` 的模型名与超时。

---

## 4. AI 依赖与知识库治理

- **AI 调用形态（Q5 定稿）**：复用 002 已评审的 Spring AI + DeepSeek/SophNet 链路，**禁止双链路**（原则 I）。本功能只新增 `sdas.ai.decision.*` 配置与医生侧提示词，不新增模型依赖。
- **药品目录（Q6 定稿）**：`drug_catalog.source='PRESET'` 数据库预置，只读使用，**不开放管理功能**。
- **签名（Q3 定稿）**：服务端托管密钥，JDK `SHA256withRSA`，密钥经 `aes-key` 加密存储，禁止明文落库。
- **AI 输出标注**：辅助决策输出明确标注「辅助参考，不作为诊断结论」，不向患者暴露内部推理（FR-014/FR-017）。
