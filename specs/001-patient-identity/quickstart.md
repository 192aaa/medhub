# 快速验证指南（患者注册与实名认证）

**功能**：`001-patient-identity`
**目的**：让开发与测试人员在不接第三方短信、不接外部白名单同步的前提下，本地跑通 4 个用户故事并验证契约。
**依据**：`plan.md`、`research.md`、`data-model.md`、`contracts/`

---

## 1. 前置条件

| 组件 | 版本 / 要求 | 检查命令 |
|------|-------------|----------|
| JDK | 17 | `java -version` |
| Maven | 3.9.x（本机未入 PATH 时用 `.m2/wrapper` 内的 `mvn.cmd`） | `mvn -v` |
| Node.js | ≥ 18（本项目验证用 24） | `node -v` |
| MySQL | 8.0，库 `cloud_consultation`、`cloud_consultation_test` | `mysql -uroot -p -e "SELECT 1"` |
| Redis | 3.2.100（3.2 协议族） | `redis-cli PING` |
| 浏览器 | Chrome / Edge；Playwright 无内置浏览器时用 `PLAYWRIGHT_CHANNEL=msedge` | — |

> 当前开发机配置（非代码缺陷）：MySQL 8.0.46 与 Redis 3.2.100 均为本机实例；`application-dev.yml` 的本机开发密码可用 `MYSQL_PASSWORD` 覆盖，Redis 用 `C:\redis\redis-server.exe C:\redis\redis.windows.conf` 启动。无 Docker 时以 `IDENTITY_TEST_USE_LOCAL_INFRA=true` 运行集成测试（宪法 VI 豁免登记见 `plan.md` Complexity Tracking），Flyway 仍强制启用且共用 `V1`。未启动 MySQL / Redis 时接口按契约返回 `503 SERVICE_UNAVAILABLE`。

## 2. 环境变量

```bash
# 生产必填（缺失时后端启动失败）；dev/test 可选，application-dev.yml / application-test.yml 已提供占位值
SDAS_JWT_SECRET=<32 字节以上随机串>
SDAS_ID_CARD_HMAC_SECRET=<随机串>
SDAS_PHONE_HMAC_SECRET=<随机串>
SDAS_AES_KEY=<Base64 编码的 32 字节密钥>

# 固定验证码（仅 dev/test；开关默认 true，prod 为 true 会拒绝启动）
# 开关不在环境变量中暴露，如需覆盖用启动参数：--sdas.identity.verification-code.fixed-enabled=false
SDAS_FIXED_CODE=888888

# 数据库 / Redis（application-dev.yml 已给默认值）
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/cloud_consultation?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=123456
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# 协议与 Cookie
SDAS_AGREEMENT_VERSION=v1.0
COOKIE_SECURE=false

# 可选：CORS 允许源；dev 默认已含 localhost/127.0.0.1:5183，生产必须显式注入
CORS_ALLOWED_ORIGINS=http://127.0.0.1:5183

# 前端
VITE_API_BASE_URL=/api
VITE_API_TARGET=http://localhost:8080
VITE_DEV_PORT=5183
```

生产 profile 额外要求：`application-prod.yml` 中 `sdas.identity.verification-code.fixed-enabled: false`（或用启动参数 `--sdas.identity.verification-code.fixed-enabled=false` 覆盖；**不存在 `SDAS_FIXED_CODE_ENABLED` 环境变量**）、`COOKIE_SECURE=true`，且必须已接入短信发送实现（本阶段未实现，属后续迭代前置条件，见 `research.md` R-01）。

## 3. 启动后端

```bash
cd server
mvn -B -DskipTests spring-boot:run        # 默认 dev profile，端口 8080
```

Flyway 在启动时自动执行 `src/main/resources/db/migration/V1__identity_schema.sql`（6 张表，dev/test/prod 共用）；演示种子 `src/main/resources/db/seed/V2__identity_whitelist_seed.sql` 仅 dev 通过 `spring.flyway.locations` 追加 `classpath:db/seed` 加载，**test/prod 不加载**（测试白名单由夹具动态插入，生产由 DBA 受控导入）。**禁止**在 `application-test.yml` 关闭 Flyway。

自检：

```bash
curl -s http://localhost:8080/api/health
```

`code=200` 且 `database`、`redis` 均为 `UP` 才继续。

## 4. 启动前端

```bash
cd web
npm install
npm run dev        # http://127.0.0.1:5183
```

端口固定 `5183`（`strictPort: true`），避免与本机其他 Vite 项目（5173）冲突。

## 5. 页面路由

| 路由 | 页面 | 访问要求 |
|------|------|----------|
| `/patient/register` | 患者注册 | 匿名 |
| `/patient/login` | 患者登录 | 匿名 |
| `/patient/password-reset` | 忘记密码 | 匿名 |
| `/patient/identity-verification` | 实名认证 | 需登录 |
| `/patient/profile` | 个人中心（安全退出 + 认证状态） | 需登录 |
| `/consultation/entry` | 门禁占位入口（仅展示认证引导/「线上问诊建设中」，不含问诊业务） | 需登录 + 需实名认证（`meta.requiresVerified`） |

未认证账户访问 `/consultation/entry` 时由 `requiresVerified` 守卫按端点 8 的 `consultationAllowed=false` 跳转 `/patient/identity-verification`；后端 403 `IDENTITY_NOT_VERIFIED` 由后续问诊模块返回，本功能不产出。

## 6. 端到端验证步骤

### 6.1 注册（用户故事 1）

```bash
# 1) 签发验证码（不发送短信，不返回验证码）
curl -s -X POST http://localhost:8080/api/v1/verification-codes \
  -H 'Content-Type: application/json' -H 'X-CSRF-Token: <sdas_csrf 值>' \
  -b 'sdas_csrf=<值>' \
  -d '{"phone":"13800138000","purpose":"REGISTER"}'
# 期望：code=200，data 无验证码字段

# 2) 注册（固定验证码 888888）
curl -s -X POST http://localhost:8080/api/v1/patients \
  -H 'Content-Type: application/json' -H 'X-CSRF-Token: <值>' -b 'sdas_csrf=<值>' \
  -d '{"phone":"13800138000","verificationCode":"888888","password":"abc12345","agreementAccepted":true,"agreementVersion":"v1.0"}'
# 期望：code=200，authStatus=UNVERIFIED，consultationAllowed=false，Set-Cookie 含 sdas_refresh（HttpOnly）
```

重复使用同一验证码再提交一次，期望 `400 AUTHENTICATION_FAILED`（一次性）。将 `purpose` 换成 `RESET_PASSWORD` 签发后用于注册，期望同样失败（用途隔离）。

### 6.2 登录与退出（用户故事 2）

```bash
curl -s -X POST http://localhost:8080/api/v1/patient-sessions \
  -H 'Content-Type: application/json' -H 'X-CSRF-Token: <值>' -b 'sdas_csrf=<值>' \
  -d '{"phone":"13800138000","password":"abc12345"}'
# 期望：code=200，返回 accessToken

curl -s -X DELETE http://localhost:8080/api/v1/patient-sessions/current \
  -H "Authorization: Bearer <accessToken>" -H 'X-CSRF-Token: <值>' -b 'sdas_csrf=<值>'
# 期望：code=200；再次用同一 accessToken 访问端点 8 应返回 401
```

用不存在的手机号登录，比较响应 `message`：必须与密码错误、验证码错误完全一致。

### 6.3 实名认证（用户故事 3）

1. 由测试夹具向 `hospital_identity_whitelist` 插入一条与测试身份匹配的记录（随机生成，禁止真实身份信息）：

```sql
INSERT INTO hospital_identity_whitelist (real_name, id_card_hash, id_card_cipher, enabled, source, created_at, updated_at, version)
VALUES ('测试姓名', '<HMAC 摘要>', <密文>, 1, 'PRESET', NOW(), NOW(), 0);
```

2. 提交认证：

```bash
curl -s -X POST http://localhost:8080/api/v1/identity-verifications \
  -H 'Content-Type: application/json' -H "Authorization: Bearer <accessToken>" \
  -H 'X-CSRF-Token: <值>' -b 'sdas_csrf=<值>' \
  -d '{"realName":"测试姓名","idCardNumber":"<与白名单匹配的 18 位证件号>"}'
# 期望：code=200，authStatus=VERIFIED，consultationAllowed=true
```

3. 未命中的身份提交，期望 `400 AUTHENTICATION_FAILED`，且 `GET /api/v1/patients/current/identity-status` 仍为 `UNVERIFIED`。
4. 已认证账户重复提交，期望 `200` 且不新增认证记录。

### 6.4 忘记密码（用户故事 4）

```bash
curl -s -X POST http://localhost:8080/api/v1/password-resets \
  -H 'Content-Type: application/json' -H 'X-CSRF-Token: <值>' -b 'sdas_csrf=<值>' \
  -d '{"phone":"13800138000","verificationCode":"888888","newPassword":"xyz98765"}'
# 期望：code=200；旧密码登录失败，新密码登录成功；原有 accessToken 访问受保护端点返回 401
```

### 6.5 问诊门禁占位入口（SC-003）

1. 用 6.1 注册的未认证账户访问 `http://127.0.0.1:5183/consultation/entry`，期望被守卫重定向至 `/patient/identity-verification`，且端点 8 返回 `consultationAllowed=false`。
2. 完成 6.3 认证后再次访问 `/consultation/entry`，期望进入占位页并显示「线上问诊建设中」，页面不得出现问诊表单或提交动作（不调用任何问诊 API）。
3. 记录未认证账户成功进入该入口的次数，必须为 **0**，结果写入 `testsdoc/patient-identity/`。

## 7. 测试命令

```bash
# 后端：单测 + 集成测试（Testcontainers 需 Docker）
cd server && mvn -B test

# 前端：类型 / 规范 / 单测 / 构建
cd web
npm run typecheck
npm run lint
npm run test
npm run build

# Mock E2E（使用 contracts/mocks.md 的 M1–M15）
npm run test:e2e

# 真实后端 E2E（需后端 + MySQL + Redis 全部就绪，断言响应 code）
$env:E2E_REAL_BACKEND='1'
$env:PLAYWRIGHT_CHANNEL='msedge'
# mysql 客户端未加入 PATH 时指定其绝对路径；CI 通常无需
$env:E2E_MYSQL_CLI='<mysql.exe 绝对路径>'
npm run test:e2e
```

`E2E_REAL_BACKEND=1` 时 5 个 Mock 套件自动跳过，仅执行 4 个真实套件（其中重置密码用例需跨过 60 s 发送限流窗口，整轮约 1.2 min）。

测试报告写入 `testsdoc/patient-identity/[测试名].md`；每迭代执行一次 `contracts/README.md` 第 6 节的契约一致性验证。

## 8. 常见问题

| 现象 | 原因 | 处理 |
|------|------|------|
| 启动即失败，日志提示固定验证码 | `prod` profile 下 `fixed-enabled=true` | 置为 `false` 并接入短信实现（后续迭代） |
| 所有接口 `503 SERVICE_UNAVAILABLE` | MySQL 授权失败或 Redis 未启动 | 修复 `root/123456` 授权、启动 Redis，`/api/health` 复检 |
| 修改类请求 403 `CSRF_TOKEN_MISMATCH` | 未带 `sdas_csrf` Cookie 或 `X-CSRF-Token` 不一致 | 先访问任一页面获取 Cookie，再复制值到请求头 |
| 修改类请求 403 `Invalid CORS request` | `CORS_ALLOWED_ORIGINS` 未包含当前前端源 | dev 默认值已含 `127.0.0.1:5183`；自定义端口时补入，生产必须显式注入 |
| Navicat / 控制台查看中文乱码 | 客户端连接编码不是 UTF-8，或查看的是本机其他项目库 | 本项目库、表、列均为 `utf8mb4` / `utf8mb4_general_ci`，数据本身正确（`SELECT HEX(real_name) FROM hospital_identity_whitelist` 可见标准 UTF-8 字节）；在 Navicat 连接属性「高级」把编码设为「自动 / 65001 (UTF-8)」，或先执行 `SET NAMES utf8mb4;`；JDBC 连接已固定 `characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci` |
| 注册返回 `AUTHENTICATION_FAILED` 但验证码正确 | 验证码已使用 / 过期 / 用途不符 / 手机号已注册 | 重新签发（60 s 间隔），确认 `purpose` 与手机号 |
| 前端请求 404 且路径含 `/api/api` | 具体路径重复写了 `/api` | `src/api/` 内改为 `/v1/...` |
| 未认证账户能进问诊页 | 前端守卫未校验 `consultationAllowed` | 检查 `requiresVerified` 守卫与端点 8 的返回 |
| Playwright 打开的是其他项目页面 | 端口被占用且复用已有服务 | 确认 `VITE_DEV_PORT=5183`、`strictPort: true`、`reuseExistingServer: false` |

## 9. 完成判定

- [ ] 4 个用户故事的验收场景全部通过（含统一模糊提示、用途隔离、一次性、幂等、门禁拦截）。
- [ ] `contracts/README.md` 第 6 节 10 项一致性验证通过。
- [ ] 后端单测覆盖率 ≥ 70%，全部 REST API 正向 + 异常集成测试通过。
- [ ] 静态检查零错误零警告；测试报告已归档。
- [ ] 日志与响应中不存在密码明文、验证码明文、完整手机号、完整身份证号。