# 医生接诊 技术方案

**功能**：`003-doctor-consultation`
**分支**：`003-doctor-consultation`
**日期**：2026-11-08
**关联**：`spec.md`、`research.md`、`data-model.md`、`contracts/`
**上游**：`001-patient-identity`（认证/双重 Cookie/审计）、`002-appointment-consultation`（图文问诊单、AI 基础设施、医生档案）

---

## 0. 本轮规划决策（来自用户输入与 /speckit.clarify）

| 编号 | 决策 | 来源 | 落点 |
|------|------|------|------|
| D1 | 医生认证复用 001 身份模块 + 新增 `doctor_account` 与 `DoctorIdExtractor`；管理员建号界面不在范围 | `spec.md` 9.1 Q1 | R-01、`doctor_account` 表、`DoctorIdExtractor` |
| D2 | 24h 接诊超时用 `@Scheduled` 定时扫描；MVP「标记超时 + 通知管理员」，自动分配列后续 | `spec.md` 9.1 Q2 | R-02、`sdas.doctor.timeout-hours`、`ConsultationTimeoutStatus` |
| D3 | 处方服务端托管密钥签名（`SHA256withRSA`），医生确认触发服务端签名 | `spec.md` 9.1 Q3 | R-03、`prescription.signature`、`PRESCRIPTION_SIGN_FAILED` |
| D4 | 处方二维码（zxing）+ 水印 + 3 天有效期（自签名起算） | `spec.md` 9.1 Q4 | R-04/R-05、`drug_catalog`、`PrescriptionStatus` |
| D5 | AI 辅助决策复用 002 DeepSeek/SophNet 链路，禁止双链路 | `spec.md` 9.1 Q5 | R-06、`sdas.ai.decision.*` |
| D6 | 药品目录 Flyway 预置只读，无维护界面 | `spec.md` 9.1 Q6 | R-07、`drug_catalog.source='PRESET'` |

> 以上决策与 `research.md`、契约、`data-model.md` 完全一致；无 `NEEDS CLARIFICATION` 残留。

---

## 1. 摘要（Summary）

本方案为 `003` 提供可编码的落地设计：新建后端 `com.sdas.server.doctor` 模块（JdbcTemplate 持久化，沿用 001/002 分层与契约风格）与前端 `web/src/api/doctor-consultation.ts` + `doctorRequest` 实例 + 一组 `/workbench/*` 视图；实现 9 个 REST 端点（医生登录、待接诊列表、问诊单详情、接诊、对话、开具处方、查询处方、AI 辅助决策）。核心治理点：**复用 001 认证新增医生角色**、**24h 超时定时扫描 + 标记超时通知管理员**、**服务端托管密钥签名（零签名依赖）**、**二维码引入 zxing（唯一新增依赖，经评审）**、**AI 辅助决策复用 002 链路禁止双链路**、**一张单一张处方与接诊全链路幂等/事务一致**。

---

## 2. 技术上下文（Technical Context）

| 项 | 值 |
|----|----|
| 语言/运行时 | Java 17（server）、Vue 3 + TypeScript（web） |
| 框架 | Spring Boot 3.4.5、Spring MVC、Spring AI 1.0.0（已含）、Spring `@Scheduled` |
| 持久化 | MySQL（Flyway `V5` 迁移）、JdbcTemplate（沿用 001/002 模式）、Redis（复用会话/令牌吊销） |
| 认证 | 复用 001 JWT、新增 `DoctorIdExtractor`、双重 Cookie + `X-CSRF-Token` |
| AI | `spring-ai-starter-model-openai` → DeepSeek/SophNet（复用 002 配置） |
| 签名/二维码 | JDK `java.security.Signature`（零依赖）、`com.google.zxing`（唯一新增） |
| 构建/质量 | Maven（checkstyle、spotbugs、Jacoco ≥70%）、Vitest、Playwright、ESLint/Prettier |
| 关键目录 | `server/src/main/java/com/sdas/server/doctor`（新增）、`web/src/api/`、`web/src/views/`、`web/src/router/` |

---

## 3. 架构评审记录（宪法原则 I）

| 候选依赖/组件 | 结论 | 依据 |
|---------------|------|------|
| `com.google.zxing:core` + `javase` | **引入（经评审）** | D4：电子处方二维码生成，业界标准纯 Java、无传递依赖 |
| 数字签名库 | 不引入 | D3：JDK `java.security.Signature` 内置 |
| 消息队列 | 不引入 | D2：`@Scheduled` 定时扫描，规约不引入 MQ |
| 对象存储 SDK | 不引入 | 处方/水印图片沿用 002 本地文件系统 |
| 本地 AI 模型 / 第二套 AI 链路 | 不引入 | D5：复用 002 链路，禁止双链路 |
| 新增 AI starter | 不引入 | `spring-ai-starter-model-openai` 已存在 |
| 图片水印库 | 不引入 | JDK `java.awt` + `javax.imageio` |

**结论**：本次**仅新增 `zxing` 一个第三方依赖**（二维码），已在 `pom.xml` 登记并经架构评审；其余能力零新增依赖。

---

## 4. 宪法门禁复核（G1–G6）

> 门禁编号与 `.specify/memory/constitution.md`「Constitution Check 强制门禁」保持一致：G1 技术栈 / G2 分层与目录 / G3 契约 / G4 安全 / G5 代码 / G6 测试。

| 门禁 | 要求 | 复核结论 | 违规登记 |
|------|------|----------|----------|
| **G1 技术栈** | 仅使用原则 I 指定技术；新增依赖有架构评审记录 | 复用 001/002 全套技术栈；仅新增 `com.google.zxing`（二维码，§3 架构评审）；AI 复用 002 DeepSeek/SophNet 单链路（D5/R-06），无 MQ/对象存储/第二 AI 链路 | 无 |
| **G2 分层与目录** | `server/`+`web/` 布局；三层调用链；角色 axios；`XxxIdExtractor`；Pinia 初始化；路由词干无冲突 | `com.sdas.server.doctor.{controller,service,repository,entity,model,dto,support}`；新增 `DoctorIdExtractor`（镜像 `PatientIdExtractor`）；`doctorRequest`/`patientRequest` 独立；store 从 `localStorage` 初始化；`/api/v1/workbench/*` 与 `/doctors`、`/consultations`、`/patient/*`、`/appointment/*` 错开；多表变更 `@Transactional`（R-08） | 无 |
| **G3 契约** | `contracts/` 与 `data-model.md` 先于实现；响应结构、枚举、日期格式、URL 前缀一致 | 已产出 `contracts/doctor-consultation-api.md`（9 端点）+ `mocks.md` + `README.md`；`data-model.md` 1.1–1.7 唯一来源，枚举 `String` + 显式 `@Converter`，无同名不同值；日期 `yyyy-MM-dd'T'HH:mm:ss`；前端路径 `/v1/...` 不含 `/api` | 无 |
| **G4 安全** | BCrypt≥10；JWT≤24h+Refresh；模糊失败；`ErrorCode` 统一；日志脱敏 | 复用 001 BCrypt/JWT/Refresh/双重 Cookie；登录失败统一 `AUTHENTICATION_FAILED`；新增 11 个 `ErrorCode`（data-model 1.6）；处方签名/二维码服务端生成、响应与审计脱敏 | 无 |
| **G5 代码** | Checkstyle/SpotBugs、ESLint/Prettier 零告警；无 `@Enumerated` 与 `@Converter` 并存；代码审查 | `tasks.md` T085 静态检查、T089 代码审查；枚举统一显式 `@Converter(autoApply=false)`（T010），规避并存冲突（实现期验证） | 无 |
| **G6 测试** | 覆盖≥70%；所有 REST 正向/异常集成；Playwright 全故事；风险项用例；testsdoc；契约一致性 | `tasks.md` T025–T072 测试先行；T081 覆盖率≥70%；T083–T084 Mock+真实后端 E2E（5 故事）；T087 契约一致性(SC-012)；T088 报告归档（实现期验证） | 无 |

> 全部门禁通过；唯一新增依赖 zxing 已在第 3 节登记，无未登记违规。

---

## 5. 项目结构（Project Structure）

### 5.1 后端（server）

```
server/src/main/java/com/sdas/server/
├── doctor/                          # 新增模块（镜像 identity/appointment 结构）
│   ├── controller/                  # WorkbenchController / PrescriptionController / AiDecisionController
│   ├── dto/                         # 请求/响应记录（*Request / *Response）
│   ├── entity/                      # DoctorAccount / ConsultationMessage / Prescription /
│   │                                #   PrescriptionItem / DrugCatalog / AiDecisionSession
│   ├── model/                       # 枚举（PrescriptionStatus / MessageType /
│   │                                #   ConsultationTimeoutStatus / DoctorAccountStatus）
│   │   └── converter/               # 各枚举 ↔ VARCHAR 的 AttributeConverter
│   ├── repository/                  # 每个实体一个 JdbcTemplate Repository
│   ├── service/                     # DoctorAuthService / WorkbenchService /
│   │                                #   PrescriptionService / AiDecisionService
│   └── support/                     # 处方签名、二维码、水印、超时调度
└── common/                          # 复用：Result / ErrorCode（扩展 003 新码）/
                                     #   DoctorIdExtractor（新增）/ BusinessException
server/src/main/resources/
├── db/migration/V5__doctor_consultation_schema.sql   # 新建 doctor_account/consultation_message/
│                                                      #   drug_catalog/prescription/prescription_item/
│                                                      #   ai_decision_session + ALTER consultation
├── db/seed/V6__doctor_consultation_seed.sql           # 医生账号/药品目录/演示问诊单
└── application.yml                  # 新增 sdas.doctor / sdas.ai.decision（见 data-model §3）
```

- **复用 001/002**：`PatientIdExtractor`（模式）、`Result`、`BusinessException`、`CsrfFilter`、`security_audit_event`、`ConsultationStatus`。
- **签名/二维码**：`PrescriptionSigningService`（JDK `Signature`）+ `QrCodeService`（zxing）+ `WatermarkService`（java.awt）。
- **超时调度**：`ConsultationTimeoutScheduler`（`@Scheduled`，扫描 `accepted_at` 超 `timeout-hours` 的 `IN_PROGRESS` 单，置 `timeout_status=ADMIN_NOTIFIED` + 审计）。

### 5.2 前端（web）

```
web/src/
├── api/
│   ├── doctor-consultation.ts       # 9 端点封装（标注 contracts/ 路径）
│   ├── types.ts                     # 追加 003 枚举与响应类型
│   ├── mocks/doctor-consultation.ts # 同步 mocks.md（M1–M9）
│   └── request/doctor-request.ts    # 独立医生请求实例（注入 doctor JWT）
├── views/
│   ├── workbench-login-view.vue         # /workbench/login
│   ├── workbench-consultations-view.vue # /workbench/consultations（待接诊/已接诊）
│   ├── workbench-chat-view.vue          # /workbench/consultations/:id（接诊对话）
│   ├── workbench-prescription-view.vue  # /workbench/prescriptions/new（开具处方）
│   └── workbench-prescription-detail-view.vue # /workbench/prescriptions/:id
├── stores/doctor-store.ts           # Pinia，从 localStorage 初始化
└── router/index.ts                  # 新增 /workbench/* 路由（与 /appointment/*、/consultation/entry 错开）
```

- `views/` 不直接调 axios，统一经 `src/api/doctor-consultation.ts`；`doctorRequest` 与 `patientRequest` 相互独立（G5）。

---

## 6. 阶段说明

### 阶段 0：研究定稿（已完成）
`research.md` 关闭 R-01–R-10；`data-model.md`/`contracts/` 产出；门禁 G1–G6 通过。

### 阶段 1：实现（建议提交顺序）
1. **数据层**：`V5` 迁移 + `V6` 种子；`entity`/`repository`/`model(converter)`。
2. **认证与工作台**：`DoctorIdExtractor` + `DoctorAuthService` + `WorkbenchController`（端点 1–6）。
3. **电子处方**：`PrescriptionService` + 签名/二维码/水印 + `PrescriptionController`（端点 7–8）。
4. **AI 辅助决策**：`AiDecisionService` + `AiDecisionController`（端点 9）。
5. **超时调度**：`ConsultationTimeoutScheduler`。
6. **前端**：`doctorRequest` → 类型/Mock 同步 → 视图 → 路由 → store → E2E。
7. **测试**：单元（状态机/幂等/签名/校验）→ 集成（Testcontainers 或本机 MySQL/Redis）→ Playwright（5 用户故事）→ 契约一致性验证（SC-012）。

---

## 7. 复杂度跟踪（Complexity Tracking）

| 维度 | 评估 | 说明 |
|------|------|------|
| 状态机数量 | 中 | 复用 002 `ConsultationStatus` + 新增 4 类（处方/消息/超时/账号），已在 data-model 固化 |
| 外部依赖 | 低 | 仅 DB + AI（复用 002）+ zxing（唯一新增） |
| 并发/幂等 | 中 | 接诊状态 CAS + 一张单一张处方唯一约束 + 事务（R-08） |
| 安全面 | 中 | 复用 001 门禁 + 医生角色 + 处方签名/脱敏（R-03/R-09） |
| AI 不确定性 | 中 | 复用 002 超时降级，将不确定性收敛为确定 200 分支（D5） |
| 门禁违规 | 无 | G1–G6 全部通过，zxing 已登记 |

**结论**：复杂度受控，无需触发架构升级或分拆（如触发将登记并回归 G1–G6）。

---

## 8. 下一步

1. 运行 `/speckit.tasks` 将阶段 1 拆解为可执行任务（含测试先写项）。
2. 实施时严格以 `contracts/` 为唯一接口参考，`web/src/api` 类型与 Mock 同步 `mocks.md`。
3. 每迭代执行契约一致性验证（SC-012），差异项归零方可视为功能完成。
