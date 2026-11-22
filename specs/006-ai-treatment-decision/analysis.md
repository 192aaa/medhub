# 交叉一致性分析：AI辅助治疗决策

**Branch**: `006-ai-treatment-decision` | **Date**: 2026-11-08
**分析对象**：[spec.md](./spec.md) · [plan.md](./plan.md) · [research.md](./research.md) · [data-model.md](./data-model.md) · [contracts/](./contracts/) · [tasks.md](./tasks.md)
**结论**：**有条件通过**——存在 1 个阻断级不一致（未登记错误码）、1 个 MUST 需求缺少实现层兜底，修复后即可进入 `/speckit.implement`。

**修复状态**：A–H 共 8 项已于 2026-11-08 全部修复（明细见第 13 节），当前可进入 `/speckit.implement`。

---

## 1. 汇总

| 严重度 | 数量 | 编号 |
|--------|------|------|
| 🔴 阻断（会导致契约与实现不一致 / MUST 无法满足） | 2 | A、D |
| 🟠 中等（可验证性弱 / 结构不规范） | 3 | C、E、G |
| 🟡 轻微（表述二义 / 文档滞后） | 3 | B、F、H |

覆盖情况：

| 维度 | 结果 |
|------|------|
| FR-001–FR-024 → 任务 | 24/24 已映射（矩阵见 tasks.md） |
| SC-001–SC-010 → 验证任务 | 9/10 已映射；**SC-009 缺基线测量任务**（C） |
| contracts 5 端点 → plan/tasks | 5/5 一致（路径、方法、编号） |
| data-model 3 枚举 → contracts/tasks/前端 | 完全一致（枚举名与取值逐字相同） |
| data-model 4 错误码 → ErrorCode/tasks | 4/4 一致；**contracts 另有 1 个未登记码**（A） |
| data-model 10 配置项 → application.yml/tasks | 10/10 一致 |
| 免责文案 | 3 处逐字一致（spec/data-model/contracts） |

---

## 2. 🔴 A（已修复）：`INVALID_QUERY_PARAM` 未在 `ErrorCode` 与 data-model 登记

**位置**：`contracts/ai-treatment-decision-api.md` 端点 5 异常表（第 206 行）、`tasks.md` T024。

**问题**：契约要求端点 5 在「分页越界或 `keyword` 超长」时返回 `400 INVALID_QUERY_PARAM`，但：

1. `ErrorCode` 中**不存在**该常量（仅有 `BAD_REQUEST(400,"请求参数错误")`）；
2. `data-model.md` §5 只登记 4 个 006 错误码，未含此码；
3. `tasks.md` T021 也只要求新增 4 个码。

**影响**：实现时要么临时发明第 5 个码（违反宪法「错误 message 全部落 `ErrorCode`」），要么返回与契约不符的 `BAD_REQUEST`，导致 SC-010 契约一致性验证失败。

**建议修复（二选一）**：

- **方案 1（推荐）**：契约改用既有 `BAD_REQUEST`。端点 5 的参数错误与其他端点的参数错误无本质区别，不需要专属码。
  ```diff
  - **异常**：401 `UNAUTHORIZED`；403 `DOCTOR_ROLE_REQUIRED`；400 `INVALID_QUERY_PARAM`（分页越界或 `keyword` 超长）。
  + **异常**：401 `UNAUTHORIZED`；403 `DOCTOR_ROLE_REQUIRED`；400 `BAD_REQUEST`（分页越界或 `keyword` 超长）。
  ```
  同步：`tasks.md` T024 的「越界返回 `INVALID_QUERY_PARAM`」改为 `BAD_REQUEST`。
- **方案 2**：在 `data-model.md` §5 与 T021 增补 `INVALID_QUERY_PARAM(400, "查询参数不合法")`，并更新 tasks.md T021 的「4 个常量」为 5 个。

---

## 3. 🔴 D：FR-007「禁止确定性诊断措辞」缺少实现层兜底

**位置**：`spec.md` FR-007、SC-003；`tasks.md` T050（仅提示词约束）、T038（仅 JSON 解析校验）。

**问题**：FR-007 是 **MUST**，SC-003 要求「输出中出现确定性诊断措辞的样本数为 0」。当前设计只在 `DecisionPromptBuilder`（T050）里要求模型「禁止确定性措辞」，**没有解析层校验**。模型不遵守提示词时，`DecisionPayloadParser` 会原样放行「确诊为肺炎」「一定是流感」。

**影响**：SC-003 无法达成；该表述一旦展示给医生，属于合规风险（把 AI 参考包装成结论）。

**建议修复**：

1. 在 `DecisionPayloadParser` 增加兜底规则（T017 的任务描述内补一条）：
   - 命中词表：`确诊`、`一定是`、`百分之百`、`必然`、`可以排除`；
   - 命中后处理：`diagnosis` 前置「疑似」并去除命中词；若去除后长度 < 2，则该条丢弃。
2. 新增单测（T038 内补用例）：模型输出「确诊为肺炎」→ 解析结果 `diagnosis` 不含「确诊」且以「疑似」开头。
3. `plan.md` M2（解析器职责）补一句「确定性措辞归一化」。

---

## 4. 🟠 C：SC-009「耗时下降 ≥25%」无测量任务

**位置**：`spec.md` SC-009；`tasks.md` T069 只列 P95/组装/检索耗时，无前后对比基线。

**问题**：SC-009 需要「接入前基线 + 接入后测量」两次采样，当前任务只测绝对性能。

**建议**：在 T069 补两步，或新增 T069a：

- 基线：在 003 未接入辅助的开方页记录 50 次「进入开方页 → 签名成功」的平均耗时/步数；
- 对比：接入后同条件复测，记录下降比例，写入 `testsdoc/ai-treatment-decision/test-report.md`。
- 若无法做真实基线（时间成本），退化为「平均填写字段数」对比，并在报告中说明口径。

---

## 5. 🟠 E：spec 用户故事顺序与优先级不一致

**位置**：`spec.md` §3：US1(P1)、US2(P1)、US3(**P2**)、US4(**P1**)。

**问题**：speckit 规范要求用户故事按优先级排序（P1 在 P2 前）；当前 US3(P2) 夹在两个 P1 之间，`tasks.md` 又把 US4 提前到 Phase 3，形成「spec 顺序 ≠ 实施顺序」的双重口径。

**建议**（择一）：

- **方案 1（最小改动，推荐）**：在 spec §3 开头补一句：「编号沿用讨论顺序；实施顺序以优先级与依赖为准：US4 → US1 → US2 → US3（详见 tasks.md Phase 3–6）」。
- **方案 2**：把 US3 与 US4 对调编号，并同步 tasks/plan 中所有 `US3`/`US4` 引用（改动面大，易漏）。

---

## 6. 🟠 G：SC-007「可回溯到当次辅助请求」关联弱

**位置**：`spec.md` SC-007；`data-model.md` §2.1（无 `prescription_id`）；`tasks.md` T052（采纳仅在签名前回填 `appliedItemCount`）。

**问题**：处方与决策记录之间**没有外键**。签名后只能按 `consultation_id` 取「最近一条」，若医生在同一次开方中生成过 2–3 次（AUTO + REGENERATE），无法确定最终采纳的是哪一次。

**影响**：SC-007 的「可回溯」在审计场景下会变成推断而非事实。

**建议**（按成本排序）：

1. **低成本**：约定「采纳回填为最后一次生成」，并在 `us*-verification.md` 中固定该口径；审计报告写「以 `adopted_at` 最近的记录关联处方签名时间 ±10 分钟」。
2. **中成本（推荐）**：`V11` 再增一列 `prescription_id BIGINT NULL`，在端点 4 之外由开方成功后回填——但这需要改 003 `PrescriptionServiceImpl`，扩大 C1 的改动面。
3. **折中**：端点 4 请求体增加可选 `prescriptionId`（签名成功后前端再调一次），仅回填关联，不改 003。

> 本期若选方案 1，需在 `contracts` 的端点 4 语义中补一句「以最后一次回填为准」，避免歧义。

---

## 7. 🟡 B：`generatedAt` 出现两次且语义未说明

**位置**：`data-model.md` §7.2（顶层 `generatedAt` 与 `summary.generatedAt`）、`contracts` 示例 1 两者同值。

**问题**：两个同名字段，未说明区别；示例中恰好相等，实现时容易只写一个或写成不同值，导致前端展示不一致。

**建议**：明确语义并写入 `data-model.md` §7.2：

- 顶层 `generatedAt`：**记录创建时间**（即 `ai_decision_session.created_at`）；
- `summary.generatedAt`：**摘要文本生成时间**（同一次调用内通常相同，复用场景下为首次生成时间）。

---

## 8. 🟡 F：`AiDecisionSession` 的 record 组件顺序未给出

**位置**：`data-model.md` §2.1（仅给 SQL 列位置）、`tasks.md` T009（"补 6 个默认值"，未给顺序）。

**问题**：record 构造是位置参数，组件顺序决定 003 调用点怎么改。T009 只说"位置见 §2.1"，但 §2.1 是 DDL 的 `AFTER` 顺序，不等于 Java 组件顺序。

**建议**：在 `data-model.md` §2.1 末尾补 record 签名（与 003 兼容的追加式顺序）：

```java
record AiDecisionSession(Long id, Long consultationId, Long doctorId, String inputSummary,
        String output, String modelUsed, TriageResultStatus status, Integer elapsedMs,
        LocalDateTime createdAt, TreatmentTriggerType triggerType, String dialogVersion,
        String resultJson, DegradedReason degradedReason, Integer appliedItemCount,
        LocalDateTime adoptedAt)
```

> 追加在末尾可让 003 的既有构造调用只需补 `null`，避免改动参数位置引发错位。

---

## 9. 🟡 H：plan 的「Phase 0/1 产出」表格状态滞后

**位置**：`plan.md` 末尾「Phase 0 / Phase 1 产出」表中，`tasks.md` 一行仍写「待 `/speckit.tasks`」。

**问题**：`tasks.md` 已生成（74 项），表格状态未更新，会误导后续执行者。

**建议**：改为「完成（74 项）」。

---

## 10. 已核对一致的项（无需处理）

| 检查项 | 结果 |
|--------|------|
| 5 端点路径与方法 | contracts ↔ plan ↔ tasks 完全一致 |
| 3 枚举名与取值 | spec 未命名、data-model/contracts/tasks/前端 types 一致 |
| 4 个错误码 `code` 与 `message` | data-model ↔ contracts ↔ tasks 一致（A 之外） |
| 10 配置项键名与默认值 | data-model ↔ tasks T005 ↔ plan Summary 一致 |
| 免责文案 | 「AI 生成，仅供参考，最终诊疗决策由医生负责」三处逐字一致 |
| `dialog_version` 定义 | research R-03 ↔ plan M3 ↔ data-model §2.1 一致 |
| Redis 键名 | research R-04 ↔ data-model §4 ↔ tasks T019 一致（`ai:treatment:lock|rate:{cid}`） |
| 降级语义 | spec FR-012 ↔ research R-08 ↔ contracts 示例 2 ↔ tasks T032 一致（HTTP 200 + 枚举） |
| 药品映射 | research R-07 ↔ plan M5 ↔ data-model §3 ↔ tasks T058 一致 |
| V11 迁移版本 | 与既有 V1–V10 不冲突（V10 为 005 最新） |
| C1 兼容风险 | research R-05 ↔ plan Complexity Tracking ↔ tasks T009 三方登记 |

---

## 11. 建议修复顺序

1. **A（阻断）**：改 contracts 端点 5 为 `BAD_REQUEST` + tasks T024 同步（1 行改动，5 分钟）。
2. **D（阻断）**：在 tasks T017/T038 补「确定性措辞归一化」规则与用例（15 分钟）。
3. **F（轻微但影响 T009 执行）**：data-model §2.1 补 record 签名（5 分钟）。
4. **B（轻微）**：data-model §7.2 补两个 `generatedAt` 的语义（3 分钟）。
5. **C（中等）**：tasks T069 补基线测量步骤（5 分钟）。
6. **E（中等）**：spec §3 补「编号 vs 实施顺序」说明（3 分钟）。
7. **G（中等）**：选定回溯口径并写入 contracts 端点 4 语义（10 分钟）。
8. **H（轻微）**：plan 末尾状态表更新（1 分钟）。

---

## 12. 结论

- **阻断项**：A、D。两者均为局部修补，不涉及架构调整。
- **修复后**可进入 `/speckit.implement`；建议从 **T009（C1 兼容改造）** 开始，因为它同时是编译前置与 003 回归的观测点。
- **不建议**在 A、D 未修前开始实现：A 会导致契约测试与实现互相打架，D 会导致 SC-003 在验收时被判不通过。

---

## 13. 修复记录（2026-11-08）

| 编号 | 修复内容 | 改动文件 |
|------|----------|----------|
| A | 端点 5 参数错误改用既有 `BAD_REQUEST`，不新增专属码 | `contracts/ai-treatment-decision-api.md`、`tasks.md` T024 |
| B | 明确顶层 `generatedAt`（记录时间）与 `summary.generatedAt`（摘要生成时间）的区别 | `data-model.md` §7.2 |
| C | T069 补 SC-009 基线对比测量（含退化口径） | `tasks.md` T069 |
| D | 解析器新增确定性措辞归一化，并在 T017/T038 落地规则与用例 | `plan.md` Summary、`research.md` R-02、`data-model.md` §3、`tasks.md` T017/T038 |
| E | spec §3 补「编号与实施顺序」说明（US4 先于 US1/US2/US3） | `spec.md` §3 |
| F | 补 `AiDecisionSession` record 组件顺序（新组件追加在末尾） | `data-model.md` §2.1 |
| G | 端点 4 补 SC-007 回溯口径（以最后一次回填为准 + 时间窗口） | `contracts/ai-treatment-decision-api.md` 端点 4 |
| H | plan 产出表更新为「tasks 74 项完成、分析完成」 | `plan.md` 末尾 |

**修复后复核**：

- 错误码：`data-model` 4 个新增码 ↔ `ErrorCode` 计划 ↔ `contracts` 全部对齐，无孤立码；
- FR-007：提示词约束（T050）+ 解析层归一化（T017）双层，SC-003 可验证；
- SC 映射：SC-001–SC-010 全部有对应任务；
- record 顺序：T009 可直接按 §2.1 签名实现，003 调用点仅需补 `null`。

**残余风险（不阻断）**：

1. SC-009 若无法采集真实基线，退化为「平均填写字段数」对比，结论强度下降；
2. SC-007 采用「最后一次回填 + 时间窗口」推断口径，跨多次生成时仍属近似，已在契约中固化披露；
3. 确定性措辞词表为固定枚举，模型换用同义词（如「铁定」）仍可能漏网，后续可扩展为可配置词表。
