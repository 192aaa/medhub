# 快速验证：AI辅助治疗决策

**Branch**: `006-ai-treatment-decision` | **Date**: 2026-11-08

本文件用于 `/speckit.implement` 完成后 5 分钟内让功能跑起来，并按步骤验证 US1–US4 与降级链路。

## 1. 前置条件

| 依赖 | 要求 | 检查方式 |
|------|------|----------|
| JDK | 17+ | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node | 20+ | `node -v` |
| MySQL | 8.0（库 `cloud_consultation`） | `mysql -uroot -p -e "show databases;"` |
| Redis | 3.2.100（**不可用 5+ 特性**） | `redis-cli info server \| findstr redis_version` |
| 模型密钥 | `DEEPSEEK_API_KEY` | 环境变量已设置 |

```powershell
$env:DEEPSEEK_API_KEY = "<your-key>"
$env:SDAS_DOCTOR_KEYSTORE = "./keystore/doctor-signing.jks"   # 003 签名必需
```

## 2. 启动

```powershell
# 后端（Flyway 自动执行 V11 扩展 ai_decision_session）
cd server
mvn -o spring-boot:run

# 前端
cd ../web
npm install
npm run dev
```

预期：后端日志出现 `Migrating schema ... to version 11 - ai treatment decision`；启动无告警。

## 3. 准备数据

使用 003 的契约夹具（测试库）或手工插入：

```sql
-- 医生与药品（003 夹具同款）
INSERT IGNORE INTO department(id, name) VALUES (1, '内科');
INSERT IGNORE INTO doctor(id, department_id, name, title, hospital, status)
VALUES (101, 1, '张*', '主任医师', '示例医院', 'ONLINE');
INSERT IGNORE INTO drug_catalog(id, name, spec, manufacturer, dosage_form, unit,
                                 prescription_only, enabled, source)
VALUES (901, '阿莫西林胶囊', '0.25g*20', '示例药厂', '胶囊', '盒', 1, 1, 'PRESET');

-- 已接诊问诊单（IN_PROGRESS）
INSERT INTO consultation(id, appointment_id, patient_id, doctor_id, department_id,
                         complaint, description, status, accepted_at, accepted_by_doctor_id)
VALUES (501, 1, 1001, 101, 1, '咳嗽伴咳痰 3 天', '3 天前受凉后咳嗽，白色黏痰，无发热。',
        'IN_PROGRESS', NOW(), 101);

-- 两条对话（用于验证摘要/建议）
INSERT INTO consultation_message(consultation_id, sender_type, sender_id, content)
VALUES (501, 'PATIENT', 1001, '夜里咳得更厉害，之前吃了两天阿莫西林没啥效果'),
       (501, 'DOCTOR', 101, '有没有发热、胸痛或者喘不上气？');
```

## 4. 验证 US1 / US2 / US3（成功链路）

1. 医生登录 → 进入 `/workbench/consultations/501` → 点击「开具处方」。
2. 开方页应自动发起 `POST /api/v1/workbench/consultations/501/treatment-decision`。
3. 观察：

| 检查项 | 预期 |
|--------|------|
| 摘要卡 | 出现主诉、现病史要点、对话关键信息、风险提示四段 |
| 免责标注 | 每处均显示「AI 生成，仅供参考，最终诊疗决策由医生负责」 |
| 建议列表 | 至少 1 条疑似诊断，含依据与「辅助参考，不作为诊断结论」 |
| 用药草稿 | 出现「阿莫西林胶囊 0.25g*20」及用法用量，状态为「待确认」 |
| 表单 | 草稿已填入开方表单，可直接修改；未点「确认并签名」不会生成处方 |

4. 修改用法用量 → 提交 → 处方详情应为修改后的值。
5. 再次进入开方页（不新增对话）→ 响应 `reused=true`，`elapsedMs=0`，不产生新留痕：

```sql
SELECT id, trigger_type, status, dialog_version, elapsed_ms
FROM ai_decision_session WHERE consultation_id = 501 ORDER BY id DESC;
```

## 5. 验证 US4（降级链路）

将超时压到 1ms 并重启后端：

```powershell
$env:SDAS_AI_TREATMENT_TIMEOUT_MILLIS = "1"
```

| 检查项 | 预期 |
|--------|------|
| 接口 | HTTP **200**，`data.status=TIMEOUT_FALLBACK`，`summary=null`，`suggestions=[]` |
| 页面 | 辅助区域显示「AI 辅助暂不可用」+ 重试，无空白卡片/原始 JSON |
| 表单 | 可正常填写并签名开方 |
| 留痕 | 新增 1 行，`status=TIMEOUT_FALLBACK`、`degraded_reason=TIMEOUT` |
| 处方 | 无半成品处方记录 |

恢复 `$env:SDAS_AI_TREATMENT_TIMEOUT_MILLIS = ""` 并重启，点击「重新生成」应恢复成功内容。

## 6. 验证频控与并发

```powershell
# 连续 4 次重新生成，第 4 次应 429
1..4 | ForEach-Object {
  curl.exe -s -X POST "http://localhost:8080/api/v1/workbench/consultations/501/treatment-decision/regenerate" `
    -H "Authorization: Bearer $env:DOCTOR_TOKEN" -H "X-CSRF-Token: $env:CSRF" `
    -b "sdas_csrf=$env:CSRF"
}
```

预期第 4 次：`{"code":429,"message":"辅助生成过于频繁，请稍后重试","data":null}`。

并发互斥：两个终端同时发起首次生成，第二个应返回 `409 {"code":409,"message":"辅助生成进行中，请稍候"}`。

## 7. 验证越权与状态

| 场景 | 预期 |
|------|------|
| 用另一位医生令牌请求 501 | `403 CONSULTATION_FORBIDDEN`，响应体无病情字段 |
| 对 `PENDING` 问诊单请求 | `400 INVALID_CONSULTATION_STATUS` |
| 对已开方问诊单请求 | `409 PRESCRIPTION_ALREADY_EXISTS` |
| 缺失 CSRF 的 POST | `403 CSRF_TOKEN_MISMATCH` |

## 8. 自动化测试

```powershell
# 后端单测
cd server
mvn -o test -Dtest='com.sdas.server.doctor.unit.*Test'

# 后端契约 + 集成（需容器或本机实例）
$env:IDENTITY_TEST_USE_LOCAL_INFRA = "true"
mvn -o test -Dtest='TreatmentDecisionContractTest,TreatmentDecisionFlowIT'

# 前端
cd ../web
npx vitest run
npx playwright test tests/e2e/treatment-decision.mock.spec.ts

# 真实后端 E2E（需后端已启动 + 医生令牌）
$env:E2E_REAL_BACKEND = "1"
npx playwright test tests/e2e/treatment-decision.real.spec.ts
```

## 9. 常见问题

| 现象 | 排查 |
|------|------|
| 开方页没有辅助面板 | 确认 `GET .../treatment-decision` 返回 404 后是否触发 POST；检查 `requiresDoctor` 路由守卫 |
| 草稿药品显示空白 | 端点 5 未生效或该 `drugId` 已被停用；`SELECT id,name,enabled FROM drug_catalog` |
| 一直 `TIMEOUT_FALLBACK` | 检查 `DEEPSEEK_API_KEY`、`spring.ai.openai.base-url` 与网络；调大 `timeout-millis` 复测 |
| 一直 `EMPTY_FALLBACK` | 模型未按 JSON 输出；查看后端日志中的解析失败原因（不含原文） |
| 摘要含患者姓名/手机号 | 属缺陷：`input_summary` 脱敏未生效，检查正则与 `result_json` 内容 |
| `/regenerate` 恒 429 | Redis 计数未过期；`redis-cli del ai:treatment:rate:501` 后复测 |
| 开方页 500 | 检查 V11 是否执行：`SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1` |
