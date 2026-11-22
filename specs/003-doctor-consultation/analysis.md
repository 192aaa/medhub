# 医生接诊 复杂度与可行性分析

**功能**：`003-doctor-consultation` | **日期**：2026-11-08
**输入**：`spec.md`、`research.md`（R-01–R-10 定稿）、`data-model.md`、`contracts/`（`doctor-consultation-api.md`、`mocks.md`、`README.md`）、`plan.md`、`tasks.md`
**分析目的**：在 `/speckit.implement` 之前，依据 `.specify/memory/constitution.md` 的 **Constitution Check（G1–G6）** 复核设计，识别复杂度、风险与可行性缺口，给出放行/修订建议。
**上游**：`001-patient-identity`（认证/实名/门禁/双重 Cookie/审计/`GlobalExceptionHandler`/`PatientIdExtractor`/`security_audit_event`）、`002-appointment-consultation`（图文问诊单、`doctor` 档案、`spring.ai.openai` 配置、`ConsultationStatus`/`TriageResultStatus`）均已实现，可复用。

---

## 1. 分析结论（TL;DR）

| 维度 | 结论 |
|------|------|
| 门禁 G1–G6 | **全部通过**，无违规登记项（见 §3） |
| 技术可行性 | **高**：复用 001 全套认证/安全/审计与 002 的问诊单/AI 基础设施，本次**仅新增 `zxing` 一个第三方依赖**（二维码，经架构评审） |
| 复杂度 | **中**：接诊状态 CAS + 一张单一张处方 + 处方签名 + 24h 超时兜底 + AI 不确定性；已在 `plan.md` Complexity Tracking 受控 |
| 关键风险 | 8 项（见 §4）：签名合规与密钥托管、`@Scheduled` 多实例重复、AI 同步等待、本地图片不共享、演示种子误入生产、验真链接 host 未配置、药品目录/医生账号无维护界面 |
| 待决项 | 2 项非阻断（Redis 版本、电子签名合规）；AI 形态与 TS 两项已在 002/本功能闭环 |
| 放行建议 | **可进入 `/speckit.implement`**；8 项风险列入实现期监控，不阻塞启动 |

---

## 2. 复杂度评估

### 2.1 维度评分

| 维度 | 等级 | 依据 |
|------|------|------|
| 业务规则 | 中 | 接诊状态 CAS（`PENDING→IN_PROGRESS`）、一张问诊单仅一张处方、回复 ≤1000 字、处方 3 天有效期、24h 超时兜底 |
| 数据模型 | 中 | 6 张新表（`doctor_account`/`consultation_message`/`drug_catalog`/`prescription`/`prescription_item`/`ai_decision_session`）+ `consultation` 扩展 5 列 |
| 外部依赖 | 低 | 仅 MySQL + Redis（复用）+ DeepSeek/SophNet（复用 002）+ zxing（唯一新增）；无 MQ/对象存储/第二 AI 链路 |
| 并发与一致性 | 中 | 接诊状态 CAS + `prescription.consultation_id` `UNIQUE` + 超时 `timeout_status=NONE` 幂等 + 多表 `@Transactional`（R-08） |
| 安全面 | 中-高 | 复用 001 门禁 + 医生角色隔离 + 处方 `SHA256withRSA` 签名 + 密钥 aes 加密 + 二维码/水印 + 脱敏（R-03/R-09） |
| AI 不确定性 | 中 | 复用 002 超时降级，收敛为确定 `TIMEOUT_FALLBACK`（HTTP 200）分支（R-06/SC-009） |
| 测试面 | 中-高 | 9 端点契约 + 集成 + 单测 + Playwright 5 故事；覆盖率 ≥70%（原则 VI） |

**综合**：复杂度受控，无需触发架构升级或模块分拆。若实现期任一门禁或指标越界，须回写 `plan.md` Complexity Tracking 并标注「违规项/必要性/被否决的更简单方案」（宪法 §Governance）。

### 2.2 复杂度热点与对应任务

| 热点 | 影响 | 已规划控制（tasks.md） |
|------|------|------------------------|
| 接诊并发幂等 | 重复接诊 | T043/T049：`accepted_at IS NULL` CAS + `CONSULTATION_ACCEPT_CONFLICT`(409) |
| 一张单一张处方 | 重复开方 | T055/T056/T062：`UNIQUE(consultation_id)` + `PRESCRIPTION_ALREADY_EXISTS`(409) |
| 处方签名防篡改 | 未签名处方落库 | T017/T022/T054–T056/T062：`SHA256withRSA` 规范化摘要 + 失败不落库 |
| 24h 超时兜底 | 问诊单滞留 | T078–T080：`@Scheduled` 扫描 + `timeout_status=NONE` 幂等去重 + 审计 |
| 药品目录外开方 | 违规开方 | T055/T056/T062：`enabled=1` 校验 + `DRUG_NOT_IN_CATALOG`/`DRUG_NOT_FOUND` |
| AI 超时 | 主流程卡顿 | T019/T068/T074：`timeout-millis` 超时降级 `suggestions=[]`，不阻塞接诊开方 |
| 越权访问 | 病情资料泄漏 | T016/T023/T027/T035：`DoctorIdExtractor` + `doctor_id` 归属校验 |

---

## 3. Constitution Check 门禁复核（强制）

> 来源：宪法「Constitution Check 强制门禁」（G1 技术栈 / G2 分层与目录 / G3 契约 / G4 安全 / G5 代码 / G6 测试）。每次 `/speckit.analyze` 须复核 G1–G6。

| 门禁 | 要求 | 复核证据 | 结论 |
|------|------|----------|------|
| **G1 技术栈** | 仅原则 I 技术；新增依赖有评审 | `plan.md` §3 架构评审：仅引入 `com.google.zxing`（二维码）；签名用 JDK `Signature`、水印用 `java.awt`，零新增；无 MQ/对象存储/第二 AI 链路；复用 `spring-ai-starter-model-openai` | ✅ PASS |
| **G2 分层与目录** | server/+web/ 布局；三层链；角色 axios；`XxxIdExtractor`；Pinia 初始化；路由词干无冲突 | `plan.md` §5：`controller→service→repository`；新增 `DoctorIdExtractor`（镜像 `PatientIdExtractor`）；`doctorRequest` 与 `patientRequest` 独立；store 从 `localStorage` 初始化；`/api/v1/workbench/*` 词干错开 `/doctors`、`/consultations`、`/patient/*`、`/appointment/*` | ✅ PASS |
| **G3 契约** | contracts/ + data-model.md 先于实现；结构/枚举/日期/前缀一致 | 9 端点契约 + `mocks.md`(M1–M9) 先于代码；`data-model.md` 1.1–1.7 为唯一来源；日期 `yyyy-MM-dd'T'HH:mm:ss`；前端路径 `/v1/...` 不含 `/api`（README §1/§6） | ✅ PASS |
| **G4 安全** | BCrypt≥10；JWT≤24h+Refresh；模糊失败；ErrorCode 统一；日志脱敏 | 复用 001（BCrypt/JWT/Refresh/双重 Cookie）；登录失败统一 `AUTHENTICATION_FAILED`；新增 11 个 `ErrorCode`（data-model 1.6）；签名密钥经 `aes-key` 加密禁止明文；响应/审计脱敏（README §4） | ✅ PASS |
| **G5 代码** | Checkstyle/SpotBugs/ESLint/Prettier 零告警；无 `@Enumerated`+`@Converter` 并存；代码审查 | `tasks.md` T085 静态检查、T089 代码审查；枚举统一用显式 `@Converter(autoApply=false)`（T010），规避并存冲突（data-model §1） | ✅ PASS（实现期验证） |
| **G6 测试** | 覆盖≥70%；所有 REST 正向/异常集成；Playwright 覆盖全故事；风险项用例；testsdoc；契约一致性 | `tasks.md` T025–T072 测试先行；T081 覆盖率≥70%；T083–T084 Mock+真实后端 E2E（5 故事）；T087 契约一致性(SC-012)；T088 报告归档 | ✅ PASS（实现期验证） |

**结论**：6 门禁全部满足，无违规项需登记。`G5/G6` 的「实现期验证」类结论在 `/speckit.implement` 完成时由静态检查与测试报告最终确认。

---

## 4. 风险登记（Risk Register）

| 编号 | 风险 | 类别 | 可能性 | 影响 | 缓解（已在设计/任务中） |
|------|------|------|--------|------|--------------------------|
| RK-01 | 电子处方签名合规（电子签名法/CA）未闭环，密钥丢失/轮换策略未定义 | 安全/合规 | 低(MVP) | 中 | `research.md` R-03 已声明「MVP 服务端托管密钥，生产接入时补 CA 评审」；密钥经 `aes-key` 加密存储；**建议**：实现期明确密钥库备份/轮换与丢失恢复 SOP |
| RK-02 | 24h 超时用 `@Scheduled` 单机扫描，多实例部署时并发扫描可能重复通知 | 一致性/可扩展 | 高(生产) | 中 | `timeout_status=NONE` 幂等去重（R-02）；dev/test 单实例无碍；**建议**：生产多实例前评估分布式锁或唯一任务分配（非阻断） |
| RK-03 | AI 决策在请求线程同步等待（默认 5s），高并发下占用 servlet 线程池 | 性能/可扩展 | 中 | 中 | `DecisionProperties.timeout-millis` + `orTimeout` 降级（R-06/T019）；**建议**：量产前改为 `@Async`/响应式，分离超时与业务线程 |
| RK-04 | 处方/水印图片沿用本地文件系统，多实例部署时文件不共享 | 架构/生产就绪 | 高(生产) | 中 | 复用 002 本地文件系统（R-05 已明确）；**建议**：生产前接对象存储（OSS/S3），当前 dev/test 单实例无碍 |
| RK-05 | 演示种子 `V6__doctor_consultation_seed.sql` 误加载到生产，伪造医生账号/药品目录 | 安全/合规 | 低 | 高 | 种子仅 `application-dev.yml` 追加 `classpath:db/seed`（T013）；**建议**：CI 拦截 prod 配置加载 seed |
| RK-06 | 处方验真链接 `verify-url-template` 默认 `localhost`，生产未配置则二维码不可验真 | 配置 | 中 | 低 | `SDAS_PRESCRIPTION_VERIFY_URL` 环境变量注入（data-model §3）；**建议**：prod 显式配置并校验非 localhost |
| RK-07 | 药品目录预置只读、无维护界面，目录/下架变更需种子或 DBA 变更 | 运维 | 中 | 低 | D6/R-07 已定「预置只读」；**建议**：建立目录变更评审流程（不在本功能范围） |
| RK-08 | 医生账号由 Flyway 预置 + 运维侧创建、无管理界面，禁用/重置流程未定义 | 运维/安全 | 中 | 低 | Q1 已定「建号界面不在范围」；**建议**：明确医生离职禁用与密码重置的运维 SOP |

---

## 5. 待决项与宪法闭环

### 5.1 宪法「解释与待决项」闭环

| 宪法待决项 | 状态 | 说明 |
|------------|------|------|
| AI 模型调用形态（官方 API vs 本地部署） | **已闭环** | `research.md` R-06 锁定复用 002 DeepSeek/SophNet 单链路（`spring-ai-starter-model-openai`），禁止双链路 |
| 前端 TypeScript | **已闭环** | `web` 已启用 TS，`src/api/types.ts` 从 contracts 同步（原则 III） |
| Redis 具体版本 | **待确认（非阻断）** | 复用 001/002 配置；实现期在 `plan.md` Technical Context 显式登记实际版本号 |

### 5.2 本功能自身待决项

| 待决项 | 状态 | 说明 |
|--------|------|------|
| 电子签名合规（CA/电子签名法） | **待确认（非阻断）** | R-03 已定 MVP 服务端托管密钥；生产接入时补 CA 评审，不阻塞启动 |
| 超时「自动分配其他医生」 | **列后续增强** | Q2 定稿 MVP「标记超时 + 通知管理员」；`transferred_to_doctor_id` 字段预留 |

### 5.3 文档一致性发现（建议实现期前修正，非阻断）

- `plan.md` §4「宪法门禁复核（G1–G6）」的编号命名与 `constitution.md`「Constitution Check 强制门禁」的官方定义不一致：`plan.md` 写为「G1 契约前置 / G2 枚举唯一来源 / G3 安全约束 / G4 分层与事务 / G5 路由与命名 / G6 AI 依赖治理」，而宪法官方为「G1 技术栈 / G2 分层与目录 / G3 契约 / G4 安全 / G5 代码 / G6 测试」。**实质约束内容均有覆盖，但编号错位**，建议在 `plan.md` §4 按宪法官方案重排编号，避免后续审查与 CI 门禁复核时对照歧义。本 `analysis.md` §3 已按宪法官方案复核。

---

## 6. 可行性评估

| 能力 | 是否就绪 | 证据 |
|------|----------|------|
| 后端技术栈（Spring Boot/JdbcTemplate/AI/签名/二维码） | 是 | 001/002 已落地；003 仅新增 `doctor` 包 + `zxing`（JDK 签名/水印零新增） |
| 前端技术栈（Vue3/Pinia/Router/TS） | 是 | 001/002 已落地 `src/api`/`stores`/`views`/`router`/`mocks` 模式 |
| 测试基础设施 | 是 | 001/002 的 Testcontainers/Vitest/Playwright 模式可复刻；`DoctorTestSupport`(T006) 继承 |
| 认证/门禁/审计 | 是 | 复用 `DoctorIdExtractor`(镜像)/`AuditEventService`/`GlobalExceptionHandler`/`security_audit_event` |
| AI 调用 | 是 | 复用 002 `spring.ai.openai` 配置 + DeepSeek/SophNet Key |
| 处方签名/二维码/水印 | 是 | JDK `Signature` + zxing + `java.awt`，密钥环境注入 |
| 部署/CI | 是 | Maven/Checkstyle/SpotBugs/Jacoco + ESLint/Prettier 已配置 |

**结论**：技术可行性高，无能力缺口；外部约束为 DeepSeek API Key 与签名密钥库（均环境变量注入，沿用 001/002 机制）。

---

## 7. 建议与放行

### 7.1 放行判断
**建议放行进入 `/speckit.implement`**。6 门禁通过，复杂度受控，风险已规划缓解，无阻断性待决项。

### 7.2 实现期必须落实的 5 项
1. **RK-05**：确认 `V6` 种子仅 dev 加载，CI 拦截 prod 加载 seed（T013/T090）。
2. **RK-06**：prod 显式配置 `SDAS_PRESCRIPTION_VERIFY_URL`（非 localhost），否则二维码不可验真。
3. **RK-02/RK-03**：`@Scheduled` 幂等去重验证；若压测显示线程池吃紧，将 AI 决策移至异步线程（仍守超时降级）。
4. **RK-01**：明确签名密钥库备份/轮换与丢失恢复 SOP；密钥经 `aes-key` 加密、禁止明文落库（T090 安全审查回归）。
5. **契约一致性（SC-012）**：实现完一轮即跑 `tasks.md` T087，差异归零方可视为功能完成。

### 7.3 不建议在本功能范围的事（避免范围蔓延）
- 不接 CA/电子签名法合规认证（R-03：MVP 托管密钥，生产再补）。
- 不实现「自动分配其他医生」超时转诊（Q2：列后续增强，仅预留字段）。
- 不实现药品目录维护后台与医生账号管理界面（Q6/Q1：预置只读 + 运维侧创建）。
- 不引入对象存储/消息队列/第二 AI 链路（架构评审零新增，仅 zxing）。
- 不改动 001 既有认证逻辑（仅复用与扩展 `AuditEventType`/`ErrorCode`）。

---

## 8. 与 tasks.md 的衔接

- 风险缓解已映射到任务：RK-01→T017/T022/T090；RK-02→T080；RK-03→T019/T068；RK-05→T013/T090；RK-06→T005；RK-07→T013。
- 复杂度热点见 §2.2，与 `tasks.md` Phase 2–7 一一对应。
- 门禁实现期验证：G5→T085/T089；G6→T081–T088。
- 文档一致性修正（§5.3）为 `plan.md` §4 编号重排，不改变任务范围，实现期初顺手完成即可。

**分析产出物**：本 `analysis.md` 与 `plan.md`/`tasks.md`/`contracts/`/`data-model.md` 共同构成实施依据；进入 implement 后若设计变更，须回写相应文档并保持「契约唯一来源」原则。
