# Implementation Plan: AI辅助治疗决策

**Branch**: `006-ai-treatment-decision` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md) | **Data Model**: [data-model.md](./data-model.md) | **Contracts**: [contracts/](./contracts/)

**Input**: Feature specification from `/specs/006-ai-treatment-decision/spec.md`（US1–US4 / FR-001–FR-024 / SC-001–SC-010；Q1–Q6 已于 2026-11-08 定稿）

## Summary

为医生在**开具处方**环节提供 AI 辅助：进入开方页即自动汇总本次问诊单与医患对话，结合医学知识库生成**结构化病情摘要**、**诊断建议**与**用药草稿**；医生逐条确认或修改后，仍走 003 原有签名开方流程。AI 超时/异常/解析失败时按降级返回，开方流程不受影响。

技术路线（详见 [research.md](./research.md)）：

1. **模型调用**（R-01/R-02）：新增 `TreatmentDecisionClient` + `TreatmentDecisionClientImpl`，复用 003 的 `ChatClient.Builder` 与 `sdas.ai.treatment.*` 配置，**同步一次性**拉取严格 JSON（非流式）；Spring AI 类型不越过该接口。
2. **上下文组装**（R-06）：`DecisionContextAssembler` 只读聚合问诊单（主诉/描述/科室）、`consultation_message`（最近 40 条、3000 字符最近优先）、图片数量、`department_disease_kb` Top-5（复用 005 `KnowledgeRetriever`）。
3. **提示词与解析**（R-02）：`DecisionPromptBuilder` 约束「只输出 JSON」与安全边界；`DecisionPayloadParser` 做围栏剥离、括号截取、字段级校验，并执行**确定性措辞归一化**（`确诊`/`一定是`/`百分之百`/`必然`/`可以排除` → 去词 + 前置「疑似」，过短则丢弃该条），失败即 `EMPTY_FALLBACK`。
4. **触发与幂等**（R-03）：`dialog_version` 指纹（sha256 前 16 位）+ 30 分钟窗口复用，命中即 `reused=true` 且不调用模型。
5. **并发与频控**（R-04）：Redis `ai:treatment:lock:{cid}`（`SET NX EX 30`）与 `ai:treatment:rate:{cid}`（`INCR` + `EXPIRE 3600`）；Redis 不可用降级放行。
6. **药品映射**（R-07）：模型只出通用名，服务端映射 `drug_catalog`（`enabled=1`），映射失败丢弃并回传 `ignoredMedications`。
7. **留痕与降级**（R-05/R-08）：扩展 003 `ai_decision_session`（V11 加 6 列 + 1 索引），降级仍落一条记录并返回 HTTP 200 + 状态枚举。

## Technical Context

**Language/Version**：Java 17（LTS）；前端 TypeScript + Vue 3.4+

**Primary Dependencies**：Spring Boot 3.4.5（MVC）、Spring AI 1.0.0（`spring-ai-starter-model-openai`，DeepSeek 官方 API 走 OpenAI 兼容协议）、`spring-boot-starter-data-redis`（Lettuce）、JdbcTemplate、Vue 3 + Pinia + vue-router、Vitest + Playwright。**本功能零新增第三方依赖**。

**Storage**：MySQL 8.0（`cloud_consultation` / `cloud_consultation_test`，Flyway 强制）；Redis **3.2.100**（仅 String + `SET NX EX` / `INCR` / `EXPIRE` / `WATCH` / `MULTI`，禁止 Streams / `UNLINK`）。

**Testing**：JUnit5 + Mockito（Client、解析器、映射器、Service 编排）、Spring Boot Test + MockMvc（5 端点契约、状态/权限矩阵）、Testcontainers（mysql:8.0 / redis:3.2-alpine）或 `IDENTITY_TEST_USE_LOCAL_INFRA=true`；前端 Vitest + Playwright（Mock 用 `page.route()`，真实后端断言 `code`）。

**Target Platform**：Linux 服务器 / 浏览器 PC（医生工作台以桌面为主，≥1280×800）

**Project Type**：web-service（`server/` + `web/`）

**Performance Goals**：单次辅助生成 P95 ≤ 10s（`timeout-millis=15000` 上限内）；上下文组装 ≤ 100ms；知识检索 ≤ 50ms；开方表单在生成期间保持可交互（阻塞率 0）；幂等复用响应 ≤ 100ms。

**Constraints**：对话读取 ≤ 40 条 / 3000 字符（最近优先）；建议 ≤ 5 条、草稿 ≤ 10 条；`result_json` ≤ 64 KB；重新生成 ≤ 3 次/小时/问诊单；日志与审计禁止病情明文与患者身份信息；AI 输出不得进入患者端。

**Scale/Scope**：单院、医生日活 ≤ 500；每医生日均开方 ≤ 40 单；1 个迁移（V11 扩展表）+ 5 个 REST 端点 + 4 个前端组件 + 1 个开方页改造。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

依 `.specify/memory/constitution.md`（v1.0.0）逐项校验：

- **G1 技术栈与依赖**：通过。仅使用既有 Java 17 / Spring Boot 3.4.5 / Spring AI / MySQL 8 / Redis 3.2.100 / Vue 3；**零新增依赖**。AI 仍为单一链路（复用 003 的 `ChatClient.Builder`，R-01），未引入第二套接入。
- **G2 分层与目录**：通过。新增类落在 `server/.../doctor/{controller,service,service/impl,support,model,model/converter,dto}`（与 003 同域，避免再造顶层包）；Controller → Service → Repository 单向；医生身份统一 `DoctorIdExtractor`；前端沿用 `doctorRequest` 实例与既有 store 持久化键，不新建 axios 实例。
- **G3 契约与 API**：通过。已产出 [contracts/ai-treatment-decision-api.md](./contracts/ai-treatment-decision-api.md)（5 端点）与 [contracts/mocks.md](./contracts/mocks.md)（M1–M9），并定义 [data-model.md](./data-model.md)（3 枚举 + 6 列扩展 + 4 错误码 + 10 配置项）；统一响应 `{code,message,data}`、分页 `{list,total,page,pageSize}`、时间 `yyyy-MM-dd'T'HH:mm:ss`。
- **G4 安全**：通过。仅接诊医生可访问（`CONSULTATION_FORBIDDEN` / `CONSULTATION_NOT_FOUND`，响应不含病情）；`input_summary` 脱敏截断；审计只记 `consultationId`/`decisionId`/状态；AI 输出不落患者端；处方提交仍走 003 既有校验与签名。
- **G5 代码规范**：通过。枚举统一 `SafeEnumConverter`（新增 3 个枚举 + 3 个转换器，不并存 `@Enumerated`）；Javadoc 与 Checkstyle / SpotBugs 零告警；`AiDecisionSession` record 扩展为破坏性变更，需同批次更新 003 调用点与测试。
- **G6 测试门禁**：通过。Service 覆盖率 ≥ 70%；5 端点正向 + 异常契约测试；4 个用户故事映射测试；降级（超时/异常/空/解析失败）、越权、并发、频控、目录外药品 5 类风险项各有用例；Playwright 覆盖 US1–US4；报告落 `testsdoc/ai-treatment-decision/`。

**结论**：无阻断项，G1–G6 全部通过。

### Complexity Tracking（非违规设计取舍，登记备查）

| 项 | 取舍 | 理由 | 缓解 |
|----|------|------|------|
| C1 扩展 003 的 `AiDecisionSession` record | 新增 6 个组件，破坏既有构造签名 | Q3 定稿「复用并扩展」，避免两套留痕口径 | 同批次更新 `AiDecisionServiceImpl`、`AiDecisionSessionRepository`、`AiDecisionServiceTest`；回归 003 端点 9 |
| C2 006 依赖 005 的 `KnowledgeRetriever` | 跨功能包依赖（doctor → healthconsultation） | 复用优先，避免 2/3-gram 与排序口径分叉 | 仅依赖只读接口，无反向依赖；在 `TreatmentDecisionService` 构造注入 |
| C3 新增端点 5（药品目录查询） | 扩大了 006 的范围 | 003 开方页硬编码 `DEMO_DRUGS`，草稿无法展示 | 独立端点 + 独立任务，可单独回滚（前端回退硬编码） |

## Project Structure

### Documentation (this feature)

```text
specs/006-ai-treatment-decision/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── ai-treatment-decision-api.md
│   └── mocks.md
└── tasks.md              # /speckit.tasks 产出（尚未生成）
```

### Source Code (backend)

```text
server/src/main/java/com/sdas/server/
├── config/
│   ├── TreatmentDecisionProperties.java      # sdas.ai.treatment.*
│   └── DoctorConfig.java                     # 追加 @EnableConfigurationProperties
├── doctor/
│   ├── controller/
│   │   ├── TreatmentDecisionController.java  # 端点 1–4
│   │   └── DrugCatalogController.java        # 端点 5
│   ├── service/
│   │   ├── TreatmentDecisionService.java     # generate / latest / regenerate / markAdopted
│   │   ├── TreatmentDecisionClient.java
│   │   ├── TreatmentDecisionResult.java
│   │   └── impl/{TreatmentDecisionServiceImpl,TreatmentDecisionClientImpl}.java
│   ├── support/
│   │   ├── DecisionContextAssembler.java
│   │   ├── DecisionPromptBuilder.java
│   │   ├── DecisionPayloadParser.java
│   │   ├── MedicationMapper.java
│   │   ├── TreatmentLimitStore.java          # Redis 锁 + 频控
│   │   └── TreatmentDecisionDisclaimer.java
│   ├── model/{TreatmentDecisionStatus,TreatmentTriggerType,DegradedReason}.java
│   ├── model/converter/{...Converter}.java
│   ├── dto/{TreatmentDecisionResponse,CaseSummaryView,DiagnosisSuggestionView,
│   │        MedicationDraftView,TreatmentAdoptionRequest,TreatmentAdoptionResponse,
│   │        DrugView,DrugListResponse}.java
│   ├── entity/AiDecisionSession.java         # 扩展 6 个组件
│   └── repository/{AiDecisionSessionRepository,DrugCatalogRepository}.java  # 只读方法扩展
├── resources/db/migration/V11__ai_treatment_decision.sql
└── resources/application.yml                 # 追加 sdas.ai.treatment.*
```

### Source Code (frontend)

```text
web/src/
├── api/
│   ├── treatment-decision.ts                 # 5 个端点封装（doctorRequest + csrfHeaders）
│   └── mocks/treatment-decision.ts           # M1–M9
├── stores/treatment-decision.ts              # 决策状态 / 草稿合并 / 降级提示
├── components/
│   ├── treatment-decision-panel.vue          # 面板容器（成功/降级/加载/重试）
│   ├── treatment-summary-card.vue            # 摘要四段
│   ├── treatment-suggestion-list.vue         # 建议列表 + 逐条应用
│   └── treatment-medication-drafts.vue       # 草稿列表 + 应用到处方表单
└── views/workbench-prescription-view.vue     # 改造：接入面板 + 目录下拉 + 采纳回填
```

### Tests

```text
server/src/test/java/com/sdas/server/doctor/
├── unit/{TreatmentDecisionClientTest,DecisionContextAssemblerTest,DecisionPayloadParserTest,
│         MedicationMapperTest,TreatmentLimitStoreTest,TreatmentDecisionServiceTest}.java
├── contract/TreatmentDecisionContractTest.java
├── integration/TreatmentDecisionFlowIT.java
└── support/DoctorTestSupport.java            # 补 mock bean 与夹具（问诊单 + 对话 + 药品）

web/tests/unit/treatment-decision-store.spec.ts
web/tests/e2e/{treatment-decision.mock.spec.ts,treatment-decision.real.spec.ts}
testsdoc/ai-treatment-decision/{test-report.md,contract-conformance.md}
```

**结构决策**：不新建顶层包 `treatmentdecision`。本功能与 003 共享问诊单、处方、医生身份与 AI 配置，落在 `doctor/` 下可避免跨包循环依赖；命名以 `TreatmentDecision*` 前缀区分（G2）。

## Phase 0 / Phase 1 产出

| 阶段 | 产出 | 状态 |
|------|------|------|
| Phase 0 研究 | [research.md](./research.md) R-01–R-09 | 完成 |
| Phase 1 数据模型 | [data-model.md](./data-model.md)：3 枚举 + 6 列 + 4 错误码 + 10 配置 | 完成 |
| Phase 1 契约 | [contracts/ai-treatment-decision-api.md](./contracts/ai-treatment-decision-api.md)（5 端点）、[contracts/mocks.md](./contracts/mocks.md)（M1–M9） | 完成 |
| Phase 1 快速验证 | [quickstart.md](./quickstart.md) | 完成 |
| Phase 2 任务分解 | [tasks.md](./tasks.md)（74 项，T001–T074） | 完成 |
| 交叉一致性分析 | [analysis.md](./analysis.md)（A–H 共 8 项，已修复） | 完成 |

## 关键机制设计（实现级）

### M1 模型调用与结构化输出

```java
public interface TreatmentDecisionClient {
    TreatmentDecisionResult decide(TreatmentDecisionCommand command);
}

public record TreatmentDecisionCommand(String systemPrompt, String userPrompt,
                                       int maxOutputTokens, String model) {}

public record TreatmentDecisionResult(boolean success, String rawJson,
                                      String modelUsed, DegradedReason reason) {}
```

- `TreatmentDecisionClientImpl` 构造注入 `ChatClient.Builder` + `TreatmentDecisionProperties`；`defaultOptions(model, maxOutputTokens)`。
- 调用方式与 003 一致：`CompletableFuture.supplyAsync(() -> chatClient.prompt().system(...).user(...).call().content())` + `future.get(timeoutMillis, MILLISECONDS)`。
- 异常映射：`TimeoutException → TIMEOUT`；`ExecutionException/InterruptedException → UPSTREAM_ERROR`；`content` 空白 → `EMPTY`。
- **不生成兜底文案**，不返回原始异常堆栈。

### M2 上下文组装

```java
public record DecisionContext(String chiefComplaint, String description, List<ChatTurn> dialog,
                              int imageCount, boolean evidenceLimited,
                              List<KnowledgeHit> knowledge) {}

DecisionContext assemble(long consultationId, long departmentId);
```

1. 问诊单：`consultation.complaint`、`consultation.description`（各截断 500）。
2. 对话：`findByConsultationId` 升序 → 取最近 `dialog-max-messages`（40）→ 从最新往前累加字符，超 `dialog-max-chars`（3000）即停 → 反转为正序；角色映射 `PATIENT→user`、`DOCTOR→assistant`。
3. 图片：`consultationImageRepository.findByConsultationId` 只取数量；有图无文字说明时 `evidenceLimited=true`，摘要风险段追加「含 N 张图片，未提供文字描述」。
4. 知识：`knowledgeRetriever.retrieve(主诉 + 描述 + 最近 3 条患者消息, topK=5)`，结果按同 `departmentId` 优先重排；全部跨科室时仍保留但标注「跨科室参考」。

### M3 触发与幂等

- `dialog_version = sha256(lastMessageId + ":" + messageCount + ":" + hasPrescription)` 取前 16 位十六进制。
- 复用查询：`findReusable(consultationId, doctorId, dialogVersion, createdAt >= now - reuseWindowMinutes)`，命中即返回 `reused=true`、`elapsedMs=0`，**不写新记录、不加频控计数**。
- 自动触发只发 1 次（前端 store 保证 `if (state.loaded) return`）；刷新走端点 2（GET），不重复生成。

### M4 并发锁与频控

- 锁：`ai:treatment:lock:{consultationId}`，`setIfAbsent(token, 30s)`；未获取 → `409 TREATMENT_DECISION_BUSY`；释放用 `WATCH/MULTI` 校验令牌后 `DEL`。
- 频控：仅 `REGENERATE` 计数，`INCR ai:treatment:rate:{consultationId}` 且首次 `EXPIRE 3600`；`> regenerate-limit-per-hour` → `429 TREATMENT_DECISION_RATE_LIMITED`（**不消耗锁、不写留痕**）。
- Redis 异常：锁与计数均**降级放行**，`WARN` 只记 `consultationId` 与异常类名。

### M5 药品草稿映射与裁剪

- 模型输出 `medications[] = { drugName, dosage, caution }`，**不含 drugId**。
- 映射：`drugCatalogRepository.findEnabledByNames(names)` → 归一化（去空格、去括号规格）→ 先精确等值，再取「目录名是模型名的前缀」中的最长匹配；仍失败 → 丢弃并加入 `ignoredMedications`。
- 字段补齐：`spec` 取目录值；`dosage` 空或 <2 字符 → `needsCompletion=true`；`caution` 空则留 `null`。
- 裁剪：`medications` 超过 `max-medications`（10）截断；`suggestions` 超过 `max-suggestions`（5）截断；`basis` 为空视为结构非法 → 该条丢弃。
- 危险剂量检测（本期最小实现）：`dosage` 中抽取数字，若单次剂量 > 目录 `spec` 数值 × 10 或日频次 > 6，置 `needsCompletion=true` 并在 `caution` 前追加「剂量需医生核对」。不确定的解析不阻断，只标注。

### M6 留痕、降级与审计

- 落库：`insert(consultationId, doctorId, inputSummary, output, modelUsed, status, elapsedMs, createdAt, triggerType, dialogVersion, resultJson, degradedReason, appliedItemCount, adoptedAt)`。
- `status` 映射：成功 `SUCCESS`；否则按 `DegradedReason` 映射 `TIMEOUT_FALLBACK` / `ERROR_FALLBACK` / `EMPTY_FALLBACK`。
- `input_summary`：`主诉/描述(≤500) + 对话要点(≤1500)`，**剥离姓名/手机号/身份证模式**（正则替换为 `*`）。
- 审计：`AI_TREATMENT_DECISION_REQUESTED`（成功/降级都记，`result` 区分 `SUCCESS`/`FAILURE`）、`AI_TREATMENT_DECISION_DEGRADED`（仅降级，`reasonCode=DegradedReason`）、`AI_TREATMENT_DECISION_ADOPTED`（端点 4）。
- 免责文案：`TreatmentDecisionDisclaimer.TEXT = "AI 生成，仅供参考，最终诊疗决策由医生负责"`，响应固定下发，前端只渲染。

### 一次开方完整时序（15 步）

1. 医生在接诊对话页点击「开具处方」→ 路由 `/workbench/prescriptions/new?consultationId=501`。
2. 开方页 `onMounted` 调用 `GET .../treatment-decision`；404 则调用 `POST .../treatment-decision`（`AUTO`）。
3. `TreatmentDecisionController` 校验角色与 CSRF，`DoctorIdExtractor` 取 `doctorId`。
4. `TreatmentDecisionServiceImpl.generate`：查问诊单 → 归属校验 → 状态必须 `IN_PROGRESS` → 查是否已开方（是则 409）。
5. 计算 `dialog_version`，查 30 分钟内可复用记录，命中 → 直接返回（`reused=true`）。
6. `TreatmentLimitStore.lock` 抢锁（失败 409；Redis 不可用放行）。
7. `DecisionContextAssembler.assemble`（问诊单 + 对话 + 图片数 + 知识 Top-5）。
8. `DecisionPromptBuilder.build`（系统提示 + 对话 + 知识 + JSON Schema 约束）。
9. `TreatmentDecisionClient.decide`（≤15s）。
10. `DecisionPayloadParser.parse`（围栏剥离 + 括号截取 + 字段校验）；失败 → `EMPTY_FALLBACK`。
11. `MedicationMapper.map`（药名→目录，丢弃目录外，补齐 `spec`，标注 `needsCompletion`）。
12. 落一条 `ai_decision_session`（含 `result_json`、`status`、`degraded_reason`、`elapsed_ms`）。
13. 释放锁，返回 200；前端渲染：成功 → 摘要/建议/草稿，草稿以「待确认」填入表单；降级 → 提示 + 空表单。
14. 医生逐条应用/修改；点击「应用」时静默调用端点 4 回填 `applied_item_count`（失败不提示）。
15. 医生提交 → 调 003 端点 7 开方（契约不变），服务端二次校验药品/剂量/条数后签名。

**异常分支**：锁未获取 → 409；状态不符 → 400；已开方 → 409；模型降级 → 200 + 状态枚举；处方校验失败 → 003 既有码。

## 性能与安全要点

- **可交互优先**：辅助请求与表单渲染解耦，生成中表单可填、可提交（若医生跳过辅助直接开方）。
- **复用省成本**：幂等命中不调用模型（SC-001 的 P95 只统计真实调用）。
- **最小化病情外泄**：`result_json` 只含结构化摘要；日志不打 `rawJson`；审计只记 ID 与状态。
- **不可绕过**：草稿只是前端状态，处方必须经 003 `create` 校验 + 签名；无自动签名路径。

## 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| `AiDecisionSession` record 扩展破坏 003 | 003 端点 9 编译/测试失败 | 同批次改 3 个调用点 + 单测；回归端点 9 |
| 提示词注入（患者对话含「忽略指令」） | 模型输出越界 | 服务端只信结构校验 + 药品目录白名单；越界字段直接丢弃 |
| 药名同名多规格 | 映射到非预期规格 | 取 `id` 最小 + `needsCompletion=true` 提示医生核对 |
| 模型输出超长/嵌套 | 解析失败或 `result_json` 膨胀 | 截断建议/草稿条数；`result_json` 上限 64 KB |
| 知识库跨科室误命中 | 建议偏差 | 同科室优先排序，跨科室标注 |
| Redis 不可用 | 频控与互斥失效 | 降级放行 + WARN，保证开方不中断 |
| 端点 5 扩大范围 | 交付延迟 | 独立任务、可单独回滚 |

## 下一步

1. `/speckit.tasks`：按 US1–US4 与 5 端点拆分任务（测试先行，含 C1 兼容改造、端点 5 独立任务）。
2. `/speckit.analyze`：交叉检查 spec ↔ plan ↔ data-model ↔ contracts 一致性（重点：4 个错误码、3 个枚举、`disclaimer` 文案）。
3. `/speckit.implement`：按 tasks 执行，先跑通降级链路（US4）再补成功链路，确保任何时刻都可开方。
