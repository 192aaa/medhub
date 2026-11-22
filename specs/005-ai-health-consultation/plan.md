# Implementation Plan: AI健康咨询

**Branch**: `005-ai-health-consultation` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-ai-health-consultation/spec.md`

## Summary

为患者提供「AI健康咨询」：以流式（SSE）多轮对话回答医学问题，回答时**只读**引用该患者既往问诊/处方信息与既有医学知识库，识别到紧急症状时先发高对比度预警事件并按上下文推送挂号弹窗，全部会话与消息持久化、支持多对话管理与手动清空。

技术路线（详见 [research.md](./research.md)）：
1. **模型调用封装**：新增 `HealthChatClient` 接口 + `HealthChatClientImpl`（复用既有 Spring AI `ChatClient` + DeepSeek 官方 API，OpenAI 协议兼容），流式返回 `Flux<String>`；Service 订阅后写入 `SseEmitter`，Controller 只返回 emitter，禁止跨层直接依赖 Spring AI。
2. **病历上下文**：`MedicalContextAssembler` 按 `patientId` 只读聚合最近 3 次已完成问诊 + 对应已签名处方与用药注意事项，生成带**字符预算**的摘要文本，超预算按「最近优先」截断并置 `truncated`。
3. **知识库**：复用 002 的 `department_disease_kb`（只读，关键词 Top-K 检索），并新增 **V9 预置迁移**写入医疗知识与医院信息知识条目（`source='PRESET'`）；医院结构化信息（名称/地址/电话/工作时间）走 `sdas.hospital.*` 配置，不塞进知识库。
4. **多轮上下文**：MySQL 为真值源（长期保留），Redis 仅存**热窗口**（最近 12 条 + 会话元信息，TTL 24h，List/Hash 结构，**不使用 Redis 5+ 特性**），miss 时从 DB 回填。
5. **紧急预警**：规则表（`sdas.ai.health.emergency.rules` 配置，内置默认值）先命中，再交模型复核；模型超时/失败时**按规则命中即预警**（安全优先），全过程写 `ai_health_emergency_decision`。
6. **免责声明**：服务端常量「仅供参考，不构成医疗诊断」，以 SSE `disclaimer` 事件发送并随消息落库；前端只渲染、禁止自行拼接。

## Technical Context

**Language/Version**：Java 17（LTS）；前端 TypeScript + Vue 3.4+

**Primary Dependencies**：Spring Boot 3.4.5（`spring-boot-starter-web` MVC，非 WebFlux）、Spring AI 1.0.0（`spring-ai-starter-model-openai`，DeepSeek 官方 API 走 OpenAI 兼容协议）、`spring-boot-starter-data-redis`（Lettuce + `commons-pool2`）、JdbcTemplate、zxing（既有）。**本功能不新增任何第三方依赖**：SSE 用 MVC 原生 `SseEmitter`，流式消费用 Spring AI 已引入的 reactor `Flux`。

**Storage**：MySQL 8.0（`cloud_consultation` / 测试库 `cloud_consultation_test`，Flyway 强制启用，test/prod 同一套脚本）；Redis **3.2.100**（宪法「Redis 3.2.1x」在本项目落地版本，**禁止使用 Redis 5+ 特性**：不用 Streams/XAUTOCLAIM/UNLINK，仅 String/List/Hash + EXPIRE/LTRIM）；测试环境固定 `database: 1`。

**Testing**：JUnit5 + Mockito（Service/纯函数单测）、Spring Boot Test + MockMvc（契约与 SSE 事件顺序）、Testcontainers（mysql:8.0 / redis:3.2-alpine）或 `IDENTITY_TEST_USE_LOCAL_INFRA=true` 本机实例；前端 Vitest + Playwright（`page.route()` Mock E2E + 真实后端 E2E 断言响应 `code`）。

**Target Platform**：Linux 服务器（后端）/ 浏览器 PC + 移动端响应式（前端）

**Project Type**：web-service（前后端分离，`server/` + `web/`）

**Performance Goals**：SSE 首个可见字符 P95 ≤ 1.5s；单会话单轮上下文组装 ≤ 100ms；知识库关键词检索 ≤ 50ms（10 万行内）；并发流式连接按 Tomcat 线程数上限设计，单实例 ≥ 50 路并发对话。

**Constraints**：AI 调用超时 `sdas.ai.health.timeout-millis`（默认 8000ms，流式首包 ≤ 3000ms）；上下文总字符预算 `context-max-chars`（默认 6000，其中病历摘要 ≤ 1500）；单条患者输入 ≤ 2000 字符、≤ 20 轮/会话热窗口；日志与审计禁止出现病情明文；连续两次同会话并发提问按「后到者排队/拒绝」处理。

**Scale/Scope**：单院场景，日活患者 1 万以内；每人平均 3 个会话、每会话 ≤ 20 条消息；3 张新表 + 1 个迁移（V9 预置知识）+ 7 个 REST 端点（含 1 个 SSE）。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

依 `.specify/memory/constitution.md`（v1.0.0）逐项校验：

- **G1 技术栈与依赖**：通过。仅使用 Java 17 / Spring Boot 3.4.5 / Spring AI / MySQL 8 / Redis 3.2.100 / Vue 3.4；**零新增依赖**（SSE 用 MVC 自带 `SseEmitter`，流式用 Spring AI 已带的 reactor）。AI 形态闭环见 [research.md](./research.md) R-01：**仅实现 DeepSeek 官方 API 一条链路**。
- **G2 分层与目录**：通过。新增包 `server/.../healthconsultation/{controller,service,service/impl,repository,entity,model,model/converter,dto,support}`；Controller → Service → Repository 严格单向；患者身份统一 `PatientIdExtractor`；前端 `patientRequest` 独立实例（禁止新 axios 实例）；路由使用新词干 `/health-consultation`，与既有 `/consultation/*`、`/appointment/consultation*` 不冲突；Pinia store 创建时从 `localStorage` 恢复。
- **G3 契约与 API**：Phase 1 产出 `contracts/ai-health-consultation-api.md`（7 端点 + SSE 事件表）与 `data-model.md`（4 枚举 + 3 表）；统一响应 `{code,message,data}`、分页 `{list,total,page,pageSize}`、`LocalDateTime` ISO 8601；SSE 端点按事件流单独定义（**不使用**统一响应包裹）。
- **G4 安全**：通过。`PatientIdExtractor` + 会话归属校验（越权一律 `SESSION_NOT_FOUND`）；日志与审计脱敏（病情明文不入日志，只存规则码与消息 ID）；免责声明服务端强制追加；错误 message 全部落 `ErrorCode`。
- **G5 代码规范**：通过。Checkstyle + SpotBugs 零告警；枚举统一 `SafeEnumConverter`（不并存 `@Enumerated`）；新增枚举值须全局搜索 switch/if-else。
- **G6 测试门禁**：通过。Service 覆盖率 ≥ 70%（新增纯函数与编排均有单测）；7 个端点正向 + 异常契约测试；SSE 事件顺序测试；越权/清空/超时降级/规则误判 4 类风险项映射为用例；Playwright 覆盖 US1–US3；报告落 `testsdoc/ai-health-consultation/`；契约一致性验证纳入迭代。

**结论**：无阻断项，G1–G6 全部通过；`Complexity Tracking` 仅登记 2 项设计取舍（非违规）。

## Project Structure

### Documentation (this feature)

```text
specs/005-ai-health-consultation/
├── plan.md              # 本文件
├── spec.md              # 规约（含 Clarifications）
├── research.md          # Phase 0：8 项技术决策
├── data-model.md        # Phase 1：枚举 + 3 张表 + 只读依赖
├── quickstart.md        # Phase 1：本地起服务/迁移/自检
├── contracts/
│   ├── ai-health-consultation-api.md
│   └── mocks.md
└── tasks.md             # Phase 2（/speckit.tasks 生成，非本命令产出）
```

### Source Code (repository root)

```text
server/src/main/java/com/sdas/server/healthconsultation/
├── controller/          # HealthConsultationSessionController、HealthChatController（SSE）
├── service/             # HealthConsultationService、HealthChatService、EmergencySymptomService
│   └── impl/
├── repository/          # HealthSessionRepository、HealthMessageRepository、EmergencyDecisionRepository
├── entity/              # HealthSession、HealthMessage、EmergencyDecision
├── model/               # MessageRole、SessionStatus、StreamStatus、EmergencyVerdict
│   └── converter/
├── dto/                 # 请求/响应 + SSE 事件载荷
└── support/             # HealthChatClient(Impl)、MedicalContextAssembler、KnowledgeRetriever、
                         # EmergencyRuleMatcher、HealthPromptBuilder、SseEventWriter、RedisDialogStore

server/src/main/resources/
├── application.yml                            # sdas.ai.health.* / sdas.hospital.*
├── db/migration/V9__ai_health_consultation_schema.sql    # 3 张表
└── db/migration/V10__ai_health_knowledge_preset.sql      # 58 条知识预置 + 兜底科室

web/src/
├── api/health-consultation.ts                 # 唯一请求出口（SSE 用 fetch + ReadableStream）
├── api/types.ts                               # 由 contracts 同步的类型
├── api/mocks/health-consultation.ts
├── stores/health-consultation.ts              # 会话列表/当前会话/流式状态
├── views/health-consultation-view.vue         # 列表 + 对话（PC 侧栏 / 手机抽屉）
├── components/health-emergency-banner.vue     # 紧急提示条
├── components/health-booking-dialog.vue       # 挂号引导弹窗
└── router/index.ts                            # /health-consultation

server/src/test/java/com/sdas/server/healthconsultation/{unit,contract,integration}/
web/tests/                                     # Vitest + Playwright
testsdoc/ai-health-consultation/
```

**Structure Decision**：沿用宪法固定布局；后端新功能独立包 `healthconsultation`（与 `doctor`/`pharmacy` 平级），避免与 002 导诊、003 医生辅助决策混淆；前端对话页为单视图 + 2 个受控组件，控制在 300 行内（组件拆分理由见 Complexity Tracking）。

## Phase 0：研究与决策摘要

完整内容见 [research.md](./research.md)，关键结论：

| 编号 | 决策 | 结论 |
|------|------|------|
| R-01 | AI 调用形态（宪法待决项） | **仅 DeepSeek 官方 API**（OpenAI 兼容），不实现本地部署链路；模型 `deepseek-v4-flash` |
| R-02 | 流式实现 | MVC `SseEmitter` + Spring AI `stream()`，**不引入 WebFlux** |
| R-03 | 模型调用封装 | `HealthChatClient` 接口隔离 Spring AI；Controller 不感知模型库 |
| R-04 | 病历上下文 | 只读聚合 + 字符预算 + 最近优先截断；不落库原文、不回传前端 |
| R-05 | 知识库 | 复用 `department_disease_kb`，V9 预置医疗/医院信息知识；医院结构化信息走配置 |
| R-06 | 多轮上下文 | MySQL 真值源 + Redis 3.2 热窗口（List/Hash，TTL 24h，容量 12） |
| R-07 | 紧急识别 | 规则先命中 → 模型复核；模型不可用时按规则预警（安全优先） |
| R-08 | 免责声明 | 服务端常量 + SSE `disclaimer` 事件 + 落库，前端仅渲染 |

## Phase 1：设计与契约

- **数据模型**：[data-model.md](./data-model.md)（4 个枚举、3 张新表、5 张只读依赖表、Redis key 规范）
- **API 契约**：[contracts/ai-health-consultation-api.md](./contracts/ai-health-consultation-api.md)（6 端点 + SSE 事件与顺序）
- **Mock**：[contracts/mocks.md](./contracts/mocks.md)（与契约同源，前端 Mock/测试复用）
- **快速开始**：[quickstart.md](./quickstart.md)

### SSE 事件契约（服务端 → 客户端，`text/event-stream`）

| 顺序 | event | data | 说明 |
|------|-------|------|------|
| 1 | `accepted` | `{messageId, sessionId}` | 患者消息已落库，前端可立即上屏 |
| 2 | `emergency`（可选） | `{level, title, advice, ruleCode, booking:{departmentId,departmentName}}` | 仅紧急时发送，**必须先于正文**，前端据此显示提示条并弹窗 |
| 3 | `delta`（多次） | `{text}` | 正文增量 |
| 4 | `disclaimer` | `{text:"仅供参考，不构成医疗诊断"}` | 固定文案，服务端追加 |
| 5 | `done` | `{messageId, truncated, modelUsed}` | 正常结束 |
| 5' | `error` | `{code, message, retryable}` | 失败结束（与 `done` 互斥），已发送内容保留 |

## Complexity Tracking

> 仅登记设计取舍，均为通过项；无门禁违规。

| 取舍 | 为什么需要 | 被否决的更简单方案及原因 |
|------|-----------|--------------------------|
| MySQL 持久化 + Redis 热窗口双写 | 宪法要求多轮上下文用 Redis；但「长期保留 + 刷新/重登恢复 + 手动清空」要求可靠真值源 | 只用 Redis：TTL 到期/清库即丢失历史，不满足 SC-007/SC-010；只用 MySQL：每轮拼装历史需全表扫描，无法满足 1.5s 首字目标 |
| 规则 + 模型两段式紧急判定 | 纯规则漏判同类表述（如「喘不上气」），纯模型无法保证 100% 召回与可解释 | 纯规则：SC-003 召回无法保障；纯模型：结论不可复现、无法留痕到具体规则 |
| 对话页拆为 1 视图 + 2 组件 | 宪法要求组件 ≤ 300 行；提示条与弹窗在 US2 被独立验证 | 单文件视图：将超 400 行，触发 G5 违规 |

## 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| DeepSeek API 抖动/限流 | 回答中断 | 首包超时 3s、总超时 8s；`error` 事件 + 重试；患者消息不丢（FR-019） |
| 病历上下文过长挤占预算 | 回答质量下降 | 字符预算 + 最近优先截断 + `truncated` 标记并在回答中提示 |
| 知识库覆盖面不足（面向导诊） | 用药/康复建议空泛 | R-05 预置知识 + 回答中「依据有限」声明；缺口清单在 tasks 阶段拆为补库任务 |
| 紧急规则误报 | 患者焦虑、弹窗打扰 | 模型复核收敛；误报样本回流规则优化；弹窗可关闭但提示条保留 |
| Redis 3.2 无 Streams | 不能用消息流做上下文 | 明确只用 List/Hash；热窗口由 Service 串行写 |
| 多 Tab 并发同会话 | 消息乱序/重复回答 | 会话级「进行中」锁（Redis `SET NX EX`），冲突返回 `CHAT_BUSY` |
| 病情明文进日志 | 违反原则 IV | 日志白名单字段；审计只存规则码与 ID |

## 关键机制设计（实现级）

> 本节把六项重点机制落到可编码的粒度；决策依据见 [research.md](./research.md) 对应 R-xx，字段与枚举见 [data-model.md](./data-model.md)。

### M1 模型调用封装（R-01 / R-03）

```java
// support/HealthChatClient.java —— 唯一的模型出口，Spring AI 类型不越过此层
public interface HealthChatClient {
    /** 流式生成：每个元素为一段正文增量；错误以 onError 传播 */
    Flux<String> stream(HealthChatCommand command);
}

// support/HealthChatCommand.java
public record HealthChatCommand(
        String systemPrompt,        // 角色 + 安全边界 + 免责要求 + 首行 emergency 协议
        List<ChatTurn> turns,       // 历史窗口 + 本轮患者消息
        int maxOutputTokens,
        String model) {}

public record ChatTurn(String role, String content) {} // role: "user" / "assistant"
```

**实现要点**
- `HealthChatClientImpl` 构造注入 `ChatClient.Builder`（复用 002 AI 自动配置），`defaultOptions(model, maxOutputTokens)`；内部只做 `chatClient.prompt().system(...).messages(...).stream().content()`。
- **超时**：`flux.timeout(Duration.ofMillis(firstTokenTimeoutMillis))` 为「item 间空闲超时」，天然覆盖首包超时与中途卡死；另叠加整体截止 `.take(Duration.ofMillis(timeoutMillis))`，两者任一触发即 `onError`。
- **线程模型**：Spring AI 的 OpenAI 客户端为阻塞 HTTP，`Flux` 需 `.subscribeOn(Schedulers.boundedElastic())`，禁止在 Tomcat 请求线程上阻塞。
- **错误语义**：Client 只抛 `HealthChatException(reason)`（`TIMEOUT` / `UPSTREAM_ERROR` / `EMPTY`），**不生成任何兜底文案**；是否发 `error` 事件、是否标 `stream_status` 由 Service 决定（FR-020）。
- **可测性**：单测三桩 —— ①正常增量序列；②`Flux.error(...)`；③永不结束（验证超时）。契约/集成测试用 `@MockBean HealthChatClient`，不触真实 API。

### M2 病历上下文获取（R-04）

```text
MedicalContext assemble(long patientId)          // support/MedicalContextAssembler
  ├─ ① 最近 3 次问诊：SELECT id, complaint, created_at, doctor_id FROM consultation
  │     WHERE patient_id = ? AND status = 'COMPLETED' ORDER BY created_at DESC LIMIT 3
  ├─ ② 对应处方（每问诊一张，签名态）：
  │     SELECT p.id, p.signed_at FROM prescription p WHERE p.consultation_id IN (...) AND p.status = 'SIGNED'
  ├─ ③ 明细与药名：prescription_item JOIN drug_catalog（drug_id）
  └─ ④ 组装摘要文本 → 预算裁剪 → MedicalContext(summaryText, referencedConsultationIds, truncated)
```

**摘要模板**（纯文本，无 JSON，便于模型理解）

```text
【最近就诊记录】（仅供本次咨询参考）
1) 2026-11-01 内科 张*｜主诉：咳嗽三天
   处方：阿莫西林胶囊 0.25g*20｜用法：口服，一次 2 粒，一日 3 次｜注意：青霉素过敏者禁用
2) …
```

**预算算法**（`context-medical-max-chars`，默认 1500）

```text
for entry in 就诊记录（时间倒序）:
    if 已用长度 + entry 长度 > 预算: truncated = true; break
    append entry
若一条都放不下: 退化为「最近一次主诉 + 处方药名」一句话，并 truncated = true
```

**约束**：无就诊记录时 `summaryText = "（该患者暂无历史就诊记录）"`，prompt 要求模型据此声明「依据有限」（对应 US1 场景 2）；摘要**不回传前端、不写日志、不进 Redis**。

### M3 医学知识库配置（R-05）

**V10 预置清单**（写入 `department_disease_kb`，`source='PRESET'`、`kb_version='2026.11'`，合计 58 条；`V9` 只建 3 张表）

| 分类（用 `disease_name` 承载要点） | 条数 | 示例 |
|---|---|---|
| 常见症状与处置要点 | 20 | 发热（<38.5 物理降温、多饮水）、咳嗽咳痰（保持湿度、避免烟尘） |
| 用药注意事项 | 10 | 抗菌药需足疗程、勿自行停药；布洛芬胃病患者慎用 |
| 康复与生活指导 | 8 | 上感恢复期休息与补水；术后避免负重 |
| 就医指征 | 8 | 持续高热 >3 天、呼吸困难、意识改变 |
| 医院信息知识 | 12 | 门诊时间、急诊位置、检验需空腹、医保结算窗口、儿科/发热门诊位置 |

**检索算法** `KnowledgeRetriever.retrieve(String patientText, int topK)`
1. 规整化：去标点、转小写、按空白与标点切词；中文取 2-gram 与 3-gram 作为候选关键词；
2. 命中：对 `disease_keyword` 做 `LIKE '%kw%'`（一次查询用 `IN` 拼装候选词，避免 N 次查询）；
3. 计数排序：按命中词数降序，同分按条目 ID 升序，取 Top-K（默认 5）；
4. 输出 `List<KnowledgeHit(diseaseName, departmentId, kbVersion)>`，拼入 prompt「知识依据」段并标注 `kb_version`；
5. **空命中**：prompt 明确「若知识依据为空，必须说明依据有限，禁止编造来源」（FR-004）。

> 已知缺口：该表无「康复指导/用药注意」专用列，本阶段以 `disease_name` 承载一句话要点；若知识规模突破 200 条或需要结构化字段，另立功能补表（已登记观察项）。

### M4 多轮上下文（Redis 3.2.100，R-06）

| 时机 | 动作 |
|---|---|
| 会话创建 | 写 DB；Redis 不预建（懒加载） |
| 收到患者消息 | ① 校验归属与上限 → ② 获取锁 `SET ai:health:lock:{sid} NX EX 60`（失败 → `CHAT_BUSY`）→ ③ 落库患者消息 |
| 生成前 | 读 Redis 窗口 `LRANGE ai:health:ctx:{sid} 0 -1`；为空则从 DB 取最近 12 条 `COMPLETED` 重建并 `RPUSH`+`LTRIM`+`EXPIRE` |
| 生成成功后 | `@Transactional` 提交后（`TransactionSynchronization.afterCommit`）`RPUSH` 患者与助手两条摘要、`LTRIM -12 -1`、`EXPIRE 86400`；更新 `meta` Hash 与 DB `last_active_at/message_count` |
| 生成失败 | 只落 DB 助手消息（`FAILED`/`INTERRUPTED`），**不写入 Redis 窗口**（下一轮上下文不必带残缺回答） |
| 删除/清空 | 删 DB（逻辑/物理）+ `DEL ai:health:ctx:{sid}`、`DEL ai:health:meta:{sid}`、释放锁 |
| 锁释放 | `finally` 中 `DEL`（仅当值匹配时删除，避免误删他人锁：Redis 3.2 无 Lua 扩展依赖，可用 `WATCH/MULTI` 或校验值后删） |
| Redis 不可用 | 整体降级为「每轮从 MySQL 重建窗口」，记录 WARN（不含明文），对话不中断 |

**窗口摘要格式**：`PATIENT|<content 截断 500 字符>` / `ASSISTANT|<content 截断 500 字符>`；重建时忽略 `FAILED`/`INTERRUPTED`。

### M5 紧急症状识别与预警（R-07）

**规则配置示例**（`sdas.ai.health.emergency.rules`，缺省用内置默认表，配置非空则整体覆盖）

```yaml
sdas:
  ai:
    health:
      emergency:
        rules:
          - code: CHEST_PAIN_DYSPNEA
            name: 胸痛伴呼吸困难
            level: HIGH
            departmentName: 心血管内科
            keywords: [胸痛, 胸口疼, 胸闷, 喘不上气, 呼吸困难]
          - code: STROKE_SIGNS
            name: 卒中征象
            level: HIGH
            departmentName: 神经内科
            keywords: [口角歪斜, 一侧无力, 说话不清, 言语不清, 突然倒地]
          - code: SEVERE_BLEEDING
            name: 大出血
            level: HIGH
            departmentName: 急诊科
            keywords: [大出血, 止不住血, 呕血, 咯血]
```

**模型首行协议**（system prompt 片段，强制且可解析）

```text
你是医院面向患者的健康咨询助手，只能提供健康科普与就医建议，不得给出诊断结论或处方调整。
回答结构：
第 1 行必须且只能是 EMERGENCY: YES 或 EMERGENCY: NO（依据是否出现需要立即就医的症状判断）；
第 2 行起为给患者的正文，使用中文，简洁分点，不重复本段说明。
正文中禁止出现「确诊」「处方加减量」等表述；涉及用药须引导复诊或咨询开方医生。
```

**判定与留痕**

| 规则命中 | 模型首行 | `model_verdict` | `warned` | 表现 |
|---|---|---|---|---|
| 是 | `YES` | `CONFIRMED` | true | 发 `emergency` 事件 + 弹窗 |
| 是 | `NO` | `REJECTED` | false | 仅正文；写判定记录供规则优化 |
| 是 | 超时/异常/无首行 | `UNAVAILABLE` | **true** | 按规则预警（安全优先） |
| 否 | `YES` | `MODEL_ONLY` | true | 发 `emergency` 事件（`ruleCode=MODEL_ONLY`） |
| 否 | `NO` | — | false | 不写判定记录（避免表膨胀） |

- 首行在 `HealthChatService` 内解析并**从正文中剥离**（不展示给患者）；首行缺失时按 `UNAVAILABLE` 处理。
- `emergency` 事件必须在首个 `delta` 之前发送：Service 在收到首元素时先判定再决定事件顺序（`switchOnFirst`）。
- `booking` 由规则 `departmentName` 精确匹配 `department.name`（该表无 `code` 列且 005 只读依赖其结构）得到 `departmentId/departmentName`；查不到时 `booking=null`（提示条仍展示）。

### M6 免责声明强制点（R-08）

| 位置 | 强制方式 |
|---|---|
| 服务端常量 | `HealthDisclaimer.TEXT = "仅供参考，不构成医疗诊断"`（唯一来源，禁止散落字符串） |
| SSE | `delta` 结束后固定发 `disclaimer` 事件，文案取自常量 |
| 落库 | `ai_health_message.disclaimer` 单独列（不拼进 `content`），助手消息必填 |
| 契约/前端 | 前端只渲染 `disclaimer` 字段，禁止硬编码或拼接；`types.ts` 注释标注来源契约 |
| 测试 | 单测断言常量；契约测试断言 SSE 第 4 步；契约一致性检查逐字比对（SC-002） |

### 一轮对话完整时序

```text
1  前端 POST /v1/health-consultation/sessions/{sid}/messages（fetch + ReadableStream + CSRF）
2  Controller：PatientIdExtractor.extract → 参数校验 → service.chat(...)
3  Service：校验会话归属(SESSION_NOT_FOUND) → 校验输入长度/消息数 → 抢锁(CHAT_BUSY)
4  Service：落库患者消息(PATIENT) → 发 accepted 事件(messageId)
5  Service：读 Redis 窗口(miss 则 DB 重建) → assemble 病历上下文 → retrieve 知识 Top-K
6  Service：拼 systemPrompt（角色/边界/首行协议/免责）→ HealthChatClient.stream
7  Service：首元素到达 → 解析 EMERGENCY 首行 → 需要预警则先发 emergency 事件
8  Service：逐块发 delta 事件（首行已剥离）
9  Service：流结束 → 落库助手消息(COMPLETED, disclaimer, emergency/rule_code)
10 Service：发 disclaimer 事件 → 发 done 事件 → 释放锁
11 Service：afterCommit 写 Redis 窗口 + meta，更新 last_active_at/message_count
12 异常分支：流中失败 → 落库助手消息(FAILED) → 发 error 事件 → 释放锁（已发内容保留）
13 异步兜底：emitter 超时 → completeWithError，标记 INTERRUPTED
```

## 下一步

`/speckit.tasks`：按 US1（P1）→ US2（P2）→ US3（P3）拆分，测试任务先于实现任务，SSE 事件顺序、越权、清空、超时降级、规则误判 5 类风险项必须有对应用例。
