# 健康检查 数据模型（唯一枚举来源）

**功能名**：health-check
**依据**：`.specify/memory/constitution.md` v1.0.0（原则 II、III、V）
**适用范围**：`GET /api/health` 的请求/响应模型；前后端与测试代码引用枚举时须标注本文件路径，禁止硬编码。

## 1. 枚举定义

### 1.1 HealthStatus（组件/整体状态）

| 值 | 含义 | 触发条件 |
|----|------|----------|
| `UP` | 可用 | 该探针在超时时间内正常返回；整体为 `UP` 当且仅当全部 3 个组件均为 `UP` |
| `DOWN` | 不可用 | 任一探针抛出运行时异常或超时；整体即为 `DOWN` |

### 1.2 ErrorCode（本功能涉及的 code/message，全局唯一定义）

| code | 枚举常量 | message | HTTP 状态 |
|------|----------|---------|-----------|
| 200 | `SUCCESS` | 成功 | 200 |
| 400 | `BAD_REQUEST` | 请求参数错误 | 400 |
| 401 | `UNAUTHORIZED` | 未授权 | 401 |
| 403 | `FORBIDDEN` | 禁止访问 | 403 |
| 404 | `NOT_FOUND` | 资源不存在 | 404 |
| 405 | `METHOD_NOT_ALLOWED` | 请求方法不支持 | 405 |
| 500 | `INTERNAL_ERROR` | 服务端内部错误 | 500 |
| 503 | `HEALTH_UNAVAILABLE` | 依赖组件不可用 | 503 |

> 业务异常 message 统一取自 `ErrorCode`，禁止 Controller/Service 硬编码；登录类模糊提示等后续功能复用同一枚举。

## 2. 响应结构

### 2.1 统一响应 `Result<T>`

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 见 1.2，200 成功 / 4xx 客户端错误 / 5xx 服务端错误 |
| `message` | String | 取自 `ErrorCode`，禁止自定义文案 |
| `data` | T | 成功或降级时均返回 `HealthReport`；其他错误为 `null` |

### 2.2 `HealthReport`

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | HealthStatus | 整体状态，见 1.1 |
| `components` | List\<ComponentStatus\> | 固定 3 项，顺序 `server` → `database` → `redis` |
| `checkedAt` | LocalDateTime | ISO 8601 `yyyy-MM-dd'T'HH:mm:ss`，禁止时间戳数组 |

### 2.3 `ComponentStatus`

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | `server` / `database` / `redis` |
| `status` | HealthStatus | 见 1.1 |
| `detail` | String | `UP` 时为脱敏后的探针结果（如 `SELECT 1 = 1`、`PING = PONG`）；`DOWN` 时固定 `unavailable`，不回传连接串或异常堆栈 |
| `elapsedMs` | long | 探针耗时（毫秒） |

## 3. 约束

- `server` 组件只要 JVM 进程可响应请求即为 `UP`；`database` 探针固定执行 `SELECT 1`；`redis` 探针固定执行 `PING`。
- 探针异常仅记录异常类名（WARN），禁止打印 JDBC URL、密码、API Key 等敏感信息。
- 本骨架不引入实体表，无 `@Enumerated` / `@Converter` 使用场景；后续功能新增枚举须回到本文件登记。
