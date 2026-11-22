# 快速验证：处方取药

**功能**：`004-prescription-dispensing` | **契约**：[contracts/](./contracts/) | **数据模型**：[data-model.md](./data-model.md)

> 目标：本地 10 分钟内完成「患者看码 → 药店扫码验真 → 核销 → 重复扫码看到已取药」的闭环验证。

## 1. 前置环境

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 17 | `java -version` |
| Maven | 3.9+ | `C:\Users\<user>\maven\apache-maven-3.9.6\bin` |
| Node | 20+ | `npm -v` |
| MySQL | 8.0 | 库 `cloud_consultation`（dev）；测试库 `cloud_consultation_test` |
| Redis | 7+ | 会话撤销标记（001） |
| 浏览器 | Chrome/Edge 最新 | 扫码需 `localhost` 或 HTTPS |

## 2. 数据库迁移与种子

```bash
cd server
mvn -o flyway:info            # 确认 V7/V8 待执行（或直接启动应用自动迁移）
```

- dev 启动自动按 `db/migration` + `db/seed` 执行（`application-dev.yml`）。
- 测试/生产仅 `db/migration`，**不加载种子**。
- 新增迁移：`V7__prescription_dispensing_schema.sql`；种子：`V8__prescription_dispensing_seed.sql`。

种子药店账号（dev）：

| 账号 | 口令 | 药店 |
|------|------|------|
| `pharmacy_hp` | `password` | 示例大药房（总部店） |
| `pharmacy_xh` | `password` | 示例大药房（西湖店） |

## 3. 签名密钥（关键）

首次 dev 启动会自动生成 `server/keystore/doctor-signing.jks` 并打印 WARN；**请勿删除**，否则历史二维码全部失效。

```bash
# 生产：注入密钥文件与口令（示例）
export SDAS_DOCTOR_KEYSTORE=/etc/sdas/doctor-signing.jks
export SDAS_DOCTOR_KEYSTORE_PASSWORD='********'
```

校验密钥可用（重启后仍能验签）：

```bash
mvn -o -Dtest=PrescriptionSigningServiceTest test
```

## 4. 启动

```bash
# 终端 1：后端
cd server && mvn -o spring-boot:run

# 终端 2：前端
cd web && npm run dev        # http://localhost:5183
```

## 5. 手工闭环（5 步）

1. **患者取号取方**：以患者身份登录（或复用 dev 患者），走完 `002` 预约 → 支付 → `003` 医生接诊开方，得到一张已签名处方。
2. **患者出示二维码**：访问 `/prescriptions` → 打开详情，页面应显示处方要素、水印、二维码图片与 12 位验真码（`M9` 对应）。
3. **药店登录**：访问 `/pharmacy/login`，用 `pharmacy_hp / password` 登录；`localStorage['sdas.pharmacy.token']` 应存在。
4. **扫码验真**：访问 `/pharmacy/verify`，三选一：
   - 摄像头扫码（允许授权后对准第 2 步的二维码）；
   - 上传二维码截图（`Html5Qrcode.scanFile` 解析）；
   - 手工输入处方号 + 验真码。
   预期：`code=200`、`dispenseStatus=PENDING`、`dispensable=true`、药品明细完整。
5. **核销并重复验真**：点击「确认取药」→ 返回 `DISPENSED / idempotent=false`；再次点击「确认取药」→ `ALREADY_DISPENSED / idempotent=true`；再次扫码 → `dispenseStatus=DISPENSED`、`dispensable=false`、显示取药时间与核销人。

## 6. 反向验证（必须全绿）

| 场景 | 期望 |
|------|------|
| 把二维码内容里 `pid` 段改一个字符 | 400 `INVALID_VERIFICATION_PROOF`，响应无处方字段 |
| 用其它药店的码去扫（或旧格式 `?prescriptionId=&sig=`） | 400 `INVALID_VERIFICATION_PROOF` |
| 患者 token 调验真端点 | 403 `PHARMACY_ROLE_REQUIRED` |
| 药店 token 调 `GET /api/v1/prescriptions` | 401 `UNAUTHORIZED` |
| 患者 A 访问患者 B 的处方详情 | 404 `PRESCRIPTION_NOT_FOUND`（与不存在同文案） |
| 过期处方核销 | 400 `PRESCRIPTION_EXPIRED` |
| 已作废处方验真/核销 | 400 `PRESCRIPTION_REVOKED` |
| 两个浏览器同时核销同一处方 | 一个 `DISPENSED`、一个 `ALREADY_DISPENSED`；`prescription_dispense_record` 仅 1 行 |
| 缺 `X-CSRF-Token` 的 POST | 403 `CSRF_TOKEN_MISMATCH` |
| 停 MySQL 后核销 | 503 `SERVICE_UNAVAILABLE`，无半成品记录 |

## 7. curl 速查

```bash
# 1) 药店登录（先取 CSRF Cookie）
curl -s -c jar.txt http://localhost:8080/api/v1/health > /dev/null
CSRF=$(grep sdas_csrf jar.txt | awk '{print $7}')
TOKEN=$(curl -s -b jar.txt -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' \
  -d '{"username":"pharmacy_hp","password":"password"}' \
  http://localhost:8080/api/v1/pharmacy/sessions | jq -r .data.accessToken)

# 2) 验真（令牌来自患者详情接口的 qrCode）
curl -s -b jar.txt -H "X-CSRF-Token: $CSRF" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"qrContent":"http://localhost:5183/pharmacy/verify?t=v1.xxxxx.yyyyy"}' \
  http://localhost:8080/api/v1/pharmacy/prescription-verifications | jq

# 3) 核销
curl -s -b jar.txt -H "X-CSRF-Token: $CSRF" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"qrContent":"..."}' \
  http://localhost:8080/api/v1/pharmacy/prescriptions/7001/dispensing | jq
```

## 8. 自动化测试

```bash
# 后端（含契约 + 集成，集成测试需本机 MySQL/Redis）
cd server && IDENTITY_TEST_USE_LOCAL_INFRA=true mvn -o verify

# 前端静态检查 + 单测
cd web && npm run typecheck && npm run lint && npm run test

# E2E：Mock（无需后端）
cd web && npx playwright test prescription.mock.spec.ts pharmacy-dispensing.mock.spec.ts

# E2E：真实后端（需先起 server + web）
cd web && E2E_REAL_BACKEND=1 npx playwright test prescription.real.spec.ts pharmacy-dispensing.real.spec.ts
```

## 9. 故障排查

| 现象 | 原因 | 处置 |
|------|------|------|
| 验真恒 400 `INVALID_VERIFICATION_PROOF` | 服务重启后密钥变化（旧实现）或 keystore 被删 | 确认 keystore 存在且未被覆盖；重新签发处方 |
| 药店端 POST 恒 403 CSRF | `pharmacy-request.ts` 未开 `withCredentials` | 按 plan P0 修复并重启前端 |
| 摄像头无法启动 | 非 `localhost`/HTTPS，或用户拒绝授权 | 用「上传图片」或「手工输入」路径 |
| 上传图片解析失败 | 图片分辨率过低/反光 | 提高截图清晰度；改用摄像头或手工输入 |
| `dispensable=true` 但核销报过期 | 前端时间与服务器时区不一致 | 以服务端 `validUntil/expired` 为准，禁止前端自算 |
| 密钥轮换后旧码全部失效 | 未保留旧密钥 | 保留旧密钥 ≥ 一个处方有效期（3 天）再切换 |
