# 预约挂号与图文问诊 本地启动与验证指南

**功能**：`002-appointment-consultation`
**配套**：`plan.md`、`research.md`、`data-model.md`、`contracts/`
**依赖上游**：`001-patient-identity` 须已启动（提供认证/实名/门禁与 `security_audit_event` 表）

> 本指南覆盖 dev 环境一键启动、关键配置、验证脚本与常见异常排查。生产部署不在此列（支付渠道、AI 形态、图片存储位置待产品确认后另文）。

---

## 1. 环境前置

- JDK 17、Node 20+、MySQL 8（或 Testcontainers）、Redis（复用 001 同实例）。
- DeepSeek API Key：通过环境变量注入（与 001 一致）
  ```bash
  export DEEPSEEK_API_KEY=sk-xxxx
  ```
- 其余密钥沿用 001：`SDAS_JWT_SECRET`、`SDAS_ID_CARD_HMAC_SECRET`、`SDAS_PHONE_HMAC_SECRET`、`SDAS_AES_KEY`。

---

## 2. 关键配置（application.yml 新增项）

```yaml
sdas:
  appointment:
    mock-payment-enabled: true        # D1：MVP 模拟支付
    payment-timeout-seconds: 900       # 待支付 15 分钟过期
  ai:
    triage:
      timeout-millis: 5000            # D2：默认 5 秒超时降级
      fallback: FULL_DEPARTMENT_LIST
      model: ${SDAS_TRIAGE_MODEL:deepseek-v4-flash}
  storage:
    images:
      location: ${APP_UPLOAD_DIR:./uploads/consultation}   # D5：本地存储
      allowed-content-types: image/jpeg,image/png,image/webp
      max-size-bytes: 5242880
      max-count: 9
      serve-path: /static/consultation-images
```

`application-dev.yml` 可覆盖 `APP_UPLOAD_DIR`；Spring `ResourceHandler` 将 `serve-path` 映射至 `location` 目录（只读）。

---

## 3. 数据库初始化

Flyway 按顺序执行：
- `V1__identity_schema.sql`（001，已存在）
- `V2__appointment_consultation_schema.sql`（本功能，建 9 张表，见 `data-model.md` §2）
- 种子：`db/seed/V2__appointment_consultation_seed.sql`（科室、医生、排班、知识库预置、演示历史就诊）

> 知识库与医生/科室为**预置只读**数据（D3），无管理界面；如需更新走数据库变更。

---

## 4. 启动后端

```bash
cd server
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
- 端口 8080（与 001 一致）；所有 002 端点挂在 `/api/v1/...`。

---

## 5. 启动前端

```bash
cd web
npm install
npm run dev      # Vite，默认 5173（与 001 同源，CORS 已含）
```

前端新增页面挂载于 `/appointment/*`（科室、医生、导诊、创建预约、支付、提交问诊单、问诊单详情），问诊入口复用既有 `/consultation/entry`。

---

## 6. 端到端验证脚本（9 端点）

以下用 `curl` 演示主流程；token 取自 001 注册/登录返回（须已完成实名认证且存在历史就诊记录，否则端点 5 返回 `REVISIT_NOT_ELIGIBLE` 403）。

```bash
BASE=http://localhost:8080/api/v1
TOKEN=<从 001 登录获取>

# 1) 科室列表
curl $BASE/departments

# 2) 医生列表（按科室 + 在线）
curl "$BASE/doctors?departmentId=1&online=ONLINE"

# 3) 医生详情
curl $BASE/doctors/101

# 4) AI 导诊（成功）
curl -X POST $BASE/triage-sessions -H "X-CSRF-Token: <csrf>" -H "Cookie: sdas_csrf=<csrf>" \
  -H 'Content-Type: application/json' -d '{"complaint":"持续咳嗽伴低热"}'

# 4b) AI 导诊超时降级（注入 >5s 延迟时）
#     预期 200 + status=TIMEOUT_FALLBACK + fallbackDepartments=全量科室列表

# 5) 创建挂号预约（须已认证且有复诊资格）
curl -X POST $BASE/appointments -H "Authorization: Bearer $TOKEN" -H "X-CSRF-Token: <csrf>" \
  -H "Cookie: sdas_csrf=<csrf>" -H 'Content-Type: application/json' \
  -d '{"doctorId":101,"scheduleId":201,"patientNote":"尽快"}'

# 6a) 模拟支付（amount 必须等于 feeAmount）
curl -X POST $BASE/appointments/401/payments -H "Authorization: Bearer $TOKEN" \
  -H "X-CSRF-Token: <csrf>" -H "Cookie: sdas_csrf=<csrf>" \
  -H 'Content-Type: application/json' -d '{"channel":"MOCK","amount":30.00}'

# 6b) 查询支付状态
curl $BASE/appointments/401/payments -H "Authorization: Bearer $TOKEN"

# 7) 上传问诊图片（multipart）
curl -X POST $BASE/consultations/images -H "Authorization: Bearer $TOKEN" \
  -H "X-CSRF-Token: <csrf>" -H "Cookie: sdas_csrf=<csrf>" \
  -F "file=@./demo.jpg;type=image/jpeg"

# 8) 提交图文问诊单（appointment 须已 PAID）
curl -X POST $BASE/consultations -H "Authorization: Bearer $TOKEN" \
  -H "X-CSRF-Token: <csrf>" -H "Cookie: sdas_csrf=<csrf>" \
  -H 'Content-Type: application/json' \
  -d '{"appointmentId":401,"complaint":"咳嗽伴低热三天","description":"夜间加重","imageUrls":["/static/consultation-images/abc123.jpg"]}'

# 9) 查询问诊单详情
curl $BASE/consultations/701 -H "Authorization: Bearer $TOKEN"
```

---

## 7. 验证要点（对照 SC）

| SC | 验证方式 | 预期 |
|----|----------|------|
| SC-003 | 未认证/无复诊资格调端点 5 | 0 放行，返回 401 / `REVISIT_NOT_ELIGIBLE` |
| SC-004 | 主诉 201 字 / 10 张图 / 单张 >5MB / 改后缀伪装 | 100% 拦截，无脏数据 |
| SC-005 | 重复点击支付、刷新后查询 | 无重复扣费，状态一致 |
| SC-006 | 注入 AI >5s 延迟 | 返回 `TIMEOUT_FALLBACK` + 全量科室列表，HTTP 200 |
| SC-008 | 多标签并发提交预约/问诊单 | 无重复记录（唯一约束） |
| SC-009 | 检查响应/审计/AI 抽样 | 无明文手机号/身份证/支付敏感信息 |
| SC-010 | 越权访问他人问诊单 | 返回 `CONSULTATION_FORBIDDEN` 403 |
| SC-012 | 契约一致性脚本比对 | 端点/`code`/字段/日期格式差异为 0 |

---

## 8. 常见排查

- **端点 5 返回 403 `REVISIT_NOT_ELIGIBLE`**：确认当前患者已在 001 完成实名认证，且 `patient_history_visit` 存在该 `patient_id` 预置记录（dev 种子默认提供演示患者）。
- **端点 4 返回 503**：检查 `DEEPSEEK_API_KEY` 与网络；注意 5 秒超时**不是** 503，而是 200 降级分支。
- **端点 7 返回 400 `INVALID_IMAGE_FORMAT`**：多为内容伪装（后缀为 jpg 但实际非图片），服务端 `ImageIO.read` 嗅探失败；确认文件确为 jpg/png/webp 像素。
- **端点 6a 返回 400 `INVALID_PAYMENT_AMOUNT`**：前端传入 `amount` 与 `feeAmount` 不符；MVP 以服务端金额为准。
- **图片访问 404**：确认 `APP_UPLOAD_DIR` 与 `serve-path` 映射一致，且目录有读权限。

---

## 9. 测试命令

```bash
# 后端
cd server && ./mvnw test            # 单元 + 集成（Testcontainers MySQL/Redis）
cd server && ./mvnw verify          # 含 checkstyle/spotbugs/jacoco(≥70%)

# 前端
cd web && npm run test              # Vitest 单元
cd web && npm run test:e2e          # Playwright（3 用户故事，page.route 用 mocks.md）
cd web && npm run typecheck        # vue-tsc 类型校验
```
