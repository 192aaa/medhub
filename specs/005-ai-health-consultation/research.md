# Phase 0 研究：AI健康咨询

**输入**：[spec.md](./spec.md)（含 2026-11-08 澄清）· [plan.md](./plan.md)
**结论**：8 项决策全部闭合，无残留 `NEEDS CLARIFICATION`；零新增第三方依赖。

---

## R-01 AI 调用形态（闭环宪法「解释与待决项」）

**问题**：宪法原则 I 原文同时出现「DeepSeek 官方 API」与「本地部署」，且要求不得同时实现两套链路。

**决策**：**仅实现 DeepSeek 官方 API**（OpenAI 协议兼容），模型 `deepseek-v4-flash`，经既有 `spring-ai-starter-model-openai` 接入；**不实现**本地部署链路。

**理由**：
1. 既有 002/003 已按 OpenAI 兼容协议接入（`AiDecisionClientImpl` 使用 `ChatClient` + `OpenAiChatOptions`），本功能沿用可零成本复用连接、超时与观测配置。
2. 流式输出（SSE 逐字）在官方 API 上为原生能力；本地部署需额外保障流式增量与并发，收益不抵成本。
3. 单一链路满足宪法「不得同时实现两套」的硬约束。

**被否决方案**：① 同时实现两条链路（违反宪法）；② 本地部署（需额外 GPU/模型运维，且流式稳定性无保障）。

**风险**：API 抖动 → 见 plan「风险与缓解」；`model` 通过 `sdas.ai.health.model` 可配置，切换供应商只需改配置与评审记录。

---

## R-02 流式输出实现：MVC `SseEmitter`（不引入 WebFlux）

**问题**：契约要求 SSE 逐字输出，如何在不新增依赖前提下实现。

**决策**：Controller 返回 `SseEmitter`（`spring-boot-starter-web` 原生），Service 订阅 Spring AI `Flux<String>` 并逐块 `emitter.send(SseEmitter.event()...)`；完成/失败分别发 `done`/`error` 并 `emitter.complete()` / `completeWithError()`。

**理由**：
1. 工程为 Spring MVC（非 WebFlux），引入 WebFlux 会与原 MVC 自动配置冲突（宪法 G1 需架构评审），`SseEmitter` 零依赖达成同等效果。
2. reactor 已随 Spring AI 进入 classpath，`Flux` 可直接消费，无需新增依赖。
3. MockMvc 对 `SseEmitter` 有 `request().asyncStarted()` + 输出流断言支持，便于写 SSE 事件顺序测试。

**被否决方案**：① 返回 `Flux<ServerSentEvent>`（MVC 支持但事件控制粒度弱、测试断言困难）；② 前端轮询（首字延迟高，违背 SC-001）；③ WebSocket（需网关与心跳改造，成本过高）。

**约束**：`emitter` 超时设 `timeout-millis + 2000`；异步线程池使用既有 `ThreadPoolTaskExecutor`，禁止无界队列。

---

## R-03 模型调用封装：`HealthChatClient` 接口隔离

**问题**：如何避免 Spring AI 类型（`ChatClient`、`Flux`、`ChatResponse`）穿透到 Controller 与业务层，且便于降级与测试。

**决策**：
- 接口 `support/HealthChatClient`：`Flux<String> stream(HealthChatCommand command)`，`HealthChatCommand` 为自有 record（systemPrompt、turns、maxTokens、model）。
- 实现 `HealthChatClientImpl`：构造注入 `ChatClient.Builder`（复用 002 自动配置），`defaultOptions` 设 model/maxTokens，内部 `chatClient.prompt().system(...).messages(...).stream().content()`。
- 上层（`HealthChatService`）只依赖接口，可 Mock 为「固定增量序列 + 超时 + 异常」三种桩，覆盖 FR-012/FR-019/FR-020。
- 超时：首包超时与总超时分别由 `sdas.ai.health.first-token-timeout-millis`、`timeout-millis` 控制；**不在 Client 内做降级文案**，只发错误信号，由 Service 决定 `error` 事件。

**理由**：与既有 `AiDecisionClient` / `TriageClient` 的接口-实现分离保持一致；Controller 完全不 import 模型库。

---

## R-04 病历上下文获取机制

**问题**：如何「参考患者就诊信息」而不泄露、不超预算、不越权。

**决策**：新增 `support/MedicalContextAssembler`，签名 `MedicalContext assemble(long patientId)`：
1. 取该患者最近 **3** 次 `consultation`（`status='COMPLETED'`），按 `created_at DESC`；
2. 对每次取对应 `prescription`（`status='SIGNED'`，一咨询一处方）：药品名、规格、用法用量、注意事项（`prescription_item.caution`）；
3. 生成 `summaryText`（纯文本、中文、按「时间 → 主诉 → 用药/注意」组织），**不含**手机号、身份证、真实姓名（库内已是脱敏名）；
4. 字符预算 `sdas.ai.health.context-medical-max-chars`（默认 1500）：超限按「最近优先」裁剪，并置 `truncated=true`；
5. 输出 `MedicalContext(summaryText, referencedConsultationIds, truncated)`。

**理由/约束**：
- 所有查询**强制带 `patient_id`**（Repository 方法签名保证），越权在数据层即不可达（SC-004）。
- 上下文**只进 prompt**：不回传前端、不写日志、不进 Redis（避免二次泄露面）。
- 只读：不新增对 002/003 表的写操作（宪法 G2 跨模块只读）。

**被否决方案**：① 把病历全文塞进 prompt（超预算、泄露面大）；② 让前端传病史（可伪造、不可信）。

---

## R-05 医学知识库配置（预置医疗 + 医院信息知识）

**问题**：知识从哪来、怎么检索、医院信息放哪。

**决策**：
1. **知识库表**：复用 002 的 `department_disease_kb`（`department_id`、`disease_keyword`、`disease_name`、`source`、`kb_version`），**不新增表**；
2. **预置数据**：新增迁移 `V9`，以 `source='PRESET'`、`kb_version='2026.11'` 写入首版知识（覆盖：发热/咳嗽/腹泻/头痛/皮疹/高血压/糖尿病等常见症状与处置要点、用药注意、康复指导、就医指征），**同时写入医院信息知识**（就诊流程、科室位置、检验检查须知、医保/挂号说明）；
3. **检索**：`support/KnowledgeRetriever` 关键词匹配（对患者消息做分词式切分 + `disease_keyword LIKE`），Top-K=5，返回条目 + `kb_version`；检索结果拼入 prompt 的「知识依据」段；
4. **医院结构化信息**（名称/地址/总机/工作时间/急诊电话）走 `sdas.hospital.*` 配置，供回答与页面直接展示，**不放知识库**（结构化字段不适合关键词表）；
5. **缺口声明**：命中为空时，prompt 明确要求模型「说明依据有限，不得编造来源」（FR-004）。

**理由**：宪法要求「先复用、无必要不加表」；V9 预置满足「当前阶段可预置部分医疗知识与医院信息知识」。**已知缺口**：该表无「康复指导/用药注意」专用列，本期用 `disease_name` 承载一句话要点，若后续知识规模扩大，另立功能补表（在 `tasks.md` 登记观察项）。

**被否决方案**：① 新建独立知识表（本期内容量小，重复建设）；② 医院信息入表（地址/电话属于配置而非知识，改一次要发版迁移，成本更高）。

---

## R-06 多轮对话管理：MySQL 真值源 + Redis 3.2 热窗口

**问题**：多轮上下文如何维护，且满足「长期保留 + 刷新恢复 + 手动清空」。

**决策**：
- **MySQL**（真值源）：`ai_health_session`、`ai_health_message` 全量持久化，长期保留；「清空全部历史」= 逻辑删除会话 + 物理删除消息（或统一 `status='DELETED'` 过滤），并写审计。
- **Redis 热窗口**：
  - `ai:health:ctx:{sessionId}` → **List**，`RPUSH` 追加消息摘要（`role|content`，单条截断 500 字符），`LTRIM -12 -1` 保留最近 12 条，`EXPIRE 86400`；
  - `ai:health:meta:{sessionId}` → **Hash**（`patientId`、`title`、`lastActiveAt`、`messageCount`），`EXPIRE 86400`；
  - `ai:health:lock:{sessionId}` → `SET NX EX 60`，防同会话并发两路生成（冲突返回 `CHAT_BUSY`）。
- **miss 回填**：Redis 无窗口数据时，从 MySQL 取最近 12 条重建，保证刷新/换实例后仍连续（SC-007）。
- **版本约束**：Redis 3.2.100 **禁止** Streams/XAUTOCLAIM/UNLINK；只用 List/Hash/String + `LTRIM`/`EXPIRE`/`SET NX EX`。

**一致性**：先落 MySQL（`@Transactional`）再写 Redis；Redis 写失败仅降级为「下一轮重建」，不影响回答（记录 WARN，不含明文）。

**被否决方案**：① 仅 Redis（TTL 到期丢历史，违反 SC-007/SC-010）；② 仅 MySQL（每轮全量拼装，首字时延不达标）；③ Redis Streams（3.2 不支持）。

---

## R-07 紧急症状识别与预警

**问题**：既要 100% 召回，又要控制误报且可解释。

**决策**：两级链路
1. **规则层** `support/EmergencyRuleMatcher`：规则表由 `sdas.ai.health.emergency.rules` 配置（含 `code`、`name`、`keywords`、`level`、`departmentCode`），内置默认值兜底（配置缺失不失效）。首版覆盖：胸痛/胸闷伴呼吸困难、意识障碍、抽搐、大出血、剧烈腹痛、过敏性休克征象、卒中征象（口角歪斜/单侧无力/言语不清）、高热惊厥、自杀/自伤倾向。命中即得 `ruleCode`。
2. **模型复核层**：同一次生成调用中，system prompt 要求模型**首行**输出 `EMERGENCY: YES|NO`，随后正文。解析首行：
   - 规则命中 + 模型 `YES` → 预警（`warned=true`）；
   - 规则命中 + 模型 `NO` → 不预警，但写判定记录（`model_verdict='REJECTED'`），供规则优化；
   - 规则命中 + 模型超时/异常 → **仍预警**（`model_verdict='UNAVAILABLE'`，安全优先）；
   - 规则未命中 + 模型 `YES` → 预警并记录（`rule_code='MODEL_ONLY'`）。
3. **留痕**：`ai_health_emergency_decision`（会话/消息、`rule_code`、`model_verdict`、`warned`、时间），满足 SC-008；**不存病情明文**。
4. **前端表现**：`emergency` SSE 事件先于 `delta`；提示条常驻，弹窗可关闭且可从提示条重开（FR-008）。

**被否决方案**：① 纯规则（同义表述漏判）；② 纯模型（不可复现、无规则可解释）；③ 规则命中直接预警不复核（误报率高，骚扰患者）。

---

## R-08 免责声明

**决策**：服务端常量 `HealthDisclaimer.TEXT = "仅供参考，不构成医疗诊断"`：
- SSE 在 `delta` 结束后发送 `disclaimer` 事件；
- 落库时把声明作为助手消息的独立字段（`disclaimer`），不拼进 `content` 正文（避免模型重复生成或前端误删）；
- 契约与前端只渲染 `disclaimer` 字段，**禁止**前端硬编码拼接；
- 测试断言：任何助手消息响应都含该字段且文案逐字一致（SC-002）。

**理由**：满足用户明确要求与本功能安全边界；单一来源避免多端文案漂移（宪法 G3 契约唯一）。
