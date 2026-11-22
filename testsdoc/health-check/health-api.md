# 测试报告：健康检查 API

**功能**：health-check
**契约**：`specs/health-check/contracts/health-api.md`
**数据模型**：`specs/health-check/data-model.md`
**创建日期**：2026-11-07
**执行状态**：自动化用例已执行通过；真实组件 E2E 待环境就绪（见「环境问题」）

## 测试用例

| 编号 | 类型 | 用例 | 预期 | 来源 | 结果 |
|------|------|------|------|------|------|
| T1 | 单元 | 三探针全部正常 | `code=200`、`status=UP`、3 个组件、checkedAt 非空 | M1 | 通过 |
| T2 | 单元 | 数据库探针抛异常 | `code=503`、database=DOWN、detail=unavailable、redis=UP | M2 | 通过 |
| T3 | 单元 | Redis 探针抛异常 | `code=503`、redis=DOWN | M2 变体 | 通过 |
| T4 | 单元 | 两个探针同时异常 | `code=503`、server=UP | M2 变体 | 通过 |
| T5 | 单元 | SELECT 1 返回非 1（二次校验） | `code=503`、database=DOWN | 宪法原则 II | 通过 |
| T6 | 集成 | `GET /api/health` 全 UP | HTTP 200 + `code=200` + 组件顺序 + ISO 8601 | M1 | 通过 |
| T7 | 集成 | `GET /api/health` 数据库 DOWN | HTTP 503 + `code=503` + detail=unavailable | M2 | 通过 |
| T8 | 集成 | `POST /api/health` | HTTP 405 + `code=405` | M3 | 通过 |
| T9 | 契约一致性 | 端点/code/字段结构/日期格式/枚举/脱敏逐项比对 | 契约清单 6 项 | contracts | 部分通过（见下） |

## 执行结果

- 命令：`mvn -B -f server/pom.xml test`（本地 `.m2/wrapper` 中的 Apache Maven 3.9.16 + OpenJDK 17.0.20.1）。
- 结果：**BUILD SUCCESS**；`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。
- T9 已由 MockMvc 断言覆盖：端点暴露、`code` 值、三组件字段与顺序、`checkedAt` 的 ISO 8601 格式、`UP`/`DOWN` 枚举、`detail` 脱敏；端点与真实后端响应的离线比对仍需在 CI 中执行一次。

## 环境问题（不属代码缺陷）

- 本机 `127.0.0.1:3306` 端口开放，但使用 `root/123456` 连接 `cloud_consultation` 返回 `ERROR 1045 (28000): Access denied`。请核对账号密码、root 的 host 授权及数据库是否已创建；配置按要求保留 `root/123456`，未擅自更改。
- 本机 `127.0.0.1:6379` 未开放且无 `redis-cli`，Redis PING 的真实链路未验证。
- 因此当前环境直连启动时 `/api/health` 预期为 `503 / DOWN`（database、redis 之一或全部不可用），符合契约 M2；全部 `UP` 需 MySQL 与 Redis 均就绪。

## 执行方式

```bash
cd server
mvn -B test
```

## 宪法符合性说明与后续项

- 已落地：测试先行用例、Mock 数据从 `contracts/` 同步、真实后端 E2E 断言响应 `code` 值、日期格式断言、`application-test.yml` 未禁用 Flyway。
- 待补充（需 Docker 与 CI）：按宪法原则 VI 追加 Testcontainers（MySQL + Redis）真实组件用例，覆盖连接池超时场景；Spec 阶段 checklist 风险项「API 超时」落地为超时测试；每迭代执行一次契约一致性离线比对。
- 健康检查不涉及用户数据写入，「动态数据（随机手机号）」与「支付恢复/多 Tab 并发」风险项不适用；后续业务功能须按宪法补齐。
- 前端尚未建立，Playwright 用户故事用例与 `page.route()` Mock E2E 在 `web/` 建立后补充，Mock 直接使用 contracts 的 M1–M3。
- 静态检查：Checkstyle/SpotBugs 与 JaCoCo（覆盖率 ≥ 70%）需按团队规则集接入 CI 后出报告，本骨架未内置规则文件。
