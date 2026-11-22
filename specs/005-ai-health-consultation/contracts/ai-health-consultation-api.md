# API 契约：AI健康咨询

**来源**：[spec.md](../spec.md) · [plan.md](../plan.md) · [data-model.md](../data-model.md)
**通用约定**（宪法原则 III）：统一响应 `{ code, message, data }`；分页 `data: { list, total, page, pageSize }`；`LocalDateTime` 为 `yyyy-MM-dd'T'HH:mm:ss`；认证 `Authorization: Bearer <token>`（患者角色，统一 `PatientIdExtractor`）；**修改类请求必须带 CSRF 双重 Cookie**（`sdas_csrf` Cookie + `X-CSRF-Token` 头）。
**URL 前缀**：前端调用写 `/v1/health-consultation/...`（baseURL 已含 `/api`，禁止重复 `/api`）。
**枚举**：`MessageRole` / `SessionStatus` / `StreamStatus` / `EmergencyVerdict` 见 [data-model.md](../data-model.md) 第 1 节。

---

## 端点 1：会话列表

`GET /api/v1/health-consultation/sessions?page=1&pageSize=20`

**认证**：患者 | **CSRF**：否 | **需求**：FR-010、FR-017

响应 `data`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `list[].sessionId` | number | 会话主键 |
| `list[].title` | string | 标题（默认取首条患者消息前 20 字） |
| `list[].lastActiveAt` | string | 最近活跃时间（倒序排序键） |
| `list[].messageCount` | number | 消息条数 |
| `list[].lastMessagePreview` | string \| null | 最后一条消息前 40 字（无消息为 null） |
| `total` / `page` / `pageSize` | number | 分页 |

```json
{
  "code": 200, "message": "成功",
  "data": {
    "list": [{
      "sessionId": 3001, "title": "咳嗽有痰能吃阿莫西林吗", "lastActiveAt": "2026-11-08T10:12:00",
      "messageCount": 6, "lastMessagePreview": "建议复诊确认是否需要继续用药…"
    }],
    "total": 1, "page": 1, "pageSize": 20
  }
}
```

**异常**：401 `UNAUTHORIZED`、503 `SERVICE_UNAVAILABLE`

---

## 端点 2：新建会话

`POST /api/v1/health-consultation/sessions`

**认证**：患者 | **CSRF**：是 | **需求**：FR-009

请求体可省略（或 `{}`）。响应 `data`：`{ "sessionId": 3002, "title": "新对话", "createdAt": "2026-11-08T10:20:00" }`

**异常**：401、403 `CSRF_TOKEN_MISMATCH`、503

---

## 端点 3：重命名会话

`PATCH /api/v1/health-consultation/sessions/{sessionId}`

**认证**：患者 | **CSRF**：是 | **需求**：FR-010

请求体：`{ "title": "咳嗽用药咨询" }`（1–64 字符）

响应 `data`：`{ "sessionId": 3002, "title": "咳嗽用药咨询" }`

**异常**：400 `SESSION_TITLE_INVALID`、403、404 `SESSION_NOT_FOUND`（含越权）、503

---

## 端点 4：删除单个会话

`DELETE /api/v1/health-consultation/sessions/{sessionId}`

**认证**：患者 | **CSRF**：是 | **需求**：FR-010

响应 `data`：`{ "sessionId": 3002, "deleted": true }`（逻辑删除，`status='DELETED'`）

**异常**：403、404 `SESSION_NOT_FOUND`、503

---

## 端点 5：清空全部历史

`DELETE /api/v1/health-consultation/sessions`

**认证**：患者 | **CSRF**：是 | **需求**：FR-017、SC-010

响应 `data`：`{ "deletedCount": 3 }`；同一 `@Transactional` 内逻辑删除全部会话、物理清理消息、删除 Redis 热窗口与元信息键，并写审计（只记数量，不记内容）。

**异常**：403、503

---

## 端点 6：会话消息历史

`GET /api/v1/health-consultation/sessions/{sessionId}/messages?page=1&pageSize=50`

**认证**：患者 | **CSRF**：否 | **需求**：FR-002、FR-017

响应 `data.list[]`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `messageId` | number | 消息主键 |
| `role` | string | `PATIENT` / `ASSISTANT` |
| `content` | string | 正文（助手消息**不含**免责声明） |
| `disclaimer` | string \| null | 助手消息固定「仅供参考，不构成医疗诊断」 |
| `emergency` | boolean | 是否触发预警 |
| `streamStatus` | string | `COMPLETED` / `FAILED` / `INTERRUPTED` |
| `createdAt` | string | 时间 |

**异常**：403、404 `SESSION_NOT_FOUND`、503

---

## 端点 7：流式问答（SSE）

`POST /api/v1/health-consultation/sessions/{sessionId}/messages`

**认证**：患者 | **CSRF**：是 | **需求**：FR-001–FR-008、FR-012、FR-015、FR-019、FR-020
**响应类型**：`text/event-stream`（**不是**统一响应包裹；错误在流建立前仍返回 JSON 统一响应）

请求体：`{ "content": "我这两天咳嗽有痰，之前开的阿莫西林还能吃吗" }`（1–2000 字符）

### 事件序列（顺序固定）

| # | `event` | `data` | 说明 |
|---|---------|--------|------|
| 1 | `accepted` | `{ "messageId": 90001, "sessionId": 3001 }` | 患者消息已落库，可立即上屏 |
| 2 | `emergency`（可选） | `{ "level": "HIGH", "title": "症状可能存在紧急风险", "advice": "请立即拨打 120 或前往最近急诊", "ruleCode": "CHEST_PAIN_DYSPNEA", "booking": { "departmentId": 1, "departmentName": "心血管内科" } }` | 仅紧急时发送，**必须先于任何 `delta`**；前端据此显示提示条并弹出挂号引导 |
| 3 | `delta`（≥1 次） | `{ "text": "建议" }` | 正文增量，前端追加渲染 |
| 4 | `disclaimer` | `{ "text": "仅供参考，不构成医疗诊断" }` | 固定文案，服务端追加（禁止前端拼接） |
| 5 | `done` | `{ "messageId": 90002, "truncated": false, "modelUsed": "deepseek-v4-flash" }` | 正常结束 |

失败时第 5 步替换为：

| # | `event` | `data` | 说明 |
|---|---------|--------|------|
| 5' | `error` | `{ "code": 503, "message": "智能助手暂时不可用，请稍后重试", "retryable": true }` | 与 `done` 互斥；已发送的 `delta` 内容保留，消息标记 `stream_status='FAILED'` |

### 例外与降级

- **流建立前失败**（未认证、越权、并发生成、输入超长）：返回 JSON 统一响应，`Content-Type: application/json`，不进入 SSE。
- **流中失败**：已发 `accepted` 与部分 `delta` 后发送 `error` 并关闭连接；患者消息保留，助手消息 `stream_status='FAILED'`，前端显示重试入口。
- **客户端断开**：服务端检测到写失败后停止生成，助手消息 `stream_status='INTERRUPTED'`，保留已生成内容。

### 异常（流建立前）

| code | 常量 | 触发条件 |
|------|------|----------|
| 400 | `BAD_REQUEST` | 请求体为空、`content` 超长（>2000） |
| 400 | `MESSAGE_TOO_LONG` | `content` 超长（与上条择一，按实现取更具体者） |
| 400 | `SESSION_MESSAGE_LIMIT` | 单会话消息数 ≥ 200 |
| 401 | `UNAUTHORIZED` | 未登录 / token 失效 |
| 403 | `CSRF_TOKEN_MISMATCH` | 双重 Cookie 校验失败 |
| 404 | `SESSION_NOT_FOUND` | 会话不存在 / 越权 / 已删除 |
| 409 | `CHAT_BUSY` | 同会话已有生成中的回答 |
| 503 | `AI_SERVICE_UNAVAILABLE` | 模型不可用且未产生任何内容 |

### 约定

1. 本端点**既是读也是写**：先落库患者消息（`accepted` 前置），再流式生成。
2. `emergency` 事件的 `booking` 字段与 002 挂号入口对齐；前端弹窗预填 `departmentId` 后跳转既有挂号流程。
3. 助手消息落库内容 `content` 与 `delta` 拼接结果一致，`disclaimer` 单独存列。
4. 前端必须用 `fetch` + `ReadableStream` 消费（`EventSource` 不支持 POST），逐事件按 `\n\n` 分帧。

---

## 契约一致性检查清单（每迭代执行）

- [ ] 7 个端点的路径、方法、`code` 值与本文一致
- [ ] SSE 事件名与顺序（`accepted` → `emergency`? → `delta`* → `disclaimer` → `done`/`error`）
- [ ] 助手消息响应均含 `disclaimer` 且文案逐字为「仅供参考，不构成医疗诊断」
- [ ] 日期格式 `yyyy-MM-dd'T'HH:mm:ss`
- [ ] 越权访问统一 `404 SESSION_NOT_FOUND`
- [ ] 前端 `src/api/health-consultation.ts` 路径无重复 `/api`
