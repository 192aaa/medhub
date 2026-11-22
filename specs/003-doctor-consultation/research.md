# 医生接诊 研究报告（Phase 0）

**功能**：`003-doctor-consultation`
**关联规约**：`spec.md`（Q1–Q6 已于 2026-11-08 `/speckit.clarify` 确认定稿）
**目标**：在编码前关闭所有架构与契约层面的未知项，输出可执行结论供 `plan.md` 与 `contracts/` 引用。

> 宪法原则 I：新增第三方依赖须经架构评审。本报告对每个潜在新增依赖给出「采用 / 不采用」结论。

---

## R-01 医生账号与认证体系（定稿 Q1）

**问题**：医生账号是复用 001 身份模块还是独立认证？管理员建号界面是否在本范围？

**决策**：复用 `001-patient-identity` 的 JWT / Session / 双重 Cookie 基础设施，**新增「医生」角色**与独立的 `doctor_account` 表承载医生凭据（用户名 + BCrypt 密码 + 关联 `doctor`）。
- 新增 `DoctorIdExtractor`（镜像 `PatientIdExtractor`，含 Redis 吊销校验），禁止控制器自行解析令牌（原则 II）。
- 医生登录走独立端点 `POST /api/v1/workbench/sessions`，签发含「医生」角色的 access token；失败返回 `AUTHENTICATION_FAILED` 统一模糊提示。
- 医生账号由 Flyway 种子预置（`db/seed/V6__doctor_consultation_seed.sql`），**管理员建号界面不在本范围**。
- 患者侧 `patientRequest` 与医生侧 `doctorRequest` 请求实例相互独立（原则 II）。

**结论**：复用认证基础设施，新增 `doctor_account` + `DoctorIdExtractor`，不新增第三方认证依赖。

---

## R-02 24 小时接诊超时转诊机制（定稿 Q2）

**问题**：超时如何触发（定时扫描 vs 懒触发）？「转给其他医生」与「通知管理员」二选一还是都做？

**决策**：采用 **Spring `@Scheduled` 定时扫描 + 状态迁移**（不引入消息队列，原则 I）。
- 定时任务周期性扫描 `consultation.accepted_at` 距今超过 24 小时、状态仍为 `IN_PROGRESS` 且 `timeout_status=NONE` 的记录。
- MVP 落地为「**标记超时 + 通知管理员**」：将 `consultation.timeout_status` 置 `ADMIN_NOTIFIED`，写审计事件 `CONSULTATION_TIMEOUT`；「自动分配其他医生」列为后续增强（`transferred_to_doctor_id` 预留字段）。
- 超时判定与迁移在 `@Transactional` 内完成并去重（`timeout_status=NONE` 作幂等条件），避免重复通知（SC-008）。
- 24 小时阈值配置化：`sdas.doctor.timeout-hours: 24`。

**结论**：无 MQ、无分布式调度依赖；定时扫描 + 状态幂等迁移。

---

## R-03 电子处方签名与密钥管理（定稿 Q3）

**问题**：医生私钥如何生成/存储？签名算法？验签？

**决策**：采用 **服务端托管密钥** 方案，医生「确认」动作触发服务端签名，医生不本地持有私钥。
- 签名算法：`SHA256withRSA`（JDK 内置 `java.security.Signature`），**零新增依赖**；如后续需国密可经架构评审替换为 SM2。
- 密钥存储：服务端密钥对经 `sdas.identity.crypto.aes-key` 加密后存于密钥库（或环境注入），**禁止明文落库**（原则 IV）。
- 签名对象：处方核心字段的规范化摘要（`prescriptionId + doctorId + consultationId + 药品明细摘要 + createdAt`），防篡改。
- 签名失败或密钥缺失时**不得生成未签名处方**，返回 `PRESCRIPTION_SIGN_FAILED`（SC-007）。

**结论**：JDK 内置签名，零新增依赖；服务端托管密钥，合规性（CA/电子签名法）在后续生产接入时补评审。

---

## R-04 处方二维码与水印（定稿 Q4）

**问题**：二维码内容、水印内容、3 天有效期起点？

**决策**：
- **二维码**：内容为处方验真链接（`{host}/verify-prescription?prescriptionId={id}&sig={signature_short}`），扫码可核对处方号与签名摘要。
- **水印**：内容为「医院名 + 处方单号 + 生成时间」，叠加于处方预览图，患者端可见。
- **有效期**：从签名时间 `signed_at` 起算 3 天（`valid_until = signed_at + 3d`），过期后状态置 `EXPIRED`。
- 二维码生成需引入 `com.google.zxing`（见 R-05）；水印用 JDK `java.awt` + `javax.imageio` 零依赖。

**结论**：二维码引入 zxing；水印与有效期计算零依赖。

---

## R-05 新增第三方依赖评审（宪法原则 I）

| 候选依赖 | 是否引入 | 结论与依据 |
|----------|----------|------------|
| `com.google.zxing:core` + `javase` | **是（经评审）** | 电子处方二维码生成（R-04）。业界标准、纯 Java、无传递依赖；记录于 `plan.md` 架构评审 |
| 数字签名库 | 否 | JDK `java.security.Signature` 内置（R-03） |
| 消息队列 | 否 | 规约已声明不引入 MQ，超时转诊用 `@Scheduled`（R-02） |
| 对象存储 SDK | 否 | 处方/水印图片沿用 002 本地文件系统 |
| 本地 AI 模型服务 / 第二套 AI 链路 | 否 | R-06：复用 002 链路，禁止双链路 |
| 新增 AI starter | 否 | `spring-ai-starter-model-openai` 已存在 |

**结论**：本次仅新增 `zxing`（二维码）一个第三方依赖，**须经架构评审并记录**；其余零新增。

---

## R-06 AI 辅助决策：复用 002 链路（定稿 Q5）

**问题**：AI 辅助决策具体能力？是否复用 002 AI 基础设施？

**决策**：复用 002 的 Spring AI + DeepSeek/SophNet（`spring-ai-starter-model-openai`，`base-url: https://api.sophnet.com`，模型 `DeepSeek-Flash-1017`），**禁止另起第二套调用链路**（原则 I）。
- 能力范围：基于问诊资料 + 药品目录给出「疑似疾病参考 + 用药配伍/禁忌提醒」。
- 输出明确标注「辅助参考，不作为诊断结论」；医生仍自行决策（FR-014）。
- 超时/异常降级：不阻塞接诊与开方，仅返回空辅助参考（SC-009）。
- 每次调用记录 `ai_decision_session`（输入、输出、`model_used`、`elapsed_ms`），审计可追溯，不向患者暴露内部推理（FR-017）。

**结论**：零新增 AI 依赖；复用 002 已评审链路。

---

## R-07 药品目录：预置只读（定稿 Q6）

**问题**：药品目录来源与字段？

**决策**：药品目录为 Flyway 种子预置（`drug_catalog`，`source='PRESET'`），**只读、无维护界面**。
- 字段：`name`（药品名）、`spec`（规格）、`manufacturer`（厂商）、`dosage_form`（剂型）、`unit`（单位）、`prescription_only`（是否处方药）。
- 开方时药品必须存在于目录且 `enabled=1`，否则 `DRUG_NOT_FOUND`；自由输入药品名返回 `DRUG_NOT_IN_CATALOG`（FR-008）。

**结论**：无管理模块，数据来源单一。

---

## R-08 状态机、幂等与事务（SC-005 / SC-006 / SC-008）

**问题**：接诊、回复、开方、超时转诊如何保证一致与幂等？

**决策**：
- **接诊幂等**：`consultation` 增加 `accepted_at`、`accepted_by_doctor_id`、`timeout_status`、`transferred_to_doctor_id`；接诊以「状态 CAS：`PENDING → IN_PROGRESS` 且 `accepted_at IS NULL`」为幂等条件，并发仅一个成功，另一个 `CONSULTATION_ACCEPT_CONFLICT`(409)。
- **一张单一张处方**：`prescription.consultation_id` `UNIQUE`，重复/并发开方返回 `PRESCRIPTION_ALREADY_EXISTS`(409)（SC-006）。
- **事务编排**：接诊状态变更 + 审计；开方（处方 + 药品明细 + 审计）在 `@Transactional` 内完成并注明关联表与流转路径（原则 II）。
- **回复消息**：`consultation_message` 单表写入，回复字数 ≤ 1000（`INVALID_REPLY_FORMAT`）。

**结论**：唯一约束 + 状态 CAS + 事务编排，无分布式锁。

---

## R-09 鉴权与越权防护（SC-003 / SC-010）

**问题**：如何复用 001 认证并做医生角色与归属隔离？

**决策**：
- 医生端点统一经 `DoctorIdExtractor` 提取医生身份；非医生角色（患者 token 等）访问工作台返回 `DOCTOR_ROLE_REQUIRED`(403)。
- 问诊单/处方操作强制 `doctor_id` 归属校验，越权返回 `CONSULTATION_FORBIDDEN`(403)（复用 002）。
- 修改类请求沿用双重 Cookie + `X-CSRF-Token`（`CSRF_TOKEN_MISMATCH` 403）。
- 处方含患者姓名、诊断等敏感信息，响应与审计日志脱敏（原则 IV）。

**结论**：复用 001/002 鉴权模式，新增 `DoctorIdExtractor` + `DOCTOR_ROLE_REQUIRED`。

---

## R-10 路由词干、前端结构与契约（原则 II/III/VI）

**问题**：新增路由/请求实例如何与 001/002 不冲突？契约如何先行？

**决策**：
- **API 词干**：医生侧统一挂 `/api/v1/workbench/*`，与 002 的 `/doctors`（患者浏览医生）、`/consultations`（患者问诊）**词干明确错开**，避免 `/doctors` 与 `/doctor/*` 近似冲突（原则 II）。
- **请求实例**：新增 `doctorRequest`（独立 axios 实例，注入医生 JWT），与 `patientRequest` 相互独立。
- **前端路由**：`/workbench/*`（工作台、待接诊列表、接诊对话、开具处方、处方预览），与既有 `/patient/*`、`/appointment/*`、`/consultation/entry` 错开。
- **契约先行**：`contracts/doctor-consultation-api.md` 定义 9 端点；`web/src/api/types.ts` 与 `web/src/api/mocks/` 标注来源并同步；测试先写，Playwright 覆盖 5 个用户故事，真实后端 E2E 断言 `code` 值（原则 VI）。

---

## 研究结论汇总

| 编号 | 议题 | 结论 | 状态 |
|------|------|------|------|
| R-01 | 医生认证 | 复用 001 + `doctor_account` + `DoctorIdExtractor` | 定稿 Q1 |
| R-02 | 超时转诊 | `@Scheduled` 扫描 + 标记超时通知管理员 | 定稿 Q2 |
| R-03 | 处方签名 | JDK `SHA256withRSA`，服务端托管密钥 | 定稿 Q3 |
| R-04 | 二维码/水印 | zxing 二维码 + `java.awt` 水印，3 天自签名起算 | 定稿 Q4 |
| R-05 | 新增依赖 | 仅 zxing（经架构评审） | 评审通过 |
| R-06 | AI 辅助决策 | 复用 002 DeepSeek/SophNet，禁止双链路 | 定稿 Q5 |
| R-07 | 药品目录 | Flyway 预置只读 | 定稿 Q6 |
| R-08 | 状态/幂等 | 唯一约束 + 状态 CAS + 事务 | 设计定稿 |
| R-09 | 鉴权 | `DoctorIdExtractor` + 归属校验 + 脱敏 | 设计定稿 |
| R-10 | 路由/契约 | `/api/v1/workbench/*` + `doctorRequest` + 契约先行 | 设计定稿 |

**无 `NEEDS CLARIFICATION` 残留**：Q1–Q6 与全部技术未知项均已定稿，可进入 `plan.md` 与编码。
