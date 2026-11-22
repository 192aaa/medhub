# 数据模型：AI健康咨询

**来源**：[spec.md](./spec.md) · [plan.md](./plan.md) · [research.md](./research.md)
**约定**：本文件是枚举与表结构的**唯一来源**，前后端引用枚举时必须标注 `specs/005-ai-health-consultation/data-model.md`，禁止各自硬编码。

---

## 1. 枚举定义

### 1.1 消息角色 `MessageRole`

| 值 | 含义 | 说明 |
|----|------|------|
| `PATIENT` | 患者发言 | 落库前做长度校验（≤2000 字符） |
| `ASSISTANT` | 智能体回答 | 含 `disclaimer` 字段，正文不含声明文案 |

**持久化**：VARCHAR(16)，`SafeEnumConverter` 转换，未知值降级为 `PATIENT`（保守：不把未知当助手消息展示）。

### 1.2 会话状态 `SessionStatus`

| 值 | 含义 |
|----|------|
| `ACTIVE` | 正常会话 |
| `DELETED` | 患者已删除（逻辑删除，查询一律过滤） |

> 不设 `ARCHIVED`：本期无归档需求，避免枚举膨胀。

### 1.3 流式完成状态 `StreamStatus`

| 值 | 含义 |
|----|------|
| `COMPLETED` | 正常结束，正文完整 |
| `FAILED` | 模型/网络失败，正文可能为空或部分 |
| `INTERRUPTED` | 客户端断开或患者主动停止，保留已生成内容 |

**用途**：`error` 事件与「重试入口」的依据；`FAILED`/`INTERRUPTED` 的消息在会话历史中标注「未完成」，前端不复用于上下文（R-06 热窗口仅收 `COMPLETED`）。

### 1.4 紧急判定结论 `EmergencyVerdict`

| 值 | 含义 |
|----|------|
| `CONFIRMED` | 规则命中且模型确认 |
| `REJECTED` | 规则命中但模型否决（不预警，留痕） |
| `UNAVAILABLE` | 规则命中但模型复核不可用（**仍预警**，安全优先） |
| `MODEL_ONLY` | 规则未命中、模型独立判定为紧急（预警） |

---

## 2. 表结构

### 2.1 `ai_health_session`（咨询会话）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `patient_id` | BIGINT UNSIGNED | NOT NULL, FK → `patient_account(id)` | 归属，一切查询强制带此条件 |
| `title` | VARCHAR(64) | NOT NULL | 默认取首条患者消息前 20 字，可重命名 |
| `last_active_at` | DATETIME | NOT NULL | 列表排序键（倒序） |
| `message_count` | INT | NOT NULL DEFAULT 0 | 冗余计数，避免列表页 count 查询 |
| `status` | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE' | `SessionStatus` |
| `created_at` / `updated_at` | DATETIME | NOT NULL | |
| `version` | INT | NOT NULL DEFAULT 0 | 乐观锁 |

索引：`idx_ai_session_patient (patient_id, last_active_at)`；`uk_ai_session_patient_id (patient_id, id)`（便于按患者+会话定位）。

### 2.2 `ai_health_message`（咨询消息）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `session_id` | BIGINT | NOT NULL, FK → `ai_health_session(id)` | |
| `role` | VARCHAR(16) | NOT NULL | `MessageRole` |
| `content` | TEXT | NOT NULL | 患者输入或模型正文（**不含**免责声明） |
| `disclaimer` | VARCHAR(64) | NULL | 仅助手消息：固定「仅供参考，不构成医疗诊断」 |
| `emergency` | TINYINT(1) | NOT NULL DEFAULT 0 | 本条是否触发预警 |
| `rule_code` | VARCHAR(32) | NULL | 命中的规则码（可解释性） |
| `stream_status` | VARCHAR(16) | NOT NULL DEFAULT 'COMPLETED' | `StreamStatus` |
| `model_used` | VARCHAR(64) | NULL | 实际模型名（审计与排查） |
| `created_at` | DATETIME | NOT NULL | |

索引：`idx_ai_message_session (session_id, id)`。

### 2.3 `ai_health_emergency_decision`（紧急判定留痕）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `session_id` | BIGINT | NOT NULL, FK → `ai_health_session(id)` | |
| `message_id` | BIGINT | NULL, FK → `ai_health_message(id)` | 助手消息落库后回填 |
| `rule_code` | VARCHAR(32) | NOT NULL | 命中规则；`MODEL_ONLY` 表示仅模型判定 |
| `model_verdict` | VARCHAR(16) | NOT NULL | `EmergencyVerdict` |
| `warned` | TINYINT(1) | NOT NULL | 最终是否预警 |
| `created_at` | DATETIME | NOT NULL | |

索引：`idx_ai_emergency_session (session_id, id)`。
**禁止**：本表不得写入患者原话、症状描述等病情明文（原则 IV）。

---

## 3. 只读依赖（不修改其结构与数据）

| 表 | 来源 | 用途 | 读取条件 |
|----|------|------|----------|
| `consultation` | 002 | 病历上下文（主诉、状态、时间） | `patient_id = ?` AND `status = 'COMPLETED'`，取最近 3 条 |
| `prescription` / `prescription_item` | 003 | 用药与注意事项 | 由上述 consultation 关联，`status = 'SIGNED'` |
| `drug_catalog` | 003 | 药品名/规格 | 按 `prescription_item.drug_id` |
| `department_disease_kb` | 002 | 医学知识检索（含 V10 预置医疗/医院信息知识） | 该表**无 `enabled` 列**，检索不加启用条件；关键词 Top-K=5 |
| `department` | 002 | 紧急预警弹窗的科室名称/ID | 按规则配置的 `departmentName` 精确匹配 `department.name`（该表无 `code` 列） |

> V10 预置写入 `department_disease_kb` 时统一 `source='PRESET'`、`kb_version='2026.11'`，不修改既有行；
> 并幂等补建兜底科室「全科医学科」作为预置条目的 `department_id` 归属。

---

## 4. Redis 键规范（Redis 3.2.100，仅 List/Hash/String）

| Key | 结构 | TTL | 说明 |
|-----|------|-----|------|
| `ai:health:ctx:{sessionId}` | List | 24h | 最近 12 条摘要（`role\|content`，单条 ≤500 字符），`RPUSH` + `LTRIM -12 -1` |
| `ai:health:meta:{sessionId}` | Hash | 24h | `patientId`/`title`/`lastActiveAt`/`messageCount` |
| `ai:health:lock:{sessionId}` | String | 60s | `SET NX EX`，阻塞同会话并发生成 → `CHAT_BUSY` |

**miss 回填**：从 `ai_health_message` 取最近 12 条 `COMPLETED` 消息重建，`truncated` 标记不落 Redis。
**禁止**：Streams、`XAUTOCLAIM`、`UNLINK`、`OBJECT ENCODING` 等 3.2 之后或不适用的命令。

---

## 5. 错误码（`ErrorCode` 追加，按既有顺序）

| code | 常量 | message（逐字） | 触发条件 |
|------|------|------------------|----------|
| 404 | `SESSION_NOT_FOUND` | 会话不存在 | 会话不存在 / 不属于当前患者 / 已删除（越权与不存在同码） |
| 400 | `SESSION_TITLE_INVALID` | 会话标题不合法 | 重命名为空或超过 64 字符 |
| 400 | `MESSAGE_TOO_LONG` | 消息内容过长 | 患者输入 > 2000 字符 |
| 400 | `SESSION_MESSAGE_LIMIT` | 当前会话消息数已达上限 | 单会话 > 200 条，建议新建对话 |
| 409 | `CHAT_BUSY` | 当前会话正在生成回答，请稍候 | `ai:health:lock:{sessionId}` 已被占用 |
| 503 | `AI_SERVICE_UNAVAILABLE` | 智能助手暂时不可用，请稍后重试 | 模型不可用且无法给出任何内容（流式已建立则走 `error` 事件） |

复用既有：`UNAUTHORIZED`(401)、`CSRF_TOKEN_MISMATCH`(403)、`BAD_REQUEST`(400)、`SERVICE_UNAVAILABLE`(503)。

---

## 6. 配置项（`sdas.ai.health.*` / `sdas.hospital.*`）

| Key | 默认值 | 说明 |
|-----|--------|------|
| `sdas.ai.health.model` | `deepseek-v4-flash` | 模型名（R-01） |
| `sdas.ai.health.timeout-millis` | 8000 | 单轮总超时 |
| `sdas.ai.health.first-token-timeout-millis` | 3000 | 首包超时（SC-001） |
| `sdas.ai.health.max-output-tokens` | 1024 | 单轮最大输出 |
| `sdas.ai.health.context-max-chars` | 6000 | prompt 总预算 |
| `sdas.ai.health.context-medical-max-chars` | 1500 | 病历摘要预算（R-04） |
| `sdas.ai.health.history-window` | 12 | Redis/DB 热窗口条数 |
| `sdas.ai.health.message-max-chars` | 2000 | 患者单条输入上限 |
| `sdas.ai.health.session-message-limit` | 200 | 单会话消息上限 |
| `sdas.ai.health.knowledge-top-k` | 5 | 知识检索条数 |
| `sdas.ai.health.emergency.rules` | 内置默认表 | 规则列表（`code`/`name`/`level`/`departmentName`/`keywords`）；为空即启用内置默认表 |
| `sdas.hospital.name` / `address` / `phone` / `emergency-phone` / `service-hours` | 示例医院等 | 医院结构化信息（R-05） |

---

## 7. 状态流转

```text
会话：  ACTIVE ──(患者删除)──▶ DELETED
消息：  (流式) ──成功──▶ COMPLETED
                ├─模型/网络失败──▶ FAILED
                └─客户端断开/停止──▶ INTERRUPTED
```

约束：仅 `COMPLETED` 的助手消息进入下一轮上下文（R-06）；`FAILED`/`INTERRUPTED` 保留展示但标注「未完成」。
