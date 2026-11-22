# 数据模型：AI辅助治疗决策

**Branch**: `006-ai-treatment-decision` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

本文件定义枚举、表结构变更、Redis 键、错误码与配置的**唯一来源**。后续 `contracts/`、`tasks.md`、前后端类型必须引用本文件，禁止自行发明字段名或取值。

---

## 1. 枚举

### 1.1 `TreatmentDecisionStatus`（辅助生成结果状态）

| 值 | 含义 | 前端表现 |
|----|------|----------|
| `SUCCESS` | 生成成功且结构完整 | 正常展示摘要/建议/草稿 |
| `TIMEOUT_FALLBACK` | 模型超时 | 降级提示 + 重试 |
| `ERROR_FALLBACK` | 上游异常/网络错误 | 降级提示 + 重试 |
| `EMPTY_FALLBACK` | 返回空或结构不可解析 | 降级提示 + 重试 |

- 列：`ai_decision_session.status`（`VARCHAR(16)`，沿用既有列）。
- 默认值：`EMPTY_FALLBACK`（低于 `SUCCESS` 的任何未知值均按降级处理，安全优先）。
- 转换器：`TreatmentDecisionStatusConverter`（`autoApply=false`，参照 `TriageResultStatusConverter`）。
- **不复用** `TriageResultStatus`：后者只有 `SUCCESS/TIMEOUT_FALLBACK`，无法区分异常与解析失败。

### 1.2 `TreatmentTriggerType`（触发方式）

| 值 | 含义 |
|----|------|
| `AUTO` | 进入开方页自动生成（含幂等复用） |
| `REGENERATE` | 医生手动重新生成 |

- 列：`ai_decision_session.trigger_type`（`VARCHAR(16)`）。
- 默认：`AUTO`。

### 1.3 `DegradedReason`（降级原因）

| 值 | 含义 |
|----|------|
| `NONE` | 未降级 |
| `TIMEOUT` | 超时 |
| `UPSTREAM_ERROR` | 上游异常 |
| `PARSE_FAILED` | JSON 解析/字段校验失败 |
| `EMPTY` | 返回空内容 |
| `KNOWLEDGE_UNAVAILABLE` | 知识库不可用（仍可用模型通用知识，标注依据有限） |

- 列：`ai_decision_session.degraded_reason`（`VARCHAR(64)`）。
- 默认：`NONE`。

### 1.4 可能性等级（非独立枚举，使用字符串）

建议诊断的 `likelihood` 取 `HIGH` / `MEDIUM` / `LOW` 三值字符串，由提示词约束、服务端校验（非法值降级为 `LOW`）。不建独立枚举，避免为单字段引入转换器。

---

## 2. 表结构

### 2.1 扩展：`ai_decision_session`（003 既有表，V11 迁移）

```sql
-- V11__ai_treatment_decision.sql
ALTER TABLE ai_decision_session
    ADD COLUMN trigger_type      VARCHAR(16)  NULL AFTER doctor_id,
    ADD COLUMN dialog_version    VARCHAR(64)  NULL AFTER trigger_type,
    ADD COLUMN result_json       MEDIUMTEXT   NULL AFTER output,
    ADD COLUMN degraded_reason   VARCHAR(64)  NULL AFTER status,
    ADD COLUMN applied_item_count INT         NULL AFTER elapsed_ms,
    ADD COLUMN adopted_at        DATETIME     NULL AFTER applied_item_count,
    ADD INDEX idx_ai_dialog_version (consultation_id, dialog_version);
```

**既有列（不改动）**：`id`、`consultation_id`、`doctor_id`、`input_summary`（`VARCHAR(2000)`，脱敏截断）、`output`（`VARCHAR(2000)`，建议文本拼接，保持 003 兼容）、`model_used`、`status`、`elapsed_ms`、`created_at`。

**写入规则**：

- `input_summary`：`主诉/描述（截断 500）+ 对话摘要（脱敏、截断 1500）`，**禁止写入完整病情明文**。
- `result_json`：完整结构化结果（见 §7.3），单条 ≤ 64 KB；超限截断草稿列表至 10 条。
- `output`：`suggestions[].diagnosis` 以「；」拼接，截断 2000 字符（保持 003 端点 9 的读取兼容）。
- `degraded_reason`：仅降级时非 `NONE`。
- `applied_item_count` / `adopted_at`：由端点 4 回填；未采纳为 `NULL`。

**索引理由**：`idx_ai_dialog_version` 支撑幂等复用查询（R-03）：`WHERE consultation_id = ? AND dialog_version = ? AND status = 'SUCCESS' ORDER BY id DESC LIMIT 1`。

**兼容影响**：`AiDecisionSession` 为 record，新增 6 个组件将改变构造签名，003 的 `AiDecisionSessionRepository.insert` 与 `AiDecisionServiceImpl`、`AiDecisionServiceTest` 必须同步修改（plan Phase 2 显式任务）。

**record 组件顺序（唯一来源，T009 按此实现）**——新组件**追加在末尾**，使 003 既有构造调用只需补 `null`：

```java
record AiDecisionSession(
        Long id,
        Long consultationId,
        Long doctorId,
        String inputSummary,
        String output,
        String modelUsed,
        TriageResultStatus status,
        Integer elapsedMs,
        LocalDateTime createdAt,
        TreatmentTriggerType triggerType,   // 新增
        String dialogVersion,               // 新增
        String resultJson,                  // 新增
        DegradedReason degradedReason,      // 新增
        Integer appliedItemCount,           // 新增
        LocalDateTime adoptedAt)            // 新增
```

> DDL 的列位置（`AFTER`）服务于可读性，与 Java 组件顺序无关；两者以本节为准分别实现。

### 2.2 只读复用的既有表

| 表 | 来源 | 本功能用途 | 约束 |
|----|------|-----------|------|
| `consultation` | 002 | 主诉/描述/状态/科室/接诊医生 | 只读；状态必须 `IN_PROGRESS` |
| `consultation_message` | 003 | 医患对话上下文 | 只读；`findByConsultationId` 升序 |
| `consultation_image` | 002 | 图片说明（仅取 `image_url` 与数量，不读二进制） | 只读；无文字说明时摘要标注「含 N 张图片，未提供文字描述」 |
| `drug_catalog` | 003 | 药品名称→ID 映射、目录查询端点 | 只读；`enabled=1` |
| `department_disease_kb` | 002 | 知识依据 | 只读；按 `department_id` 过滤 |
| `prescription` | 003 | 判断是否已开方（状态校验） | 只读；`findByConsultationId` |
| `doctor` | 003 | 医生姓名脱敏展示（可选） | 只读 |

**禁止**：本功能不新增业务表；不修改 002/003 既有表结构（`ai_decision_session` 属 003 但 Q3 已确认可扩展）。

---

## 3. 领域对象（内存模型，不落库）

| 对象 | 字段 | 说明 |
|------|------|------|
| `CaseSummary` | `chiefComplaint` / `presentIllness` / `dialogHighlights[]` / `riskAlerts[]` / `evidenceLimited` / `generatedAt` / `modelUsed` | 病情摘要；`evidenceLimited=true` 时前端提示依据有限 |
| `DiagnosisSuggestion` | `diagnosis` / `likelihood` / `basis[]` / `workup[]` / `riskAlert` | 诊断建议；`basis` 至少 1 条，否则视为解析失败；`diagnosis` 必须经确定性措辞归一化（去「确诊/一定是/百分之百/必然/可以排除」并前置「疑似」），见 `plan.md` Summary 第 3 条 |
| `MedicationDraft` | `drugId` / `drugName` / `spec` / `dosage` / `caution` / `needsCompletion` | 用药草稿；`drugId` 由服务端映射（R-07），不来自模型 |
| `TreatmentDecisionPayload` | `summary` / `suggestions[]` / `medicationDrafts[]` / `ignoredMedications[]` / `degradedReason` | 整体结果，序列化进 `result_json` |

**草稿状态**：不引入枚举。`needsCompletion=true` 表示模型未给出用法用量，前端必须高亮要求医生补充（FR-009）。

---

## 4. Redis 键

| 键 | 结构 | TTL | 用途 | 不可用时 |
|----|------|-----|------|----------|
| `ai:treatment:lock:{consultationId}` | String（UUID 令牌） | 30s | 生成互斥（`SET NX EX`） | 降级放行（无锁） |
| `ai:treatment:rate:{consultationId}` | String（计数） | 3600s | 重新生成频控（`INCR` + 首次 `EXPIRE`） | 降级放行 |

- 释放锁使用 `WATCH/MULTI` 校验令牌后 `DEL`（Redis 3.2 无 Lua 依赖，禁止 `UNLINK`）。
- 计数键在 `INCR` 返回 1 时设置 `EXPIRE`，避免每次 `INCR` 重置窗口。
- 日志只记 `consultationId` 与异常类名，禁止记录对话内容。

---

## 5. 错误码（`ErrorCode` 新增，005 段之后）

| 常量 | HTTP | message（逐字） | 触发场景 |
|------|------|------------------|----------|
| `TREATMENT_DECISION_NOT_FOUND` | 404 | 暂无治疗决策辅助记录 | 端点 2 查询时该问诊单无任何记录 |
| `TREATMENT_DECISION_BUSY` | 409 | 辅助生成进行中，请稍候 | 并发请求未获得锁 |
| `TREATMENT_DECISION_RATE_LIMITED` | 429 | 辅助生成过于频繁，请稍后重试 | 重新生成超过 3 次/小时 |
| `INVALID_TREATMENT_ADOPTION` | 400 | 采纳数量不合法 | 端点 4 的 `appliedItemCount` 缺省或超出 0–20 |

**复用既有码**：`CONSULTATION_NOT_FOUND`(404)、`CONSULTATION_FORBIDDEN`(403)、`INVALID_CONSULTATION_STATUS`(400)、`PRESCRIPTION_ALREADY_EXISTS`(409)、`DOCTOR_ROLE_REQUIRED`(403)、`SERVICE_UNAVAILABLE`(503)、`RATE_LIMITED`(429，仅用于非 AI 场景)。

**AI 失败不使用错误码**：降级统一走 `200 + TreatmentDecisionStatus`（R-08）。

---

## 6. 配置（`sdas.ai.treatment.*`）

| 键 | 默认值 | 说明 |
|----|--------|------|
| `model` | `DeepSeek-Flash-1017` | 模型名（与 003 默认一致） |
| `timeout-millis` | `15000` | 单次生成整体超时（含一次重试预算外的上限） |
| `max-output-tokens` | `2048` | 结构化 JSON 所需的输出上限 |
| `dialog-max-messages` | `40` | 读取对话条数上限 |
| `dialog-max-chars` | `3000` | 对话字符预算，超出按「最近优先」截断 |
| `knowledge-top-k` | `5` | 知识检索条数 |
| `max-suggestions` | `5` | 诊断建议条数上限 |
| `max-medications` | `10` | 用药草稿条数上限（与 003 处方明细上限一致） |
| `regenerate-limit-per-hour` | `3` | 重新生成频控 |
| `reuse-window-minutes` | `30` | 幂等复用窗口 |

- 绑定类：`TreatmentDecisionProperties`（`@ConfigurationProperties(prefix = "sdas.ai.treatment")`），在 `DoctorConfig` 中 `@EnableConfigurationProperties` 注册（沿用 003 的配置装配方式）。
- 全部支持 `SDAS_AI_TREATMENT_*` 环境变量覆盖。

---

## 7. DTO 字段（与 contracts 一一对应）

### 7.1 请求

| 端点 | 请求体 | 字段 |
|------|--------|------|
| 1 `POST .../treatment-decision` | 无（空 body 允许） | — |
| 2 `GET .../treatment-decision` | — | query：无 |
| 3 `POST .../treatment-decision/regenerate` | 无 | — |
| 4 `POST .../treatment-decision/adoptions` | `TreatmentAdoptionRequest` | `appliedItemCount`（int，0–20） |
| 5 `GET /v1/workbench/drugs` | — | query：`keyword`（≤50，可选）、`page`（≥1，默认 1）、`pageSize`（1–50，默认 20） |

### 7.2 响应（端点 1/2/3 共用）

| 字段 | 类型 | 说明 |
|------|------|------|
| `decisionId` | long | 留痕主键 |
| `consultationId` | long | 问诊单 |
| `status` | `TreatmentDecisionStatus` | 见 §1.1 |
| `triggerType` | `TreatmentTriggerType` | 见 §1.2 |
| `reused` | boolean | 是否复用既有结果（未调用模型） |
| `summary` | object\|null | `CaseSummary`，降级时为 `null` |
| `suggestions` | array | `DiagnosisSuggestion[]`，降级时 `[]` |
| `medicationDrafts` | array | `MedicationDraft[]`，降级时 `[]` |
| `ignoredMedications` | array\<string\> | 目录外被忽略的药名 |
| `disclaimer` | string | 固定文案 `AI 生成，仅供参考，最终诊疗决策由医生负责` |
| `degradedReason` | `DegradedReason` | 未降级为 `NONE` |
| `elapsedMs` | int | 本次耗时（复用时为 0） |
| `generatedAt` | datetime | `yyyy-MM-dd'T'HH:mm:ss` |

> **两个 `generatedAt` 的区别（唯一口径）**：
> - 顶层 `generatedAt`：**记录创建时间**，等于 `ai_decision_session.created_at`；复用时为被复用记录的时间。
> - `summary.generatedAt`：**摘要文本生成时间**；同一次调用内与顶层相同，复用时为首次生成时间。
> 二者不得互相替代；降级时 `summary=null`，顶层 `generatedAt` 照常返回。

### 7.3 `result_json` 结构

```json
{
  "summary": {
    "chiefComplaint": "咳嗽伴咳痰 3 天",
    "presentIllness": "3 天前受凉后出现咳嗽，咳白色黏痰，无发热、无胸痛。",
    "dialogHighlights": ["患者自述夜间咳嗽加重", "已服用阿莫西林 2 天效果一般"],
    "riskAlerts": ["既往青霉素过敏史待确认"],
    "evidenceLimited": false
  },
  "suggestions": [
    {
      "diagnosis": "疑似上呼吸道感染",
      "likelihood": "MEDIUM",
      "basis": ["主诉咳嗽咳痰 3 天", "无发热与胸痛"],
      "workup": ["必要时血常规"],
      "riskAlert": null
    }
  ],
  "medicationDrafts": [
    {
      "drugId": 901,
      "drugName": "阿莫西林胶囊",
      "spec": "0.25g*20",
      "dosage": "口服，一次 2 粒，一日 3 次",
      "caution": "青霉素过敏者禁用",
      "needsCompletion": false
    }
  ],
  "ignoredMedications": ["某目录外药品"],
  "degradedReason": "NONE"
}
```

**约束**：`result_json` 只存本表结构与 `degradedReason`，不存提示词原文、不含患者姓名/手机号/身份证、不含 `doctorId` 以外的医生信息。

---

## 8. 状态与权限矩阵

| 问诊单状态 | 已存在处方 | 允许生成 | 返回 |
|-----------|-----------|---------|------|
| `IN_PROGRESS` | 否 | 是 | 200 |
| `IN_PROGRESS` | 是 | 否 | 409 `PRESCRIPTION_ALREADY_EXISTS` |
| `PENDING` / `REPLIED` / `COMPLETED` / `CANCELLED` | — | 否 | 400 `INVALID_CONSULTATION_STATUS` |
| 不存在 | — | 否 | 404 `CONSULTATION_NOT_FOUND` |
| 非本单接诊医生 | — | 否 | 403 `CONSULTATION_FORBIDDEN` |
| 非医生角色 | — | 否 | 403 `DOCTOR_ROLE_REQUIRED` |

**越权与不存在**：按 003 既有口径分别返回 403/404，但响应体**不得**包含主诉、描述、对话等任何病情字段。
