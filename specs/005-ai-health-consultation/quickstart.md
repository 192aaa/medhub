# Quickstart：AI健康咨询（005）

**前置**：`server/` 与 `web/` 依赖已安装；MySQL 8.0.46 与 Redis 3.2.100 本机可用（无 Docker 时用 `IDENTITY_TEST_USE_LOCAL_INFRA=true`）。

## 1. 配置

`server/src/main/resources/application.yml` 中确认：

```yaml
sdas:
  ai:
    health:
      model: deepseek-v4-flash
      timeout-millis: 8000
      first-token-timeout-millis: 3000
      max-output-tokens: 1024
      context-max-chars: 6000
      context-medical-max-chars: 1500
      history-window: 12
      message-max-chars: 2000
      session-message-limit: 200
      knowledge-top-k: 5
  hospital:
    name: 示例医院
    address: 示例市示例区示例路 1 号
    phone: 0000-00000000
    emergency-phone: 120
    service-hours: 08:00-17:30
```

AI 凭据沿用 002 既有配置（`spring.ai.openai.api-key` / `base-url`），本功能不新增密钥项。

## 2. 迁移与启动

```powershell
cd server
# Flyway 自动执行 V9（3 张新表）与 V10（58 条知识预置 + 兜底科室）
mvn -o spring-boot:run -Dspring-boot.run.profiles=dev
```

自检（应返回 3 张新表与预置知识条数）：

```sql
SELECT COUNT(*) FROM ai_health_session;
SELECT COUNT(*) FROM ai_health_message;
SELECT COUNT(*) FROM ai_health_emergency_decision;
SELECT COUNT(*) FROM department_disease_kb WHERE source = 'PRESET' AND kb_version = '2026.11';
```

## 3. 前端

```powershell
cd web
npm run dev
```

访问 `/health-consultation`（未登录会跳患者登录并带回跳）。手机宽度下会话列表为抽屉，PC 为侧栏。

## 4. 接口冒烟

```powershell
# 1) 新建会话（CSRF：Cookie 与头一致）
curl -X POST http://localhost:8080/api/v1/health-consultation/sessions `
  -H "Authorization: Bearer <patient-token>" `
  -H "Cookie: sdas_csrf=demo" -H "X-CSRF-Token: demo"

# 2) 流式问答（-N 关闭缓冲，逐事件观察）
curl -N -X POST http://localhost:8080/api/v1/health-consultation/sessions/3001/messages `
  -H "Authorization: Bearer <patient-token>" -H "Content-Type: application/json" `
  -H "Cookie: sdas_csrf=demo" -H "X-CSRF-Token: demo" `
  -d "{\"content\":\"我这两天咳嗽有痰，之前开的阿莫西林还能吃吗\"}"
```

期望事件顺序：`accepted` →（紧急时 `emergency`）→ `delta`* → `disclaimer` → `done`；文案必须逐字包含「仅供参考，不构成医疗诊断」。

## 5. 常见问题

| 现象 | 排查 |
|------|------|
| SSE 一次性返回、无逐字效果 | 反向代理缓冲未关闭（需 `X-Accel-Buffering: no`）；或前端用了 `EventSource`（本端点需 `fetch` + `ReadableStream`） |
| 回答无请假条式免责声明 | 检查是否绕过了 `HealthChatService` 的 `disclaimer` 事件（前端禁止自行拼接） |
| 连续提问返回 409 | 同会话上一轮仍在生成（`ai:health:lock:*` 未释放），等待或检查超时配置 |
| Redis 连接报错但对话仍可用 | 热窗口降级为「每轮从 MySQL 重建」，属预期（日志有 WARN） |
| 紧急症状未预警 | 检查 `sdas.ai.health.emergency.rules` 是否非空但字段缺失（`departmentName`/`keywords`）；规则为空数组时使用内置默认表 |
| 回答引用了他人的就诊信息 | 严重缺陷：检查 `MedicalContextAssembler` 是否漏传 `patientId` 条件 |
