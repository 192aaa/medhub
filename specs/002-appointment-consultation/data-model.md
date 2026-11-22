# 预约挂号与图文问诊 数据模型

**功能**：`002-appointment-consultation`
**契约**：`contracts/appointment-consultation-api.md`
**Mock**：`contracts/mocks.md`
**通用约定**：`contracts/README.md`
**本文件状态**：枚举与 `code` 的唯一来源（宪法原则 II/III）。字段名、类型、枚举取值、`code` 与 `message` 不得在前端或后端另行推测；如需变更须同步 `contracts/` → `web/src/api/types.ts` → `web/src/api/mocks/` → E2E 断言。

---

## 1. 枚举（状态机唯一来源）

> 全部枚举以 `String` 持久化，沿用 `001-patient-identity` 的显式 `@Converter` 模式（禁止 JPA 自动 `EnumType.STRING` 隐式约定），保证数据库列值与枚举名一致、可被 Flyway 校验。

### 1.1 预约状态 `AppointmentStatus`

| 取值 | 说明 | 初始/流转 |
|------|------|-----------|
| `PENDING_PAYMENT` | 待支付（已建挂号单，未支付） | 创建后初始态 |
| `PAID` | 已支付（可进入图文问诊） | `PENDING_PAYMENT → PAID` |
| `CANCELLED` | 已取消（患者主动取消或超时未支付自动关单） | `PENDING_PAYMENT → CANCELLED` |
| `CONSULTING` | 问诊中 | `PAID → CONSULTING` |
| `COMPLETED` | 已完成 | `CONSULTING → COMPLETED` |

### 1.2 支付状态（挂号预约侧）`PaymentStatus`

| 取值 | 说明 |
|------|------|
| `UNPAID` | 未支付 |
| `PAID` | 已支付 |
| `REFUNDED` | 已退款（范围外，预留枚举值） |
| `CLOSED` | 已关闭（超时未支付，不可再支付） |

### 1.3 支付记录状态 `PaymentRecordStatus`

| 取值 | 说明 |
|------|------|
| `PENDING` | 支付处理中 |
| `SUCCESS` | 支付成功 |
| `FAILED` | 支付失败（可重试） |

### 1.4 支付渠道 `PaymentChannel`

| 取值 | 说明 |
|------|------|
| `MOCK` | 模拟支付（**MVP**：`dev`/`test` 闭环；生产渠道待产品确认后另行接入，见 `spec.md` 9.1 Q2） |

### 1.5 问诊单状态 `ConsultationStatus`

| 取值 | 说明 |
|------|------|
| `PENDING` | 待接诊（医生尚未响应） |
| `IN_PROGRESS` | 沟通中 |
| `REPLIED` | 已回复 |
| `COMPLETED` | 已完成 |
| `CANCELLED` | 已取消 |

### 1.6 医生在线状态 `DoctorOnlineStatus`

| 取值 | 说明 |
|------|------|
| `ONLINE` | 在线可接诊 |
| `OFFLINE` | 离线 |
| `BUSY` | 忙碌（暂不接新单） |

### 1.7 排班时段 `SchedulePeriod`

| 取值 | 说明 |
|------|------|
| `MORNING` | 上午 |
| `AFTERNOON` | 下午 |
| `EVENING` | 晚间 |

### 1.8 导诊结果状态 `TriageResultStatus`

| 取值 | 说明 | HTTP |
|------|------|------|
| `SUCCESS` | AI 推荐成功（返回推荐科室/医生） | 200 |
| `TIMEOUT_FALLBACK` | AI 超时降级（返回全量科室列表，**不阻塞主流程**） | 200 |

> 超时降级**不视为错误**：默认 5 秒（`timeout-millis: 5000`）后返回 `TIMEOUT_FALLBACK` 与全量科室列表，前端据此展示手动选择路径（满足用户决策与 `SC-006`）。

### 1.9 新增 `ErrorCode`（本功能定义项）

继承自 `001-patient-identity/data-model.md` 1.9 的全部错误码（无同名不同值）。以下为 `002` 新增项；`code`/`message` 唯一来源即本表。

| code | 常量 | HTTP | 触发条件 |
|------|------|------|----------|
| 404 | `DEPARTMENT_NOT_FOUND` | 404 | 科室不存在或已停用 |
| 404 | `DOCTOR_NOT_FOUND` | 404 | 医生不存在 |
| 404 | `APPOINTMENT_NOT_FOUND` | 404 | 挂号预约不存在或不属于当前患者 |
| 404 | `CONSULTATION_NOT_FOUND` | 404 | 问诊单不存在或不属于当前患者 |
| 400 | `INVALID_COMPLAINT_FORMAT` | 400 | 主诉为空 / 超长（>500）/ 含控制字符 |
| 400 | `INVALID_DESCRIPTION_FORMAT` | 400 | 选填描述超长（>2000） |
| 400 | `INVALID_IMAGE_FORMAT` | 400 | 图片内容类型不在白名单（jpg/png/webp）或内容伪装 |
| 400 | `INVALID_IMAGE_SIZE` | 400 | 单张图片超过 5 MB |
| 400 | `TOO_MANY_IMAGES` | 400 | 图片数量超过 9 张 |
| 400 | `INVALID_PAYMENT_AMOUNT` | 400 | 客户端传入金额与服务端金额不一致 / 金额为 0 |
| 400 | `INVALID_APPOINTMENT_STATUS` | 400 | 挂号单状态不允许当前操作（重复支付、已取消后支付、未支付即提交问诊单） |
| 400 | `INVALID_SCHEDULE` | 400 | 排班不存在 / 已被约满 / 不在可预约窗口 |
| 403 | `REVISIT_NOT_ELIGIBLE` | 403 | 未实名认证或不具备历史就诊记录，禁止发起预约/支付/问诊 |
| 403 | `CONSULTATION_FORBIDDEN` | 403 | 越权访问他人问诊单或图片 |
| 409 | `PAYMENT_CONFLICT` | 409 | 并发支付竞争（仅一个成功，另一个冲突） |
| 400 | `PAYMENT_FAILED` | 400 | 模拟支付明确失败（可重试） |

复用 001 既有：`UNAUTHORIZED`(401)、`CSRF_TOKEN_MISMATCH`(403)、`RATE_LIMITED`(429)、`SERVICE_UNAVAILABLE`(503)、`AUTHENTICATION_FAILED`(400)、`BAD_REQUEST`(400)、`NOT_FOUND`(404)、`METHOD_NOT_ALLOWED`(405)、`INTERNAL_ERROR`(500) 等，不重复定义。

### 1.10 审计事件类型 `AuditEventType`（002 新增）

| 取值 | 说明 |
|------|------|
| `APPOINTMENT_CREATED` | 创建挂号预约 |
| `APPOINTMENT_PAID` | 挂号费支付成功 |
| `PAYMENT_FAILED` | 支付失败 |
| `CONSULTATION_SUBMITTED` | 提交图文问诊单 |
| `IMAGE_UPLOADED` | 上传问诊图片 |
| `TRIAGE_RECOMMENDED` | AI 导诊推荐成功 |
| `TRIAGE_TIMEOUT_FALLBACK` | AI 导诊超时降级 |
| `REVISIT_GATE_DENIED` | 复诊门禁拒绝 |
| `CONSULTATION_GATE_DENIED` | 问诊门禁拒绝 |

---

## 2. 数据表结构

**迁移脚本**：`server/src/main/resources/db/migration/V2__appointment_consultation_schema.sql`（在 001 的 `V1` 之后执行）。
**约定**：InnoDB / utf8mb4 / `utf8mb4_general_ci`；时间列 `DATETIME`；`created_at`/`updated_at` 写入时填充；`version INT` 乐观锁；金额 `DECIMAL(10,2)`。

### 2.1 `department` 科室

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK AUTO_INCREMENT | 科室主键 |
| `name` | VARCHAR(64) | NOT NULL | 科室名 |
| `description` | VARCHAR(255) | NULL | 简介 |
| `icon_url` | VARCHAR(255) | NULL | 图标 |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 展示排序 |
| `enabled` | TINYINT(1) | NOT NULL DEFAULT 1 | 是否启用 |
| `created_at` / `updated_at` | DATETIME | NOT NULL | 时间戳 |
| `version` | INT | NOT NULL DEFAULT 0 | 乐观锁 |

### 2.2 `doctor` 医生

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 医生主键 |
| `name_mask` | VARCHAR(32) | NOT NULL | 脱敏姓名，如 `张*` |
| `title` | VARCHAR(32) | NULL | 职称 |
| `department_id` | BIGINT UNSIGNED | FK→department(id) | 所属科室 |
| `hospital` | VARCHAR(128) | NULL | 医院 |
| `years_of_experience` | INT | NULL | 从业年限 |
| `good_at` | VARCHAR(512) | NULL | 擅长 |
| `consultation_fee` | DECIMAL(10,2) | NOT NULL | 挂号费（服务端定价） |
| `rating` | DECIMAL(3,2) | NULL | 评分 |
| `consultation_count` | INT | NOT NULL DEFAULT 0 | 接诊量 |
| `online_status` | VARCHAR(16) | NOT NULL | `DoctorOnlineStatus` |
| `avatar_url` | VARCHAR(255) | NULL | 头像 |
| `created_at`/`updated_at`/`version` | — | — | 同 2.1 |

### 2.3 `doctor_schedule` 排班

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 排班主键 |
| `doctor_id` | BIGINT UNSIGNED | FK→doctor(id) | 医生 |
| `work_date` | DATE | NOT NULL | 出诊日期 |
| `period` | VARCHAR(16) | NOT NULL | `SchedulePeriod` |
| `slots_total` | INT | NOT NULL | 总号源 |
| `slots_booked` | INT | NOT NULL DEFAULT 0 | 已约号源 |
| `created_at`/`updated_at`/`version` | — | — | 同 2.1 |

`UNIQUE(doctor_id, work_date, period)`。

### 2.4 `department_disease_kb` 科室-疾病知识库（数据库预置，无管理界面）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 主键 |
| `department_id` | BIGINT UNSIGNED | FK→department(id) | 关联科室 |
| `disease_name` | VARCHAR(128) | NOT NULL | 疾病/症状名 |
| `keywords` | VARCHAR(512) | NOT NULL | 关键词（逗号分隔，用于导诊匹配） |
| `advice` | VARCHAR(1024) | NULL | 建议描述 |
| `source` | VARCHAR(32) | NOT NULL DEFAULT 'PRESET' | 数据来源（仅 `PRESET`） |
| `created_at`/`updated_at`/`version` | — | — | 同 2.1 |

> **用户决策**：知识库为数据库预置（`DATABASE_PRESET`），**暂不开放任何管理/写界面**；AI 导诊只读此表。

### 2.5 `patient_history_visit` 历史就诊记录（只读，dev/test 预置）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 主键 |
| `patient_id` | BIGINT UNSIGNED | — | 患者主键（与 `patient_account.id` 同源） |
| `department_id` | BIGINT UNSIGNED | FK→department(id) | 历史就诊科室 |
| `last_visit_at` | DATETIME | NOT NULL | 最近就诊时间 |
| `created_at` | DATETIME | NOT NULL | — |

> 本功能**只读**此表以判定复诊资格（Q1）；不维护其来源数据，`dev`/`test` 由 Flyway 预置演示数据。

### 2.6 `appointment` 挂号预约

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 预约主键 |
| `patient_id` | BIGINT UNSIGNED | FK→patient_account(id) | 患者 |
| `doctor_id` | BIGINT UNSIGNED | FK→doctor(id) | 医生 |
| `department_id` | BIGINT UNSIGNED | FK→department(id) | 科室 |
| `schedule_id` | BIGINT UNSIGNED | FK→doctor_schedule(id) | 排班 |
| `appointment_date` | DATE | NOT NULL | 就诊日期 |
| `period` | VARCHAR(16) | NOT NULL | `SchedulePeriod` |
| `fee_amount` | DECIMAL(10,2) | NOT NULL | 挂号费（服务端定价快照） |
| `payment_status` | VARCHAR(16) | NOT NULL | `PaymentStatus` |
| `appointment_status` | VARCHAR(16) | NOT NULL | `AppointmentStatus` |
| `consultation_id` | BIGINT UNSIGNED | NULL（FK→consultation(id)） | 关联问诊单 |
| `created_at`/`updated_at`/`version` | — | — | 同 2.1 |

`UNIQUE(patient_id, schedule_id)`（防同一时段重复预约）；`KEY idx_patient(patient_id)`；`KEY idx_appointment_status(appointment_status)`。

### 2.7 `payment_record` 支付记录

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 支付记录主键 |
| `appointment_id` | BIGINT UNSIGNED | UNIQUE, FK→appointment(id) | 一对一（幂等：一个预约仅一条成功支付） |
| `patient_id` | BIGINT UNSIGNED | FK→patient_account(id) | 患者 |
| `amount` | DECIMAL(10,2) | NOT NULL | 支付金额 |
| `channel` | VARCHAR(16) | NOT NULL | `PaymentChannel`（`MOCK`） |
| `status` | VARCHAR(16) | NOT NULL | `PaymentRecordStatus` |
| `out_trade_no` | VARCHAR(64) | NULL | 模拟交易号 |
| `paid_at` | DATETIME | NULL | 支付时间 |
| `created_at`/`updated_at`/`version` | — | — | 同 2.1 |

> **幂等**：`UNIQUE(appointment_id)` + 同事务写 `appointment.payment_status=PAID`；重复支付返回首次成功结果（200），并发竞争仅一个成功，另一个 `PAYMENT_CONFLICT`(409)。

### 2.8 `consultation` 图文问诊单

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 问诊单主键 |
| `patient_id` | BIGINT UNSIGNED | FK→patient_account(id) | 患者 |
| `doctor_id` | BIGINT UNSIGNED | FK→doctor(id) | 医生 |
| `department_id` | BIGINT UNSIGNED | FK→department(id) | 科室 |
| `appointment_id` | BIGINT UNSIGNED | UNIQUE, FK→appointment(id) | 关联挂号单 |
| `complaint` | VARCHAR(500) | NOT NULL | 主诉 |
| `description` | VARCHAR(2000) | NULL | 选填详细描述 |
| `images` | JSON | NULL | 图片 URL 列表 |
| `summary` | VARCHAR(2000) | NULL | AI 生成的病情摘要（随单同步医生侧） |
| `status` | VARCHAR(16) | NOT NULL | `ConsultationStatus` |
| `created_at`/`updated_at`/`version` | — | — | 同 2.1 |

### 2.9 `consultation_image` 问诊图片

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 图片主键 |
| `consultation_id` | BIGINT UNSIGNED | FK→consultation(id) | 关联问诊单（上传时可为空，提交时绑定） |
| `patient_id` | BIGINT UNSIGNED | FK→patient_account(id) | 患者 |
| `storage_path` | VARCHAR(512) | NOT NULL | 本地存储路径 |
| `url` | VARCHAR(512) | NOT NULL | 访问 URL |
| `content_type` | VARCHAR(64) | NOT NULL | 校验后的内容类型 |
| `size_bytes` | BIGINT | NOT NULL | 字节大小 |
| `created_at`/`version` | — | — | 同 2.1 |

### 2.10 `ai_triage_session` AI 导诊会话（可追溯，FR-023）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT UNSIGNED | PK | 会话主键 |
| `patient_id` | BIGINT UNSIGNED | NULL | 患者（可匿名） |
| `complaint` | VARCHAR(500) | NOT NULL | 输入主诉 |
| `recommended_department_ids` | JSON | NULL | 推荐科室 ID 列表 |
| `status` | VARCHAR(16) | NOT NULL | `TriageResultStatus` |
| `model_used` | VARCHAR(64) | NULL | 模型名 |
| `kb_version` | VARCHAR(32) | NULL | 知识库版本/快照标记 |
| `elapsed_ms` | INT | NULL | 耗时（含超时） |
| `created_at` | DATETIME | NOT NULL | — |

### 2.11 `security_audit_event`（复用 001 表）

沿用 `V1` 已建表，本功能仅新增事件类型（见 1.10），不改动表结构。

---

## 3. 配置项（唯一来源）

`server/src/main/resources/application.yml` 在 `sdas:` 下新增：

```yaml
sdas:
  appointment:
    # 用户决策：MVP 模拟支付，不接入真实第三方支付网关
    mock-payment-enabled: true
    # 待支付过期时间：超过未支付自动 CLOSED
    payment-timeout-seconds: 900
  ai:
    triage:
      # 用户决策：默认 5 秒超时降级
      timeout-millis: 5000
      # 超时降级策略：返回全量科室列表
      fallback: FULL_DEPARTMENT_LIST
      model: ${SDAS_TRIAGE_MODEL:deepseek-v4-flash}
  storage:
    images:
      # 用户决策：本地文件系统存储
      location: ${APP_UPLOAD_DIR:./uploads/consultation}
      allowed-content-types: image/jpeg,image/png,image/webp
      max-size-bytes: 5242880   # 5 MB
      max-count: 9
      serve-path: /static/consultation-images
```

---

## 4. AI 依赖与知识库治理

- **AI 调用形态（Q3 定稿）**：沿用 `001-patient-identity` 的 Spring AI + DeepSeek 官方 API 结论（见 001 `research.md` R-04），**禁止双链路**（原则 I / 宪法「解释与待决项」）。本功能只新增 `sdas.ai.triage.*` 配置，不新增模型依赖。
- **科室-疾病知识库（用户决策）**：数据库预置（`department_disease_kb.source='PRESET'`），只读使用，**暂不开放任何管理功能**。
- **超时降级（用户决策）**：默认 `timeout-millis: 5000`，超时返回 `TIMEOUT_FALLBACK` + 全量科室列表，HTTP 200，不计入错误；满足 `SC-006`（降级手动路径成功率 ≥ 99%）。
