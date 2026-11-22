# 测试报告归档（T112）

模块：`001-patient-identity`（患者身份体系：注册/登录/退出/续期/实名认证/忘记密码）。
测试运行环境：`IDENTITY_TEST_USE_LOCAL_INFRA=true`（本机 MySQL 8.0.46 + Redis 3.2.100，测试库 `cloud_consultation_test` / Redis DB 1，固定验证码 `888888`）。
前置检查：运行前须确认 `redis-cli PING` 返回 `PONG`；Redis 掉线会让所有依赖缓存的契约/集成测试统一降级 503。

## 1. 后端（`server`）

| 类别 | 文件 | 用例数 | 结果 |
|---|---|---|---|
| 契约测试 | `PatientRegisterContractTest` | 5 | ✅ |
| 契约测试 | `PatientLoginContractTest` | 4 | ✅ |
| 契约测试 | `PatientLogoutContractTest` | 3 | ✅ |
| 契约测试 | `RefreshTokenContractTest` | 3 | ✅ |
| 契约测试 | `VerificationCodeContractTest` | 5 | ✅ |
| 契约测试 | `IdentityVerificationContractTest` | 7 | ✅ |
| 契约测试 | `IdentityStatusContractTest` | 4 | ✅ |
| 契约测试 | `PasswordResetContractTest` | 5 | ✅ |
| 单元测试 | `AuditEventServiceTest` / `CryptoServiceTest` / `IdempotencyServiceTest` / `JwtTokenServiceTest` / `MaskServiceTest` / `RateLimitServiceTest` / `VerificationCodeServiceTest` | 各 3–6 | ✅ |
| 单元测试 | `PatientAccountServiceTest` | 4 | ✅ |
| 单元测试 | `PatientSessionServiceTest` | 4 | ✅ |
| 单元测试 | `IdentityVerificationServiceTest` | 8 | ✅ |
| 单元测试 | `PasswordResetServiceTest`（含 `revokedMarkerUsesAccessTokenTtl`） | 6 | ✅ |
| 集成测试 | `FoundationalIT` | 7 | ✅ |
| 集成测试 | `RegisterFlowIT` | 4 | ✅ |
| 集成测试 | `SessionFlowIT` | 4 | ✅ |
| 集成测试 | `IdentityVerificationFlowIT` | 5 | ✅ |
| 集成测试 | `PasswordResetFlowIT` | 4 | ✅ |

**合计：单元测试 94 例 + 集成测试 24 例 = 118 例，全部通过。**
命令：`mvn -B verify`（94 例）与 `mvn -B -Dtest=...FlowIT test`（24 例）。

## 2. 前端（`web`）

| 类别 | 文件 | 用例数 | 结果 |
|---|---|---|---|
| 单元测试（Vitest） | 16 个 spec：6 个页面视图（注册/登录/个人中心/实名认证/重置密码/门禁占位入口）、3 个公共组件、3 个 composable、1 个 store、API 封装、路由表与守卫 | 63 | ✅ |
| Mock E2E（Playwright） | `health` / `patient-register` / `patient-session` / `patient-identity` / `patient-password-reset` | 17 | ✅ |
| 真实后端 E2E（Playwright） | 4 个故事各 1 套件（`E2E_REAL_BACKEND=1`） | 4 | ✅ |
| 生产构建 | `npm run build`（`vue-tsc --noEmit && vite build`） | — | ✅ 145 modules，`dist/` 185 KB JS / 7 KB CSS |

- `npm run test:e2e`（默认 Mock 模式）：**17 passed / 5 skipped**。
- `E2E_REAL_BACKEND=1 npm run test:e2e`：**4 passed / 18 skipped**（Mock 套件在该模式自动跳过），耗时约 1.2 min（含跨过 60 s 发送限流窗口）。
- 真实 E2E 前置：本机 MySQL / Redis / Spring Boot（dev, 8080）/ Vite（5183，Playwright `webServer` 自动拉起），`E2E_MYSQL_CLI` 指向本机 mysql 客户端绝对路径；白名单由夹具动态生成并清理，不使用演示种子。

## 3. 静态检查（T109）

| 工具 | 结果 |
|---|---|
| `mvn -B verify` → Checkstyle | ✅ 0 违规 |
| `mvn -B verify` → SpotBugs | ✅ `Error size is 0` / `No errors/warnings found` |
| `mvn -B verify` → JaCoCo check | ✅ LINE ≥ 70% 门禁通过 |
| `npm run lint` | ✅ 0 警告 |
| `npm run typecheck` | ✅ 通过 |
| `npm run format:check` | ✅ All matched files use Prettier code style |
| `mvn -B verify` / `npm run build` 整体 | ✅ BUILD SUCCESS |

SpotBugs 原 41 项已清零：实际修复 3 类真实缺陷（`DM_CONVERT_CASE`、`NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`、`PageResult` 内部可变对象暴露），其余为 Spring 构造器注入字段 `EI_EXPOSE_REP2`（34 项）与 `PreparedStatementCreator` lambda `OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE`（4 项）两类框架误报，已在 `config/spotbugs/spotbugs-exclude.xml` 中按已知组件包/方法精准豁免（getter 暴露 `EI_EXPOSE_REP` 不在豁免范围）。详见 `code-review-security.md`。

## 4. 覆盖率（T105/T106）

| 侧 | 工具 | INSTRUCTION / STATEMENTS | LINE | BRANCH | FUNCTIONS |
|---|---|---|---|---|---|
| 后端 | JaCoCo 0.8.12（`mvn verify`，单轮干净探针） | 91.99% | **88.40%** | 73.06% | — |
| 前端 | Vitest V8（`npm run test:coverage`） | 89.03% | **89.03%** | 84.17% | 81.94% |

- 后端覆盖全部 9 个 Service 与 8 个端点契约；`pom.xml` 的 `jacoco-check` 绑定 `verify`，强制 BUNDLE LINE ≥ 0.70。
- 前端覆盖 3 个 composable、1 个 store、6 个页面视图、3 个公共组件、8 个 API 封装与路由表；`vite.config.ts` 的 `coverage.thresholds` 四维均设为 70%。
- 统计范围排除 `src/main.ts`、`*.d.ts` 与 `src/api/mocks/**`（Playwright E2E 的测试替身数据，非生产逻辑）。
- 注意：`jacoco.exec` 默认为 append 模式，多轮 `mvn verify` 会累积执行数据使读数偏高。出正式报告前先删除 `server/target/jacoco.exec` 再跑一次 `mvn -B verify`，即可得到单轮准确值。

## 5. 相关文档

- 契约一致性：`contract-conformance.md`
- 代码审查与安全抽样：`code-review-security.md`
- quickstart 第 6 节逐条验证：`manual-qa-checklist.md`
