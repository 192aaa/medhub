# 实施方案：处方取药

**分支**：`004-prescription-dispensing` | **日期**：2026-11-08 | **规约**：[spec.md](./spec.md)

**输入**：`/specs/004-prescription-dispensing/spec.md`（已经 `/speckit.clarify` 定稿 Q1–Q7）

## 摘要

本方案交付「处方取药」：患者端查看并出示带验真二维码的电子处方；药店端登录后扫码（或上传二维码图片、或手工输入）验真，查看取药状态并核销。

技术要点有四处：

1. **二维码防伪令牌**：把 003 的「明文处方号 + 32 位截断签名前缀」升级为 `v1.<AES-GCM 密文>.<RSA-SHA256 签名>` 的紧凑令牌，处方 ID 加密不可枚举、签名可离机验真，长度 ≤ 512 字符并同步把 `prescription.qr_code` 扩到 `VARCHAR(1024)`。
2. **验真接口 `POST /api/v1/pharmacy/prescription-verifications`**：先验令牌签名 → 解密 → 校验令牌有效期 → 载入处方 → 复验 003 的处方内容签名 → 校验 `valid_until` → 派生取药状态；**验真为纯读操作，二维码可被任意次重复扫描**，仅写审计。
3. **核销幂等**：新增 `prescription_dispense_record` 表并以 `UNIQUE (prescription_id)` 兜底并发，重复/并发核销统一返回 `200 + ALREADY_DISPENSED`，不产生第二条记录、不回退状态。
4. **修复 003 遗留缺陷**：签名密钥从「进程内临时 RSA KeyPair」改为 keystore 加载（否则服务重启后所有历史二维码验真必然失败）；补 `PRESCRIPTION_NOT_FOUND` 并修正 003 `detail()` 误用 `CONSULTATION_NOT_FOUND`；给 `pharmacy-request.ts` 补 `withCredentials`（否则药店端 POST 必 403 CSRF）。

## 技术上下文

**语言/版本**：Java 17 + Spring Boot 3.4.5（后端）；TypeScript 5.7 + Vue 3.4 + Vite 5.4 + Pinia 2.3（前端）。

**主要依赖**：后端已有 `spring-jdbc`（JdbcTemplate，非 JPA）、`spring-security-crypto`（BCrypt/HMAC）、`jjwt`（HS256）、`zxing 3.5.3`（二维码生成，003 已引入）；**本功能新增 0 个后端依赖**。前端新增 `html5-qrcode@2.3.8`（客户端扫码 + 图片解析，见 G1 评审）。

**存储**：MySQL 8.0（Flyway 迁移，奇数版本 = migration、偶数 = seed；下一个版本号 `V7__prescription_dispensing_schema.sql` / `V8__prescription_dispensing_seed.sql`）；Redis 8.x（会话撤销标记，复用 001）。

**测试**：JUnit 5 + Mockito + Spring Boot Test（本机 MySQL/Redis 或 Testcontainers）；Vitest（jsdom）+ Playwright（`page.route()` Mock E2E 与真实后端 E2E 成对）。

**目标平台**：Web（Chrome/Edge 桌面 + 移动浏览器）；药店端扫码需 `navigator.mediaDevices`（要求 HTTPS 或 localhost）。

**性能目标**：验真接口 P95 ≤ 200 ms（端到端扫码→结果 ≤ 2 s，SC-001）；核销 P95 ≤ 300 ms；二维码图片 ≤ 16 KB。

**约束**：二维码内容 ≤ 1024 字符；验真为只读、可重复；核销一次且不可回退；响应与日志零敏感明文；不新增后端运行时依赖。

**规模**：5 个 REST 端点、2 张新表 + 1 处列扩容、5 个前端页面、3 个用户故事。

## 宪法检查

*门禁：Phase 0 研究前必须通过；Phase 1 设计后复核。*

依 `.specify/memory/constitution.md`（v1.0.0）：

| 门禁 | 结论 | 证据 / 处置 |
|------|------|-------------|
| **G1 技术栈与依赖** | 通过（带 1 项登记） | 后端零新增依赖；前端新增 `html5-qrcode@2.3.8`，架构评审记录见 `research.md` R-07，理由与被否方案登记于本文件 Complexity Tracking |
| **G2 分层与目录** | 通过 | 新增 `server/.../pharmacy/{controller,service,repository,entity,dto,support}`，Controller → Service → Repository 严格分层；新增 `PharmacyIdExtractor`（禁止 Controller 自解析 token）；路由 `/prescriptions/*` 与 `/pharmacy/*` 与既有 `/patient/*`、`/workbench/*` 词干不冲突；前端 `pharmacyRequest` 独立实例 + `sdas.pharmacy.token`，Pinia store 从 `localStorage` 恢复 |
| **G3 契约与 API** | 通过（含 1 项修订） | 先产出 `data-model.md` + `contracts/`（5 端点 + M1–M12 Mock）再实现；响应 `{code,message,data}`、时间 `yyyy-MM-dd'T'HH:mm:ss`、`/api` 前缀沿用 001–003；**需修订 003 契约中 `qrCode` 字段说明**（单一二维码规则，见下「兼容与回滚」） |
| **G4 安全** | 通过 | 药店 JWT HS256 ≤ 24h、`role="PHARMACY"`；修改类请求双重 Cookie；统一 `ErrorCode` 与模糊失败提示；验真/核销/失败全量审计且脱敏；验签在服务端、私钥不出服务端；不提供匿名验真入口（Q4） |
| **G5 代码规范** | 通过（带 2 项登记） | 新增类全部补 Javadoc、圈复杂度 ≤ 10；**修复 003 签名密钥未读 keystore**（RK-01）与 `PRESCRIPTION_NOT_FOUND` 误用；SpotBugs 需把 `pharmacy` 包纳入既有豁免口径（`EI_EXPOSE_REP2`/`OBL` 归因于 Spring 注入与 JdbcTemplate，与 identity/appointment/doctor 一致），登记于 Complexity Tracking |
| **G6 测试门禁** | 通过（计划） | 后端行覆盖率 ≥ 70%（`mvn verify` 的 jacoco check）；5 端点正向/异常契约测试；并发核销/篡改令牌/过期边界集成测试；Playwright 覆盖 3 个用户故事（mock + real 成对）；报告落 `testsdoc/prescription-dispensing/` |

无未登记违规；`/speckit.tasks` 前如需变更需回写本表。

## Phase 0 研究结论（详见 [research.md](./research.md)）

| 编号 | 决策 | 关键依据 |
|------|------|----------|
| R-01 | 二维码令牌格式 `v1.<b64url(cipher)>.<b64url(sig)>` | 处方 ID 必须不可枚举；签名必须在服务端外也可验证；单段紧凑格式便于扫码与手工粘贴 |
| R-02 | 载荷加密复用 `CryptoService.encrypt/decrypt`（AES-GCM，密钥来自 `IdentityProperties.crypto.aesKey`） | 已有实现与密钥管理，避免第二套 KMS |
| R-03 | 签名密钥改为 keystore 加载（`sdas.doctor.signature.*` 已配置但未使用） | 进程内 2048 位 KeyPair 重启即失效，历史二维码全部验真失败；keystore 是 003 既定设计 |
| R-04 | 验真用 `POST` + 请求体传令牌，不用 `GET` + query | GET 会把签名写进 Nginx/访问日志；POST 也便于统一 CSRF 与人工粘贴 |
| R-05 | 验真只读、可重复扫描；不消费令牌 | 用户显式要求「可被多次扫描验真」；状态由处方 + 核销记录派生，反复扫码无副作用 |
| R-06 | 取药状态由核销记录派生，不新增 `DISPENSED` 状态值 | 遵守 Q5，避免改动 003 已落库状态机与既有 `switch` |
| R-07 | 前端扫码用 `html5-qrcode`（`Html5Qrcode` 摄像头 + `Html5Qrcode.scanFile` 图片解析） | 一个库同时覆盖摄像头与上传图片两条路径，路由级懒加载，避免自研取流/解码 |
| R-08 | 核销幂等 = 唯一约束 + `DuplicateKeyException` 兜底，重复返回 `200 + ALREADY_DISPENSED` | 无需分布式锁；避免 409 让前端把「已核销」当错误处理 |
| R-09 | 二维码 PNG 尺寸 480×480、纠错级别 M | 令牌约 460 字符，300px 时模块过密；M 级兼顾破损容忍与容量 |
| R-10 | 手工输入验真码 = 令牌签名段前 12 位（约 72 bit 熵） | 满足 FR-006 无摄像头场景，同时把枚举成本抬到不可行区间 |

## Phase 1 设计产物

- [data-model.md](./data-model.md)：2 张新表 DDL、`qr_code` 扩容、3 个枚举（`PharmacyAccountStatus`、`DispenseStatus`、`DispenseResult`）、派生优先级、新增 `ErrorCode`、与 003 `PrescriptionStatus` 的映射。
- [contracts/README.md](./contracts/README.md)：统一响应、认证/CSRF、错误码索引、敏感信息约束、契约一致性清单、端点一览。
- [contracts/prescription-dispensing-api.md](./contracts/prescription-dispensing-api.md)：5 个端点的请求/响应/异常明细。
- [contracts/mocks.md](./contracts/mocks.md)：M1–M12 Mock 场景（含篡改、过期、并发、越权）。
- [quickstart.md](./quickstart.md)：本地起服务、预置账号、手工验真与核销的验证步骤。

## 二维码防伪签名机制（专项设计）

### 1. 令牌结构

```text
v1.<cipher>.<sig>
```

| 段 | 内容 | 生成方式 | 长度 |
|----|------|----------|------|
| `v1` | 版本前缀 | 常量，便于后续轮换 | 2 |
| `cipher` | 加密载荷 | `Base64Url(CryptoService.encrypt(JSON))`，AES-GCM（12B IV + 16B tag 内置） | ≈ 91–120 |
| `sig` | 私钥签名 | `Base64Url(SHA256withRSA(sign("v1." + cipher)))`，RSA-2048 | 342 |

载荷明文（**不含任何患者身份明文**）：

```json
{"v":1,"pid":123456,"iat":1762588800,"exp":1762857600}
```

- `pid` 为处方主键，仅存在于密文中；`iat/exp` 用于令牌层面的粗粒度时限（默认 = 处方 `valid_until`，由服务端生成时写入）。
- 患者姓名、手机号、身份证号、诊断、药品明细**一律不进二维码**（FR-005）。

### 2. 签名与验签

- 签名算法：`SHA256withRSA`，密钥来自 keystore（`sdas.doctor.signature.key-store-path` / `key-alias` / `key-store-password`），私钥不出服务端。
- 签名原文：`"v1." + cipher` 的 ASCII 字节（前缀防降级：未来 `v2` 不能被当作 `v1` 验过）。
- 验签步骤（`PrescriptionQrTokenService.verify`）：
  1. 长度与结构校验（`^v1\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$`，总长 ≤ 1024）；
  2. RSA 验签（失败 → `INVALID_VERIFICATION_PROOF`，**不进入解密**，避免 padding/解密侧信道）；
  3. AES-GCM 解密（GCM tag 失败同样归为 `INVALID_VERIFICATION_PROOF`）；
  4. 校验载荷 `v==1` 且 `now ≤ exp`（令牌过期 → `INVALID_VERIFICATION_PROOF`，与处方过期区分：前者是码失效，后者是业务状态）；
  5. 返回 `pid`。
- 防重放：令牌**设计为可重复使用**（R-05），不做一次性消费；真正的「只能用一次」约束在核销记录的唯一键上。

### 3. 与 003 处方内容签名的关系（双层校验）

| 层 | 保护对象 | 算法 | 存在位置 |
|----|----------|------|----------|
| 令牌层 | 二维码本身（防伪造、防枚举） | RSA-SHA256 over `v1.<cipher>` | 二维码字符串 / `prescription.qr_code` |
| 内容层 | 处方正文（药品、剂量、医生、时间） | RSA-SHA256 over `canonicalize(...)`（003 沿用） | `prescription.signature` |

验真成功前必须两层都通过：令牌层证明「这张码是本服务签发的」，内容层证明「库里的处方正文未被篡改」。

### 4. 攻击面与对策

| 攻击 | 对策 |
|------|------|
| 扫他人处方码取药 | 不绑定药店（Q2）；靠「先核销成功者为准」+ 核销人留痕 + 全量审计 |
| 伪造/篡改二维码 | RSA 验签失败即拒绝，响应不含任何处方字段（FR-007） |
| 枚举处方号 | 码内 ID 为 AES-GCM 密文；手工入口需 12 位验真码（≈72 bit） |
| 重放/多次扫描 | 允许（验真只读）；核销层唯一约束兜底 |
| 抓包改 `prescriptionId` 核销 | 核销必须携带 `proof`（完整令牌或验真码），由服务端重新验签 |
| 服务重启后历史码失效 | 修复 R-03：keystore 持久化密钥 |
| 日志泄漏签名 | 令牌只走 POST 请求体；`AccessLogFilter` 不记录 body；审计仅存令牌摘要（SHA-256 前 16 hex） |

### 5. 生成时机与展示

- 生成：003 处方签名成功时调用 `PrescriptionQrTokenService.issue(pid, validUntil)`，把完整 URL 写入 `prescription.qr_code`（模板 `sdas.pharmacy.qr.verify-url-template`）。
- 展示：患者详情接口同时返回 `qrCode`（内容字符串，兼容 003 字段）与 `qrImageDataUrl`（`data:image/png;base64,...`，服务端用 `QrCodeService` 480×480、纠错 M 生成），前端直接 `<img>` 渲染，**不引入二维码生成库**。
- 页面同时展示「验真码」12 位短码，供无摄像头时手工输入。

## 验真 API 设计（专项设计）

### 1. 端点与语义

`POST /api/v1/pharmacy/prescription-verifications`，药店身份，CSRF 必需。

请求（二选一，互斥）：

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `qrContent` | string | 条件 | 8–1024，匹配 `^v1\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$` 或完整 URL | 扫码/粘贴所得 |
| `prescriptionId` | number | 条件 | >0 | 手工输入 |
| `verifyCode` | string | 条件 | 12 位 `[A-Za-z0-9_-]` | 手工输入验真码 |

响应 `data` 关键字段：`verificationResult`（`VALID`）、`prescriptionId`、`status`（003 原状态）、`dispenseStatus`（`PENDING`/`DISPENSED`/`EXPIRED`/`REVOKED`）、`dispensable`、`signedAt`、`validUntil`、`expired`、`dispensedAt`、`dispensedByMask`、`items[]`、`doctorNameMask`、`watermark`、`verifyCode`。

### 2. 为什么是 POST（而非 GET）

- 令牌含签名与密文，GET 会进入反向代理/访问日志与浏览器历史，违反 FR-016。
- POST 走统一 CSRF 双重 Cookie，与既有修改类端点一致；也便于前端把「上传图片解析出的内容」直接放入请求体。
- 语义上是「验真查询」但因携带凭证，按「提交凭证」处理；**服务端保证无副作用**（除审计）。

### 3. 校验顺序（失败即中断，错误码见 data-model 1.6）

```text
药店身份 → CSRF → 参数互斥校验 → 令牌结构/签名 → 解密 → 令牌 exp
→ 处方存在性(404) → 处方内容签名复验(400) → 状态判定（`DRAFT` → 400 `PRESCRIPTION_NOT_SIGNED`；`REVOKED` → 200 + `dispenseStatus=REVOKED` 状态展示，不作为错误）
→ 派生 dispenseStatus(expired 判定用服务端时间)
→ 组装响应 + 写审计(VERIFICATION_SUCCESS / VERIFICATION_FAILED)
```

### 4. 幂等与并发

- 验真：完全幂等，无写库（仅审计），可无限次调用（R-05）。
- 核销：`INSERT ... ` 命中 `uk_dispense_prescription` 唯一键 → 捕获 `DuplicateKeyException` → 读回既有记录 → 返回 `200 + ALREADY_DISPENSED + idempotent=true`；首次 `idempotent=false`。**不使用 409**，避免前端把重复扫码当故障。

### 5. 错误码映射（新增 6 个，复用 5 个）

| 场景 | code | 常量 |
|------|------|------|
| 非药店身份 | 403 | `PHARMACY_ROLE_REQUIRED` |
| 药店账号不存在/禁用 | 404 | `PHARMACY_ACCOUNT_NOT_FOUND` |
| 令牌/验真码无效、被篡改、令牌过期 | 400 | `INVALID_VERIFICATION_PROOF` |
| 处方不存在 | 404 | `PRESCRIPTION_NOT_FOUND` |
| 处方未签名（草稿） | 400 | `PRESCRIPTION_NOT_SIGNED` |
| 处方已作废（**核销**） | 400 | `PRESCRIPTION_REVOKED` |
| 处方已作废（**验真**） | 200 | 非错误码：返回 `dispenseStatus=REVOKED` + `dispensable=false` |
| 处方业务过期（核销时） | 400 | `PRESCRIPTION_EXPIRED`（复用 003；验真返回 200 + `dispenseStatus=EXPIRED`） |
| 未登录 | 401 | `UNAUTHORIZED`（复用） |
| CSRF 失败 | 403 | `CSRF_TOKEN_MISMATCH`（复用） |
| 依赖不可用 | 503 | `SERVICE_UNAVAILABLE`（复用） |
| 登录失败 | 400 | `AUTHENTICATION_FAILED`（复用，模糊提示） |

## 项目结构

### 文档（本功能）

```text
specs/004-prescription-dispensing/
├── plan.md                  # 本文件
├── research.md              # Phase 0 决策记录
├── data-model.md            # 枚举与表结构唯一来源
├── quickstart.md            # 本地验证步骤
├── contracts/               # 前后端契约唯一来源
│   ├── README.md
│   ├── prescription-dispensing-api.md
│   └── mocks.md
└── tasks.md                 # /speckit.tasks 产出（本阶段不创建）
```

### 源码（新增 / 修改清单）

```text
server/src/main/java/com/sdas/server/pharmacy/          # 新增限界上下文
├── controller/PharmacySessionController.java            # 端点1 药店登录
├── controller/PharmacyVerificationController.java        # 端点2 验真、端点3 核销
├── service/PharmacyAuthService.java                     # 登录编排 + 审计
├── service/PrescriptionVerificationService.java          # 验真编排（双层校验）
├── service/PrescriptionDispenseService.java              # 核销编排 + 幂等
├── service/DispenseStatusResolver.java                   # 派生状态规则（纯函数，重点单测）
├── service/impl/...                                      # 上述实现
├── repository/PharmacyAccountRepository.java            # findById/findByUsername/insert
├── repository/DispenseRecordRepository.java             # insert/findByPrescriptionId
├── entity/PharmacyAccount.java / DispenseRecord.java
├── model/PharmacyAccountStatus.java / DispenseStatus.java / DispenseResult.java
├── dto/*.java                                           # 契约字段一一对应
└── support/PrescriptionQrTokenService.java              # 令牌签发/校验（重点单测）
server/src/main/java/com/sdas/server/prescription/       # 新增患者只读子域
├── controller/PatientPrescriptionController.java        # 端点4 列表、端点5 详情
├── service/PatientPrescriptionService.java
└── repository/PatientPrescriptionQueryRepository.java   # 列表分页 + 详情（复用 003 表）

server/src/main/java/com/sdas/server/common/PharmacyIdExtractor.java   # 新增
server/src/main/java/com/sdas/server/identity/service/PharmacyTokenClaims.java + JwtTokenService 增 issuePharmacy/parsePharmacy
server/src/main/java/com/sdas/server/doctor/support/PrescriptionSigningService.java   # 改：keystore 加载 + verify
server/src/main/java/com/sdas/server/doctor/support/QrCodeService.java                # 改：480px + 纠错 M
server/src/main/java/com/sdas/server/doctor/service/impl/PrescriptionServiceImpl.java # 改：签发令牌、修 NOT_FOUND、修 detail
server/src/main/java/com/sdas/server/common/ErrorCode.java                            # 改：新增 6 个
server/src/main/resources/db/migration/V7__prescription_dispensing_schema.sql         # 新增
server/src/main/resources/db/seed/V8__prescription_dispensing_seed.sql                # 新增（dev 药店账号）
server/src/main/resources/application.yml                                             # 改：sdas.pharmacy.* 配置与默认值

web/src/api/prescription.ts / pharmacy.ts                 # 新增 API 模块（标注 contracts 路径）
web/src/api/types.ts / error-codes.ts                     # 新增类型与 code 常量
web/src/api/mocks/prescription.ts / pharmacy.ts           # 新增 Mock（message 逐字取自 mocks.md）
web/src/api/request/pharmacy-request.ts                   # 改：withCredentials + onUnauthorized
web/src/stores/pharmacy.ts                                # 新增（sdas.pharmacy.token / .profile）
web/src/views/prescriptions-view.vue                      # 我的处方
web/src/views/prescription-detail-view.vue                # 处方详情（含二维码 img + 验真码）
web/src/views/pharmacy-login-view.vue                     # 药店登录
web/src/views/pharmacy-verify-view.vue                    # 处方验真（摄像头 + 图片上传 + 手工输入）
web/src/views/pharmacy-dispense-view.vue                  # 取药核销确认
web/src/router/index.ts / guards.ts                       # 改：5 条路由 + requiresPharmacy
web/tests/unit/*.spec.ts                                  # 新增：QR 解析容错、状态徽标、store
web/tests/e2e/prescription.mock.spec.ts + prescription.real.spec.ts            # US3
web/tests/e2e/pharmacy-verification.mock.spec.ts + .real.spec.ts               # US1
web/tests/e2e/pharmacy-dispensing.mock.spec.ts + .real.spec.ts                 # US2
web/tests/e2e/fixtures/prescription-dispensing.ts                              # 统一 mock 注入器

testsdoc/prescription-dispensing/                          # 测试报告归档（新建）
```

**结构决策**：沿用宪法固定布局，新增两个后端包（`pharmacy` 药店端、`prescription` 患者只读），不改造既有包层级；前端沿用「一角色一请求实例 + 一业务域一 api 模块」。

## 实施阶段

| 阶段 | 内容 | 完成标志 |
|------|------|----------|
| P0 基建 | V7/V8 迁移、`ErrorCode` 6 项、`PharmacyIdExtractor`、JWT `issuePharmacy/parsePharmacy`、keystore 改造、`pharmacy-request` 修复、路由与 store 骨架 | `mvn test` 绿；`/pharmacy/login` 可登录；历史二维码在新进程仍可验签 |
| P1 US1（P1） | 令牌服务 + 端点 2 验真 + 药店登录 + 验真页（摄像头/图片/手工） | 契约测试全绿；篡改/过期/越权场景通过 |
| P2 US2（P1） | 端点 3 核销 + 派生状态 + 并发唯一约束 + 审计 | 并发/重复核销测试通过；SC-004 达标 |
| P3 US3（P2） | 端点 4/5 + 患者两页 + 二维码图片 | 患者可见二维码；越权 403；E2E US3 通过 |
| P4 收口 | 覆盖率、Checkstyle/SpotBugs、Playwright 全量、`testsdoc/` 报告、契约一致性核对 | `mvn verify` 与 `npm run lint && npm run test && npm run test:e2e` 全绿 |

## 迁移与配置变更

```sql
-- V7（节选，完整见 data-model.md 第 2 节）
CREATE TABLE pharmacy_account (...);
CREATE TABLE prescription_dispense_record (... UNIQUE KEY uk_dispense_prescription (prescription_id) ...);
ALTER TABLE prescription MODIFY COLUMN qr_code VARCHAR(1024) NULL;
CREATE INDEX idx_prescription_patient ON prescription (patient_id, signed_at);
```

```yaml
sdas:
  pharmacy:
    qr:
      verify-url-template: ${SDAS_PHARMACY_VERIFY_URL:http://localhost:5183/pharmacy/verify?t=%s}
      token-version: v1
      verify-code-length: 12
      image-size: 480
      error-correction: M
```

## 兼容与回滚

- **二维码规则变更（唯一规则，非并行规则）**：令牌格式取代「明文处方号 + 32 位前缀」。执行时同一次提交内更新 003 `contracts/doctor-consultation-api.md` 中 `qrCode` 的说明，并在 `data-model.md` 标注替代关系；前端 Mock 与 E2E 断言同步，杜绝双规则并存。
- **存量数据**：003 尚未上线，`prescription` 仅有 dev 种子/测试数据；V7 后由开发环境重建处方（重新签名）即可。若出现无法识别的旧格式 `qr_code`，验真返回 `INVALID_VERIFICATION_PROOF` 并写审计，不做静默降级。
- **密钥**：首次启动若 keystore 不存在，`prod` profile 启动失败（fail-fast）；`dev/test` 允许自动生成并写入 `./keystore/doctor-signing.jks`（仅开发路径，且日志警示）。
- **回滚**：V7 为纯新增 + 列扩容，无数据破坏性变更；回滚仅需下线端点与前端路由，列扩容可保留。

## 复杂度与门禁登记

| 违规/偏差 | 为什么需要 | 被否决的更简单方案 |
|-----------|-----------|--------------------|
| 新增前端依赖 `html5-qrcode@2.3.8` | 一个库同时覆盖摄像头扫码与上传图片解析（`Html5Qrcode.scanFile`），并自带取流/降级/权限提示 | ①`jsQR`：需自研 `getUserMedia` 取流、逐帧绘制与图片解码，代码量与兼容风险高；②`@zxing/browser`：能力重叠但包体更大、文件解析 API 更繁琐；③纯后端解析上传图片：需引入 ZXing Java 依赖 + 图片上传链路，违背「不新增后端依赖」 |
| 二维码规则修订（影响 003 契约） | 现状 `sig` 只是 32 位截断前缀，无法验签；处方号明文可枚举，不满足防伪要求 | 保留 `prescriptionId+sig` 并仅校验前缀：任何人可枚举处方号伪造 URL，防伪形同虚设 |
| 加密 + 签名双层（非单一签名） | 加密解决「ID 不可枚举」，签名解决「码不可伪造且可离机验证」，缺一不可 | 只签名不加密：处方 ID 明文进码，可被批量枚举探测状态；只加密不签名：GCM 密钥仅在服务端，无法满足「私钥签名」合规要求 |
| SpotBugs 豁免扩至 `pharmacy` 包 | `EI_EXPOSE_REP2`（Spring 注入 Bean 保存引用）、`OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE`（JdbcTemplate 关闭 PS）为框架固有模式，与既有豁免口径一致 | 逐类加 `@SuppressFBWarnings` 分散且易漏；改造为构造器注入新对象会破坏 Spring 单例语义 |

## 质量门禁复核（Phase 1 后）

- **G1**：`package.json` 仅新增 `html5-qrcode`；后端 `pom.xml` 无新增依赖。
- **G2**：新增包与既有包无循环依赖；Controller 不含 SQL；前端 `views/` 不直接调 axios。
- **G3**：`contracts/` + `data-model.md` 已产出，字段/`code`/时间格式/分页与既有功能一致；003 契约在同一次提交内修订。
- **G4**：无匿名验真入口；`proof` 必填且服务端复验；审计脱敏；keystore 密码仅走环境变量。
- **G5**：`mvn -o checkstyle:check` 0 违规；SpotBugs 豁免口径一致；无 `@Enumerated` 与 `@Converter` 并存。
- **G6**：测试矩阵见 spec 8.1 与 `tasks.md` 的测试任务；覆盖率 ≥ 70% 由 jacoco check 强制。
