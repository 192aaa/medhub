# 实施计划：患者注册与实名认证

**功能分支**：`001-patient-identity` | **日期**：2026-11-07 | **规约**：[spec.md](./spec.md)

**输入**：`specs/001-patient-identity/spec.md`（中文，状态：已完成 AI 复查并同步规划决策）；`/speckit.plan` 附加决策见「0. 本轮规划决策」。

**说明**：本文件由 `/speckit.plan` 生成，包含 Phase 0（`research.md`）与 Phase 1（`data-model.md`、`contracts/`、`quickstart.md`）的设计结论。Phase 2 的 `tasks.md` 已按本计划生成（T001–T114，按用户故事分阶段、测试先行）。本计划只做设计，不实现业务代码。

## 0. 本轮规划决策（用户输入，优先级高于规约假设）

| 编号 | 决策原文 | 落地结论 | 影响产物 |
|------|----------|----------|----------|
| D1 | 「验证码方式无需真正发送，可使用固定验证码来实现，暂不接入第三方验证码服务。」 | 验证码**生成与发送**环节改为 dev/test 固定值（默认 `888888`，环境变量可覆盖）；**校验、有效期、一次性使用、用途隔离、频次限制、审计脱敏**全部保留，仍由 Redis 管理生命周期；生产 profile 启动即拒绝启用固定验证码（fail-fast）。不新增短信 SDK，不新增第三方依赖。 | `research.md` R-01；`data-model.md` 2/3/4；`contracts/patient-identity-api.md` 端点 1；`quickstart.md`；`spec.md` 7.1、9.4 |
| D2 | 「暂不对外接入白名单同步机制，仅使用数据库预置数据即可。」 | 白名单为 MySQL 表 `hospital_identity_whitelist`，由 Flyway 迁移建表 + 受控预置数据提供；本功能只读查询，不实现任何外部同步接口、定时任务或 HTTP 客户端；测试夹具向同一张表动态写入随机身份数据，避免硬编码真实身份信息。 | `research.md` R-02；`data-model.md` 3.5；`contracts/` 端点 7/8；`quickstart.md`；`spec.md` 7.1、9.4 |

> 决策落地后，`spec.md` 中「短信发送通道为既有外部能力」「医院白名单由医院侧预置并以只读方式提供」「外部依赖 / 白名单接口超时」等表述已同步修订为「固定验证码」「数据库预置白名单」「内部依赖（MySQL / Redis）」。规约的 FR、SC、用户故事与验收场景不变，仅实现假设与风险描述收敛。

## Summary

本功能为患者提供「手机号注册并自动登录 → 登录 / 安全退出 → 实名认证 → 解锁线上问诊」的完整身份链路，以「已认证」状态作为问诊门禁。实现采用宪法既定的前后端分离架构：后端 `server/`（Java 17 + Spring Boot 3.4.5）按 `Controller → Service → Repository` 分层，MySQL 8.0 存账户、实名信息、协议同意、会话与白名单，Redis 存验证码生命周期、频次计数、幂等键与会话撤销状态；前端 `web/`（Vue 3.4 + Vite 5 + TypeScript + Pinia + Vue Router 4）通过 `src/api/` 的患者角色独立 axios 实例调用契约端点。

两项关键简化：**验证码不真正发送**（D1），**白名单不外部同步**（D2）。二者都只替换「数据从哪来」，不替换「数据怎么被校验」：验证码仍具备一次性、5 分钟有效期、用途隔离与频次限制；白名单仍以「姓名 + 身份证号」精确比对并受唯一约束保护。因此 `spec.md` 的 FR-001 ~ FR-026、SC-001 ~ SC-010 全部保持可测。

契约与枚举在 Phase 1 前置产出：`data-model.md` 是状态枚举与 `ErrorCode` 的唯一来源，`contracts/` 是请求/响应结构与 mock 的唯一来源；所有端点统一 `{ code, message, data }`，鉴权类失败共用一个模糊文案，参数格式类失败各用 `ErrorCode` 固定文案。

## Technical Context

**Language/Version**：后端 Java 17（LTS，`server/pom.xml` 已设 `<java.version>17</java.version>`）；前端 TypeScript 5.7 + Vue 3.4 SFC（`<script setup>`，已启用 `vue-tsc` 类型检查）。

**Primary Dependencies**：
- 后端（现有）：Spring Boot 3.4.5（`spring-boot-starter-web` / `jdbc` / `data-redis` / `validation` / `test`）、Spring AI 1.0.0（`spring-ai-starter-model-openai`）、`mysql-connector-j`、`commons-pool2`（Lettuce 连接池）。
- 后端（本功能新增，均须架构评审，见「架构评审记录」）：`spring-security-crypto`（BCrypt ≥ 10）、`io.jsonwebtoken:jjwt-* 0.12.x`（JWT）、`flyway-core` + `flyway-mysql`（迁移与预置数据）、`spring-boot-testcontainers` + `org.testcontainers:mysql` + `org.testcontainers:junit-jupiter`（集成测试）、`maven-checkstyle-plugin` + `spotbugs-maven-plugin`（静态检查）。
- 前端（现有，无新增）：`axios@^1.7.9`、`pinia@^2.3.0`、`vue@^3.4.38`、`vue-router@^4.4.5`；测试 `vitest@^2.1.8`、`@playwright/test@^1.49.1`、`@vue/test-utils`；静态检查 `eslint@^8.57.1` + `prettier@^3.4.2`。
- **明确不引入**：短信服务商 SDK（D1）、白名单同步 HTTP 客户端 / 定时任务（D2）、消息队列（原则 I 禁止）、Spring AI 相关新增组件（本功能不涉及 AI 调用）。

**Storage**：MySQL 8.0（`cloud_consultation` / 测试库 `cloud_consultation_test`，`application-test.yml` 已配置）；表清单与约束见 `data-model.md` 第 3 节。Redis **3.2.100**（宪法「Redis 3.2.1x」在本项目落地为 3.2.100，保持 3.2 协议族；由 `SPRING_DATA_REDIS_HOST/PORT` 注入，Lettuce + `commons-pool2`，测试环境固定 database=1）；验证码、频次、幂等键、会话撤销状态均存 Redis，不落 MySQL 表。

**Testing**：后端 JUnit 5 + Mockito（Service 单测）、Spring Boot Test + Testcontainers（全部 REST API 正向 / 异常集成测试）；前端 Vitest（composables / 公共组件 / Pinia store）、Playwright（`page.route()` Mock E2E + `E2E_REAL_BACKEND=1` 真实后端 E2E，须断言响应 `code`）；契约一致性验证每迭代一次。测试报告落 `testsdoc/patient-identity/[测试名].md`。`application-test.yml` 与生产共用 `src/main/resources/db/migration` 同一套迁移目录与 `V1` 结构脚本（`V2` 演示种子仅 dev 生效，测试白名单由夹具动态写入），禁止禁用 Flyway 绕过迁移。

**Target Platform**：后端 Linux/Windows 服务器 JVM 17（`SERVER_PORT` 默认 8080）；前端现代浏览器（PC + 手机响应式），Vite dev server 端口由 `VITE_DEV_PORT=5183` 固定且 `strictPort: true`，`VITE_API_BASE_URL=/api` 代理到后端。

**Project Type**：前后端分离的 Web 服务 + 单页应用（`server/` + `web/`），非库 / CLI / 移动端。

**Performance Goals**：注册、登录、实名认证单请求 p95 < 500 ms（不含外部网络，本功能已无外部网络调用）；验证码发送接口 p95 < 200 ms；实名认证比对走 `uk_id_card_hash` 唯一索引，单次查询。

**Constraints**：JWT access token ≤ 24 小时（落地 2 小时）+ Refresh Token（7 天，一次轮换）；BCrypt strength ≥ 10；修改类请求 `SameSite=Lax` + 双重 Cookie；统一模糊失败提示；`ErrorCode` 统一 message；日志脱敏（禁止密码明文、验证码明文、完整手机号 / 身份证号）；`LocalDateTime` 序列化为 `yyyy-MM-dd'T'HH:mm:ss`；URL 遵循 `/api` 前缀规约（前端 `src/api/` 具体路径不含 `/api`）；静态检查零错误零警告；函数 ≤ 80 行、圈复杂度 ≤ 10、前端组件 ≤ 300 行。

**Scale/Scope**：单功能 8 个 REST 端点、6 张 MySQL 表、4 类 Redis key（共 6 个键模板，见 `data-model.md` 第 4 节）、5 个患者页面 + 1 个门禁占位入口（`/consultation/entry`，仅供 SC-003 验证，不含问诊业务）；4 个用户故事、26 条 FR、10 条 SC。不覆盖线上问诊、医生 / 药房角色、白名单维护界面、协议版本更新后的重新同意。

## 架构评审记录（原则 I：新增第三方依赖须经架构评审）

| 新增依赖 / 插件 | 用途 | 评审结论 | 被否决的更简单方案 |
|-----------------|------|----------|--------------------|
| `spring-security-crypto` | 仅取 `BCryptPasswordEncoder`，满足 BCrypt ≥ 10 | 批准：Spring 官方模块，版本由 Boot BOM 管理，不引入 Servlet 过滤器链 | 引入 `spring-boot-starter-security`：会自动启用默认认证过滤器链，与本功能自定义 `PatientIdExtractor` + 契约响应冲突，需大量排除配置 |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` 0.12.x | 生成 / 解析 JWT access token | 批准：成熟实现，避免自研签名与验签漏洞 | 用 JDK `Mac`/`HmacSHA256` 手写 JWT：需自行处理 header/payload、过期、算法混淆、密钥轮换，安全风险高且难以通过安全审查 |
| `flyway-core` + `flyway-mysql` | 建表、唯一约束、白名单预置数据；测试库同套脚本（原则 VI） | 批准：宪法 VI 显式要求 Flyway，当前骨架尚未引入 | 手工执行 SQL / `spring.sql.init`：无法保证 test 与 prod 同套脚本，也无法审计版本 |
| `spring-boot-testcontainers` + Testcontainers MySQL/JUnit | 集成测试覆盖全部 REST API（原则 VI） | 批准：宪法 VI 显式要求 Testcontainers | 依赖本机 MySQL `root/123456`：当前环境实测 `ERROR 1045` 且数据易污染，不可用于 CI |
| `maven-checkstyle-plugin` + `spotbugs-maven-plugin` | G5 静态检查零告警 | 批准：原则 V 显式要求 | 仅靠人工代码审查：无法作为 CI 门禁 |

> 评审结论记录于本表即视为完成「架构评审记录」要求；实现阶段不得在本表之外再引入新依赖。D1 / D2 使短信 SDK 与白名单同步客户端的依赖数量保持为 0。

## Constitution Check

*GATE：Phase 0 research 前逐项校验，Phase 1 设计完成后复核。依据 `.specify/memory/constitution.md` v1.0.0。*

| 门禁 | Phase 0 前结论 | Phase 1 设计后复核 | 依据 / 产物 |
|------|----------------|--------------------|-------------|
| **G1 技术栈与依赖** | 通过：Java 17 / Spring Boot 3.4.5 / Vue 3.4 / Vite 5 / MySQL 8 / Redis 3.2.100 均为既定栈；不引入 MQ | 通过：新增 5 类依赖/插件已在上表完成架构评审；D1/D2 使短信 SDK、同步客户端依赖为 0；Redis 具体版本已落地 | `server/pom.xml`、`web/package.json`、本文件「架构评审记录」、`research.md` R-03/R-05 |
| **G2 分层与目录** | 通过：`server/` + `web/` 布局、前端目录固定、角色独立 axios 实例与 Pinia `localStorage` 初始化已在骨架具备 | 通过：Controller 只做校验 + 调 Service；Service 编排事务与二次校验；Repository 持久化；`PatientIdExtractor` 统一取身份；前端路由词干错开（`/patient/*` 与 `/system/health`）；多表变更在同一 `@Transactional`（注册 / 实名认证 / 重置） | `research.md` R-09/R-10、本文件「Project Structure」、`data-model.md` 第 3 节 |
| **G3 契约与 API** | 通过（前置条件）：本阶段产出 `contracts/` 与 `data-model.md` 后再进入实现 | 通过：8 个端点均为复数资源路径 + 正确 HTTP 语义；统一 `{ code, message, data }`；枚举与 `ErrorCode` 唯一来源为 `data-model.md`；日期 `yyyy-MM-dd'T'HH:mm:ss`；前端具体路径为 `/v1/...`，运行时拼为 `/api/v1/...`，无 `/api/api/...` | `contracts/README.md`、`contracts/patient-identity-api.md`、`contracts/mocks.md`、`data-model.md` |
| **G4 安全** | 通过（设计约束）：BCrypt ≥ 10、JWT ≤ 24h + Refresh Token、模糊失败提示、`ErrorCode` 统一 message、日志脱敏 | 通过：`AUTHENTICATION_FAILED` 单一文案覆盖所有鉴权类失败；格式类失败各用 `ErrorCode` 固定文案且不含账户存在性；`SameSite=Lax` + 非 HttpOnly CSRF Cookie 双重校验；Refresh Token 存 HttpOnly Cookie 且一次轮换；固定验证码仅 dev/test 且 prod fail-fast；身份证号以 HMAC 摘要 + 密文存储 | `data-model.md` 1/2/4、`research.md` R-01/R-07/R-08、`contracts/README.md` |
| **G5 代码规范** | 通过（门禁待实现阶段执行）：ESLint / Prettier 前端已配置并验证零告警 | 通过（设计约束）：后端公共方法 Javadoc + SLF4J、禁 `System.out.println`、Checkstyle / SpotBugs 纳入构建；前端文件小写横线 + `<script setup>` + `<style scoped>`；import 冲突用 `as` 别名；无 `@Enumerated` 与 `@Converter` 并存（枚举统一用显式 `@Converter`，且处理 null / 未知值降级）；功能名冻结不变 | `.specify/memory/constitution.md` 原则 V、`research.md` R-11、本文件「Constraints」 |
| **G6 测试门禁** | 通过（门禁待实现阶段执行）：`checklists/requirements.md` 41 项全部 `[x]`，风险项已映射到测试用例 | 通过（设计约束）：`contracts/mocks.md` 提供 M1–M15 供 `page.route()` 与前端 mock 同步；真实后端 E2E 断言 `code`；随机手机号 + 测试夹具动态写入白名单表；Testcontainers 走同套 Flyway 脚本；覆盖率 ≥ 70%、全部 REST API 正向/异常集成测试、Playwright 覆盖 4 个用户故事、报告落 `testsdoc/patient-identity/` | `spec.md` 8.1、`contracts/mocks.md`、`quickstart.md`、`research.md` R-02/R-08 |

**门禁结论**：G1–G6 无阻断项。Phase 1 设计新增的复杂度（固定验证码的生产安全边界、预置白名单与动态测试数据的共存、新增依赖、双重 Cookie）已登记在下方 Complexity Tracking，未登记项视为无。

## Project Structure

### Documentation (this feature)

```text
specs/001-patient-identity/
├── plan.md              # 本文件（/speckit.plan 输出）
├── research.md          # Phase 0 输出（/speckit.plan）
├── data-model.md        # Phase 1 输出（/speckit.plan）
├── quickstart.md        # Phase 1 输出（/speckit.plan）
├── contracts/           # Phase 1 输出（/speckit.plan）
│   ├── README.md                  # 通用约定、错误码索引、契约一致性清单
│   ├── patient-identity-api.md    # 8 个端点请求/响应结构
│   └── mocks.md                   # M1–M15，前端 mock 与 E2E 唯一来源
├── spec.md              # /speckit.specify + /speckit.clarify 输出（本阶段同步 7.1 / 9.3 / 9.4）
├── checklists/
│   └── requirements.md  # /speckit.specify 输出，审查方所有，本阶段不修改
└── tasks.md             # Phase 2 输出（T001–T114，按用户故事分阶段、测试先行）
```

### Source Code (repository root)

依 `.specify/memory/constitution.md`（原则 II）固定布局，本计划不得改用其他根目录：

```text
server/                                      # Java 17 + Spring Boot 3.4.5，严格分层
├── src/main/java/com/sdas/server/
│   ├── common/                              # Result / ErrorCode / BusinessException（已存在，扩展 ErrorCode）
│   │   └── PatientIdExtractor.java          # 统一 JWT 身份提取，禁止 Controller 自行解析
│   ├── exception/GlobalExceptionHandler.java# 已存在；新增 429 / 403 分支仍走 ErrorCode
│   ├── config/                              # 已存在 CorsConfig / JacksonConfig / RedisConfig；新增 SecurityBeans / CsrfCookieFilter
│   ├── identity/
│   │   ├── controller/                      # PatientController / PatientSessionController / IdentityVerificationController
│   │   ├── service/ + service/impl/         # 业务编排、@Transactional、二次校验
│   │   ├── repository/                      # JdbcTemplate 持久化（6 张表）
│   │   ├── entity/ 与 model/                # 枚举引用 specs/001-patient-identity/data-model.md
│   │   └── dto/                             # 请求/响应 DTO，字段与 contracts/ 一一对应
│   └── health/                              # 既有健康检查模块，不改
├── src/main/resources/
│   ├── application.yml / application-dev.yml / application-test.yml / application-prod.yml
│   ├── db/migration/                        # V1__identity_schema.sql（dev/test/prod 共用）
│   └── db/seed/                             # V2 白名单演示种子，仅 dev 的 spring.flyway.locations 加载；test/prod 不加载，测试数据由夹具动态写入
└── src/test/java/.../                       # JUnit5 + Mockito + Spring Boot Test + Testcontainers

web/                                         # Vue 3.4 + Vite 5 + Pinia 2 + Vue Router 4 + TypeScript
├── src/api/
│   ├── request/                             # public / patient / doctor / pharmacy 独立实例（已存在）
│   ├── types.ts / error-codes.ts            # 从 contracts/ + data-model.md 同步并标注来源
│   ├── mocks/                               # 从 contracts/mocks.md 同步，禁止凭空手写
│   └── patient-identity.ts                  # 8 个端点封装；具体路径 /v1/...，不含 /api
├── src/views/                               # patient-register / patient-login / patient-password-reset /
│                                            # patient-identity-verification / patient-profile /
│                                            # consultation-entry-view（门禁占位入口，T092；禁止直接调 axios）
├── src/composables/                         # usePatientSession / useVerificationCode / useIdentityVerification
├── src/stores/patient.ts                    # 创建时从 localStorage 恢复会话，防刷新后守卫误拦截
├── src/components/                          # 公共表单 / 状态卡片；<style scoped>；≤ 300 行
├── src/router/index.ts + guards.ts          # 词干错开；requiresAuth / requiresVerified 守卫
└── src/assets/styles/                       # 响应式（640px 断点，PC + 手机）
└── tests/                                   # Vitest 单测 + Playwright E2E

testsdoc/patient-identity/[测试名].md          # 测试报告
```

**Structure Decision**：沿用宪法原则 II 固定的 `server/` + `web/` 双根布局，不新增子项目。后端在既有 `com.sdas.server` 包下新增 `identity` 业务包（与既有 `health` 包平级），复用 `common/Result`、`common/ErrorCode`、`exception/GlobalExceptionHandler`，避免重复造统一响应；数据库迁移集中放 `server/src/main/resources/db/migration` 以同时满足 test / prod 同套脚本。前端在既有 `src/api/request/` 的角色实例基础上新增 `patient-identity.ts` 与 `mocks/`，不新增 axios 实例、不新增状态库。固定验证码与预置白名单均为**配置 / 数据**变化；演示种子另置 `server/src/main/resources/db/seed/` 以隔离 dev 环境（见 Project Structure 目录树），不新增子项目或模块，故结构复杂度不上升。

## 阶段说明

- **Phase 0（已完成）**：`research.md` 闭环 D1 / D2，并关闭宪法「解释与待决项」中的 AI 模型形态与 Redis 版本两项；输出新增依赖评审、失败降级、双重 Cookie、测试策略结论。无 `NEEDS CLARIFICATION` 残留。
- **Phase 1（已完成）**：`data-model.md`（枚举 / `ErrorCode` 唯一来源 + 表结构 + Redis key + 状态流转）、`contracts/`（8 端点 + M1–M15 mock + 一致性验证清单）、`quickstart.md`（本地启动与验证步骤）；同步 `spec.md` 的假设、边界、风险与第 9.4 节决策记录。
- **Phase 1 后门禁复核（已完成）**：见上方 Constitution Check 表第三列。
- **Phase 2（`tasks.md` 已生成）**：`tasks.md` 共 114 条任务，按 Setup → Foundational → US1 / US2 / US3（P1）→ US4（P2）→ Polish 编排；实现阶段按宪法 Development Workflow 先写测试用例（先失败）→ 实现 → 代码审查 → 静态检查 → 测试报告 → 契约一致性验证。

## Complexity Tracking

> G1–G5 无违规；G6 登记 1 项已批准豁免（开发机无 Docker 时的集成测试基础设施，见下表最后一行）。下表登记设计引入的复杂度决策及其必要性，避免后续阶段被误当成「更简单的实现」而回退。

| Complexity / 需跟踪项 | Why Needed | Simpler Alternative Rejected Because |
|-----------------------|------------|-------------------------------------|
| 固定验证码仅允许 dev/test，prod 启动 fail-fast（`sdas.identity.verification-code.fixed-enabled`） | D1 要求不接第三方短信；但固定值一旦进入生产等于认证形同虚设，违反原则 IV | 全局无条件启用固定验证码：生产环境任何人可用 `888888` 通过注册 / 重置，直接破坏账户安全；把固定值写死在代码里且不做环境校验同样不可审计 |
| 固定验证码只替换「生成 + 发送」，Redis 仍保存 issuing / used / purpose / 尝试次数 | FR-003、FR-017、SC-008 要求一次性、用途隔离、频次限制与幂等，不能因验证码内容固定而取消 | 直接比对常量后放行：允许无限重放、跨用途复用，违反 FR-003 且使 FR-017 / SC-008 不可测 |
| Flyway 预置白名单 + 测试夹具动态写入同一张表 | D2 要求数据库预置数据；宪法 VI 要求真实后端 E2E 使用动态数据、禁止硬编码真实身份信息 | E2E 直接复用预置演示记录：静态数据会被并发用例污染，且存在写入真实身份信息的合规风险；为每个用例建独立脚本：test / prod 不再同套迁移 |
| 新增 5 类依赖 / 插件（BCrypt、JWT、Flyway、Testcontainers、Checkstyle/SpotBugs） | 原则 I、IV、V、VI 的实现前置条件，现有骨架均不具备 | 沿用骨架现状：无法满足 BCrypt ≥ 10、JWT ≤ 24h、同套 Flyway 脚本、Testcontainers 集成测试与静态检查零告警门禁 |
| Refresh Token 存 HttpOnly Cookie + 非 HttpOnly CSRF Cookie 双重校验，access token 由前端存 `localStorage` 注入 `Authorization` | 原则 III 要求 Bearer 头，原则 IV 要求 `SameSite=Lax` + 双重 Cookie；把续期凭证留在 JS 可读位置会放大 XSS 影响 | 把 refresh token 也放响应体交前端保存：可被任意脚本读取；仅靠 SameSite 不做双重 Cookie 校验：不满足原则 IV 明文要求 |
| 全局 `ErrorCode` 新增 11 个取值，`data-model.md` 作为最新唯一权威来源 | 需区分「鉴权类统一模糊」「格式类可操作说明」「频次限制」「依赖降级」，同时保持 message 全部来自 `ErrorCode` | 全部失败都返回 `BAD_REQUEST`：与 SC-005 要求的「格式类可给规则说明」冲突，且会把白名单未命中与密码错误之外的信息混在一起；在 Controller / Service 硬编码文案：违反原则 IV |
| 开发机无 Docker / WSL 时，以 `IDENTITY_TEST_USE_LOCAL_INFRA=true` 改用本机 MySQL 8.0.46 + Redis 3.2.100 直连运行集成测试（G6 豁免） | 该机器无法运行 Testcontainers，而 `FoundationalIT` 与后续契约 / 集成测试必须可执行；本机实例版本与容器镜像（mysql:8.0 / redis:3.2-alpine）属同一协议族，Flyway `V1` 脚本与 test/prod 完全一致 | 禁用 Flyway 或改用 H2 内存库：违反宪法 VI「test/prod 同套迁移脚本」；直接跳过集成测试：T035 地基自检与 8 端点契约测试无法验证，G6 门禁失效；等待装 Docker 再开发：阻塞实现阶段，且本机已有同版本基础设施。CI 仍默认容器模式，豁免仅限本地开发 |

## 下一步

1. 人工复核本计划、`research.md`、`data-model.md`、`contracts/` 与 `quickstart.md`（对应 workflow.yml 的 `review-plan` 门禁）。
2. 按 `tasks.md` 执行 Phase 1 Setup 与 Phase 2 Foundational（T001–T036），二者完成前不开始任何用户故事。
3. 按优先级依次实现 US1（T037–T057）→ US2（T058–T076）→ US3（T077–T092）→ US4（T093–T104），最后执行 Phase 7（T105–T114）；`checklists/requirements.md` 属审查方产物，实现阶段不得修改其勾选状态。
