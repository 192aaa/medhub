# MedHub（医枢）

医疗平台：连接患者、医生与药房的互联网诊疗系统，覆盖患者身份认证（001）、预约挂号与图文问诊（002）、医生接诊（003）、处方取药（004）、AI 健康咨询（005）、AI 辅助治疗决策（006）。

## 目录结构

| 目录 | 说明 |
|---|---|
| `server/` | Spring Boot 后端（Java 17 + JdbcTemplate + Flyway） |
| `web/` | Vue 3 + TypeScript 前端（Vite + Pinia + Vue Router） |
| `specs/` | 规范文档（`001-patient-identity`、`002-appointment-consultation`） |

## 当前 Demo 限制

本项目目前是“核心后端可用 + 部分前端为演示占位”的状态，以下能力尚未生产就绪：

| 能力 | 当前状态 |
|---|---|
| 支付 | 已实现“创建待支付流水 + 沙箱确认”两段式流程；`MOCK` 渠道仍未接真实第三方支付网关 |
| 图文问诊入口 | 已替换为真实入口，可进入挂号流程、AI 健康咨询与问诊单页面 |
| 药品目录 / 工作台处方页 | 已改为从后端药品目录 API 加载，不再使用前端演示数据 |
| AI 健康咨询 | 90 项任务中已勾选 66 项，剩余 SSE / 测试夹具 / Playwright 场景待复核 |
| AI 治疗决策 | 有实现代码，但任务清单未同步，降级链路需后端测试验证 |

各模块 `specs/*/tasks.md` 顶部已补充进度说明，请勿仅凭复选框判断实际完成情况。

## 前置依赖

- JDK 17
- Maven 3.9.x（本机 `C:\tools\apache-maven-3.9.6`）
- Node 24 / npm
- MySQL 8（`localhost:3306`，账号 `root`，开发库 `cloud_consultation`，测试库 `cloud_consultation_test`）
- Redis（`localhost:6379`，test profile 使用 db 1）

### 数据库初始化

首次运行前创建空库，Flyway 会自动建表；dev profile 还会加载演示种子：

```sql
CREATE DATABASE cloud_consultation CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE cloud_consultation_test CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 环境变量

数据库密码不再写入配置文件，启动和测试前必须设置：

```powershell
$env:MYSQL_PASSWORD = "你的 MySQL root 密码"
$env:TEST_DB_PASSWORD = "你的 MySQL root 密码"   # 仅跑后端测试时需要
```

AI 功能按需设置（见“AI 导诊配置”）：

```powershell
$env:DEEPSEEK_API_KEY = "你的 key"
```

## 快速启动

### 1. 基础服务

```powershell
C:\redis\redis-server.exe   # Redis
```

MySQL 以系统服务方式运行即可。

### 2. 后端

```powershell
cd server
$env:PATH += ";C:\tools\apache-maven-3.9.6\bin"
mvn -o package -DskipTests
$env:SERVER_PORT = "8081"   # 与前端代理目标保持一致
java -jar target/sdas-server-0.0.1-SNAPSHOT.jar   # http://localhost:8081
```

> `web/.env.development` 中 `VITE_API_TARGET=http://localhost:8081`，因此本机默认用 8081。若要使用后端默认端口 8080，请把该值改为 `http://localhost:8080` 并去掉 `$env:SERVER_PORT` 设置。

- 默认 profile 为 `dev`（连 `cloud_consultation` 库，加载 `db/seed` 演示种子）。
- 需要连测试库时指定 `test`（连 `cloud_consultation_test`，不加载种子）：

  ```powershell
  $env:SPRING_PROFILES_ACTIVE = "test"
  java -jar target/sdas-server-0.0.1-SNAPSHOT.jar
  ```

- **改过 Java / 配置后必须先重新 `mvn -o package -DskipTests` 再重启**，否则 `java -jar` 跑的还是旧包（接口 404 / 500 多为旧 jar 所致）。

### 3. 前端

```powershell
cd web
npm install   # 首次运行
npm run dev   # http://localhost:5183
```

## AI 导诊配置

Provider 走 OpenAI 兼容协议对接 SophNet（模型 `DeepSeek-Flash-1017`）。Key 通过环境变量注入，避免提交进库：

```powershell
$env:DEEPSEEK_API_KEY="你的key"
```

`server/src/main/resources/application.yml` 中 `spring.ai.openai.base-url` 须为根地址
`https://api.sophnet.com`（Spring AI 会自动拼 `/v1/chat/completions`，勿写完整端点）。

## 本地联调：测试账号与数据

三套账号体系相互独立，登录入口与账号表各不相同。

### 医生工作台（`/workbench/login`，表 `doctor_account`）

| 用户名 | 密码 | 对应医生 |
|---|---|---|
| `doctor_zhang` | `password` | 张*（内科 101） |
| `doctor_li` | `password` | 李*（内科 102） |

仅 dev 种子含 `doctor_zhang/doctor_wang/doctor_sun`；test 库由测试写入，账号以实际库内数据为准。

### 药店端（`/pharmacy/login`，表 `pharmacy_account`）

密码统一为 `password`（仅 dev 种子加载）。

### 患者端（`/patient/register` → `/patient/login`，表 `patient_account`）

无预设账号，走注册流程：

1. 手机号：任意 11 位、`1[3-9]` 开头；
2. 验证码：固定 `888888`（`sdas.identity.verification-code.fixed-code`）；
3. 密码：6–20 位字母数字，至少 1 字母 + 1 数字；
4. 勾选协议后提交，注册即登录。

### 实名认证白名单（表 `hospital_identity_whitelist`）

认证要求「姓名 + 证件号 HMAC 摘要」命中白名单（证件号须 18 位、末位校验位符合 GB 11643）。
本地演示用组合（已按各 profile 密钥算好摘要，勿直接复用 dev/test 之间的摘要）：

- 姓名：`李四`
- 身份证号：`110105199001010010`

一个证件号只能绑定一个账号，绑定后其他账号提交同一证件号会返回统一失败文案（审计 `reason_code=ID_CARD_BOUND`）。

### 预约挂号的复诊门禁（表 `patient_history_visit`）

挂号仅对「有历史就诊记录」的患者开放（FR-007）。首次预约前需插入一条：

```sql
INSERT INTO patient_history_visit (patient_id, visit_date, department_id, doctor_id, summary)
VALUES (<患者ID>, '2026-10-01', 1, 101, '既往内科就诊');
```

缺少时提交预约返回 `REVISIT_NOT_ELIGIBLE`。同一患者对同一排班只能有一条预约记录，重复创建会报 `INVALID_APPOINTMENT_STATUS`。

### AI 能力

- 模型统一使用 `DeepSeek-Flash-1017`（SophNet）；AI 健康咨询模块原默认值 `deepseek-v4-flash` 在 SophNet 不存在，已修正。
- 修改类接口强制 CSRF 双 Cookie 校验（`X-CSRF-Token` 头 = `sdas_csrf` Cookie），新增写接口需显式带 `csrfHeaders()`。

## 测试

### 后端（无需 Docker，复用本机 MySQL/Redis）

> 先确认 `$env:MYSQL_PASSWORD` 和 `$env:TEST_DB_PASSWORD` 已设置（见“环境变量”）。

```powershell
cd server
.\run-test-local.ps1            # 全量测试（离线 -o）
.\run-test-local.ps1 -Reset     # 先重建测试库，清除旧 Flyway 失败历史残留
.\run-test-local.ps1 -Filter "AppointmentStatusConverterTest"   # 只跑指定类
```

也可双击 `server\run-test-local.cmd`（自动绕过 PowerShell 执行策略）。

### 前端

```powershell
cd web
npm run typecheck
npm run test
npm run build
```
