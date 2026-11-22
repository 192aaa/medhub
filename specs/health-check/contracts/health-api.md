# 契约：健康检查 API

**功能名**：health-check
**依据**：`.specify/memory/constitution.md` v1.0.0（原则 III、IV、VI）
**数据模型**：`specs/health-check/data-model.md`（`HealthStatus`、`ErrorCode`、`Result<T>`、`HealthReport`、`ComponentStatus`）
**契约地位**：前后端唯一参考；实现与本文档不一致即视为缺陷，变更接口必须先改本文件并同步 mock 与测试。

## GET /api/health

- **用途**：同时验证服务端进程、MySQL（`SELECT 1`）、Redis（`PING`）。
- **认证**：无需 `Authorization: Bearer <token>`（基础设施探针）；后续接入 JWT 时须在安全白名单中放行。
- **请求**：无路径参数、无 query、无 body。
- **HTTP 语义**：仅 `GET`；其余方法返回 405。
- **响应状态**：全部 `UP` → `200`；任一组件 `DOWN` → `503`；`code` 与 HTTP 状态一致。
- **前端调用路径**（宪法 III 前缀规约）：`src/api/` 的 baseURL 为 `/api`，故具体路径必须写 `/health`，运行时拼接为 `/api/health`；禁止写 `/api/health` 造成 `/api/api/health`。
- **响应体**：`{ code, message, data }`，`message` 取自 `ErrorCode`（见 data-model.md 1.2）。
- **日期格式**：`data.checkedAt` 为 `yyyy-MM-dd'T'HH:mm:ss`，ISO 8601 字符串，禁止时间戳数组。

### 响应结构

| 路径 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `$.code` | int | 是 | 200 / 503 / 405 / 500 等 |
| `$.message` | String | 是 | 来自 `ErrorCode` |
| `$.data.status` | string | 是 | `UP` / `DOWN`，枚举见 data-model.md 1.1 |
| `$.data.components` | array | 是 | 固定 3 项，顺序 `server`、`database`、`redis` |
| `$.data.components[].name` | string | 是 | `server` / `database` / `redis` |
| `$.data.components[].status` | string | 是 | `UP` / `DOWN` |
| `$.data.components[].detail` | string | 是 | `UP` 为探针结果；`DOWN` 固定 `unavailable` |
| `$.data.components[].elapsedMs` | number | 是 | >= 0 |
| `$.data.checkedAt` | string | 是 | ISO 8601 |

## Mock 数据（测试与前端 mock 的唯一来源，禁止凭空手写）

### M1 全部可用（HTTP 200）

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "status": "UP",
    "components": [
      { "name": "server", "status": "UP", "detail": "running", "elapsedMs": 0 },
      { "name": "database", "status": "UP", "detail": "SELECT 1 = 1", "elapsedMs": 3 },
      { "name": "redis", "status": "UP", "detail": "PING = PONG", "elapsedMs": 1 }
    ],
    "checkedAt": "2026-11-07T10:31:15"
  }
}
```

### M2 数据库不可用（HTTP 503）

```json
{
  "code": 503,
  "message": "依赖组件不可用",
  "data": {
    "status": "DOWN",
    "components": [
      { "name": "server", "status": "UP", "detail": "running", "elapsedMs": 0 },
      { "name": "database", "status": "DOWN", "detail": "unavailable", "elapsedMs": 2001 },
      { "name": "redis", "status": "UP", "detail": "PING = PONG", "elapsedMs": 1 }
    ],
    "checkedAt": "2026-11-07T10:31:17"
  }
}
```

### M3 方法不允许（HTTP 405，`POST /api/health`）

```json
{ "code": 405, "message": "请求方法不支持", "data": null }
```

## 契约一致性验证清单（每迭代执行）

1. 端点暴露：仅 `GET /api/health` 可用，`POST` 返回 405。
2. `code` 值：UP=200、DOWN=503、参数/方法/服务异常分别对应 400/405/500，且与 HTTP 状态一致。
3. 字段结构：`data.components` 恒为 3 项且顺序固定；`data` 不为 `null`（降级时也返回报告）。
4. 日期格式：`checkedAt` 匹配 `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$`。
5. 枚举：响应中只出现 `UP` / `DOWN`，与 `data-model.md` 1.1 一致。
6. 安全：`detail` 不包含 JDBC URL、密码、API Key、完整手机号/身份证号。

> 真实后端 E2E 必须断言响应 `code` 值（不能只看 UI）；Mock E2E 使用上述 M1–M3 通过 `page.route()` 注入。
