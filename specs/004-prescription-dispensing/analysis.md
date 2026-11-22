# 处方取药 一致性与可行性分析

**功能**：`004-prescription-dispensing` | **日期**：2026-11-08
**输入**：`spec.md`、`plan.md`、`research.md`（R-01–R-11）、`data-model.md`（1.1–1.7）、`contracts/`（`README.md`、`prescription-dispensing-api.md`、`mocks.md`）、`tasks.md`（T001–T082）、`quickstart.md`
**分析目的**：在 `/speckit.implement` 之前，按 Constitution Check（G1–G6）复核三方（规约 / 计划 / 任务）一致性，识别冲突、缺口与歧义，给出放行或修订建议。
**上游现状**：001（JWT/双重 Cookie/`CryptoService` AES-GCM/审计/`GlobalExceptionHandler`/`PatientIdExtractor`）、002（`doctor` 档案、`/consultations`）、003（`prescription`/`prescription_item`/`PrescriptionSigningService`/`QrCodeService`）均已实现。

---

## 1. 分析结论（TL;DR）

| 维度 | 结论 |
|------|------|
| 门禁 G1–G6 | **G1/G2/G5 通过；G3/G4/G6 有条件通过**（3 项缺陷待修，见 §4） |
| 任务覆盖 | 82 条任务覆盖 3 个用户故事与 5 个端点；**FR 覆盖 22/23 完整、1 项表述冲突**（FR-005）；**SC 覆盖 10/11**（SC-002 无测量任务） |
| 一致性问题 | 12 项（1 CRITICAL + 3 HIGH + 3 MEDIUM + 5 LOW）：**已于 2026-11-08 全部修复并复核，见 §9** |
| 技术可行性 | **高**：后端零新增依赖；仅前端新增 `html5-qrcode`（已评审）；核心能力（AES-GCM、RSA 签名、ZXing）均已存在 |
| 最大风险 | ① 二维码规则修订未回写 spec FR-005（会导致「实现符合计划但违反规约」）；② 作废处方验真语义自相矛盾；③ 契约引用不存在的错误码；④ 核销失败无审计事件 |
| 放行建议 | **可进入 `/speckit.implement`**：§4 的 12 项已全部闭环（§9），无阻断项 |

---

## 2. 需求覆盖核对

### 2.1 功能需求 → 任务

| 需求 | 覆盖任务 | 状态 |
|------|----------|------|
| FR-001 药店登录/角色 | T007、T015、T034、T035、T043 | ✅ |
| FR-002 独立请求实例/token | T003、T040、T041、T042、T045 | ✅ |
| FR-003 患者列表 | T023、T064、T065、T066、T069 | ✅ |
| FR-004 患者详情要素+水印 | T065、T066、T069 | ✅ |
| **FR-005 二维码规则** | T018、T022、T024、T065 | ⚠️ **冲突**（C-01） |
| FR-006 扫码/上传/手工 | T044、T046 | ⚠️ 缺 URL 自动验真（C-06） |
| FR-007 验签失败不泄漏 | T021、T029、T036、T039 | ✅ |
| FR-008 返回要素与状态 | T029、T036、T038 | ⚠️ REVOKED 语义冲突（C-02） |
| FR-009 服务端时间判定 | T019、T020、T065 | ✅ |
| FR-010 核销 | T053、T054、T057 | ✅ |
| FR-011 幂等 | T048、T053 | ✅ |
| FR-012 并发仅一次 | T010、T050、T053 | ✅ |
| FR-013 整单核销不可回退 | T053、T054（无撤销端点） | ✅ |
| FR-014 患者越权 404 | T059、T065 | ✅ |
| FR-015 药店端脱敏 | T034、T038、T039 | ✅ |
| **FR-016 审计零明文** | T009、T039、T053、T076 | ⚠️ 缺核销失败事件（C-04） |
| FR-017 服务端二次校验 | T036、T053 | ✅ |
| **FR-018 事务编排** | T053 | ⚠️ 措辞仍写「多表」（C-08） |
| FR-019 依赖降级 503 | T025、T079 | ✅ |
| FR-020 派生状态唯一来源 | T008、T019、T020、T065 | ✅ |
| FR-021 角色隔离 | T016、T029、T059 | ✅ |
| FR-022 失败可统计 | T009、T039、T077 | ⚠️ 依赖 C-04 |
| FR-023 药店端仅验真+核销 | T045、T057 | ✅ |

### 2.2 成功标准 → 验证任务

| 标准 | 验证任务 | 状态 |
|------|----------|------|
| SC-001 验真 P95 ≤ 2s | T077（接口 200ms + E2E 人工） | ✅ |
| **SC-002 核销 P95 ≤ 30s** | — | ❌ **无测量任务**（C-11） |
| SC-003 篡改验真通过 0 次 | T021、T029、T031 | ✅ |
| SC-004 并发/重复成功 >1 次为 0 | T049、T050 | ✅ |
| SC-005 过期/作废/草稿核销 0 次 | T047、T049 | ✅ |
| SC-006 越权返回处方 0 次 | T059、T062 | ✅ |
| SC-007 敏感信息出现 0 次 | T076 | ✅ |
| SC-008 降级无半成品 | T048、T079 | ✅ |
| SC-009 契约一致性差异 0 | T075、T079 | ✅（依赖 C-01/C-03 修复） |
| SC-010 失败可统计 + 枚举成功 0 | T029、T031 | ✅ |
| SC-011 派生状态零误差 | T020、T027 | ✅ |

### 2.3 端点 → 任务

| 端点 | 实现 | 契约测试 | 集成/E2E |
|------|------|----------|----------|
| 1 `POST /pharmacy/sessions` | T035 | T028 | T032 |
| 2 `POST /pharmacy/prescription-verifications` | T037 | T029 | T031、T032 |
| 3 `POST /pharmacy/prescriptions/{id}/dispensing` | T054 | T047 | T049、T050、T051 |
| 4 `GET /prescriptions` | T066 | T059 | T061、T062 |
| 5 `GET /prescriptions/{id}` | T066 | T059 | T061、T062 |

> 5 个端点均「实现 + 契约 + 集成/E2E」三件套齐备，无孤立端点。

---

## 3. Constitution Check 门禁复核（强制）

| 门禁 | 要求 | 复核证据 | 结论 |
|------|------|----------|------|
| **G1 技术栈** | 仅批准技术栈，新增依赖有评审 | 后端零新增（JDK `Signature`/`CryptoService` AES-GCM/ZXing 3.5.3 已有）；前端仅 `html5-qrcode@2.3.8`，`research.md` R-07 评审 + `plan.md` Complexity Tracking 登记 | ✅ PASS |
| **G2 分层与目录** | 三层链、角色实例、`XxxIdExtractor`、Pinia 初始化、路由词干无冲突 | 新增 `pharmacy`/`prescription` 两包；`PharmacyIdExtractor`（T016）；`pharmacyRequest` 独立；`/api/v1/pharmacy/*` 与 `/api/v1/prescriptions` 同既有词干错开；store 从 `localStorage` 恢复（T042） | ✅ PASS |
| **G3 契约与 API** | contracts + data-model 先于实现；结构/枚举/日期/前缀一致 | 5 端点 + M1–M12 先于代码；C-01（FR-005 规则冲突）与 C-03（未定义码 `VALIDATION_FAILED`）已修复（§9） | ✅ PASS |
| **G4 安全** | BCrypt≥10、JWT≤24h、模糊失败、`ErrorCode` 统一、审计脱敏 | 复用 001 JWT/BCrypt/双重 Cookie；`role="PHARMACY"` + 验签在服务端；令牌仅 POST body、审计存摘要；C-04（核销失败审计缺失）已补 `PRESCRIPTION_DISPENSE_FAILED`（§9） | ✅ PASS |
| **G5 代码规范** | Checkstyle/SpotBugs/ESLint 零告警；无 `@Enumerated`+`@Converter` 并存；代码审查 | T074 静态检查、T082 代码审查、T073 SpotBugs 豁免扩包；枚举显式 `@Converter`（T008），派生枚举不落库 | ✅ PASS（实现期验证） |
| **G6 测试门禁** | 覆盖≥70%；5 端点正向/异常；Playwright 覆盖全故事；风险用例；testsdoc；契约一致性 | T028–T071 测试先行；T072 覆盖率；T032/T033/T051/T052/T062/T063 六份 E2E（mock+real）；T075 契约一致性；T080 报告 | ✅ PASS（C-11 补 SC-002 核销测量、C-06 补 URL 直达验真用例） |

**结论**：无「未登记的违规项」；三处文档级缺陷已于 2026-11-08 修复（见 §9），当前 G1–G6 **全绿**。

---

## 4. 一致性问题清单（按严重度）

### C-01【CRITICAL】二维码规则：spec 与 plan 相互矛盾

- **证据**：
  - `spec.md` FR-005：「二维码内容必须与 003 已生成的处方验真链接一致（**处方号 + 签名摘要**），禁止第二套二维码规则」。
  - `plan.md` R-01 / 专项设计：令牌为 `v1.<AES-GCM 密文(pid)>.<RSA 签名>`，**处方号不出现在码内**。
- **影响**：实现必然「违反 FR-005 字面」或「放弃防枚举/防伪」，SC-003 与 FR-007 无法同时成立；`tasks.md` T024 只修订 003 契约，**无任务修订 004 spec**。
- **建议修复**：把 FR-005 改写为：
  > 二维码内容必须由服务端按 `data-model.md` 1.7 的 `v1` 令牌规则生成（加密处方 ID + 医院私钥签名），与 003 处方签名规则**同源单一**；禁止第二套二维码规则或在码内承载患者身份明文。
  
  同时在 spec 第 9 节记录「2026-11-08 因防伪要求升级 QR 规则，替代原『处方号 + 签名摘要』表述」。
- **关联任务**：T022（令牌）、T024（003 契约修订）、T075（一致性核对）。

### C-02【HIGH】「已作废」验真语义冲突：要求展示状态却返回错误码

- **证据**：
  - `spec.md` FR-008 要求验真成功返回状态含「已作废」；US1 验收场景 3：「处方已过期或已作废 → **展示对应状态**并提示不可取药」。
  - `contracts/prescription-dispensing-api.md` 端点 2 异常表：`REVOKED → 400 PRESCRIPTION_REVOKED`，`data:null`（无任何处方字段）。
- **影响**：药店看不到「已作废」状态，只能看到一个错误；与 US1 验收直接冲突；T029 断言也随之固化错误行为。
- **建议修复**：端点 2 对 `REVOKED` 改为 **200 + `dispenseStatus=REVOKED` + `dispensable=false` + `status=REVOKED`**（与 `EXPIRED` 对称）；仅 `DRAFT` 保留 400 `PRESCRIPTION_NOT_SIGNED`（未签名不构成可展示处方）。核销端点保持 400 `PRESCRIPTION_REVOKED`（写操作语义）。
- **关联任务**：T029（改断言）、T036（实现分支）、`mocks.md`（`#REVOKED` 返回体）。

### C-03【HIGH】契约引用未定义错误码 `VALIDATION_FAILED`

- **证据**：`contracts/prescription-dispensing-api.md` 端点 2 异常表列 `400 VALIDATION_FAILED`；但 `data-model.md` 1.6 未定义，`server/.../common/ErrorCode.java` 中**不存在该常量**（现有为 `BAD_REQUEST(400,"请求参数错误")`）。
- **影响**：实现期要么新增未登记常量（违反「错误码唯一来源」），要么契约与实现不一致（SC-009 失败）。
- **建议修复**：统一改为 `BAD_REQUEST`，并在 1.6「复用既有码」行补上 `BAD_REQUEST`；或在 1.6 新增该常量并逐字给 `message`（**推荐前者**，避免码表膨胀）。
- **关联任务**：T007（码表）、T029、T075。

### C-04【HIGH】核销失败缺少审计事件

- **证据**：`spec.md` FR-016 明确要求「验真、核销、**验真失败、核销失败**」均记录审计；`FR-022` 要求失败可统计。`tasks.md` T009 仅新增 6 个事件（登录成功/失败、验真成功/失败、核销成功、重复核销），**无核销失败**；`contracts` 端点 3 约定亦只提 `PRESCRIPTION_DISPENSED`/`PRESCRIPTION_DISPENSE_REPEATED`。
- **影响**：G4 审计覆盖不完整；SC-010 与 FR-022 无法证明。
- **建议修复**：`AuditEventType` 增加 `PRESCRIPTION_DISPENSE_FAILED`（覆盖过期/作废/凭证无效/唯一键异常以外的失败）；T009 与端点 3「约定 5」同步；T049 断言补一条失败审计。
- **关联任务**：T009、T053、T049、T076。

### C-05【MEDIUM】凭证摘要口径冲突：`CHAR(64)` vs 「前 16 hex」

- **证据**：`data-model.md` §2.2 `proof_digest CHAR(64) NOT NULL`（完整 SHA-256）；`plan.md` 攻击面表：「审计仅存令牌摘要（SHA-256 前 16 hex）」；T022 亦写「仅 SHA-256 摘要」。
- **影响**：实现者可能把 16 hex 写进 `CHAR(64)`（右侧补空格导致比对异常），或反向截断审计字段。
- **建议修复**：明确两级——**DB `proof_digest` 存完整 64 hex**；**审计事件属性存前 16 hex**；在 `data-model.md` §2.2 加一行说明。
- **关联任务**：T010、T022、T039。

### C-06【MEDIUM】手机相机扫码直接打开页面时缺「URL 自动验真 + 登录回跳」

- **证据**：令牌 URL 为 `/pharmacy/verify?t=...`（`data-model.md` §6）。药店常用手机相机扫患者屏幕，直接落到该 URL；但 `tasks.md` T044 仅列「摄像头 / 上传图片 / 手工输入」三入口，未覆盖「读取 `?t=` 自动发起验真」；T045 守卫在未登录时跳 `/pharmacy/login` 且**未保留 `t`**，登录后丢失令牌。
- **影响**：真实场景下扫码后需重新扫一次；US1「扫码验真」体验不完整，E2E 也无法覆盖该路径。
- **建议修复**：T044 增加验收点「`route.query.t` 存在时自动调用验真并跳过扫码 UI」；T045 增加「未登录时跳转携带 `redirect`，登录成功后回跳并保留 `t`」；`contracts/README.md` §7 补一条 URL 入口约定；E2E 增一条 `page.goto('/pharmacy/verify?t=...')` 用例。
- **关联任务**：T044、T045、T032。

### C-07【MEDIUM】药店 `sessionId` 来源未定义

- **证据**：T015 要求 `issuePharmacy(long pharmacyId, long sessionId)`，但 `plan.md`/`research.md` 未说明 `sessionId` 生成与撤销策略；003 医生端实际用 `ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)`（已核对 `DoctorAuthServiceImpl`）。
- **影响**：实现者可能自造 UUID/时间戳，导致 `RedisKeys.sessionRevoked(sessionId)` 口径与 001/003 不一致。
- **建议修复**：在 `research.md` R-11 与 T015 写明「与医生端一致：`ThreadLocalRandom` 生成 `long`，仅用于 Redis 吊销键，不落库」。
- **关联任务**：T015、T034。

### C-08【LOW】FR-018 仍写「多表（或多条件）变更」，与派生状态设计不符

- **证据**：Q5 定稿为「单表插入核销记录 + 派生状态不落库」，但 `spec.md` FR-018 保留「多表状态变更」措辞；`plan.md`/T053 实际为「核销记录插入 + 审计同事务」。
- **建议修复**：FR-018 改为「核销记录写入与审计事件写入必须在同一事务内编排；处方展示状态为派生值，不得额外落库」。
- **关联任务**：T053（描述已正确，仅需 spec 同步）。

### C-09【LOW】任务顺序与「测试先行」自相矛盾（T019 → T020）

- **证据**：T019「新建 `DispenseStatusResolver`」排在 T020「先写 `DispenseStatusResolverTest`（须先失败）」之前。
- **建议修复**：交换编号或在 T019 前加「先写测试」步骤（与 T017/T018/T021 的写法对齐）。
- **关联任务**：T019、T020。

### C-10【LOW】二维码长度上限三处口径不一致

- **证据**：`data-model.md` 1.7「令牌总长 ≤ 1024」；`contracts` 端点 2「`qrContent` 8–1024」；`prescription.qr_code VARCHAR(1024)` 但**完整 URL 含前缀**（约 50 字符）会挤占额度。
- **建议修复**：分层限额——令牌 ≤ 700、URL ≤ 1024、`qrContent` 接收 8–1024；在 1.7 补一句「URL 前缀不计入令牌长度上限」。
- **关联任务**：T010、T022、T036。

### C-11【LOW】SC-002（核销 P95 ≤ 30s）无验证任务

- **证据**：T077 只覆盖验真 P95；核销耗时无测量项。
- **建议修复**：T077 增加「核销（含页面交互）端到端计时 ≥ 20 次人工/脚本采样，记入报告」。
- **关联任务**：T077、T080。

### C-12【LOW】T006 夹具路径二义

- **证据**：T006「`createPaidPrescription`（走 002+003 真实链路**或**直接落库 + 签名）」——两种实现成本差异大，验收标准不明确。
- **建议修复**：定为主路径「直接落库 + 调用 `PrescriptionSigningService.sign` + `PrescriptionQrTokenService.issue` 写 `qr_code`」，仅 T061 的集成测试走 002+003 真实链路。
- **关联任务**：T006、T026、T061。

---

## 5. 风险登记（Risk Register，004 增补）

| 编号 | 风险 | 类别 | 可能性 | 影响 | 缓解（设计/任务） |
|------|------|------|--------|------|--------------------|
| RK-01 | 二维码规则变更导致 003 契约漂移，前后端各写一套 | 契约 | 中 | 高 | T024 同提交修订 003 契约 + T075 逐项核对；C-01 修复后 spec 与 plan 同源 |
| RK-02 | 签名密钥丢失/被覆盖，历史二维码全部失效 | 安全/运维 | 中 | 高 | T017 keystore 化 + `prod` fail-fast；`quickstart.md` §9 明确「保留旧密钥 ≥ 3 天」 |
| RK-03 | 药店账号被共用（一店多店员同一账号），核销人追溯失真 | 合规 | 高 | 中 | 记录 `pharmacy_id` + `name_mask`；Q1 已定「运维建号」，建议运维 SOP 要求一人一号（非阻断） |
| RK-04 | 截图转发导致他人抢先取药 | 业务 | 中 | 中 | Q2 已接受「先到先得 + 留痕」；后续「患者端二次确认」列入增强（spec 9.3） |
| RK-05 | `html5-qrcode` 在 iOS Safari/低端安卓上取流失败 | 兼容 | 中 | 中 | 三入口设计（上传图片/手工输入兜底）；T046 覆盖降级文案；`research.md` R-07 |
| RK-06 | 二维码图片（480×480 base64）使详情响应变大 | 性能 | 低 | 低 | 单张 ≤ 16 KB（T077 抽测）；必要时改为 `GET /prescriptions/{id}/qr` 独立端点（列为后续优化） |
| RK-07 | 演示种子药店账号误入生产 | 安全 | 低 | 高 | V8 仅 dev 加载；T027 断言 test/prod 不加载 seed |
| RK-08 | `verify-url-template` 默认 localhost，生产未配置则扫码不可用 | 配置 | 中 | 中 | `SDAS_PHARMACY_VERIFY_URL` 注入；`quickstart.md` §9 排障项 |

---

## 6. 可行性评估

| 能力 | 是否就绪 | 证据 |
|------|----------|------|
| AES-GCM 加密（加密处方 ID） | 是 | `identity/service/CryptoService#encrypt/decrypt` 已实现并被 001 使用 |
| RSA 签名/验签 | 是 | `PrescriptionSigningService` 已有 `sign/verify`；本功能改为 keystore 加载（T017） |
| 二维码生成 | 是 | `QrCodeService`（ZXing 3.5.3）已有；扩为 480×480/纠错 M（T018） |
| 二维码扫描/图片解析 | 是（新依赖） | `html5-qrcode` 客户端 `Html5Qrcode` + `scanFile`；无需后端依赖 |
| 药店身份 | 需新增 | 复用 JWT HS256 + `role="PHARMACY"`；镜像 `DoctorIdExtractor`（T015/T016） |
| 患者只读查询 | 需新增 | `PrescriptionRepository` 缺 `findByPatientId` 分页方法（T023/T064） |
| 唯一约束幂等 | 是 | MySQL `UNIQUE (prescription_id)` + `DuplicateKeyException` 捕获（T010/T053） |
| 测试基础设施 | 是 | 001–003 的 `*TestSupport`/Playwright fixture 模式可复刻（T006） |
| CI 门禁 | 是 | Maven（surefire 含 `*IT`、checkstyle、jacoco 70%、spotbugs）+ ESLint/Prettier 已配置 |

**结论**：无能力缺口；**唯一硬前置是 T017（keystore 化）**——不做则跨重启验真必然失败。

---

## 7. 建议与放行

### 7.1 放行判断

**放行**：C-01~C-12 已于 2026-11-08 全部修复并复核（见 §9），无阻断项，可直接进入 `/speckit.implement`。

### 7.2 已闭环的 4 项必做（原阻断项）

1. **C-01**：改写 `spec.md` FR-005 并在第 9 节记录规则升级理由（否则实现「合规地违反规约」）。
2. **C-02**：端点 2 `REVOKED` 改 `200 + dispenseStatus=REVOKED`；同步 `mocks.md` `#REVOKED`、T029 断言。
3. **C-03**：契约 `VALIDATION_FAILED` → `BAD_REQUEST`，`data-model.md` 1.6 复用码列表补 `BAD_REQUEST`。
4. **C-04**：新增 `PRESCRIPTION_DISPENSE_FAILED` 审计事件（T009/端点 3 约定/T049 断言）。

### 7.3 实现期必须守住的红线

- **T017 先行**：keystore 化完成前，不要签发任何令牌（否则重启即废码）。
- **T022 校验顺序不可调整**：结构 → 验签 → 解密 → 令牌 `exp` → 业务校验；解密必须先验签，避免侧信道。
- **T024 与 003 契约同提交**：禁止「先上线新码、后改文档」。
- **核销只走唯一键**：禁止用 `SELECT ... FOR UPDATE` 或应用层判重替代 `UNIQUE`。
- **验真零副作用**：除审计外不得写业务表，保证「可多次扫描」。

### 7.4 明确排除在本次范围外（防蔓延）

- 不绑定药店、不做患者端二次确认（Q2）。
- 不支持部分取药、撤销核销、退药（Q3）。
- 不做匿名验真页与失败限流（Q4）。
- 不提供药店端核销记录查询/对账（Q6）。
- 不新增后端依赖、不引入 Spring Security（G1/R-11）。

---

## 8. 与 tasks.md 的衔接（修订建议汇总）

| 问题 | 需修改的文档 | 需修改的任务 |
|------|--------------|--------------|
| C-01 | `spec.md` FR-005 + 第 9 节 | T024 说明补「004 spec 同步修订」 |
| C-02 | `contracts/prescription-dispensing-api.md` 端点 2、`mocks.md` `#REVOKED` | T029 断言、T036 分支 |
| C-03 | `contracts` 端点 2、`data-model.md` 1.6 | T007（若不新增码则无需改码表） |
| C-04 | `data-model.md` 1.6 无需改；`contracts` 端点 3 约定 5 | T009 增事件、T049 增断言 |
| C-05 | `data-model.md` §2.2 | T022、T039 |
| C-06 | `contracts/README.md` §7 | T044、T045、T032 |
| C-07 | `research.md` R-11 | T015、T034 |
| C-08 | `spec.md` FR-018 | — |
| C-09 | — | T019/T020 顺序 |
| C-10 | `data-model.md` 1.7 | T010、T022 |
| C-11 | — | T077 |
| C-12 | — | T006 |

> 上表 12 项已于 2026-11-08 全部执行完毕（见 §9），编号与任务均未增删（仍为 T001–T082）。

---

## 9. 修复记录（2026-11-08 闭环）

| 编号 | 处置 | 落地位置 |
|------|------|----------|
| **C-01** | FR-005 重写为「服务端 `v1` 令牌（加密处方 ID + 私钥签名），与 003 签名规则同源单一」；第 9.2 节新增「规则升级记录」说明原「处方号 + 32 位截断前缀」不可验签/可枚举 | `spec.md` FR-005、§9.2 |
| **C-02** | 验真端点对 `REVOKED` 改为 **200 + `dispenseStatus=REVOKED` + `dispensable=false`**；FR-008 增补「不得用错误码代替状态展示」；核销仍 400；三处约定对称说明 | `spec.md` FR-008/§9.2、`contracts/prescription-dispensing-api.md` 端点 2、`contracts/README.md` §3/§7、`contracts/mocks.md`、`plan.md` 错误码表、`data-model.md` §1.2 |
| **C-03** | 删除未定义的 `VALIDATION_FAILED`，统一为既有 `BAD_REQUEST`(400)；`data-model.md` 1.6 复用码列表补 `BAD_REQUEST` 并注明触发条件 | `contracts/prescription-dispensing-api.md` 端点 2、`data-model.md` 1.6、`contracts/README.md` §3、`tasks.md` T029 |
| **C-04** | 新增审计事件 `PRESCRIPTION_DISPENSE_FAILED`（过期/作废/草稿/凭证无效），端点 3 约定与实现任务、集成断言同步 | `tasks.md` T009/T049/T053、`contracts/prescription-dispensing-api.md` 端点 3 约定 5、`contracts/mocks.md` E2E 编排 |
| **C-05** | 明确两级口径：`proof_digest` 存完整 64 hex，审计属性存前 16 hex | `data-model.md` §2.2、`tasks.md` T022 |
| **C-06** | 新增「URL 直达自动验真 + 登录回跳保留 `t`」约定与验收点（契约、任务、E2E 场景） | `contracts/prescription-dispensing-api.md` 端点 2 约定 7、`contracts/README.md` §7 第 5 条、`tasks.md` T032/T044/T045 |
| **C-07** | 明确药店 `sessionId` 与医生端一致：`ThreadLocalRandom` 生成 `long`，仅用于 JWT claim 与 Redis 吊销键，不落库 | `research.md` R-11 |
| **C-08** | FR-018 改为「核销记录 + 审计同事务；展示状态为派生值不落库」 | `spec.md` FR-018 |
| **C-09** | T019/T020 互换：T019 先写失败测试、T020 再实现；依赖表与并行说明同步（T019→T020） | `tasks.md` Phase 2、关键任务依赖表 |
| **C-10** | 长度分层：令牌 ≤ 700、完整 URL ≤ 1024（对齐 `qr_code` 列）、`qrContent` 接收 8–1024 | `data-model.md` 1.7、`tasks.md` T022 |
| **C-11** | T077 增加 SC-002 核销端到端采样（≥20 次、P95 ≤ 30 s） | `tasks.md` T077 |
| **C-12** | 处方夹具主路径固定为「直接落库 + 签名 + 令牌写入 `qr_code`」，仅 T061 走 002+003 真实链路 | `tasks.md` T006 |

**复核结论**：12/12 闭环；任务编号无增删（T001–T082、无缺号无重复）；`spec.md`/`plan.md`/`data-model.md`/`contracts/`/`tasks.md` 之间已无 `VALIDATION_FAILED` 引用、无「验真返回 400 REVOKED」表述（本文件 §4 属历史缺陷记录，保留原文）。**准予进入 `/speckit.implement`**。
