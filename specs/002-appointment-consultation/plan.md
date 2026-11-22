# 预约挂号与图文问诊 技术方案

**功能**：`002-appointment-consultation`
**分支**：`002-appointment-consultation`
**日期**：2026-11-08
**关联**：`spec.md`、`research.md`、`data-model.md`、`contracts/`、`quickstart.md`
**上游**：`001-patient-identity`（认证/实名/门禁已落地，本功能复用其 JWT、`PatientIdExtractor`、双重 Cookie 与 `security_audit_event` 表）

---

## 0. 本轮规划决策（来自用户输入与 /speckit.clarify）

| 编号 | 决策 | 来源 | 落点 |
|------|------|------|------|
| D1 | 挂号费支付在 MVP 阶段使用**模拟支付**，不接入真实第三方支付网关 | 用户输入 + `spec.md` 9.1 Q2 | R-01、数据模型 `PaymentChannel=MOCK`、`plan.md` 架构评审（零支付依赖） |
| D2 | AI 智能导诊设计**超时降级机制**，默认 **5 秒**超时后展示**全量科室列表** | 用户输入 | R-02、`sdas.ai.triage.timeout-millis=5000`、`TriageResultStatus.TIMEOUT_FALLBACK` |
| D3 | 科室-疾病知识库使用**数据库预置数据**，**暂不开放管理功能** | 用户输入 | R-03、`department_disease_kb.source='PRESET'`、无 CRUD 界面 |
| D4 | AI 调用形态沿用 001（Spring AI + DeepSeek 官方 API），禁止双链路 | `spec.md` 9.1 Q3 | R-04 |
| D5 | 图片存本地文件系统，格式白名单 jpg/png/webp，服务端内容嗅探 | `spec.md` 9.1 Q4 | R-06、`sdas.storage.images.*` |
| D6 | 复诊资格 = 已实名认证 + 存在历史就诊记录（只读，dev/test 预置） | `spec.md` 9.1 Q1 | R-08、门禁 `REVISIT_NOT_ELIGIBLE` |

> 以上决策与 `research.md`、契约、`data-model.md` 完全一致；无 `NEEDS CLARIFICATION` 残留。

---

## 1. 摘要（Summary）

本方案为 `002` 提供可编码的落地设计：新建后端 `com.sdas.server.appointment` 模块（JdbcTemplate 持久化，沿用 001 分层与契约风格）与前端 `web/src/api/appointment-consultation.ts` + 一组 `/appointment/*` 视图；实现 9 个 REST 端点（科室/医生浏览、AI 导诊、挂号预约、模拟支付与状态查询、图片上传、图文问诊单提交与查询）。核心治理点：**模拟支付闭环且零新增依赖**、**AI 导诊 5 秒超时降级为全量科室列表（HTTP 200）**、**知识库数据库预置无管理界面**、**复用 001 门禁叠加复诊资格校验**、**支付/预约/问诊单全链路幂等与事务一致**。

---

## 2. 技术上下文（Technical Context）

| 项 | 值 |
|----|----|
| 语言/运行时 | Java 17（server）、Vue 3 + TypeScript（web） |
| 框架 | Spring Boot 3.4.5、Spring MVC、Spring AI 1.0.0（已含） |
| 持久化 | MySQL（Flyway `V2` 迁移）、JdbcTemplate（沿用 001 模式）、Redis（复用会话/缓存，不新增用法） |
| 认证 | 复用 001 JWT（`Authorization: Bearer`）、`PatientIdExtractor`、双重 Cookie + `X-CSRF-Token` |
| AI | `spring-ai-starter-model-openai` → DeepSeek 官方 API（复用 001 配置） |
| 构建/质量 | Maven（checkstyle、spotbugs、Jacoco ≥70%）、Vitest、Playwright、ESLint/Prettier |
| 关键目录 | `server/src/main/java/com/sdas/server/identity`（复用）、`web/src/api/`、`web/src/views/`、`web/src/router/` |

---

## 3. 架构评审记录（宪法原则 I）

| 候选依赖/组件 | 结论 | 依据 |
|---------------|------|------|
| 微信/支付宝支付 SDK | **不引入** | D1：MVP 模拟支付 |
| 消息队列 | **不引入** | 规约明确不引入 MQ，沿用既有缓存 |
| 对象存储 SDK（OSS/S3） | **不引入** | D5：本地文件系统 |
| 本地 AI 模型服务 / 第二套 AI 链路 | **不引入** | D4：禁止双链路 |
| 新增 AI starter | **不引入** | `spring-ai-starter-model-openai` 已存在 |
| 图片校验库（Tika 等） | **不引入** | 用 `javax.imageio` 内容嗅探即可 |
| Spring MVC `MultipartFile` 文件上传 | **内置**，无新依赖 | D5 |

**结论**：本次**不新增任何第三方依赖**，`server/pom.xml` 无需变更。新增能力均通过配置（`application.yml` 的 `sdas.appointment` / `sdas.ai.triage` / `sdas.storage.images`）与既有组件实现。架构评审通过。

---

## 4. 宪法门禁复核（G1–G6）

| 门禁 | 要求 | 复核结论 | 违规登记 |
|------|------|----------|----------|
| **G1 契约前置** | 编码前定义请求/响应结构 | 已产出 `contracts/appointment-consultation-api.md`（9 端点）+ `mocks.md`；前端类型/Mock 标注来源 | 无 |
| **G2 枚举唯一来源** | 枚举/`ErrorCode` 单一来源 | `data-model.md` 1.1–1.10 统一定义；继承 001 错误码，无同名不同值；枚举以 `String` + 显式 `@Converter` 持久化 | 无 |
| **G3 安全约束** | 鉴权/双重 Cookie/脱敏/统一模糊失败/审计 | 受保护端点复用 001 `UNAUTHORIZED`+双重 Cookie；新增 `REVISIT_NOT_ELIGIBLE`/`CONSULTATION_FORBIDDEN`；敏感字段禁出响应（M4–M16 无明文）；审计事件脱敏 | 无 |
| **G4 分层与事务** | Controller→Service→Repository；多表变更同事务 | `com.sdas.server.appointment.{controller,service,repository,entity,model,dto,support}`；支付/预约/问诊单/审计在 `@Transactional` 编排（R-07） | 无 |
| **G5 路由与命名** | 词干错开、请求实例独立、Pinia、views 不直连 axios | 新增 `/appointment/*` 词干，与既有 `/patient/*`、`/system/*`、`/consultation/entry` 错开；复用 `patientRequest`；store 从 `localStorage` 初始化；views 经 `src/api/*` | 无 |
| **G6 AI 依赖治理** | 未定形态前禁双链路；新增依赖须评审 | D4 锁定官方 API，单一链路；本方案零 AI 新增依赖（R-04/R-05） | 无 |

> 全部门禁通过；无违规项需登记至 Complexity Tracking。

---

## 5. 项目结构（Project Structure）

### 5.1 后端（server）

```
server/src/main/java/com/sdas/server/
├── appointment/                      # 新增模块（镜像 identity 结构）
│   ├── controller/                   # DepartmentController / DoctorController /
│   │                                #   TriageController / AppointmentController /
│   │                                #   PaymentController / ConsultationController / ImageController
│   ├── dto/                          # 请求/响应记录（*Request / *Response）
│   ├── entity/                      # Department / Doctor / DoctorSchedule /
│   │                                #   DepartmentDiseaseKb / PatientHistoryVisit /
│   │                                #   Appointment / PaymentRecord / Consultation /
│   │                                #   ConsultationImage / AiTriageSession
│   ├── model/                       # 枚举（AppointmentStatus 等，见 data-model 1.1–1.8）
│   │   └── converter/               # 各枚举 ↔ VARCHAR 的 AttributeConverter（沿用 identity 模式）
│   ├── repository/                  # 每个实体一个 JdbcTemplate Repository
│   ├── service/                     # AppointmentService / PaymentService /
│   │                                #   ConsultationService / TriageService /
│   │                                #   DepartmentReadService / ImageStorageService
│   └── support/                     # 复诊资格判定、金额快照、幂等键等
└── common/                          # 复用：Result / ErrorCode（扩展 002 新码）/ PatientIdExtractor / BusinessException
server/src/main/resources/
├── db/migration/V2__appointment_consultation_schema.sql   # 新建 9 张表（见 data-model §2）
├── db/seed/V2__appointment_consultation_seed.sql          # 科室/医生/排班/知识库/历史就诊预置
└── application.yml                  # 新增 sdas.appointment / sdas.ai.triage / sdas.storage.images（见 data-model §3）
```

- **复用 001**：`PatientIdExtractor`、`SecurityAuditEventRepository`、`Result`、`BusinessException`、JWT 工具、`CsrfFilter` 链路。
- **支付幂等**：`payment_record.appointment_id` 唯一约束 + 事务「先查后写」（R-07）。
- **过期清理**：`AppointmentExpiryScheduler` 将超 `payment-timeout-seconds` 未支付的 `PENDING_PAYMENT` 置 `CLOSED`+`CANCELLED`。

### 5.2 前端（web）

```
web/src/
├── api/
│   ├── appointment-consultation.ts   # 9 端点封装（标注 contracts/ 路径）
│   ├── types.ts                      # 追加 002 枚举（见 data-model）与响应类型
│   ├── mocks/appointment-consultation.ts  # 同步 mocks.md（M1–M16）
│   └── request/patient-request.ts    # 复用 001 实例（注入 Authorization）
├── views/
│   ├── appointment-departments-view.vue   # /appointment/departments
│   ├── appointment-doctors-view.vue       # /appointment/doctors
│   ├── appointment-triage-view.vue        # /appointment/triage
│   ├── appointment-create-view.vue        # /appointment/create
│   ├── appointment-pay-view.vue           # /appointment/pay
│   ├── consultation-submit-view.vue       # /appointment/consultation（提交问诊单）
│   └── consultation-detail-view.vue       # /appointment/consultation/:id（详情）
├── stores/appointment-store.ts            # Pinia，从 localStorage 初始化
└── router/index.ts                        # 新增 /appointment/* 路由（与 /consultation/entry 错开）
```

- 问诊入口沿用既有 `/consultation/entry`（存在），新页面以 `/appointment/*` 挂载，避免词干冲突（G5）。
- `views/` 不直接调用 axios，统一经 `src/api/appointment-consultation.ts`。

---

## 6. 阶段说明

### 阶段 0：研究定稿（已完成）
`research.md` 关闭 R-01–R-10；`data-model.md`/`contracts/` 产出；门禁 G1–G6 通过。

### 阶段 1：实现（建议提交顺序，单一 PR 或分阶段 PR）
1. **数据层**：`V2` 迁移 + 种子；`entity`/`repository`/`model(converter)`。
2. **只读能力**：`DepartmentController`/`DoctorController` + `DepartmentReadService`（端点 1–3）。
3. **AI 导诊**：`TriageService`（DeepSeek 调用 + 5s 超时降级）+ `TriageController`（端点 4）。
4. **挂号与支付**：`AppointmentService`/`PaymentService` + `AppointmentController`/`PaymentController`（端点 5–6b，含门禁与幂等）。
5. **图片与问诊单**：`ImageStorageService` + `ImageController` + `ConsultationService`/`ConsultationController`（端点 7–9，AI 摘要生成）。
6. **前端**：类型/Mock 同步 → 各视图 → 路由 → store → E2E。
7. **测试**：单元（状态机/幂等/校验）→ 集成（Testcontainers）→ Playwright（3 用户故事）→ 契约一致性验证（SC-012，差异归零）。

---

## 7. 复杂度跟踪（Complexity Tracking）

| 维度 | 评估 | 说明 |
|------|------|------|
| 状态机数量 | 中 | 5 类状态（预约/支付/支付记录/问诊单/医生在线/导诊），已在 data-model 固化 |
| 外部依赖 | 低 | 仅 DB + AI（官方 API），零新增依赖 |
| 并发/幂等 | 中 | 支付/预约/问诊单幂等 + 事务（R-07），已设计唯一约束 |
| 安全面 | 中 | 复用 001 门禁 + 复诊资格 + 越权防护（R-08） |
| AI 不确定性 | 中 | 超时降级将不确定性收敛为确定 200 分支（D2） |
| 门禁违规 | 无 | G1–G6 全部通过 |

**结论**：复杂度受控，无需触发架构升级或分拆（如触发将登记并回归 G1–G6）。

---

## 8. 下一步

1. 运行 `/speckit.tasks` 将阶段 1 拆解为可执行任务（含测试先写项）。
2. 实施时严格以 `contracts/` 为唯一接口参考，`web/src/api` 类型与 Mock 同步 `mocks.md`。
3. 每迭代执行契约一致性验证（SC-012），差异项归零方可视为功能完成。
