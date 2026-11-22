# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]

**Primary Dependencies**: [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]

**Storage**: [if applicable, e.g., PostgreSQL, CoreData, files or N/A]

**Testing**: [e.g., pytest, XCTest, cargo test or NEEDS CLARIFICATION]

**Target Platform**: [e.g., Linux server, iOS 15+, WASM or NEEDS CLARIFICATION]

**Project Type**: [e.g., library/cli/web-service/mobile-app/compiler/desktop-app or NEEDS CLARIFICATION]

**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]

**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]

**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

依 `.specify/memory/constitution.md`（v1.0.0）逐项校验以下门禁：

- **G1 技术栈与依赖**：仅使用批准的 Java/Spring/Vue/MySQL/Redis/DeepSeek 技术栈，新增依赖有架构评审记录。
- **G2 分层与目录**：`server/` + `web/`、Controller → Service → Repository、角色独立 axios 实例、`XxxIdExtractor`、Pinia `localStorage` 初始化、路由词干无冲突。
- **G3 契约与 API**：`contracts/`、`data-model.md` 先于实现存在；统一响应、分页结构、枚举、ISO 8601 日期、`/api` 前缀规约与实现一致。
- **G4 安全**：BCrypt ≥ 10、JWT ≤ 24h + Refresh Token、模糊失败提示、`ErrorCode` 统一异常、审计日志脱敏。
- **G5 代码规范**：Checkstyle/SpotBugs、ESLint/Prettier；无 `@Enumerated` 与 `@Converter` 并存；代码审查记录。
- **G6 测试门禁**：覆盖率 ≥ 70%、全量 REST API 正向/异常集成测试、Playwright 覆盖全部用户故事、风险项测试用例、`testsdoc/` 报告、契约一致性验证。

任一门禁未通过，须在下方 Complexity Tracking 表登记理由与被否决的更简单方案；未登记即阻断。

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

依 `.specify/memory/constitution.md`（原则 II）固定布局，plan.md 不得改用其他根目录：

```text
server/                         # Java 17 + Spring Boot 3.x，严格分层
└── src/main/java/<package>/
    ├── controller/             # 仅参数校验 + 调用 Service + 返回响应
    ├── service/                # 业务编排、@Transactional、二次校验
    ├── repository/             # 持久化
    ├── entity/                 # 枚举引用 specs/[###-feature]/data-model.md
    └── common/                 # ErrorCode、XxxIdExtractor、统一响应
└── src/test/                   # JUnit5 + Mockito + Spring Boot Test/Testcontainers

web/                            # Vue 3.4+ + Vite 5+ + Pinia + Vue Router 4
└── src/
    ├── api/                    # 唯一请求出口；角色独立 axios 实例；路径不含 /api
    ├── components/
    ├── views/                  # 禁止直接调 axios
    ├── stores/
    ├── composables/
    ├── router/
    └── assets/
└── tests/                      # Vitest + Playwright（page.route() mock E2E）

specs/[###-feature]/
├── contracts/                  # 请求/响应结构；Mock 数据同步来源
└── data-model.md               # 状态枚举唯一来源

testsdoc/[功能名]/[测试名].md     # 测试报告
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
