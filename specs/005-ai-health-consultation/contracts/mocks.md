# Mock 数据：AI健康咨询

**来源**：[ai-health-consultation-api.md](./ai-health-consultation-api.md)（同源，契约变更须同步本文件）
**用途**：前端 `web/src/api/mocks/health-consultation.ts`、Playwright `page.route()`、SSE 事件顺序测试。
**规则**（宪法原则 VI）：Mock 字段与契**约逐字一致**，禁止凭空手写；日期统一 `yyyy-MM-dd'T'HH:mm:ss`；演示数据均为合成数据。

---

## M1 会话列表（成功）

```json
{
  "code": 200, "message": "成功",
  "data": {
    "list": [
      {
        "sessionId": 3001, "title": "咳嗽有痰能吃阿莫西林吗",
        "lastActiveAt": "2026-11-08T10:12:00", "messageCount": 6,
        "lastMessagePreview": "建议复诊确认是否需要继续用药…"
      },
      {
        "sessionId": 3002, "title": "术后康复注意事项",
        "lastActiveAt": "2026-11-07T20:05:00", "messageCount": 4,
        "lastMessagePreview": "前两周避免负重，逐步恢复活动量…"
      }
    ],
    "total": 2, "page": 1, "pageSize": 20
  }
}
```

## M2 新建会话（成功）

```json
{ "code": 200, "message": "成功", "data": { "sessionId": 3003, "title": "新对话", "createdAt": "2026-11-08T10:30:00" } }
```

## M3 重命名（成功 / 非法标题）

```json
{ "code": 200, "message": "成功", "data": { "sessionId": 3003, "title": "咳嗽用药咨询" } }
```

```json
{ "code": 400, "message": "会话标题不合法", "data": null }
```

## M4 删除单个会话 / 清空全部（成功）

```json
{ "code": 200, "message": "成功", "data": { "sessionId": 3003, "deleted": true } }
```

```json
{ "code": 200, "message": "成功", "data": { "deletedCount": 3 } }
```

## M5 会话不存在（越权与不存在同码）

```json
{ "code": 404, "message": "会话不存在", "data": null }
```

## M6 消息历史（成功，含未完成消息）

```json
{
  "code": 200, "message": "成功",
  "data": {
    "list": [
      { "messageId": 90001, "role": "PATIENT", "content": "我这两天咳嗽有痰，之前开的阿莫西林还能吃吗",
        "disclaimer": null, "emergency": false, "streamStatus": "COMPLETED", "createdAt": "2026-11-08T10:10:00" },
      { "messageId": 90002, "role": "ASSISTANT",
        "content": "从你描述的咳嗽有痰看，常见于上呼吸道感染。既往处方中的阿莫西林属于处方抗菌药，是否继续使用需结合当时的诊断与疗程。",
        "disclaimer": "仅供参考，不构成医疗诊断", "emergency": false, "streamStatus": "COMPLETED", "createdAt": "2026-11-08T10:10:05" },
      { "messageId": 90003, "role": "ASSISTANT", "content": "根据你最近一次就诊记录，当时诊断为",
        "disclaimer": null, "emergency": false, "streamStatus": "INTERRUPTED", "createdAt": "2026-11-08T10:12:00" }
    ],
    "total": 3, "page": 1, "pageSize": 50
  }
}
```

## M7 SSE：普通问答（事件序列）

```text
event: accepted
data: {"messageId":90001,"sessionId":3001}

event: delta
data: {"text":"从你描述的咳嗽有痰看，"}

event: delta
data: {"text":"常见于上呼吸道感染。既往处方中的阿莫西林属于处方抗菌药，"}

event: delta
data: {"text":"是否继续使用需结合当时的诊断与疗程，建议复诊或咨询开方医生。"}

event: disclaimer
data: {"text":"仅供参考，不构成医疗诊断"}

event: done
data: {"messageId":90002,"truncated":false,"modelUsed":"deepseek-v4-flash"}
```

## M8 SSE：紧急预警（`emergency` 先于 `delta`）

```text
event: accepted
data: {"messageId":90011,"sessionId":3001}

event: emergency
data: {"level":"HIGH","title":"症状可能存在紧急风险","advice":"请立即拨打 120 或前往最近急诊","ruleCode":"CHEST_PAIN_DYSPNEA","booking":{"departmentId":1,"departmentName":"心血管内科"}}

event: delta
data: {"text":"你描述的胸痛伴呼吸困难属于需要立即处理的情况，"}

event: disclaimer
data: {"text":"仅供参考，不构成医疗诊断"}

event: done
data: {"messageId":90012,"truncated":false,"modelUsed":"deepseek-v4-flash"}
```

## M9 SSE：模型失败降级

```text
event: accepted
data: {"messageId":90021,"sessionId":3001}

event: delta
data: {"text":"从你描述的症状看，"}

event: error
data: {"code":503,"message":"智能助手暂时不可用，请稍后重试","retryable":true}
```

## M10 流建立前失败（JSON，非 SSE）

| 场景 | 响应 |
|------|------|
| 同会话并发生成 | `{"code":409,"message":"当前会话正在生成回答，请稍候","data":null}` |
| 输入超长 | `{"code":400,"message":"消息内容过长","data":null}` |
| 会话越权 | `{"code":404,"message":"会话不存在","data":null}` |

## M11 患者上下文（仅服务端可见，不得回传前端）

> 该数据仅存在于服务端 prompt 组装阶段，**不属于任何 API 响应**；列此以说明 Mock 边界。

```text
【最近就诊】2026-11-01 内科 张*  主诉：咳嗽三天
  处方：阿莫西林胶囊 0.25g*20  用法：口服，一次 2 粒，一日 3 次  注意：青霉素过敏者禁用
```
