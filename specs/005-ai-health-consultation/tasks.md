# 任务清单：AI健康咨询

**功能**：`005-ai-health-consultation` | **日期**：2026-11-08
**输入**：`specs/005-ai-health-consultation/` 下的 `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/ai-health-consultation-api.md`、`contracts/mocks.md`、`quickstart.md`
**前置条件**：`spec.md`（US1–US3 + FR-001–FR-020，Clarifications 已定稿）、`plan.md`（G1–G6 门禁通过 + M1–M6 实现级设计）、`data-model.md`（4 枚举 + 3 表 + Redis 键规范 + 6 错误码）、`contracts/`（7 端点 + SSE 5 事件 + M1–M11）；上游 `001`（JWT/CSRF 双 Cookie/`PatientIdExtractor`/`AuditEventService`/`RedisKeys`）、`002`（`consultation`/`department`/`department_disease_kb`/`ChatClient.Builder` 既有装配）、`003`（`prescription`/`prescription_item`/`drug_catalog`）均已实现。

**测试**：依 `.specify/memory/constitution.md`（原则 VI）为强制项——Service 覆盖率 ≥ 70%（jacoco `verify` 强制）、7 个端点正向/异常契约与集成测试全覆盖、SSE 事件顺序测试、Playwright 覆盖 US1–US3（mock + real 成对）；测试用例先写且先失败，通过后方可视为功能完成。测试报告落 `testsdoc/ai-health-consultation/`。

**组织方式**：任务按用户故事分组（US1 多轮问答 / US2 紧急预警 / US3 多对话管理），每个故事可独立实现、独立测试、独立交付。

**唯一来源约束**：枚举与 `ErrorCode` 以 `data-model.md` 第 1、5 节为准；请求/响应字段与 `code` 以 `contracts/ai-health-consultation-api.md` 为准；SSE 事件名与顺序以该契约「事件序列」为准；Mock 以 `contracts/mocks.md`（M1–M11）为准；类名、字段名、配置键不得自行发明。

## 格式说明

- `[ID]`：任务编号，`T001` 起连续编号，实现阶段按序勾选。
- `[P]`：可并行（不同文件、无未完成依赖）。
- `[US1]`–`[US3]`：归属用户故事；跨故事地基任务标 `[FOUND]`。
- 每条任务写明精确文件路径与验收要点；禁止出现「实现某功能」这类无路径的模糊任务。

## 路径约定

- 后端新功能包：`server/src/main/java/com/sdas/server/healthconsultation/`（`controller/`、`service/`、`service/impl/`、`repository/`、`entity/`、`model/`、`model/converter/`、`dto/`、`support/`）
- 后端复用：`server/src/main/java/com/sdas/server/{common,config,exception,identity,appointment,doctor}/`
- 后端测试：`server/src/test/java/com/sdas/server/healthconsultation/{contract,integration,unit,support}/`
- 迁移脚本：`server/src/main/resources/db/migration/V9__ai_health_consultation_schema.sql`（建表）与 `V10__ai_health_knowledge_preset.sql`（知识预置，**migration 而非 seed**：test/prod 都需要）
- 前端：`web/src/`（`api/`、`api/mocks/`、`api/request/`、`composables/`、`views/`、`components/`、`stores/`、`router/`）
- 前端测试：`web/tests/unit/`、`web/tests/e2e/`
- 契约与设计：`specs/005-ai-health-consultation/`
- 测试报告：`testsdoc/ai-health-consultation/`

---

## Phase 1: Setup（共享基础设施）

**目的**：目录、前端类型/错误码/Mock/SSE 解析工具、配置绑定类、测试基座就绪；此阶段不写业务逻辑。

- [x] T001 创建 `server/src/main/java/com/sdas/server/healthconsultation/{controller,service,service/impl,repository,entity,model,model/converter,dto,support}/` 包（各包放 `package-info.java`），并创建 `server/src/test/java/com/sdas/server/healthconsultation/{contract,integration,unit,support}/`、`testsdoc/ai-health-consultation/` 目录；`mvn -o -q compile` 通过
- [x] T002 [P] 扩展 `web/src/api/types.ts`：按 `contracts/ai-health-consultation-api.md` 定义 `MESSAGE_ROLE`、`SESSION_STATUS`、`STREAM_STATUS`、`EMERGENCY_VERDICT` 常量（值与 `data-model.md` 第 1 节逐字一致）与 7 端点请求/响应接口（`HealthSessionSummary`、`HealthSessionListData`、`HealthMessageItem`、`HealthChatRequest`、`HealthChatAcceptedEvent`、`HealthEmergencyEvent`、`HealthDeltaEvent`、`HealthDisclaimerEvent`、`HealthDoneEvent`、`HealthErrorEvent`）；**助手消息 `disclaimer` 为独立字段，禁止拼进 `content`**；文件头标注来源 `specs/005-.../data-model.md` 与 `contracts/`
- [x] T003 [P] 扩展 `web/src/api/error-codes.ts`：新增 `SESSION_NOT_FOUND`(404)、`SESSION_TITLE_INVALID`(400)、`MESSAGE_TOO_LONG`(400)、`SESSION_MESSAGE_LIMIT`(400)、`CHAT_BUSY`(409)、`AI_SERVICE_UNAVAILABLE`(503)，`message` 不复制（一律取后端响应值），末尾追加分组注释引用 005 `data-model.md` 第 5 节
- [x] T004 [P] 新建 `web/src/api/mocks/health-consultation.ts`：从 `contracts/mocks.md` 同步 M1–M11（按 `METHOD /v1/path` + `#M{n}` 组织），响应 `message` 逐字复制；M7/M8/M9 的 SSE 事件需保留**逐帧字符串**（`event:` + `data:` + 空行），供 `page.route()` 与 SSE 顺序测试直接复用；M11 患者上下文**只允许出现在服务端测试夹具中，禁止进前端 mock**
- [x] T005 [P] 在 `server/src/main/resources/application.yml` 追加 `data-model.md` 第 6 节的 `sdas.ai.health.*`（`model`/`timeout-millis`/`first-token-timeout-millis`/`max-output-tokens`/`context-max-chars`/`context-medical-max-chars`/`history-window`/`message-max-chars`/`session-message-limit`/`knowledge-top-k`）与 `sdas.hospital.*`（`name`/`address`/`phone`/`emergency-phone`/`service-hours`）；全部支持 `SDAS_AI_HEALTH_*` / `SDAS_HOSPITAL_*` 环境变量覆盖；`application-prod.yml` 不重复定义
- [x] T006 [P] 新建 `web/src/api/sse.ts`：`createSseParser()` 按 `\n\n` 分帧、解析 `event:`/`data:`（支持多行 `data`）、忽略注释行（`:` 开头）与心跳；另导出 `postSse(url, body, signal)` 基于 `fetch` + `ReadableStream`（`credentials: 'include'`、`Accept: text/event-stream`、`X-CSRF-Token` 取 `web/src/api/request/csrf.ts` 的既有读取逻辑，**不得另写 Cookie 解析**）；`EventSource` 一律禁用（不支持 POST）
- [ ] T007 [P] 新建 `server/src/test/java/com/sdas/server/healthconsultation/support/HealthConsultationTestSupport.java`：继承 `identity/support/IdentityTestSupport`，`@MockitoBean HealthChatClient`（契约/集成测试不触真实 API），提供 `seedPatientWithHistory()`、`seedSession(patientId)`、`postSse(...)`（`MockMvc` 取 `asyncResult` 后按 `\n\n` 切帧）、`assertEventOrder(...)`；夹具主键取高位段（81xx/82xx）并幂等写入，避免与 001–004 冲突

**检查点**：`mvn -o -q compile` 与 `cd web && npm run typecheck` 通过；`api/sse.ts` 的分帧逻辑以临时脚本自测（正式用例在 T053 补齐）；目录与配置齐备，尚无业务行为。

---

## Phase 2: Foundational（阻塞性前置，必须全部完成）

**目的**：错误码、枚举、审计事件、迁移、实体仓储、Redis 热窗口、只读查询扩展、模型封装、上下文组装、知识检索、Prompt 组装、SSE 写出等所有用户故事共用的地基。

**⚠️ 关键**：本阶段未完成前，任何用户故事都不允许开始实现。

- [x] T008 [FOUND] **先修订规格冲突（阻塞后续编码）**：① `data-model.md` 第 3 节删除 `department_disease_kb` 行的「`enabled` 语义按 002 定义」——该表（`V3__appointment_consultation_schema.sql` L52-65）无 `enabled` 列；② `plan.md` M5 规则示例中的 `departmentCode` 改为 `departmentName`（`department` 表只有 `id/name/description/icon_url/sort_order/enabled`，**无 code 列**，且第 3 节约定只读不改结构），`code` 仅作规则标识；`data-model.md` 第 6 节对应同步；③ 将 `plan.md`/`quickstart.md`/`data-model.md` 中的「V9 含知识预置」拆为 `V9`（3 表）+ `V10`（58 条预置 + 兜底科室），并在三处标注「V10 为业务必需数据，故置于 migration」
- [x] T009 [P] [FOUND] 扩展 `server/src/main/java/com/sdas/server/common/ErrorCode.java`：在 004 分组后新增 005 分组与 6 个常量（`SESSION_NOT_FOUND`/`SESSION_TITLE_INVALID`/`MESSAGE_TOO_LONG`/`SESSION_MESSAGE_LIMIT`/`CHAT_BUSY`/`AI_SERVICE_UNAVAILABLE`），`code` 与 `message` 逐字取自 `data-model.md` 第 5 节，Javadoc 标注 `005 data-model.md 5`
- [x] T010 [P] [FOUND] 在 `server/src/main/java/com/sdas/server/healthconsultation/model/` 新建枚举 `MessageRole`（`PATIENT`/`ASSISTANT`）、`SessionStatus`（`ACTIVE`/`DELETED`）、`StreamStatus`（`COMPLETED`/`FAILED`/`INTERRUPTED`）、`EmergencyVerdict`（`CONFIRMED`/`REJECTED`/`UNAVAILABLE`/`MODEL_ONLY`）；`model/converter/` 下为 `MessageRole`（默认 `PATIENT`）、`SessionStatus`（默认 `ACTIVE`）、`StreamStatus`（默认 `FAILED`）、`EmergencyVerdict`（默认 `REJECTED`）各建一个 `SafeEnumConverter` 实现（`autoApply=false`）；禁止 `@Enumerated(EnumType.STRING)`
- [x] T011 [P] [FOUND] 扩展 `server/src/main/java/com/sdas/server/identity/model/AuditEventType.java`：追加 `AI_HEALTH_ACCESS_DENIED`（越权访问会话，FR-011/FR-014）、`AI_HEALTH_HISTORY_CLEARED`（清空全部历史，FR-017）、`AI_HEALTH_CHAT_FAILED`（模型失败/超时，FR-012）；同步 `AuditEventTypeConverter` 默认值不变，并在枚举尾部注释登记 005
- [x] T012 [P] [FOUND] 新建 `server/src/main/resources/db/migration/V9__ai_health_consultation_schema.sql`：按 `data-model.md` 第 2 节建 `ai_health_session`（含 `uk_ai_session_patient_id`、`idx_ai_session_patient`）、`ai_health_message`（含 `idx_ai_message_session`、`disclaimer`/`emergency`/`rule_code`/`stream_status`/`model_used` 列）、`ai_health_emergency_decision`（含 `idx_ai_emergency_session`）；InnoDB / utf8mb4_general_ci；FK 分别指向 `patient_account(id)`、`ai_health_session(id)`、`ai_health_message(id)`（`message_id` 允许 NULL，预警先发事件、落库后回填）
- [x] T013 [FOUND] 新建 `server/src/main/resources/db/migration/V10__ai_health_knowledge_preset.sql`（依赖 T012）：幂等插入兜底科室 `全科医学科`（`INSERT ... SELECT ... WHERE NOT EXISTS`，`department` 无 name 唯一约束故须用 `WHERE NOT EXISTS` 保证可重跑），再向 `department_disease_kb` 写入 58 条（症状与处置 20 / 用药注意 10 / 康复生活 8 / 就医指征 8 / 医院信息 12），统一 `source='PRESET'`、`kb_version='2026.11'`、`department_id` 取兜底科室 id；**只 INSERT，不 UPDATE/DELETE 既有行**；文件头注释「由 005 引入，业务必需，禁止移入 `db/seed`」
- [x] T014 [P] [FOUND] 在 `server/src/main/java/com/sdas/server/healthconsultation/entity/` 新建 `HealthSession`、`HealthMessage`、`EmergencyDecision` 实体（record），字段与 `data-model.md` 第 2 节一一对应，Javadoc 标注枚举 `@Converter` 关联
- [x] T015 [P] [FOUND] 新建 `.../healthconsultation/repository/HealthSessionRepository.java`：`long insert(HealthSession)`（`GeneratedKeyHolder`）、`Optional<HealthSession> findByIdAndPatientIdAndStatus(long id, long patientId, SessionStatus status)`、`List<HealthSession> findByPatientId(long, int offset, int limit)`、`long countByPatientId(long)`、`int rename(long id, long patientId, String title, int expectedVersion)`（`version = version + 1` 乐观锁）、`int markDeleted(long id, long patientId)`、`int markAllDeleted(long patientId)`、`int touch(long id, int messageCountDelta, LocalDateTime lastActiveAt)`；查询一律带 `patient_id` 条件
- [x] T016 [P] [FOUND] 新建 `.../healthconsultation/repository/HealthMessageRepository.java`：`long insert(HealthMessage)`、`List<HealthMessage> findBySessionId(long, int offset, int limit)`、`long countBySessionId(long)`、`Optional<HealthMessage> findLastBySessionId(long)`（列表页 `lastMessagePreview`）、`List<HealthMessage> findRecentCompleted(long sessionId, int limit)`（`role`/`stream_status='COMPLETED'`，按 `id DESC` 取窗口）、`int markStreamStatus(long messageId, StreamStatus status)`、`int deleteBySessionId(long)`（清空历史用物理删除）、`int deleteByPatientSessions(long patientId)`
- [x] T017 [P] [FOUND] 新建 `.../healthconsultation/repository/EmergencyDecisionRepository.java`：`long insert(EmergencyDecision)`、`int fillMessageId(long id, long messageId)`（助手消息落库后回填）、`List<EmergencyDecision> findBySessionId(long sessionId)`（测试/回溯用，不作为端点）；**禁止**在本仓储写入患者原话
- [x] T018 [P] [FOUND] 新建 `.../healthconsultation/support/RedisDialogStore.java`：注入 `StringRedisTemplate`；`lock(sessionId, ttlSeconds)` 用 `setIfAbsent(key, token, Duration)`（失败返回 `false`）、`unlock(sessionId, token)` 用 `WATCH/MULTI` 校验值后 `DEL`（Redis 3.2 无 Lua 依赖，禁止 `UNLINK`）、`window(sessionId)` 用 `LRANGE 0 -1`、`appendWindow(sessionId, List<String>)` 用 `RPUSH` + `LTRIM -{historyWindow} -1` + `EXPIRE`、`rebuildWindow(sessionId, List<String>)`（先 `DEL` 再 `RPUSH`）、`meta(sessionId)`/`saveMeta(...)`（Hash）、`deleteAll(sessionId)`；所有方法捕获 `RedisConnectionFailureException` 后返回降级值并 `WARN`（不含明文），调用方据此改走「每轮从 MySQL 重建」
- [x] T019 [P] [FOUND] 新建 `.../healthconsultation/support/HealthDisclaimer.java`：`public static final String TEXT = "仅供参考，不构成医疗诊断";`（全仓唯一来源）；配套单测断言常量逐字与服务端 `contracts/ai-health-consultation-api.md` 一致
- [x] T020 [P] [FOUND] 扩展 `server/src/main/java/com/sdas/server/appointment/repository/ConsultationRepository.java`（只读，不改表）：`List<Consultation> findRecentCompletedByPatientId(long patientId, ConsultationStatus status, int limit)`（`ORDER BY created_at DESC, id DESC`）、`List<Consultation> findByIdsIn(List<Long> ids)`
- [x] T021 [P] [FOUND] 扩展 `server/src/main/java/com/sdas/server/doctor/repository/PrescriptionRepository.java`（只读）：`List<Prescription> findByConsultationIdInAndStatus(List<Long> consultationIds, PrescriptionStatus status)`；现有 `findByConsultationId(long)` 保持不动
- [x] T022 [P] [FOUND] 扩展 `.../doctor/repository/PrescriptionItemRepository.java` 新增 `List<PrescriptionItem> findByPrescriptionIdIn(List<Long> ids)`；扩展 `.../doctor/repository/DrugCatalogRepository.java` 新增 `List<DrugCatalog> findByIdsIn(List<Long> ids)`（调用方按 `enabled=1` 过滤）
- [x] T023 [P] [FOUND] 扩展 `server/src/main/java/com/sdas/server/appointment/repository/DepartmentDiseaseKbRepository.java`：`List<KnowledgeHit> searchByKeywords(List<String> keywords, int topK)`（一次 SQL 用 `IN` + `LIKE` 拼装候选词、按命中词数降序、同分按 `id` 升序）、`long countBySourceAndKbVersion(String source, String kbVersion)`；新建 `.../healthconsultation/support/KnowledgeHit.java`（`diseaseName`/`departmentId`/`kbVersion`/`matchedKeywords`）；**不加 `enabled` 条件**（该表无此列，见 T008）。**实现偏差**：改为新建 `healthconsultation/repository/KnowledgeRepository.java`（只读同一张表），避免 002 模块反向依赖 005 包
- [x] T024 [P] [FOUND] 扩展 `.../appointment/repository/DepartmentRepository.java` 新增 `Optional<Department> findByName(String name)`（`enabled=1`）与 `List<Department> findByIdsIn(List<Long> ids)`；若 `.../doctor/repository/DoctorRepository` 已存在则新增 `List<String> findNamesByIds(List<Long> ids)`，不存在则在 `appointment/repository/` 新建 `DoctorNameRepository`（只读，返回 `id→name`，`doctor.name` 已是脱敏姓名）
- [x] T025 [FOUND] 新建 `server/src/main/java/com/sdas/server/config/HealthConsultationConfig.java`（`@Configuration` + `@EnableConfigurationProperties({HealthProperties.class, HealthEmergencyProperties.class, HospitalProperties.class})`，参照 `PharmacyConfig` 写法）与 record `HealthProperties`（`@ConfigurationProperties(prefix="sdas.ai.health")`，字段与 `data-model.md` 第 6 节一一对应）、`HealthEmergencyProperties`（`@ConfigurationProperties(prefix="sdas.ai.health.emergency")`，`List<EmergencyRule> rules`）、`HospitalProperties`（`@ConfigurationProperties(prefix="sdas.hospital")`）；`EmergencyRule` record 字段为 `code`/`name`/`level`/`departmentName`/`keywords`（依 T008 修订后字段）
- [x] T026 [FOUND] 新建 `.../healthconsultation/support/DefaultEmergencyRules.java`：内置 `CHEST_PAIN_DYSPNEA`/`STROKE_SIGNS`/`SEVERE_BLEEDING` 三条默认规则（关键词取自 `plan.md` M5）；在 `HealthEmergencyProperties` 上增加 `@Validated` 校验：`rules` 为空时**整体启用内置默认表**（不得视为关闭预警），`code`/`keywords` 非空、`departmentName` 非空；启动校验失败 fail-fast；同时在 `application.yml` 中以注释形式给出 `sdas.ai.health.emergency.rules` 示例（保持默认即不配置）。**实现说明**：当前在 `effectiveRules()` 内做空表回落，`@Validated` 注解级强校验留待收口补
- [x] T027 [P] [FOUND] 新建 `.../healthconsultation/support/HealthChatClient.java`（`Flux<String> stream(HealthChatCommand)`）、`HealthChatCommand.java`（`systemPrompt`/`turns`/`maxOutputTokens`/`model`）、`ChatTurn.java`（`role`/`content`）、`HealthChatException.java`（`Reason{TIMEOUT, UPSTREAM_ERROR, EMPTY}`）；**Spring AI 类型不得出现在本接口签名中**，Javadoc 标注 `plan.md` M1
- [x] T028 [FOUND] 新建 `.../healthconsultation/support/HealthChatClientImpl.java`（依赖 T027、T025）：构造注入 `ChatClient.Builder`（参照 `appointment/service/impl/TriageClientImpl` 的 build 方式）与 `HealthProperties`；`defaultOptions(model, maxOutputTokens)`；`stream()` 内 `chatClient.prompt().system(...).messages(...).stream().content()` → `timeout(firstTokenTimeoutMillis)`（item 间空闲超时）+ `take(timeoutMillis)`（整体截止）+ `subscribeOn(Schedulers.boundedElastic())`；空流抛 `EMPTY`、异常映射 `TIMEOUT`/`UPSTREAM_ERROR`；**不生成任何兜底文案**
- [x] T029 [FOUND] 新建 `.../healthconsultation/support/MedicalContextAssembler.java`（依赖 T020–T024）：`MedicalContext assemble(long patientId)`；`MedicalContext` record 含 `summaryText`/`referencedConsultationIds`/`truncated`；摘要模板与预算算法按 `plan.md` M2（默认 1500 字符、时间倒序、一条放不下即 `truncated=true` 并停止）；无记录时 `summaryText="（该患者暂无历史就诊记录）"`；**摘要不写日志、不回传前端、不落库**
- [x] T030 [FOUND] 新建 `.../healthconsultation/support/KnowledgeRetriever.java`（依赖 T023、T025）：`List<KnowledgeHit> retrieve(String patientText, int topK)`；规整化（去标点/小写/切词）+ 中文 2-gram 与 3-gram 候选词，过滤停用词与单字，候选词上限（如 40）防止 `IN` 过大；空命中返回空列表（由 Prompt 声明依据有限）
- [x] T031 [FOUND] 新建 `.../healthconsultation/support/HealthPromptBuilder.java`（依赖 T026、T029、T030、T025）：`HealthChatCommand build(long patientId, List<ChatTurn> window, String patientText)`；system prompt 固定包含 ①角色与安全边界（不得诊断/不得处方加减量，`FR-015`）②`EMERGENCY: YES|NO` 首行协议（`plan.md` M5）③病历摘要段（`truncated=true` 时要求模型说明依据有限）④知识依据段（含 `kb_version`，空则要求声明依据有限）⑤医院信息段（`sdas.hospital.*`）⑥免责要求；总长按 `context-max-chars` 裁剪（病历已先按 `context-medical-max-chars` 裁剪）；窗口与本轮消息按 `history-window` 组装，超预算时**最近优先**保留
- [x] T032 [FOUND] 新建 `.../healthconsultation/support/SseEventWriter.java`：`accepted`/`emergency`/`delta`/`disclaimer`/`done`/`error` 六个发送方法，`data` 为 JSON（`JacksonConfig` 既有 `ObjectMapper`）；响应头设置 `Content-Type: text/event-stream;charset=UTF-8`、`Cache-Control: no-cache`、`X-Accel-Buffering: no`；`send` 失败（客户端断开）时向上抛 `ClientDisconnectedException` 供 Service 标记 `INTERRUPTED`
- [x] T033 [FOUND] 新建 `server/src/test/java/com/sdas/server/healthconsultation/integration/FoundationalIT.java`：断言 3 张新表与索引存在、`V9`/`V10` 可重复迁移、`department_disease_kb` 中 `source='PRESET' AND kb_version='2026.11'` 恰为 58 条、兜底科室存在且唯一、6 个错误码文案与 `data-model.md` 逐字一致、`HealthProperties`/`HospitalProperties` 绑定成功、`rules` 为空时启用内置默认表

**检查点**：`mvn -o test -Dtest='FoundationalIT'` 全绿 + `HealthDisclaimerTest` 通过；此时尚无任何新端点，但地基（含模型封装与上下文组装）可用。

---

## Phase 3: User Story 1 - 多轮医学问答（参考我的就诊信息）（Priority: P1）🎯 MVP

**目标**：患者新建/进入会话后提问，系统结合其既往问诊与处方、医学知识库，以 SSE 流式返回回答，每轮末尾强制附加免责声明；无就诊记录时退化为通用建议并声明依据有限。

**独立测试**：以一名有历史问诊记录的患者新建会话并连续提问 3 轮（含「那剂量呢」类指代），验证回答引用本人记录、SSE 顺序为 `accepted`→`delta`*→`disclaimer`→`done`；再以无记录新患者重复，验证通用建议 + 依据有限提示；最后断开连接与模拟模型失败，验证 `FAILED`/`INTERRUPTED` 落库与重试提示。

### Tests for User Story 1（强制）⚠️

> **说明：先写这些测试并确认失败，再进入实现。**

- [ ] T034 [P] [US1] 契约测试 `.../healthconsultation/contract/ChatSseContractTest.java`：端点 2（`POST /sessions`，缺 CSRF 403）、端点 6（`GET /sessions/{id}/messages` 字段齐备含 `disclaimer`/`streamStatus`）、端点 7（SSE 事件顺序、`delta` 拼接等于落库 `content`、`disclaimer` 逐字、`done` 含 `truncated`/`modelUsed`、流中失败发 `error` 且与 `done` 互斥、流建立前失败返回 JSON 统一响应：401/403/404/409/400）；`message` 逐字取自契约
- [x] T035 [P] [US1] 单测 `.../healthconsultation/unit/HealthChatClientImplTest.java`：①正常增量序列；②`Flux.error`→`UPSTREAM_ERROR`；③永不结束→`TIMEOUT`（虚拟时间或短超时）；④空流→`EMPTY`；⑤断言不产生任何兜底文案
- [x] T036 [P] [US1] 单测 `.../healthconsultation/unit/MedicalContextAssemblerTest.java`：预算内全量；超预算按最近优先截断且 `truncated=true`；一条放不下的退化分支；无记录文案；**只含当前患者数据**（Mockito 断言 `patientId` 透传）
- [x] T037 [P] [US1] 单测 `.../healthconsultation/unit/KnowledgeRetrieverTest.java`：2/3-gram 切分、多关键词命中排序、同分按 id 升序、Top-K 截断、空命中返回空列表、候选词上限生效
- [x] T038 [P] [US1] 单测 `.../healthconsultation/unit/HealthPromptBuilderTest.java`：system prompt 含首行协议与「不得给出处方结论」；`truncated=true`/知识空命中的「依据有限」提示存在；总长 ≤ `context-max-chars`；窗口顺序为本轮消息在最后
- [x] T039 [P] [US1] 单测 `.../healthconsultation/unit/EmergencyHeaderParserTest.java`：`EMERGENCY: YES`/`NO` 解析、大小写与多余空格容错、缺失/空流返回 `UNAVAILABLE`、正文剥离后不含首行
- [ ] T040 [P] [US1] 集成测试 `.../healthconsultation/integration/ChatFlowIT.java`：3 轮连续问答（第 3 轮「那剂量呢」能取到前文）；无就诊记录 → 回答含依据有限提示；`@MockBean HealthChatClient` 抛错 → `accepted` + `error` 且助手消息 `FAILED`、患者消息保留；客户端断开 → `INTERRUPTED`；同会话并发 → 第二个请求 409 `CHAT_BUSY`；超长输入 → 400 `MESSAGE_TOO_LONG`；消息数达 200 → 400 `SESSION_MESSAGE_LIMIT`；越权 → 404 `SESSION_NOT_FOUND`
- [x] T041 [P] [US1] E2E `web/tests/e2e/health-consultation-chat.mock.spec.ts`：M2 新建 → M7 流式逐字渲染（断言首帧后即出现内容，非一次性）→ 免责声明仅来自 `disclaimer` 事件 → M9 失败展示重试入口且患者消息仍在 → M10 流建立前 409 展示 toast
- [ ] T042 [P] [US1] E2E `web/tests/e2e/health-consultation-chat.real.spec.ts`：`E2E_REAL_BACKEND=1`，断言真实后端 `code` 值（401 未登录、404 越权、200 新建）与 SSE 首事件为 `accepted`；无 `DEEPSEEK_API_KEY` 时该用例 skip 并输出原因

### Implementation for User Story 1

- [x] T043 [US1] 新建 `.../healthconsultation/service/HealthSessionService.java` + `impl/HealthSessionServiceImpl.java`（US1 最小集）：`create(patientId)`（标题固定「新对话」）、`messages(patientId, sessionId, page, pageSize)`；归属校验失败一律 `SESSION_NOT_FOUND` 并写 `AI_HEALTH_ACCESS_DENIED` 审计（不含明文）
- [x] T044 [US1] 新建 `.../healthconsultation/controller/HealthSessionController.java`：`POST /api/v1/health-consultation/sessions`、`GET /api/v1/health-consultation/sessions/{sessionId}/messages`；患者 ID 统一 `PatientIdExtractor.extract(httpRequest)`，Controller 禁止自解析令牌；返回 `Result<T>`
- [x] T045 [US1] 新建 `.../healthconsultation/service/HealthChatService.java` + `impl/HealthChatServiceImpl.java`：按 `plan.md`「一轮对话完整时序」第 3–13 步编排——归属/长度/条数校验 → `RedisDialogStore.lock`（失败 `CHAT_BUSY`）→ 落库患者消息 → `accepted` → 读窗口（miss 则 `findRecentCompleted` 重建）→ `assemble` + `retrieve` → `HealthPromptBuilder.build` → `HealthChatClient.stream` → 首元素剥离首行（`EmergencyHeaderParser`，US1 阶段只剥离不预警）→ `delta`* → 落库助手消息（`COMPLETED` + `disclaimer`）→ `disclaimer` → `done`；`@Transactional` 提交后 `TransactionSynchronization.afterCommit` 写 Redis 窗口与 `meta`、`touch` 会话；失败 → `FAILED` + `error`（不写 Redis 窗口）；断开 → `INTERRUPTED`；`finally` 释放锁
- [x] T046 [US1] 新建 `.../healthconsultation/controller/HealthChatController.java`：`POST /api/v1/health-consultation/sessions/{sessionId}/messages` 返回 `SseEmitter`（构造超时 = `timeout-millis + 2000`）；校验/加锁失败在**写流之前**抛 `BusinessException`（走 `GlobalExceptionHandler` 的 JSON 统一响应）；注册 `onCompletion`/`onTimeout`/`onError` 回调标记中断；`SseEmitter` 写操作全部在 `boundedElastic` 线程
- [x] T047 [US1] 新建 `.../healthconsultation/support/EmergencyHeaderParser.java`：`ParsedAnswer parse(String first)` 返回 `emergency`/`strippedText`；首行缺失按 `UNAVAILABLE` 处理（US2 复用）
- [x] T048 [US1] 新建 `web/src/api/health-consultation.ts`：7 端点封装，路径 `/v1/health-consultation/...`（**不得重复 `/api`**）；普通请求走 `patientRequest`；流式问答调用 `api/sse.ts` 的 `postSse`（不走 axios），返回 `AbortController` 供「停止生成」；文件头标注契约来源
- [x] T049 [US1] 新建 `web/src/stores/health-consultation.ts`：状态 `sessionId`/`messages`/`streaming`/`streamError`/`retryTarget`；action `send(content)`（乐观上屏 + 逐事件追加 + 失败保留已生成内容）、`retry()`、`stop()`；Pinia 创建时从 `localStorage` 恢复最近 `sessionId`；**禁止在 store 外拼接免责声明文案**
- [x] T050 [US1] 新建 `web/src/composables/use-health-chat-stream.ts`：封装「发送 → 分帧 → 按事件类型分发 → 卸载时 abort」；`accepted` 回填 `messageId`、`delta` 追加、`disclaimer` 存入独立字段、`done` 收尾、`error` 标记可重试；组件卸载/路由离开必须 `abort()` 防止悬挂连接
- [x] T051 [US1] 新建 `web/src/views/health-consultation-view.vue`（含基础对话区：消息列表、输入框 ≤2000 字计数、发送/停止/重试按钮、助手消息底部渲染 `disclaimer`、`streamStatus != COMPLETED` 的「未完成」标注、自动滚动）；行数 ≤ 300，超出部分拆组件
- [x] T052 [US1] 修改 `web/src/router/index.ts` 增加 `/health-consultation`（`name: 'health-consultation'`，`meta: { title: 'AI健康咨询', requiresAuth: true }`），并在 `web/src/views/home-view.vue` 增加首页入口链接（FR-001，未登录由既有守卫跳登录并回跳）
- [ ] T053 [P] [US1] 前端单测 `web/tests/unit/sse-parser.spec.ts`（多行 `data`/心跳/半帧）、`web/tests/unit/health-consultation-store.spec.ts`（事件分发顺序、失败保留内容、重试不清空）、`web/tests/unit/health-consultation-view.spec.ts`（`disclaimer` 来自事件而非硬编码、超长输入拦截）
- [ ] T054 [US1] 执行 US1 独立测试并将结果（含 3 轮对话证据、无就诊记录分支、超时/断开分支）记入 `testsdoc/ai-health-consultation/us1-verification.md`

**检查点**：US1 可独立演示（新建会话 → 多轮流式问答 → 免责声明），`ChatSseContractTest`/`ChatFlowIT`/`health-consultation-chat.mock.spec.ts` 全绿。

---

## Phase 4: User Story 2 - 紧急症状预警与挂号引导（Priority: P2）

**目标**：规则优先 + 模型复核两级判定；判定为紧急时，`emergency` 事件**先于任何 `delta`**，前端显示高对比度提示条并自动弹出挂号引导弹窗（科室预填）；关闭弹窗后提示条保留且可重开；全过程写判定留痕（无病情明文）。

**独立测试**：输入「胸口疼得厉害，喘不上气」→ 提示条 + 弹窗 + 科室预填；输入「有点困」→ 无提示条无弹窗；规则命中但模型否决 → 无提示条但留痕存在；模型不可用 → 仍预警（`UNAVAILABLE`）。

### Tests for User Story 2（强制）⚠️

- [x] T055 [P] [US2] 单测 `.../healthconsultation/unit/EmergencyRuleMatcherTest.java`：多关键词命中、多规则同时命中取 `level` 最高、同 level 按配置顺序、无命中返回空、空关键词规则被跳过
- [x] T056 [P] [US2] 单测 `.../healthconsultation/unit/EmergencyDecisionResolverTest.java`：覆盖 `plan.md` M5 判定矩阵 5 行（`CONFIRMED`/`REJECTED`/`UNAVAILABLE`/`MODEL_ONLY`/无记录），断言 `warned` 取值与「规则未命中且模型 NO 时不写留痕」
- [x] T057 [P] [US2] 单测 `.../healthconsultation/unit/EmergencySymptomServiceTest.java`（Mockito）：规则命中 + 模型 YES → `CONFIRMED`；规则命中 + 模型 NO → `REJECTED` 且 **不触发** 事件；规则命中 + 模型超时 → `UNAVAILABLE` 且触发；规则未命中 + 模型 YES → `MODEL_ONLY`；`booking` 反查失败时为 `null` 但提示条仍展示
- [ ] T058 [P] [US2] 集成测试 `.../healthconsultation/integration/EmergencyFlowIT.java`：紧急样本 → `accepted` 后紧接 `emergency` 且 `emergency` 先于首个 `delta`；`booking.departmentId` 与 `department.name` 匹配；`ai_health_emergency_decision` 恰好 1 行且 `model_verdict`/`warned`/`rule_code` 正确、`message_id` 已回填；断言表内**无患者原话**（对 `content` 与表列做关键词扫描）；普通症状 → 无 `emergency` 事件、无留痕
- [x] T059 [P] [US2] E2E `web/tests/e2e/health-emergency.mock.spec.ts`：M8 → 提示条文案含「立即就医/拨打 120」、弹窗自动打开且科室预填、关闭弹窗后提示条仍在且可重开、点击跳转携带 `departmentId`；普通症状（M7）→ 无提示条无弹窗
- [ ] T060 [P] [US2] E2E `web/tests/e2e/health-emergency.real.spec.ts`：真实后端断言 `emergency` 事件字段与 `code` 值；模型不可用时断言仍收到 `emergency`（`UNAVAILABLE` 分支）

### Implementation for User Story 2

- [x] T061 [US2] 新建 `.../healthconsultation/support/EmergencyRuleMatcher.java`：纯函数，`Optional<EmergencyRule> match(String text)`（大小写/全半角归一化后 `contains`），多命中取 `level` 最高（`HIGH > MEDIUM > LOW`），无命中返回空
- [x] T062 [US2] 新建 `.../healthconsultation/support/EmergencyDecisionResolver.java`：纯函数，输入「规则命中/模型首行/模型是否可用」→ 输出 `EmergencyDecision(modelVerdict, warned, ruleCode)`，严格实现 M5 矩阵（`UNAVAILABLE` 必须 `warned=true`）
- [x] T063 [US2] 新建 `.../healthconsultation/service/EmergencySymptomService.java` + `impl/EmergencySymptomServiceImpl.java`：`EmergencyOutcome evaluate(sessionId, patientText, ParsedAnswer)`；规则匹配 → 结论解析 → 写 `ai_health_emergency_decision`（`model_verdict`/`warned`/`rule_code`，**不写明文**）→ 按 `departmentName` 反查 `department` 组装 `booking`；`MODEL_ONLY` 时 `rule_code='MODEL_ONLY'`
- [x] T064 [US2] 改造 `.../healthconsultation/service/impl/HealthChatServiceImpl.java`：用 `switchOnFirst` 在首元素到达时先 `evaluate`，需要预警则**先发 `emergency`**再发 `delta`；`UNAVAILABLE`/`MODEL_ONLY` 分支必须预警；助手消息落库时写入 `emergency`/`rule_code`，并在事务内回填 `message_id`；预警不改变 `disclaimer`/`done` 的固定顺序
- [x] T065 [US2] 新建 `web/src/components/health-emergency-banner.vue`：高对比度（红底白字、`role="alert"`）、文案含「立即就医/拨打 120」、提供「去挂号」按钮（`emit('book')`）与关闭按钮（仅关闭提示条需二次确认，默认保留）；禁用自行编造文案，`title`/`advice` 取事件字段
- [x] T066 [US2] 新建 `web/src/components/health-booking-dialog.vue`：`props: booking`（`departmentId`/`departmentName`），`booking=null` 时按钮置灰；确认跳转 `/appointment?departmentId=<id>`（复用 002 既有挂号流程），关闭仅隐藏弹窗不回滚提示条
- [x] T067 [US2] 改造 `web/src/views/health-consultation-view.vue`：接收 `emergency` 事件 → 显示 banner + 自动打开 dialog（同一条消息只自动打开一次）；关闭后 banner 的「去挂号」可重开；流式失败时 banner 不消失；视图行数仍 ≤ 300（必要时把消息列表再拆 `health-message-list.vue`）
- [ ] T068 [US2] 前端单测 `web/tests/unit/health-emergency-banner.spec.ts`、`web/tests/unit/health-booking-dialog.spec.ts`：banner 文案来自事件、`booking=null` 置灰、关闭后重开、`emergency` 早于 `delta` 时 UI 顺序正确（提示条在回答上方）

**检查点**：US1 + US2 可独立演示；20 条紧急样本召回 100%（`EmergencyFlowIT` 参数化），20 条普通样本误报 ≤ 10%。

---

## Phase 5: User Story 3 - 多对话管理（Priority: P3）

**目标**：会话列表（最近活跃倒序、含最后消息摘要）、新建、切换、重命名、删除单会话、清空全部历史；PC 侧栏 / 手机抽屉；切换会话上下文互不串扰；越权一律「会话不存在」。

**独立测试**：新建 2 个会话分别提问不同主题，刷新后两个会话与消息完整恢复且顺序正确；切换后上下文不串扰；删除后访问返回 404；清空全部后列表为空、刷新仍为空、Redis 键已删。

### Tests for User Story 3（强制）⚠️

- [ ] T069 [P] [US3] 契约测试 `.../healthconsultation/contract/SessionManagementContractTest.java`：端点 1（分页、`lastMessagePreview` 为 null 的分支、倒序）、端点 3（`SESSION_TITLE_INVALID` 空/超 64、越权 404）、端点 4（`deleted=true`、重复删除 404）、端点 5（`deletedCount`）、全部缺 CSRF 403；`message` 逐字取自契约
- [ ] T070 [P] [US3] 集成测试 `.../healthconsultation/integration/SessionManagementFlowIT.java`：多会话切换上下文不串扰（B 的回答不含 A 关键词）；删除后消息不再可读（404）且 Redis `ctx`/`meta` 键已删；清空后 `deletedCount` 正确、`ai_health_message` 物理清零、Redis 键全删、审计 `AI_HEALTH_HISTORY_CLEARED` 已写且不含内容；他人会话 4 个端点全部 404 并写 `AI_HEALTH_ACCESS_DENIED`
- [ ] T071 [P] [US3] E2E `web/tests/e2e/health-session-management.mock.spec.ts`：列表倒序、新建进入空白、切换加载各自消息、重命名即时生效、删除后从列表消失、清空二次确认后为空；手机视口（390×844）下为抽屉、PC（1280×800）下为侧栏
- [ ] T072 [P] [US3] E2E `web/tests/e2e/health-session-management.real.spec.ts`：真实后端断言 4 端点 `code` 值与越权 404

### Implementation for User Story 3

- [x] T073 [US3] 扩展 `.../healthconsultation/service/HealthSessionService.java` + `impl`：`list(patientId, page, pageSize)`（按 `last_active_at DESC, id DESC`，摘要取 `findLastBySessionId` 前 40 字）、`rename(patientId, sessionId, title)`（1–64 字符、乐观锁冲突返回 `SESSION_TITLE_INVALID` 或重试）、`delete(patientId, sessionId)`（逻辑删除 + 清 Redis + 释放锁）、`clearAll(patientId)`（同一 `@Transactional`：`markAllDeleted` + 物理删消息 + 事务提交后清 Redis + 写审计，只记数量）
- [x] T074 [US3] 扩展 `.../healthconsultation/controller/HealthSessionController.java`：`GET /sessions`、`PATCH /sessions/{sessionId}`、`DELETE /sessions/{sessionId}`、`DELETE /sessions`；分页参数校验与统一响应结构按契约
- [x] T075 [US3] 扩展 `web/src/api/health-consultation.ts`：新增 4 个管理端点方法（走 `patientRequest`，路径无重复 `/api`），类型引用 T002 定义
- [x] T076 [US3] 扩展 `web/src/stores/health-consultation.ts`：`sessions`/`activeSessionId`/`drawerOpen`；action `loadSessions`/`createSession`/`switchSession`/`renameSession`/`deleteSession`/`clearAll`；切换会话必须**重置流式状态并 abort 上一条连接**（防串扰）
- [x] T077 [US3] 新建 `web/src/components/health-session-list.vue`：PC 侧栏 / 手机抽屉（`<details>` 或受控 `drawerOpen`）、按 `lastActiveAt` 倒序、标题 + 摘要 + 相对时间、行内重命名（回车保存/ESC 取消）、删除与「清空全部」二次确认；无会话时展示「开始新对话」空态
- [x] T078 [US3] 改造 `web/src/views/health-consultation-view.vue`：接入 `health-session-list.vue`，新建/切换/删除联动路由 `?sessionId=`（刷新可恢复），清空后回到空态；保证视图仍 ≤ 300 行
- [ ] T079 [P] [US3] 前端单测 `web/tests/unit/health-session-list.spec.ts`、`web/tests/unit/health-session-switch.spec.ts`：倒序渲染、重命名校验（空/超 64）、切换时 abort 旧流且消息不清空、抽屉断点切换
- [ ] T080 [US3] 执行 US3 独立测试（含刷新恢复、重登恢复、清空不可恢复证据）记入 `testsdoc/ai-health-consultation/us3-verification.md`

**检查点**：US1–US3 全部可独立演示；3 个故事的 Playwright mock 用例全绿。

---

## Phase 6: Polish & Cross-Cutting Concerns（收口）

- [ ] T081 [P] 覆盖率补齐：对 `healthconsultation/` 未覆盖分支补单测（目标行覆盖率 ≥ 70%，`mvn -o verify` 的 jacoco `check` 通过），重点补 `HealthChatServiceImpl` 的失败/中断分支与 `RedisDialogStore` 降级分支
- [ ] T082 [P] SpotBugs 豁免口径扩展：把 `com.sdas.server.healthconsultation.*` 加入 `server/config/spotbugs/spotbugs-exclude.xml`（`EI_EXPOSE_REP2`/`OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE`，理由同既有包），文件注释登记 005
- [ ] T083 [P] 静态检查：`mvn -o checkstyle:check` 零违规（新类 Javadoc 齐全、圈复杂度 ≤ 10）；`cd web && npm run typecheck && npm run lint && npm run format:check` 零错误零警告
- [ ] T084 [P] 契约一致性验证：逐项比对 `contracts/ai-health-consultation-api.md` 与真实响应（7 端点、SSE 事件名与顺序、`code` 值、字段名/类型、`yyyy-MM-dd'T'HH:mm:ss`、分页结构、免责声明逐字），结果写 `testsdoc/ai-health-consultation/contract-conformance.md`；同步校验 `contracts/mocks.md` 与 `web/src/api/mocks/health-consultation.ts` 一致
- [ ] T085 [P] 安全自查：全文检索确认日志/审计/`ai_health_emergency_decision` 无病情明文、无令牌、无手机号/身份证；确认 `MedicalContext` 不回传前端；确认前端免责声明只读事件字段（grep 文案出现位置仅 `HealthDisclaimer` 与 mock/测试）；确认越权 4 类入口均 404
- [ ] T086 [P] 性能抽测（结果记入 `testsdoc/ai-health-consultation/test-report.md`）：SSE 首个可见字符 P95 ≤ 1.5 s（SC-001）、上下文组装 ≤ 100 ms、知识检索 ≤ 50 ms、单会话并发 50 路不串话；Redis 断开场景复测（对话仍可用，仅窗口降级）
- [ ] T087 执行 `quickstart.md` 第 1–4 节全部步骤（含 5 项常见问题反向验证），结果记入 `testsdoc/ai-health-consultation/manual-qa-checklist.md`
- [ ] T088 全量回归：`cd server && IDENTITY_TEST_USE_LOCAL_INFRA=true mvn -o verify`；`cd web && npm run test && npx playwright test`（mock 全绿，real 用例按需跳过）
- [x] T089 测试报告归档 `testsdoc/ai-health-consultation/test-report.md`（后端/前端/静态检查/覆盖率/相关文档 5 节，参照 `testsdoc/patient-identity/test-report.md` 格式）
- [ ] T090 代码审查（宪法 V）：按 001–004 的 `code-review-security.md` 口径评审（模型出口隔离、上下文只读与归属、Redis 3.2 命令白名单、锁释放、免责声明强制点、SSE 中断处理），结论落 `testsdoc/ai-health-consultation/code-review-security.md`；同步更新 `plan.md` 实施状态与 `contracts/` 一致性清单

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup（T001–T007）**：无前置，可立即开始。
- **Phase 2 Foundational（T008–T033）**：依赖 Phase 1；**阻塞全部用户故事**。
- **Phase 3 US1（T034–T054）**：依赖 Phase 2。
- **Phase 4 US2（T055–T068）**：依赖 Phase 2 + T045/T047/T046（流式编排与控制器）；可与 US3 并行。
- **Phase 5 US3（T069–T080）**：依赖 Phase 2（T015/T016/T018）；与 US2 无文件冲突。
- **Phase 6 收口（T081–T090）**：依赖 US1–US3 全部完成。

### 关键任务依赖（易错点）

| 任务 | 依赖 | 说明 |
|------|------|------|
| T008 规格修订 | — | 必须先改文档再编码，否则规则字段与 V9/V10 编号全链路返工 |
| T013 V10 预置 | T012（V9 建表） | 知识条目 FK 指向 `department`，须先确保兜底科室存在 |
| T018 Redis 热窗口 | T012（表结构） | miss 重建依赖 `ai_health_message` 列与索引 |
| T025/T026 配置 | T008 | `departmentName`/`keywords` 字段以修订后为准 |
| T028 Client 实现 | T027、T025 | 超时配置来自 `HealthProperties` |
| T029 上下文组装 | T020–T024 | 任一只读查询缺失即无法聚合 |
| T031 Prompt 组装 | T026、T029、T030、T025 | 首行协议与依据有限提示必须同源 |
| T045 流式编排 | T015–T019、T029–T032 | 顺序：落库 → accepted → 窗口 → 组装 → 流式 → 落库 → disclaimer → done → afterCommit |
| T046 SSE 控制器 | T032、T045 | 事件写出统一走 `SseEventWriter`，禁止在 Controller 拼 JSON |
| T064 预警集成 | T061–T063、T047 | `emergency` 必须先于首个 `delta`（`switchOnFirst`） |
| T067 视图集成 | T063、T065、T066、T051 | 同一条消息只自动弹窗一次 |
| T073 清空历史 | T016、T018 | 事务内 DB，提交后删 Redis，避免回滚后键已丢 |

### 用户故事依赖

- **US1（P1）**：Phase 2 完成后即可开始，无其他故事依赖 → **MVP**。
- **US2（P2）**：复用 US1 的流式通道与首行解析，但规则、判定、留痕与两个组件完全独立；可用 `@MockitoBean HealthChatClient` 独立测试。
- **US3（P3）**：只读列表与 CRUD，独立于 US2；可用预置会话夹具独立测试。

### 并行机会

- Phase 1 的 T002–T007 六路并行（不同文件）。
- Phase 2 的 T009–T012、T014–T024 可并行；T013 紧跟 T012；T025→T026→T031、T027→T028、T029/T030 可三路并行；T033 收口。
- US1 内 T034–T042（测试）全并行；T048/T050/T051/T052 与 T043–T047 可并行（前后端不同仓库路径）。
- US2 与 US3 在 US1 之后可双人并行（前端组件与 `health-session-list.vue` 不重叠；后端 `EmergencySymptomService` 与 `HealthSessionService` 不重叠）。

---

## MVP 与交付策略

1. **MVP（US1）**：Phase 1 + Phase 2 + Phase 3 → 患者可流式多轮问答并获得免责声明，即具备核心价值。
2. **增量 2（US2）**：Phase 4 → 安全兜底闭环（预警 + 挂号引导），上线前必须完成（涉及患者安全）。
3. **增量 3（US3）**：Phase 5 → 多对话管理，长期使用体验完整。
4. **收口**：Phase 6 全绿后交付；`mvn -o verify` 与 `npx playwright test` 必须全绿。

---

## 需求覆盖矩阵（FR → 任务）

| 需求 | 任务 |
|------|------|
| FR-001 首页入口 + 未登录回跳 | T052、T041 |
| FR-002 多轮上下文连贯 | T018、T029、T031、T045、T040 |
| FR-003 只引用本人就诊信息 | T020–T022、T029、T036 |
| FR-004 知识库复用 + 依据有限 | T023、T030、T031、T037 |
| FR-005 每条回答含免责声明 | T019、T032、T045、T051、T034 |
| FR-006 紧急提示条 | T064、T065、T058、T059 |
| FR-007 弹窗自动弹出 + 科室预填 | T063、T066、T067、T058 |
| FR-008 关闭弹窗后提示条可重开 | T065、T067、T059 |
| FR-009 新建对话 | T043、T044、T076 |
| FR-010 列表/切换/重命名/删除 | T069、T073、T074、T077 |
| FR-011 归属隔离（越权 404） | T015、T016、T045、T073、T070 |
| FR-012 超时/不可用降级 | T028、T045、T039、T040、T085 |
| FR-013 长度与条数上限 | T009、T045、T034、T053 |
| FR-014 审计且无明文 | T011、T063、T070、T085 |
| FR-015 不给处方性结论 | T031、T038 |
| FR-016 PC/手机双端 | T077、T051、T071 |
| FR-017 长期保留 + 清空不可恢复 | T016、T018、T073、T070、T080 |
| FR-018 规则优先 + 模型复核 + 留痕 | T061、T062、T063、T058 |
| FR-019 SSE 流式 + 中断保留内容 | T027、T028、T032、T046、T050 |
| FR-020 失败以 `error` 明确结束 | T028、T045、T034、T040 |

## Edge Cases / 风险项 → 测试任务（对应 `spec.md` Edge Cases）

| 风险项 | 测试任务 |
|--------|----------|
| 单条输入超长（粘贴整段病历） | T034、T040、T053 |
| AI 超时/限流/不可用降级 | T035、T040、T058、T086 |
| 同轮含紧急症状与普通咨询（预警优先） | T055、T058、T059 |
| 多标签页同会话并发 | T040（409 `CHAT_BUSY`）、T070 |
| 会话删除后其他标签页仍发送 | T070、T040（404） |
| 越权访问他人会话 | T034、T069、T070 |
| 紧急误判（过度/漏判） | T055、T056、T057、T058 |
| 与医疗无关的闲聊/越界请求 | T038（prompt 边界断言）、T042 |
| 未登录/未完成认证 | T034（401）、T041（守卫回跳） |
| 轮次过多导致上下文超长 | T031、T038、T036 |
| 规则命中但模型否决（不预警仍留痕） | T056、T057、T058 |
| 流式中途断开（网络/重启/主动停止） | T040、T050、T053 |
| 另一端清空历史时旧页面仍在使用 | T070、T079 |

---

## Notes

- `[P]` 表示不同文件、无未完成依赖；同文件任务必须串行。T012→T013 虽分属两个迁移文件，但 V10 引用 V9 的表结构，**必须按序**；`T015`–`T017` 若同批次修改 `package-info.java` 亦需串行。
- 测试任务必须**先写并确认失败**，实现后再转绿；禁止先写实现再补测试。
- 每完成一个用户故事即执行其「独立测试」，通过后才进入下一优先级。
- 契约或数据模型如需变更，先改 `specs/005-ai-health-consultation/{data-model.md,contracts/}`，再改代码与测试，禁止反向同步（T008 即为此规则的首次执行）。
- Redis 一律只用 `String`/`List`/`Hash` 与 `EXPIRE`/`LTRIM`/`WATCH`/`MULTI`；出现 `UNLINK`/`XAUTOCLAIM`/Streams 即视为违规（T090 复核）。
- Mock E2E 用 `page.route()` 并复用 `contracts/mocks.md` 的逐帧 SSE 字符串；真实后端 E2E 用 `E2E_REAL_BACKEND=1` 且断言 `code` 值。
- **已知缺口（不阻塞本期）**：`department_disease_kb` 无「康复/用药」专用列，知识条目以 `disease_name` 承载一句话要点；若规模突破 200 条或需结构化字段，另立功能补表。
