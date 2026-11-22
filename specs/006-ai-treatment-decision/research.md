# 技术调研与决策：AI辅助治疗决策

**Branch**: `006-ai-treatment-decision` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

本文件回答「为什么这样设计」。每条决策给出**结论 / 理由 / 备选方案与否决原因**，供 `plan.md`、`data-model.md`、`contracts/` 交叉引用（`R-xx`）。

---

## R-01 模型调用：复用 003 的同步一次性调用，不引入流式

**结论**：新增 `TreatmentDecisionClient` + `TreatmentDecisionClientImpl`，内部复用 003 的 `ChatClient.Builder` 与 `sdas.ai.treatment.*` 配置，以「一次性返回完整 JSON」的方式调用（`CompletableFuture.supplyAsync` + `get(timeoutMillis)`），超时或异常返回 `success=false` 的结果对象。

**理由**：

1. 开方场景需要**结构化结果**（摘要四段 + 建议四段 + 用药草稿），流式文本无法直接映射到表单，反而增加解析复杂度。
2. 003 已明确「复用 002 的全局 `spring.ai.openai.*`，禁止双链路」（`003/data-model.md` §3 注）。再引入 SSE 会形成第二套消费形态。
3. 医生在开方页对**首字延迟**不敏感（该页本身有表单可填），但对**结构完整**敏感。

**备选与否决**：

- 复用 003 `AiDecisionClient.decide(prompt)` 直接扩展：否决。其返回 `List<String>` 自由文本，无法承载摘要/草稿结构，且改动会破坏 003 端点 9 的既有语义。
- 使用 005 的 `Flux` 流式 + `switchOnFirst`：否决。结构化 JSON 需完整后才可解析，流式仅增加复杂度。

**影响**：plan M1；配置 `sdas.ai.treatment.*`。

---

## R-02 输出契约：严格 JSON + 容错解析，解析失败按降级处理

**结论**：提示词要求模型**只输出一个 JSON 对象**（字段固定），服务端解析顺序为：去 ```` ```json ```` 围栏 → 截取首个 `{` 到最后一个 `}` → Jackson 反序列化 → 字段级校验（缺字段/类型错误即失败）→ **确定性措辞归一化**（`确诊`/`一定是`/`百分之百`/`必然`/`可以排除` → 去词 + 前置「疑似」，过短则丢弃该条）。解析失败统一归 `EMPTY_FALLBACK`，**不把原始文本展示给医生**。

**理由**：

1. 医生端展示原始 JSON 或半截文本属于严重体验缺陷，且可能泄漏提示词内容。
2. 字段级校验可在入库前拦截「AI 给出目录外药品」「剂量为空」等结构问题（配合 R-07）。

**备选与否决**：

- 让模型输出 Markdown 分节：否决。分节边界不稳定，无法可靠映射四条建议。
- 输出 JSON Schema 强约束（function calling / structured output）：本期不使用——现有 Spring AI 链路为纯文本调用，引入结构化输出需改全局配置，收益不抵风险；登记为后续增强。

**影响**：plan M2；`TreatmentDecisionPayload` 解析器；测试用例（Markdown/JSON 混杂、字段缺失、超长）。

---

## R-03 触发与幂等：进入开方页自动生成，按「对话指纹」复用

**结论**：

- 前端进入 `/workbench/prescriptions/new?consultationId=` 时自动调用 `POST .../treatment-decision`（一次）。
- 幂等键 `dialog_version = sha256(lastMessageId + ":" + messageCount + ":" + prescriptionExists)` 取前 16 位十六进制。
- 若 30 分钟（`reuse-window-minutes`）内存在**同医生 + 同问诊单 + 同 dialog_version** 的成功记录，直接返回该记录并置 `reused=true`，**不调用模型**、不写新留痕。
- 手动「重新生成」走独立端点，强制调用模型并更新 `dialog_version` 记录。

**理由**：医生常在同一问诊单反复进出开方页（看对话、改药、再回来），自动触发若无幂等会造成重复计费与留痕噪声（SC-007 要求留痕可回溯，不是越多越好）。

**备选与否决**：

- 仅前端缓存（sessionStorage）：否决。多标签页与刷新会重复计费，且无法跨设备。
- 以时间窗口（如 10 分钟）粗暴去重：否决。医生在窗口内补充对话后结果应更新，指纹能精确反映「对话是否变化」。

**影响**：plan M3；`ai_decision_session.dialog_version`；FR-002、FR-021。

---

## R-04 并发与频控：Redis 锁 + 小时计数，不可用时降级放行

**结论**：

- 互斥锁：`ai:treatment:lock:{consultationId}`，`SET NX EX 30`；拿不到锁返回 409 `TREATMENT_DECISION_BUSY`。
- 频控：`ai:treatment:rate:{consultationId}`，`INCR` + `EXPIRE 3600`；仅对「重新生成」计数，> 3 返回 429 `TREATMENT_DECISION_RATE_LIMITED`。
- Redis 不可用：锁与频控**均降级放行**并打 WARN（不含病情），保证开方不中断（与 005 的 `RedisDialogStore` 降级语义一致）。

**理由**：开方是诊疗刚需，频控属于成本治理；因缓存故障阻断开方不可接受（SC-006）。

**备选与否决**：

- 数据库唯一索引做互斥：否决。需要额外的「进行中」状态行，且异常退出会留下僵尸行。
- 复用 003 的 `RateLimitService`：否决。其维度是 `phoneDigest`（患者身份），无医生/问诊单维度键。

**影响**：plan M4；`RedisKeys` 之外新增 `ai:treatment:*` 前缀；错误码 2 个。

---

## R-05 留痕：扩展 `ai_decision_session`，整体结果存 `result_json`

**结论**：`V11` 对 `ai_decision_session` 增加 6 列 + 1 个索引：

| 列 | 类型 | 用途 |
|----|------|------|
| `trigger_type` | `VARCHAR(16)` | `AUTO` / `REGENERATE` |
| `dialog_version` | `VARCHAR(64)` | 幂等指纹（R-03） |
| `result_json` | `MEDIUMTEXT` | 结构化结果整体（摘要/建议/草稿/降级原因） |
| `degraded_reason` | `VARCHAR(64)` | `TIMEOUT` / `UPSTREAM_ERROR` / `PARSE_FAILED` / `EMPTY` |
| `applied_item_count` | `INT` | 医生采纳条数（端点 4 回填） |
| `adopted_at` | `DATETIME` | 采纳时间 |

索引：`idx_ai_dialog_version (consultation_id, dialog_version)`。

**理由**：规约 Q3 结论为「复用并扩展」。整体 JSON 存储避免为摘要/建议/草稿各开三张表；查询需求只有「按问诊单取最近一次」与「按指纹复用」，索引即可覆盖。

**备选与否决**：

- 新建 `treatment_decision` 表：否决。与 003 端点 9 语义重叠，会形成两套留痕口径，效果统计需跨表 UNION。
- 三张子表（摘要/建议/草稿明细）：否决。本期无按条目查询需求，过度规范化。

**影响**：`V11__ai_treatment_decision.sql`；实体 `AiDecisionSession` 扩展 6 个组件（记录组件顺序变化，需同步 003 的 `AiDecisionSessionRepository.insert`）。

> **兼容风险**：`AiDecisionSession` 为 record，新增组件会改变构造签名，003 现有 `AiDecisionServiceImpl` 与 `AiDecisionServiceTest` 需同步调整。plan 阶段列为 Phase 2 的显式任务，测试必须一并更新。

---

## R-06 知识库：复用 002 知识表 + 005 的只读检索仓储

**结论**：复用 `department_disease_kb`（002，只读）与 005 已建的 `healthconsultation/repository/KnowledgeRepository`（`searchByKeywords(List<String>, int)`）。按问诊单所属 `department_id` 过滤 + 对话 2/3-gram 候选词，Top-K = 5。

**理由**：宪法「复用优先」。005 已实现 2/3-gram 候选词生成与命中排序，重复实现会造成两处检索口径。

**备选与否决**：

- 新建 `treatment_knowledge` 表：否决。本期无新知识来源，Q2 已确认不新增知识表。
- 在 doctor 包内复制一份检索 SQL：否决。口径漂移风险。

**影响**：006 的 Service 依赖 005 的 `KnowledgeRetriever` / `KnowledgeRepository`（同模块、单向依赖 006 → 005 只读能力）。

---

## R-07 药品映射：AI 只给药名，服务端映射到启用目录

**结论**：

1. 提示词要求用药建议只写**药品通用名 + 用法用量 + 注意事项**，**不得输出 drugId**。
2. 服务端按名称在 `drug_catalog`（`enabled=1`）匹配：先精确匹配 `name`，再尝试「去规格后缀」匹配（如「阿莫西林胶囊 0.25g」→「阿莫西林胶囊」）。
3. 匹配失败的条目丢弃，并在响应 `ignoredMedications` 中返回被忽略的药名列表（前端提示「部分建议药品不在目录内，已忽略」）。
4. 规格统一取目录值（`drug_catalog.spec`），不采用模型输出，避免伪造规格。

**理由**：让模型输出 `drugId` 会诱导编造 ID（幻觉），且 ID 与目录强耦合、无法跨环境复用；名称映射失败可解释、可提示（FR-008、SC-004）。

**影响**：`DrugCatalogRepository` 新增只读方法 `findEnabledByNames(List<String>)`；plan M5。

---

## R-08 降级：HTTP 200 + 状态枚举，不写半成品处方

**结论**：模型超时/异常/空返回/解析失败时：

- 仍落一条 `ai_decision_session`（`status` ∈ `TIMEOUT_FALLBACK` / `ERROR_FALLBACK` / `EMPTY_FALLBACK`，`degraded_reason` 记原因）。
- 响应 HTTP **200**，`data.status` 为降级枚举，`summary` / `suggestions` / `medicationDrafts` 为空数组，`disclaimer` 照常下发，附 `degradedReason`。
- 前端展示「AI 辅助暂不可用（可重试）」，处方表单完全可用。
- **不产生**处方或草稿记录；不影响 003 的 `prescription` 表。

**理由**：与 003 端点 9「降级不阻塞（HTTP 200 + TIMEOUT_FALLBACK）」保持一致，避免前端为 AI 失败新增错误分支；同时满足 SC-006（≥99% 可正常开方）。

**备选与否决**：

- 返回 503 让前端处理：否决。会把「AI 增强失败」放大成「开方失败」的错觉，且与 003 契约不一致。

**影响**：`TreatmentDecisionStatus` 枚举 4 值；契约降级示例。

---

## R-09 伴随改动：新增只读药品目录查询端点

**结论**：新增 `GET /api/v1/workbench/drugs?keyword=&page=&pageSize=`，分页返回启用药品（`id/name/spec/unit/prescriptionOnly`）。`DrugCatalogRepository` 增加 `findEnabledPage(String keyword, int offset, int limit)` 与 `countEnabled(String keyword)`。

**理由**：003 的开方页当前**硬编码 `DEMO_DRUGS`（901–905）**，注释写明「契约未含药品目录查询端点」。006 的用药草稿若不在该硬编码列表内，下拉框无法显示，会退化成「空白选项」缺陷。此改动同时修复 003 遗留，属于 006 的必要前置。

**风险**：扩大本期范围。已登记 `Complexity Tracking`，并在 tasks 中标记为 `[P]` 独立任务，可单独回滚（前端仍可回退硬编码）。

**影响**：端点 5；前端 `web/src/api/doctor-consultation.ts` 新增 `listDrugs`。

---

## 汇总表

| 决策 | 主题 | 关键产出 |
|------|------|----------|
| R-01 | 同步一次性结构化调用 | `TreatmentDecisionClient` |
| R-02 | JSON 输出 + 容错解析 | 解析器 + `EMPTY_FALLBACK` |
| R-03 | 自动触发 + 对话指纹幂等 | `dialog_version` + `reused` |
| R-04 | Redis 锁与小时频控 | `ai:treatment:*` |
| R-05 | 扩展 `ai_decision_session` | `V11` + 6 列 + 索引 |
| R-06 | 复用知识库 | 005 `KnowledgeRepository` |
| R-07 | 药名→目录映射 | `findEnabledByNames` |
| R-08 | 降级 HTTP 200 | 4 值状态枚举 |
| R-09 | 药品目录查询端点 | 修 003 硬编码 |
