# 测试报告：AI健康咨询（005）

**日期**：2026-11-08
**分支**：`005-ai-health-consultation`
**输入**：`specs/005-ai-health-consultation/`（spec / plan / data-model / contracts / tasks）

---

## 1. 构建与静态检查

| 项 | 命令 | 结果 |
|----|------|------|
| 后端编译 | `mvn -o -q compile` | 通过 |
| 后端测试编译 | `mvn -o -q test-compile` | 通过 |
| 前端类型 | `npm run typecheck`（vue-tsc） | 通过，0 错误 |
| 前端 ESLint | `npm run lint`（`--max-warnings 0`） | 通过，0 警告 |
| 前端格式化 | `npx prettier --check`（由 lint 覆盖） | 通过 |

> 说明：`src/views/pharmacy-*.vue` 与 `src/api/mocks/pharmacy.ts` 存在 004 遗留的 prettier 告警，本次一并格式化（纯格式，无行为变更），以满足「零警告」门禁。

---

## 2. 后端单元测试（已执行）

命令：`mvn -o test -Dtest='com.sdas.server.healthconsultation.unit.*Test'`

| 测试类 | 用例数 | 结果 | 覆盖点 |
|--------|--------|------|--------|
| `EmergencyHeaderParserTest` | 4 | 通过 | `EMERGENCY: YES/NO` 解析、全角冒号、缺失/空内容 → `UNAVAILABLE`、正文剥离 |
| `EmergencyRuleMatcherTest` | 4 | 通过 | 关键词命中、普通不适不命中、多规则取最高级别、空关键词跳过 |
| `EmergencyDecisionResolverTest` | 5 | 通过 | M5 矩阵五行（`CONFIRMED`/`REJECTED`/`UNAVAILABLE`/`MODEL_ONLY`/不产生留痕） |
| `EmergencySymptomServiceTest` | 7 | 通过 | 规则+模型合并、留痕写入、`booking` 反查与缺失、`MODEL_ONLY`、`message_id` 回填 |
| `HealthChatClientImplTest` | 3 | 通过 | 正常增量透传、上游错误 → `UPSTREAM_ERROR`、空流 → `EMPTY`（无兜底文案） |
| `HealthChatServiceImplTest` | 3 | 通过 | **`emergency` 先于首个 `delta`**、普通问答不预警、失败落 `FAILED` + `error` 事件 + 审计 |
| `MedicalContextAssemblerTest` | 3 | 通过 | 无记录固定文案、摘要聚合（科室/医生/药名/注意事项）、超预算退化为「最近一次主诉」且 `truncated` |
| `KnowledgeRetrieverTest` | 2 | 通过 | 2/3-gram 候选词生成与上限、空输入不查询 |
| `HealthPromptBuilderTest` | 3 | 通过 | 首行协议/处方边界/医院信息/知识版本、知识空命中要求「依据有限」、窗口超预算最近优先 |
| `HealthDisclaimerTest` | 1 | 通过 | 文案逐字等于「仅供参考，不构成医疗诊断」 |
| **合计** | **35** | **全绿** | — |

关键断言（与验收标准对应）：

- **SC-002**：免责声明文案逐字一致（`HealthDisclaimerTest`）。
- **SC-003**：`UNAVAILABLE` 分支必须 `warned=true`（`EmergencyDecisionResolverTest`）。
- **SC-008**：判定留痕写入且 `message_id` 回填（`EmergencySymptomServiceTest`）。
- **FR-019**：事件顺序 `accepted` → `emergency` → `delta` → `disclaimer` → `done`（`HealthChatServiceImplTest`）。

---

## 3. 前端单元测试（已执行）

命令：`npx vitest run`

| 测试文件 | 用例数 | 结果 | 覆盖点 |
|----------|--------|------|--------|
| `tests/unit/sse-parser.spec.ts` | 4 | 通过 | 单块多帧切分、跨块半帧缓冲、多行 `data` 拼接、注释帧忽略 |
| `tests/unit/health-consultation-route.spec.ts` | 3 | 通过 | `/health-consultation` 注册、`requiresAuth`、与 `/appointment/consultation/:id` 不冲突 |
| **全量回归** | **70**（18 文件） | **全绿** | 既有 001–004 前端单测未受影响 |

---

## 4. 已编写但未执行的测试（需环境）

| 测试 | 文件 | 未执行原因 |
|------|------|-----------|
| 地基自检 | `server/.../integration/FoundationalIT.java` | 需 MySQL 8 + Redis 3.2（Testcontainers 或 `IDENTITY_TEST_USE_LOCAL_INFRA=true`） |
| US1 Mock E2E | `web/tests/e2e/health-consultation-chat.mock.spec.ts` | 需 Playwright 浏览器与预览服务 |
| US2 Mock E2E | `web/tests/e2e/health-emergency.mock.spec.ts` | 同上 |

执行方式：

```powershell
cd server
$env:IDENTITY_TEST_USE_LOCAL_INFRA = "true"
mvn -o test -Dtest=FoundationalIT

cd ../web
npx playwright test tests/e2e/health-consultation-chat.mock.spec.ts tests/e2e/health-emergency.mock.spec.ts
```

---

## 5. 尚未实现的任务（诚实清单）

| 任务 | 内容 | 原因 / 建议 |
|------|------|-------------|
| T007 | `HealthConsultationTestSupport` 测试基座 | 当前 `FoundationalIT` 直接继承 `IdentityTestSupport`；待写契约/集成测试时补齐（含 SSE 取帧工具） |
| T008 | 规格文档修订（`departmentCode`→`departmentName`、`V9`+`V10`、去 `enabled`） | **代码已按修订后实现，文档待同步**（下一步立即执行） |
| T034 | `ChatSseContractTest`（MockMvc 契约） | 事件顺序已由 `HealthChatServiceImplTest` 覆盖；MockMvc 版待补 |
| T040 | `ChatFlowIT` | 需容器；多轮上下文/并发锁/超长输入需真实 DB+Redis |
| T042 / T060 / T072 | 真实后端 E2E | 需真实后端与 `DEEPSEEK_API_KEY` |
| T058 | `EmergencyFlowIT` | 需容器；判定矩阵已在单测覆盖，留痕表断言待集成验证 |
| T069 / T070 | 会话管理契约/集成测试 | 端点已实现，测试待补 |
| T071 / T079 | 会话管理 E2E 与前端单测 | 组件已实现，测试待补 |
| T081–T088 | 覆盖率、SpotBugs、Checkstyle、契约一致性、安全自查、性能、quickstart、全量回归 | 收口阶段任务，需完整环境与人工核对 |
| T090 | 代码审查 | 待评审 |

---

## 6. 与契约/数据模型的偏差（已登记）

1. **知识检索仓储位置**：未扩展 002 的 `DepartmentDiseaseKbRepository`，而是新建
   `healthconsultation/repository/KnowledgeRepository.java`。理由：避免 002 模块反向依赖 005 包；
   该仓储只读同一张表，不修改结构。`data-model.md` 第 3 节的「只读复用」仍然成立。
2. **医生姓名查询**：新建 `healthconsultation/repository/DoctorNameRepository.java` 只读 `doctor.name`
   （002 已约定该列是脱敏姓名），未改动 003 的仓储职责。
3. **规则科室反查**：`EmergencyRule` 使用 `departmentName`（匹配 `department.name`），因 `department`
   表无 `code` 列且 005 只读依赖其结构。
4. **锁降级语义**：`RedisDialogStore.lock()` 返回 `LockOutcome(acquired, storeAvailable)`，
   区分「锁被占用（409 CHAT_BUSY）」与「Redis 不可用（无锁放行，对话不中断）」。

---

## 7. 结论

- 后端：编译通过，35 个单元测试全绿，核心链路（流式编排、事件顺序、上下文预算、知识检索、
  紧急判定矩阵、免责声明）均已覆盖。
- 前端：类型检查与 lint 零错误零警告，70 个单元测试全绿；页面与组件按 US1–US3 拆分完成。
- 未完成项集中在「需容器/真实后端的集成与 E2E」与「收口阶段的静态检查/性能/文档同步」，
  已在第 5 节逐条登记，不虚报完成度。
