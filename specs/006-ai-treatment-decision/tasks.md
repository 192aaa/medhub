# 任务清单：AI辅助治疗决策

**功能**：`006-ai-treatment-decision` | **日期**：2026-11-08
**输入**：`specs/006-ai-treatment-decision/` 下的 `spec.md`、`plan.md`、`research.md`、`data-model.md`、`contracts/ai-treatment-decision-api.md`、`contracts/mocks.md`、`quickstart.md`
**前置条件**：`spec.md`（US1–US4 + FR-001–FR-024 + Q1–Q6 已定稿）、`plan.md`（G1–G6 门禁通过 + M1–M6 实现级设计）、`data-model.md`（3 枚举 + V11 六列扩展 + 4 错误码 + 10 配置项）、`contracts/`（5 端点 + M1–M9）；上游 `001`（医生身份提取、审计、`ErrorCode`、Redis 客户端）、`002`（问诊单、图片、知识库）、`003`（医患对话、处方签名、`AiDecisionClient`、`DoctorTestSupport`）、`005`（`KnowledgeRetriever` 只读检索）均已实现。

**测试**：依 `.specify/memory/constitution.md`（原则 VI）为强制项——Service 覆盖率 ≥ 70%、5 端点正向/异常契约全覆盖、4 个用户故事对应测试、降级链路必须可验证、Playwright 覆盖 US1–US4（mock + real 成对）。测试用例先写且先失败。测试报告落 `testsdoc/ai-treatment-decision/`。

**组织方式**：先建「降级骨架」（US4），再按 US1 → US2 → US3 填充成功链路。理由：任何阶段中断都必须保证医生能开方（`plan.md` 下一步第 3 条）。

**唯一来源约束**：枚举与 `ErrorCode` 以 `data-model.md` 第 1、5 节为准；请求/响应字段以 `contracts/ai-treatment-decision-api.md` 为准；Mock 以 `contracts/mocks.md`（M1–M9）为准；类名、方法名、配置键不得自行发明。

## 格式说明

- `[ID]`：任务编号，`T001` 起连续编号。
- `[P]`：可并行（不同文件、无未完成依赖）。
- `[US1]`–`[US4]`：归属用户故事；跨故事地基标 `[FOUND]`。
- 每条任务写明精确文件路径与验收要点；禁止出现「实现某功能」这类无路径的模糊任务。

## 路径约定

- 后端：`server/src/main/java/com/sdas/server/doctor/{controller,service,service/impl,support,model,model/converter,dto,entity,repository}/`（**不新建顶层包**，plan「结构决策」）
- 配置：`server/src/main/java/com/sdas/server/config/TreatmentDecisionProperties.java`（在 `DoctorConfig` 注册）
- 迁移：`server/src/main/resources/db/migration/V11__ai_treatment_decision.sql`（当前最大为 V10）
- 后端测试：`server/src/test/java/com/sdas/server/doctor/{unit,contract,integration,support}/`
- 前端：`web/src/{api,api/mocks,stores,components,views}/`
- 前端测试：`web/tests/unit/`、`web/tests/e2e/`
- 报告：`testsdoc/ai-treatment-decision/`

---

## Phase 1: Setup（共享基础设施）

**目的**：目录、配置、迁移脚本、前端类型/错误码/Mock、测试基座就绪；此阶段不写业务逻辑。

- [ ] T001 创建 `testsdoc/ai-treatment-decision/` 目录；确认 `server/.../doctor/support/` 与 `web/src/components/` 已存在（不存在则创建）；`mvn -o -q compile` 与 `npm run typecheck` 作为基线通过
- [ ] T002 [P] 扩展 `web/src/api/types.ts`：按 `data-model.md` 第 1 节定义 `TREATMENT_DECISION_STATUS`（`SUCCESS`/`TIMEOUT_FALLBACK`/`ERROR_FALLBACK`/`EMPTY_FALLBACK`）、`TREATMENT_TRIGGER_TYPE`（`AUTO`/`REGENERATE`）、`DEGRADED_REASON`（`NONE`/`TIMEOUT`/`UPSTREAM_ERROR`/`PARSE_FAILED`/`EMPTY`/`KNOWLEDGE_UNAVAILABLE`）常量与 5 端点请求/响应接口（`TreatmentDecisionResponse`、`CaseSummaryView`、`DiagnosisSuggestionView`、`MedicationDraftView`、`TreatmentAdoptionRequest`、`TreatmentAdoptionResponse`、`DrugView`）；文件头标注来源 `specs/006-.../data-model.md` 与 `contracts/`
- [ ] T003 [P] 扩展 `web/src/api/error-codes.ts`：新增 `TREATMENT_DECISION_NOT_FOUND`(404)、`TREATMENT_DECISION_BUSY`(409)、`TREATMENT_DECISION_RATE_LIMITED`(429)、`INVALID_TREATMENT_ADOPTION`(400)，`message` 不复制（一律取后端响应值），末尾追加分组注释引用 006 `data-model.md` 第 5 节
- [ ] T004 [P] 新建 `web/src/api/mocks/treatment-decision.ts`：从 `contracts/mocks.md` 同步 M1–M9（按 `METHOD /v1/path` + `#M{n}` 组织），响应字段与 `message` 逐字复制；导出 `MOCK_DECISION_SUCCESS`、`MOCK_DECISION_REUSED`、`MOCK_DECISION_FALLBACK`、`MOCK_DECISION_NOT_FOUND`、`MOCK_RATE_LIMITED`、`MOCK_BUSY`、`MOCK_ADOPTION`、`MOCK_DRUGS`、`MOCK_INVALID_ADOPTION`
- [ ] T005 [P] 在 `server/src/main/resources/application.yml` 追加 `data-model.md` 第 6 节 `sdas.ai.treatment.*`（`model`/`timeout-millis`/`max-output-tokens`/`dialog-max-messages`/`dialog-max-chars`/`knowledge-top-k`/`max-suggestions`/`max-medications`/`regenerate-limit-per-hour`/`reuse-window-minutes`），全部支持 `SDAS_AI_TREATMENT_*` 覆盖；`application-prod.yml` 不重复定义
- [ ] T006 [P] 新建 `server/.../doctor/support/TreatmentDecisionDisclaimer.java`：`public static final String TEXT = "AI 生成，仅供参考，最终诊疗决策由医生负责";`（全仓唯一来源）；配套单测断言逐字与 `contracts/ai-treatment-decision-api.md` 一致
- [ ] T007 [P] 扩展 `server/src/test/java/com/sdas/server/doctor/support/DoctorTestSupport.java`（当前为空壳）：加 `@MockBean TreatmentDecisionClient treatmentDecisionClient`，并提供夹具方法 `seedConsultationInProgress(long consultationId, long patientId, long doctorId)`、`seedDialog(long consultationId, String... contents)`、`seedDrugs()`（复用 `DoctorContractFixtures`）；主键取高位段（501/1001/901）并幂等写入
- [ ] T008 [P] 新建 `server/src/main/resources/db/migration/V11__ai_treatment_decision.sql`：按 `data-model.md` §2.1 对 `ai_decision_session` 增加 `trigger_type`/`dialog_version`/`result_json`/`degraded_reason`/`applied_item_count`/`adopted_at` 六列与 `idx_ai_dialog_version (consultation_id, dialog_version)`；**只加列与索引，不改既有列类型**；文件头注释「由 006 引入，兼容 003 端点 9」

**检查点**：`mvn -o -q compile`、`npm run typecheck` 通过；`V11` 可在空库与既有库上各执行一次；目录与配置齐备，尚无业务行为。

---

## Phase 2: Foundational（阻塞性前置）

**目的**：C1 兼容改造、枚举、仓储扩展、模型封装、上下文/提示词/解析/映射/限流、错误码与审计等所有故事共用的地基。

**⚠️ 关键**：T009 未完成前，任何用户故事都不允许开始实现（record 扩展会连锁破坏 003 编译）。

- [ ] T009 [FOUND] **C1 兼容改造（阻塞）**：扩展 `server/.../doctor/entity/AiDecisionSession.java` 增加 6 个组件（`triggerType`、`dialogVersion`、`resultJson`、`degradedReason`、`appliedItemCount`、`adoptedAt`，位置见 `data-model.md` §2.1）；同步 `server/.../doctor/repository/AiDecisionSessionRepository.java` 的 `insert` SQL 与参数；同步 `server/.../doctor/service/impl/AiDecisionServiceImpl.java` 的构造调用（补 `AUTO`/`null`/`null`/`NONE`/`null`/`null`）；同步 `server/src/test/java/com/sdas/server/doctor/unit/AiDecisionServiceTest.java` 的夹具；`mvn -o -q test-compile` 通过且 003 端点 9 单测回归绿
- [ ] T010 [P] [FOUND] 新建 `server/.../doctor/model/` 下 `TreatmentDecisionStatus`、`TreatmentTriggerType`、`DegradedReason` 三个枚举（取值与 `data-model.md` §1 逐字一致），并为每个枚举在 `server/.../doctor/model/converter/` 新建 `SafeEnumConverter` 实现（`autoApply=false`，默认值：`EMPTY_FALLBACK`/`AUTO`/`NONE`）；禁止 `@Enumerated(EnumType.STRING)`
- [ ] T011 [P] [FOUND] 扩展 `server/.../doctor/repository/AiDecisionSessionRepository.java`：`Optional<AiDecisionSession> findLatestByConsultationIdAndDoctorId(long consultationId, long doctorId)`（`ORDER BY id DESC LIMIT 1`）、`Optional<AiDecisionSession> findReusable(long consultationId, long doctorId, String dialogVersion, LocalDateTime since)`（`status='SUCCESS'`）、`int markAdopted(long id, int appliedItemCount, LocalDateTime adoptedAt)`；不改既有 `insert` 语义
- [ ] T012 [P] [FOUND] 扩展 `server/.../doctor/repository/DrugCatalogRepository.java`（只读）：`List<DrugCatalog> findEnabledByNames(List<String> names)`（一次 `IN` 查询，`enabled=1`）、`List<DrugCatalog> findEnabledPage(String keyword, int offset, int limit)`（`name LIKE`，`ORDER BY id`）、`long countEnabled(String keyword)`；不改既有方法
- [ ] T013 [P] [FOUND] 新建 `server/.../doctor/service/TreatmentDecisionClient.java`、`TreatmentDecisionCommand.java`（`systemPrompt`/`userPrompt`/`maxOutputTokens`/`model`）、`TreatmentDecisionResult.java`（`success`/`rawJson`/`modelUsed`/`reason`）；**Spring AI 类型不得出现在签名中**，Javadoc 标注 `plan.md` M1
- [ ] T014 [FOUND] 新建 `server/.../doctor/service/impl/TreatmentDecisionClientImpl.java`（依赖 T013、T005）：构造注入 `ChatClient.Builder` + `TreatmentDecisionProperties`；`CompletableFuture.supplyAsync(...).get(timeoutMillis)`；`TimeoutException→TIMEOUT`、`ExecutionException/InterruptedException→UPSTREAM_ERROR`、空白内容→`EMPTY`；**不生成兜底文案、不返回堆栈**
- [ ] T015 [P] [FOUND] 新建 `server/.../doctor/support/DecisionContextAssembler.java`：`DecisionContext assemble(long consultationId, long departmentId)`；对话取最近 40 条并按 3000 字符「最近优先」截断后反转为正序；图片只取数量（有图无文字说明 → `evidenceLimited=true`）；知识经 005 `KnowledgeRetriever` 取 Top-5 并按同科室优先重排；问诊单主诉/描述各截断 500
- [ ] T016 [P] [FOUND] 新建 `server/.../doctor/support/DecisionPromptBuilder.java`：`String build(DecisionContext context)`；system prompt 固定包含「只输出一个 JSON 对象」「不得输出确定性诊断措辞」「用药只写通用名+用法用量+注意事项，不得输出 drugId」「医院信息」，并给出 `data-model.md` §7.3 的字段骨架
- [ ] T017 [P] [FOUND] 新建 `server/.../doctor/support/DecisionPayloadParser.java`：`ParseResult parse(String rawJson)`；去 ```` ```json ```` 围栏 → 截取首个 `{` 到最后一个 `}` → Jackson 反序列化 → 字段级校验（`summary` 非空时必须含 `chiefComplaint`；`suggestions[].basis` 至少 1 条；`likelihood` 非法降级为 `LOW`）；**确定性措辞归一化**：`diagnosis` 命中 `确诊`/`一定是`/`百分之百`/`必然`/`可以排除` 时去除命中词并前置「疑似」，去词后长度 < 2 则丢弃该条（落实 FR-007/SC-003，不得只依赖提示词）；失败返回 `ParseResult.failed(PARSE_FAILED)`，**不返回原始文本**
- [ ] T018 [P] [FOUND] 新建 `server/.../doctor/support/MedicationMapper.java`：`MapResult map(List<RawMedication> raw, List<DrugCatalog> catalog)`；归一化后精确匹配 → 前缀最长匹配；`spec` 取目录值；`dosage` 空或 <2 字符 → `needsCompletion=true`；目录外丢弃并回传 `ignoredMedications`；超过 10 条截断
- [ ] T019 [P] [FOUND] 新建 `server/.../doctor/support/TreatmentLimitStore.java`：`LockOutcome lock(long consultationId)`（`ai:treatment:lock:{cid}`，`SET NX EX 30`，`WATCH/MULTI` 释放）、`void checkRegenerate(long consultationId)`（`ai:treatment:rate:{cid}`，`INCR` + 首次 `EXPIRE 3600`，超 `regenerate-limit-per-hour` 抛 `TREATMENT_DECISION_RATE_LIMITED`）、`void unlock(long consultationId, String token)`；`RedisConnectionFailureException` 时锁与频控**降级放行**并 `WARN`（只记 `consultationId` 与异常类名）
- [ ] T020 [P] [FOUND] 扩展 `server/.../identity/model/AuditEventType.java`：追加 `AI_TREATMENT_DECISION_REQUESTED`、`AI_TREATMENT_DECISION_DEGRADED`、`AI_TREATMENT_DECISION_ADOPTED`；同步 converter，枚举尾部注释登记 006
- [ ] T021 [P] [FOUND] 扩展 `server/.../common/ErrorCode.java`：在 005 分组后新增 006 分组与 4 个常量（`TREATMENT_DECISION_NOT_FOUND`/`TREATMENT_DECISION_BUSY`/`TREATMENT_DECISION_RATE_LIMITED`/`INVALID_TREATMENT_ADOPTION`），`code` 与 `message` 逐字取自 `data-model.md` §5
- [ ] T022 [FOUND] 新建 `server/.../doctor/service/TreatmentDecisionService.java` + `impl/TreatmentDecisionServiceImpl.java`（依赖 T009–T021）：`generate(long doctorId, long consultationId, TreatmentTriggerType trigger)`、`latest(long doctorId, long consultationId)`、`markAdopted(long doctorId, long consultationId, int appliedItemCount)`；实现 `plan.md` 时序第 4–13 步，含幂等复用、抢锁、上下文组装、模型调用、解析、映射、落痕与降级状态映射
- [ ] T023 [FOUND] 新建 `server/.../doctor/controller/TreatmentDecisionController.java`：端点 1–4（`POST/GET .../treatment-decision`、`POST .../regenerate`、`POST .../adoptions`）；医生身份统一 `DoctorIdExtractor.extract(httpRequest).doctorId()`；返回 `Result<T>`
- [ ] T024 [FOUND] 新建 `server/.../doctor/controller/DrugCatalogController.java` + DTO（`DrugView`/`DrugListResponse`）：`GET /api/v1/workbench/drugs`，`keyword` ≤50、`pageSize` 1–50，分页结构 `{list,total,page,pageSize}`；越界返回既有 `BAD_REQUEST`（**不新增专属错误码**）
- [ ] T025 [FOUND] 新建 `server/src/test/java/com/sdas/server/doctor/integration/TreatmentDecisionFoundationalIT.java`：断言 `V11` 六列与索引存在、`ai_decision_session` 既有行可读、3 枚举默认值正确、10 个配置项绑定成功、4 个错误码文案与 `data-model.md` 逐字一致

**检查点**：`mvn -o test -Dtest='AiDecisionServiceTest,TreatmentDecisionFoundationalIT'` 全绿；003 端点 9 无回归；此时尚无成功链路，但降级骨架（端点 + 留痕 + 状态）可用。

---

## Phase 3: User Story 4 - AI 不可用不影响正常开方（Priority: P1，骨架先行）

**目标**：端点 1/2/3 在模型超时、异常、空返回、解析失败时返回 `HTTP 200` + 降级状态，前端展示降级提示，处方表单完全可用并可签名成功；不产生半成品处方。

**独立测试**：注入超时（`SDAS_AI_TREATMENT_TIMEOUT_MILLIS=1`）进入开方页，验证辅助区域显示降级提示与重试、表单可填、签名成功、留痕 `status=TIMEOUT_FALLBACK`；再验证刷新时端点 2 返回同一条降级记录而非重新调用。

### Tests for User Story 4（强制）⚠️

- [ ] T026 [P] [US4] 单测 `server/.../doctor/unit/TreatmentDecisionClientTest.java`：正常 JSON 透传；`get` 超时 → `success=false, reason=TIMEOUT`；上游异常 → `UPSTREAM_ERROR`；空白内容 → `EMPTY`；断言不产生兜底文案
- [ ] T027 [P] [US4] 单测 `server/.../doctor/unit/TreatmentDecisionServiceTest.java`：降级仍落 1 条留痕（`status` 映射正确、`degraded_reason` 正确）；降级响应 `summary=null`、数组为空、`disclaimer` 照常；降级**不写处方式数据**；`latest` 无记录抛 `TREATMENT_DECISION_NOT_FOUND`
- [ ] T028 [P] [US4] 契约测试 `server/.../doctor/contract/TreatmentDecisionContractTest.java`：端点 1/2/3 降级响应结构（`code/message/data` 字段齐备、`status` 枚举值、`degradedReason`）；401/403/404/400/409 异常表逐项；越权响应体**不含**主诉/描述/对话字段；CSRF 缺失 403
- [ ] T029 [P] [US4] 集成测试 `server/.../doctor/integration/TreatmentDecisionFlowIT.java`（降级部分）：`@MockBean` 客户端超时 → 200 降级 + 留痕 1 行 → 随后调用 003 端点 7 开方成功（SC-006）；并发 2 次请求只产生 1 条留痕（FR-021）
- [ ] T030 [P] [US4] E2E Mock `web/tests/e2e/treatment-decision.mock.spec.ts`（降级用例）：M3 → 面板显示「AI 辅助暂不可用」+ 重试；表单可填；无空白卡片、无原始 JSON
- [ ] T031 [P] [US4] E2E 真实后端 `web/tests/e2e/treatment-decision.real.spec.ts`（降级用例）：`E2E_REAL_BACKEND=1` + 压超时环境变量，断言 `status=TIMEOUT_FALLBACK` 与 `code=200`

### Implementation for User Story 4

- [ ] T032 [US4] 在 `TreatmentDecisionServiceImpl` 补齐降级分支：`TreatmentDecisionResult.success=false` 时按 `reason` 映射 `TIMEOUT_FALLBACK`/`ERROR_FALLBACK`/`EMPTY_FALLBACK`，落痕（`result_json` 只写 `{"degradedReason":...}`），审计 `AI_TREATMENT_DECISION_REQUESTED(result=FAILURE)` + `AI_TREATMENT_DECISION_DEGRADED(reasonCode)`
- [ ] T033 [US4] 新建 `web/src/stores/treatment-decision.ts`：状态 `decision`/`loading`/`degraded`/`error`；action `load(consultationId, { force })`（先 GET，404 再 POST；`force` 走 regenerate）、`retry()`；**必须防止重复自动触发**（`loadedFor === consultationId` 直接返回）
- [ ] T034 [US4] 新建 `web/src/components/treatment-decision-panel.vue`：四态（加载骨架 / 成功容器 / 降级提示 + 重试 / 隐藏）；降级文案取 `data-degraded` 渲染，禁止硬编码「系统异常」等模糊文案；面板行数 ≤ 200
- [ ] T035 [US4] 改造 `web/src/views/workbench-prescription-view.vue`：`onMounted` 接入 `load()`；面板与表单并列（生成中表单可编辑、可提交）；降级时不改变表单默认值与校验
- [ ] T036 [US4] 执行 US4 独立测试（含超时、异常、空返回、解析失败四条降级分支与「降级后仍开方成功」证据）记入 `testsdoc/ai-treatment-decision/us4-verification.md`

**检查点**：US4 可独立演示：模型全挂时医生能完成开方且留痕正确。

---

## Phase 4: User Story 1 - 开方前自动生成病情摘要（Priority: P1）

**目标**：成功链路打通，摘要四段齐备、标注 `evidenceLimited`、含生成时间与免责；生成中不阻塞表单。

**独立测试**：6 条以上对话 → 摘要四段齐备；仅 1 条描述 → 摘要生成且标注「依据有限」；刷新走端点 2 不重复生成。

### Tests for User Story 1（强制）⚠️

- [ ] T037 [P] [US1] 单测 `server/.../doctor/unit/DecisionContextAssemblerTest.java`：对话取最近 40 条；超 3000 字符按最近优先截断且顺序为正序；无对话时 `evidenceLimited=true`；有图无文字说明追加提示；知识同科室优先
- [ ] T038 [P] [US1] 单测 `server/.../doctor/unit/DecisionPayloadParserTest.java`：标准 JSON；```json 围栏；前后噪声文本；缺 `summary` 字段 → `PARSE_FAILED`；超长 → 截断；非法 `likelihood` → `LOW`；**确定性措辞用例**：输入「确诊为肺炎」→ 输出 `diagnosis` 不含「确诊」且以「疑似」开头，输入「一定是流感」同理，去词后过短的条目被丢弃
- [ ] T039 [P] [US1] 契约测试补充（`TreatmentDecisionContractTest`）：端点 1 成功响应 `summary` 四段字段、`generatedAt` 格式、`modelUsed`；端点 2 返回最近一条；端点 2 无记录 404
- [ ] T040 [P] [US1] E2E Mock（`treatment-decision.mock.spec.ts` 摘要用例）：M1 → 摘要四段可见、免责可见、`evidenceLimited` 提示仅在为真时出现
- [ ] T041 [US1] 完善 `server/.../doctor/support/DecisionContextAssembler.java`：实现 `data-model.md` §3 `Context` 预算与 `evidenceLimited` 判定；主诉/描述各截断 500；知识结果按 `departmentId` 重排
- [ ] T042 [US1] 完善 `DecisionPromptBuilder` 摘要段：要求 `summary` 四字段齐备、无内容填「暂无」、`evidenceLimited` 由服务端依据上下文判定而非模型自述
- [ ] T043 [US1] 新建 `web/src/components/treatment-summary-card.vue`：四段渲染 + `暂无` 占位 + 生成时间 + 免责；`evidenceLimited` 时顶部提示「对话信息较少，依据有限」
- [ ] T044 [US1] 在 `treatment-decision-panel.vue` 接入摘要卡；`summary=null` 时（降级）不渲染空卡
- [ ] T045 [P] [US1] E2E 真实后端（`treatment-decision.real.spec.ts` 摘要用例）：断言 `code=200`、`status=SUCCESS`、`summary.chiefComplaint` 非空
- [ ] T046 [US1] 执行 US1 独立测试并记入 `testsdoc/ai-treatment-decision/us1-verification.md`

**检查点**：US1 + US4 可演示：开方页自动出摘要，AI 挂了也能开方。

---

## Phase 5: User Story 2 - 结构化诊断建议（Priority: P1）

**目标**：建议四段（诊断/可能性/依据/处置/风险）齐备、逐条免责、空 `basis` 丢弃、知识无命中标注依据有限、急症提示优先。

**独立测试**：同一问诊单验证建议字段与免责；构造知识无命中 → 「依据有限」；构造急症描述 → 顶部「建议优先排除急症」。

### Tests for User Story 2（强制）⚠️

- [ ] T047 [P] [US2] 单测 `DecisionPayloadParserTest`（建议段）：`basis` 为空该条丢弃；建议超 5 条截断；`workup` 缺失置空数组；`riskAlert` 缺失为 `null`
- [ ] T048 [P] [US2] 契约测试补充：`suggestions[]` 字段与免责文案逐字；`disclaimer` 等于 `TreatmentDecisionDisclaimer.TEXT`
- [ ] T049 [P] [US2] E2E Mock（建议用例）：建议列表可见、每条带「辅助参考，不作为诊断结论」
- [ ] T050 [US2] 完善 `DecisionPromptBuilder` 建议段：要求按可能性排序、禁止确定性措辞、急症描述时在 `riskAlerts` 置顶
- [ ] T051 [US2] 新建 `web/src/components/treatment-suggestion-list.vue`：逐条「应用」按钮（emit `apply`），未应用项默认不进入表单
- [ ] T052 [US2] 前端应用建议：点击「应用」把该条 `diagnosis` 写入本地「诊断思路」备注区（**不写处方表、不调开方接口**），并静默调用端点 4 回填 `appliedItemCount`
- [ ] T053 [P] [US2] E2E 真实后端（建议用例）：断言 `suggestions.length ≥ 1` 且 `disclaimer` 逐字
- [ ] T054 [US2] 执行 US2 独立测试并记入 `testsdoc/ai-treatment-decision/us2-verification.md`

**检查点**：US1 + US2 + US4 可演示；建议与摘要口径一致。

---

## Phase 6: User Story 3 - 用药信息自动预填并确认（Priority: P2）

**目标**：草稿来自目录且启用、规格取目录值、`needsCompletion` 标注、目录外丢弃并提示、医生确认或修改后提交、未确认不落处方；伴随端点 5 替换前端硬编码。

**独立测试**：草稿填入表单且为「待确认」；改剂量后入库为改后值；目录外药品被丢弃且出现提示；不点确认无法生成处方。

### Tests for User Story 3（强制）⚠️

- [ ] T055 [P] [US3] 单测 `server/.../doctor/unit/MedicationMapperTest.java`：精确匹配；前缀最长匹配（「阿莫西林胶囊 0.25g」→「阿莫西林胶囊」）；目录外丢弃并计入 `ignoredMedications`；停用药品（`enabled=0`）不匹配；`dosage` 空 → `needsCompletion=true`；超 10 条截断；剂量异常标注
- [ ] T056 [P] [US3] 契约测试补充：`medicationDrafts[]` 字段齐备、`spec` 等于目录值、`ignoredMedications` 数组；端点 5 分页结构与 `keyword` 过滤
- [ ] T057 [P] [US3] E2E Mock（草稿用例）：M1 → 草稿出现在表单且标注「待确认」；M1 中 `ignoredMedications` 非空时出现提示
- [ ] T058 [US3] 完善 `MedicationMapper`：实现 R-07 归一化与前缀匹配、危险剂量（单次剂量 > 目录规格数值×10 或日频次 >6）标注「剂量需医生核对」
- [ ] T059 [US3] 完善 `DecisionPromptBuilder` 用药段：只允许通用名 + 用法用量 + 注意事项，明确禁止输出 drugId 与目录外药品
- [ ] T060 [US3] 前端新增 `web/src/api/treatment-decision.ts` 方法 `listDrugs(keyword, page, pageSize)`；改造 `workbench-prescription-view.vue` 的药品下拉：`onMounted` 拉取目录（替换 `DEMO_DRUGS`），加载失败时回退到硬编码列表并提示「药品目录加载失败」
- [ ] T061 [US3] 新建 `web/src/components/treatment-medication-drafts.vue`：草稿列表 + 逐条「应用到处方」；应用后写入表单 `items`（`drugId`/`dosage`/`caution`），`needsCompletion` 项标红并要求医生补齐后才能提交
- [ ] T062 [US3] 开方页整合：草稿默认**不自动写入**表单，需医生点击「全部应用」或逐条应用（满足 FR-010/FR-023）；重复应用不产生重复行
- [ ] T063 [P] [US3] E2E 真实后端（草稿用例）：断言草稿 `drugId` 命中目录、修改剂量后处方详情为改后值、未点确认时 `prescription` 表无新增
- [ ] T064 [US3] 执行 US3 独立测试并记入 `testsdoc/ai-treatment-decision/us3-verification.md`

**检查点**：US1–US4 全部可演示；5 个端点闭环；前端无硬编码药品。

---

## Phase 7: Polish & Cross-Cutting（收口）

- [ ] T065 [P] 覆盖率补齐：`mvn -o verify` 的 jacoco `check` 通过（新包行覆盖率 ≥ 70%），重点补 `TreatmentDecisionServiceImpl` 降级/复用/并发分支与 `TreatmentLimitStore` Redis 不可用分支
- [ ] T066 [P] SpotBugs 与 Checkstyle：`server/config/spotbugs/spotbugs-exclude.xml` 增加 006 包豁免口径（若需要）；`mvn -o checkstyle:check` 零违规；前端 `npm run typecheck && npm run lint && npm run format:check` 零错误零警告
- [ ] T067 [P] 契约一致性验证：逐项比对 `contracts/ai-treatment-decision-api.md` 与真实响应（5 端点、`code` 值、枚举、字段名、日期格式、`disclaimer` 文案），结果写 `testsdoc/ai-treatment-decision/contract-conformance.md`；同步校验 mocks 与 `web/src/api/mocks/treatment-decision.ts`
- [ ] T068 [P] 安全自查：全文检索确认日志/审计/`result_json`/`input_summary` 无患者姓名、手机号、身份证、完整病情明文；确认越权响应体无病情；确认 AI 输出不出现在患者端路由与接口；确认无自动签名路径
- [ ] T069 [P] 性能抽测：真实调用 P95 ≤ 10s、幂等复用 ≤ 100ms、上下文组装 ≤ 100ms、知识检索 ≤ 50ms；50 并发开方页加载不劣化；**SC-009 基线对比**——先记录 50 例「进入开方页→签名成功」的平均耗时与操作步数（未接入辅助的基线），再复测接入后同条件数据并记录下降比例；无法采集真实基线时退化为「平均填写字段数」对比，并在报告中说明口径（结果记 `testsdoc/ai-treatment-decision/test-report.md`）
- [ ] T070 执行 `quickstart.md` 第 1–9 节（含降级、频控、并发、越权、目录外药品五类反向验证），结果记 `testsdoc/ai-treatment-decision/manual-qa-checklist.md`
- [ ] T071 全量回归：`cd server && IDENTITY_TEST_USE_LOCAL_INFRA=true mvn -o verify`；`cd web && npx vitest run && npx playwright test`（mock 全绿，real 按需）；**必须包含 003 端点 9 回归**（C1 兼容）
- [ ] T072 测试报告归档 `testsdoc/ai-treatment-decision/test-report.md`（后端/前端/静态检查/覆盖率/相关文档 5 节）
- [ ] T073 代码审查（宪法 V）：评审模型出口隔离、状态/权限矩阵、Redis 降级、药品白名单、提示词注入防护、留痕脱敏；结论落 `testsdoc/ai-treatment-decision/code-review-security.md`
- [ ] T074 文档回填：`data-model.md` / `contracts/` 与最终实现逐项对齐（含 4 个错误码、3 枚举、10 配置项）；更新 `plan.md` 实施状态

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup（T001–T008）**：无前置，可立即开始。
- **Phase 2 Foundational（T009–T025）**：依赖 Phase 1；**T009 单独阻塞全部后续**。
- **Phase 3 US4（T026–T036）**：依赖 Phase 2。
- **Phase 4 US1（T037–T046）**：依赖 US4 的端点骨架（T022/T023/T033/T034）。
- **Phase 5 US2（T047–T054）**：依赖 US1（复用解析器与面板）。
- **Phase 6 US3（T055–T064）**：依赖 US2；端点 5（T024）独立可先做。
- **Phase 7 收口（T065–T074）**：依赖 US1–US4 全部完成。

### 关键任务依赖（易错点）

| 任务 | 依赖 | 说明 |
|------|------|------|
| T009 C1 兼容改造 | — | record 扩展会连锁破坏 003 编译，必须先做且同批次改 3 个调用点 |
| T011 幂等查询 | T009 | `dialog_version` 列由 T008/T009 引入 |
| T014 Client 实现 | T005、T013 | 超时与 token 上限来自配置 |
| T015 上下文组装 | T012、005 `KnowledgeRetriever` | 知识检索失败按 `KNOWLEDGE_UNAVAILABLE` 标注但不阻断；药品映射在 T018 |
| T017 解析器 | T016 | prompt 的字段骨架与解析字段必须一一对应 |
| T019 限流存储 | T008（表无关，键独立） | Redis 不可用必须放行，否则阻断开方 |
| T022 编排 | T009–T021 | 顺序：校验 → 幂等 → 抢锁 → 组装 → 调用 → 解析 → 映射 → 落痕 → 释放锁 |
| T024 端点 5 | T012 | 前端 T060 依赖该端点 |
| T042 摘要 prompt | T017 | 服务端判定 `evidenceLimited`，不得由模型自述 |
| T052 应用建议 | T023（端点 4）、T034 | 回填失败必须静默，不阻断签名 |
| T058 药品映射 | T012、T018 | 目录外丢弃必须回传 `ignoredMedications` |
| T060 目录下拉 | T024 | 端点 5 不可用时要回退，避免开方页白屏 |

### 并行机会

- Phase 1 的 T002–T008 七路并行（不同文件）。
- Phase 2 的 T010–T013、T015–T021 可并行；T014 紧跟 T013；T022 收口后 T023/T024 并行。
- US4 内 T026–T031（测试）全并行；T033–T035 前后端可并行。
- US3 的 T055–T057（测试）与 T060（目录下拉）可并行；T058 依赖 T055 的用例定义。

---

## MVP 与交付策略

1. **可交付切片 1（US4）**：Phase 1 + Phase 2 + Phase 3 → 端点骨架、降级、留痕、面板；此阶段医生体验与 003 等价（无辅助内容但流程完整）。
2. **可交付切片 2（US4 + US1）**：加 Phase 4 → 病情摘要上线，核心价值成立。
3. **可交付切片 3（+US2）**：Phase 5 → 诊断建议上线。
4. **完整交付（+US3）**：Phase 6 → 用药草稿与药品目录替换。
5. **收口**：Phase 7 全绿后交付；003 端点 9 回归必须通过。

---

## 需求覆盖矩阵（FR → 任务）

| 需求 | 任务 |
|------|------|
| FR-001 开方页自动触发 | T033、T035、T022 |
| FR-002 幂等不重复生成 | T011、T022、T027 |
| FR-003 仅引用本单资料 | T015、T037 |
| FR-004 摘要四段 + 暂无占位 | T041、T042、T043 |
| FR-005 生成时间/免责/来源 | T016、T043、T067 |
| FR-006 建议四段 | T050、T051、T047 |
| FR-007 逐条免责 + 禁确定性措辞 | T016、T050、T048 |
| FR-008 草稿仅目录内药品 | T012、T018、T058、T055 |
| FR-009 草稿字段 + 需补充标注 | T018、T058、T061 |
| FR-010 医生确认才可签名 | T035、T062、T068 |
| FR-011 AI 输出不写处方表 | T022、T027、T063 |
| FR-012 明确降级提示 | T032、T034、T030 |
| FR-013 降级保留已填内容 | T035、T029、T030 |
| FR-014 重新生成 ≤3 次/小时 | T019、T023、T028 |
| FR-015 留痕（脱敏/截断） | T009、T022、T032 |
| FR-016 审计与采纳回填 | T020、T022、T052 |
| FR-017 仅接诊医生可见 | T022、T028、T068 |
| FR-018 状态限制 | T022、T028、T029 |
| FR-019 输入/输出上限 | T015、T016、T017 |
| FR-020 患者端不可见 | T068 |
| FR-021 并发互斥 | T019、T029 |
| FR-022 服务端二次校验 | T058（草稿）+ 003 既有 `PrescriptionServiceImpl` |
| FR-023 不静默覆盖已改内容 | T062、T044 |
| FR-024 免责文案服务端下发 | T006、T022、T067 |

## 风险项 → 测试任务（对应 spec 7.1）

| 风险项 | 测试任务 |
|--------|----------|
| 摘要引用他人病情 | T037（数据隔离）、T028（越权响应体） |
| AI 输出被当作诊断结论 | T048（免责逐字）、T038（措辞拦截不通过则丢弃） |
| 目录外药品被预填 | T055、T056、T057 |
| 自动签名/未经确认开方 | T035、T062、T063、T068 |
| AI 超时阻塞开方 | T026、T029、T030、T031 |
| 并发重复生成 | T029 |
| 越权访问 | T028 |
| 已开方后重复生成 | T028、T029 |
| 覆盖医生修改 | T062 |
| 病情明文进日志 | T068 |
| 契约漂移 | T067 |
| C1 改造破坏 003 | T009、T071 |

---

## Notes

- `[P]` 表示不同文件、无未完成依赖；同文件任务必须串行（如 T015/T041 同为 `DecisionContextAssembler`，T017/T038 同为解析器）。
- 测试任务必须**先写并确认失败**，实现后再转绿；禁止先写实现再补测试。
- 每完成一个用户故事即执行其「独立测试」，通过后才进入下一优先级。
- 契约或数据模型如需变更，先改 `specs/006-ai-treatment-decision/{data-model.md,contracts/}`，再改代码与测试。
- **任何阶段都不允许出现「AI 不可用时无法开方」**：US4 先于成功链路交付。
- `AiDecisionSession` record 扩展是**破坏性变更**，`T009` 必须在同一提交内更新 003 的调用点与测试。
- 提示词注入防护：患者对话内容只作为 `user` 消息拼接，不得拼进 system prompt；模型输出的字段一律经 `DecisionPayloadParser` 白名单校验。
- **已知缺口（不阻塞本期）**：无药物相互作用/过敏原数据库，草稿的 `caution` 仅来自模型与目录字段；若需要真实相互作用检查，另立功能。
