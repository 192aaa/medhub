# 技术研究：处方取药

**功能**：`004-prescription-dispensing` | **日期**：2026-11-08 | **上游**：[spec.md](./spec.md)、[plan.md](./plan.md)

> 本文件记录 Phase 0 的决策、被否方案与遗留风险。结论已回写 `plan.md` 与 `data-model.md`。

## 现状盘点（研究起点，均经代码核对）

| 项 | 现状 | 影响 |
|----|------|------|
| 签名实现 | `PrescriptionSigningService`：`SHA256withRSA`，密钥为**进程内懒加载的 2048 位 KeyPair**；`sdas.doctor.signature.key-store-path/alias/password` 已配置但代码未读取 | 服务重启后历史二维码全部无法验签（003 analysis.md RK-01 遗留） |
| 二维码 | `QrCodeService`：ZXing 3.5.3，`300×300`，未指定纠错级别；`buildVerifyUrl(pid, signatureShort)`，`signatureShort = signature.substring(0,32)` | 32 位前缀不构成密码学凭证；处方号明文可枚举 |
| 处方接口 | 仅医生侧 `POST /workbench/consultations/{id}/prescriptions`、`GET /workbench/prescriptions/{id}` | 无患者侧接口、无验真端点、无二维码图片端点 |
| 处方仓储 | 仅 `insert/findById/findByConsultationId/sign` | 缺按患者分页查询与状态查询 |
| 认证 | 无 Spring Security；JWT HS256（`role="DOCTOR"` 仅医生侧）；授权靠 Controller 显式调用 `PatientIdExtractor/DoctorIdExtractor` | 药店角色需新增 `issuePharmacy/parsePharmacy` + `PharmacyIdExtractor` |
| 前端 | `pharmacy-request.ts` 已存在但**缺 `withCredentials`**，无 `onUnauthorized`；无 pharmacy api 模块/store/路由 | 药店端 POST 必 403 CSRF，须先修 |
| 迁移 | 奇数 = migration、偶数 = seed；最新 `V6` | 新版本号 `V7`（schema）/`V8`（seed） |
| 错误码 | 无 `PRESCRIPTION_NOT_FOUND`；003 `detail()` 误用 `CONSULTATION_NOT_FOUND` | 本功能补齐并修正 |

## R-01 二维码令牌格式

**决策**：`v1.<Base64Url(AES-GCM 载荷)>.<Base64Url(RSA-SHA256 签名)>`，签名原文为 `"v1." + cipher`。

**理由**：① 处方 ID 进码必须不可枚举（合规与防探测）；② 「医院私钥签名」是用户明确要求，需可离机验证；③ 单段紧凑字符串同时适配扫码、复制粘贴与二维码容量；④ 版本前缀为后续轮换（`v2`）留位，并防止降级混淆。

**被否方案**：
- `prescriptionId + signatureShort`（现状）：前缀非凭证，可枚举，防伪不成立。
- 纯 JWS（`header.payload.signature`）：Base64 头与 JSON 载荷冗余，长度增加约 30%，且载荷明文暴露处方 ID。
- 数据库随机 token + 查表：需要额外表与索引、无法离机验证、二维码无法自证真伪。

**后果**：`prescription.qr_code` 需扩到 `VARCHAR(1024)`；令牌总长约 440–470 字符，二维码需 480×480 + 纠错 M（R-09）。

## R-02 载荷加密复用既有 `CryptoService`

**决策**：直接调用 `CryptoService.encrypt/decrypt`（AES-GCM/NoPadding，12 字节 IV 前置，128 位 tag），密钥沿用 `sdas.identity.crypto.aes-key`。

**理由**：已有实现、已有密钥轮换口径、已被 001 测试覆盖；避免为本功能引入第二套密钥管理与 KMS 依赖。

**被否方案**：新建 `pharmacy.qr.aes-key`：多一套密钥生命周期与运维面；自研 RSA-OAEP 加密：体积更大（256 字节）且与 003 签名算法混淆。

**后果**：`aes-key` 轮换会导致历史令牌无法解密 → 在 `data-model.md` 记录「密钥轮换需保留旧密钥至少一个处方有效期（3 天）」。

## R-03 签名密钥改为 keystore 加载（修复 003 RK-01）

**决策**：`PrescriptionSigningService` 改为从 `sdas.doctor.signature.key-store-path/alias/key-store-password` 加载 `KeyStore`（PKCS12/JKS），暴露 `sign/verify` 与 `publicKey`；`prod` profile 缺 keystore 启动即失败；`dev/test` 缺文件时自动生成并写回（日志 WARN）。

**理由**：进程内 KeyPair 重启即失效，验真跨重启必失败，属阻断性缺陷；配置键已存在，改造成本低。

**被否方案**：把公钥/私钥 Base64 塞进配置：密钥进配置文件与 Git 风险更高；用 HMAC 替签名：无法公开验签，不满足「私钥签名」。

**后果**：`dev` 首次启动会生成 `./keystore/doctor-signing.jks`（需加入 `.gitignore`）；`prod` 由部署方注入密钥文件与环境变量密码。

## R-04 验真接口使用 POST + 请求体

**决策**：`POST /api/v1/pharmacy/prescription-verifications`，令牌放请求体。

**理由**：GET 会把含签名的 URL 写入 Nginx/网关访问日志、浏览器历史与 Referer；POST 可复用既有 CSRF 双重 Cookie；与「携带凭证的查询」语义一致。服务端保证无副作用（仅审计），因此不违反「查询用 GET」的一般约定。

**被否方案**：`GET /pharmacy/prescriptions/verify?t=...`：日志泄漏签名；`POST` + `multipart` 传图片：后端需引入图像解码依赖，与 G1 冲突（改为前端解析，见 R-07）。

## R-05 验真只读、可重复扫描

**决策**：验真不消费令牌、不写业务表，仅写审计；同一二维码可被任意次扫描。

**理由**：用户明确要求；药店常需「先扫一眼状态，再决定是否核销」；状态由处方 + 核销记录派生，重复读取天然一致。

**被否方案**：一次性 nonce：需 Redis 存储与过期策略，且「扫第二次就失效」与真实业务冲突（换店员、换设备都会重复扫）。

## R-06 取药状态由核销记录派生

**决策**：不新增 `PrescriptionStatus.DISPENSED`；新增 `DispenseStatus`（`PENDING/DISPENSED/EXPIRED/REVOKED`）由「核销记录 + `valid_until` + `prescription.status`」派生，纯函数实现（`DispenseStatusResolver`）。

**理由**：遵守 spec Q5；003 的状态机已落库并被多处 `switch` 引用，新增值会波及既有测试与前端枚举；派生规则集中一处即可，且天然支持「已取药优先于已过期」。

**被否方案**：新增 `DISPENSED` 值：需迁移旧数据、改 003 全部状态判断、双写一致性风险；双写状态 + 记录表：一致性维护成本最高。

**后果**：状态派生的组合矩阵必须单测全覆盖（见 `data-model.md` 第 4 节，8 种组合）。

## R-07 前端扫码库 `html5-qrcode`（G1 依赖评审）

**决策**：新增 `html5-qrcode@2.3.8`，仅在选择「扫码」时动态 `import()`（路由级/组件级懒加载）；摄像头用 `Html5Qrcode.start`，上传图片用 `Html5Qrcode.scanFile`；解析结果仅作为字符串提交后端，前端不做任何真伪判断。

**评审要点**：客户端运行时依赖、零后端依赖、MIT/Apache 许可、gzip 后约 70 KB、`facingMode: environment` 支持移动端后置摄像头、支持从静态图片解码（满足「无摄像头时上传图片」）。

**被否方案**：`jsQR`（只解码，需自研取流与文件解码）；`@zxing/browser`（体积更大、文件解析 API 更绕）；后端 ZXing 解码上传图片（新增后端依赖 + 上传链路 + 大文件风控，违反 G1）。

**后果**：登记于 `plan.md` Complexity Tracking；单元测试需 mock 该模块以避免 jsdom 下无 `mediaDevices`。

## R-08 核销幂等实现

**决策**：`prescription_dispense_record` 上 `UNIQUE KEY uk_dispense_prescription (prescription_id)`；捕获 `DuplicateKeyException` → `SELECT` 既有记录 → 返回 `DispenseResult.ALREADY_DISPENSED`（HTTP 200，`idempotent=true`）。首次成功返回 `DISPENSED`（`idempotent=false`）。

**理由**：数据库唯一键是并发下最强保证，无需 Redis 锁；返回 200 使前端把「已核销」当正常状态展示（与 spec FR-011 一致）。

**被否方案**：`SELECT ... FOR UPDATE` + 状态判断：需事务与行锁，且无唯一键时仍可能重复插入；返回 409：前端需额外分支，且与「幂等成功」语义冲突。

## R-09 二维码尺寸与纠错级别

**决策**：480×480 px、纠错级别 `M`、边距 2 模块；内容 ≤ 1024 字符（实际约 470）。

**理由**：令牌 ~470 字符在 300 px 下模块约 2.1 px，手机屏易糊；480 px 提升至约 3.4 px；`M`（15% 冗余）比默认 `L`（7%）更耐屏幕反光/折痕，容量仍充足。

**被否方案**：600 px（页面占比过大、PNG 体积上升）；`H` 级（模块更密，反而降低扫描率）。

## R-10 手工输入验真码

**决策**：验真码 = 令牌 `sig` 段前 12 位 Base64Url 字符（约 72 bit 熵），服务端从 `prescription.qr_code` 复核（常量时间比较）。

**理由**：满足 FR-006「无摄像头同等验真」；72 bit 使在线枚举不可行，同时保持可人工输入长度；无需新增存储列。

**被否方案**：6 位数字码（可枚举）；单独生成随机码并落库（新增列与唯一约束，且丢码即无法手工验真）。

**后果**：验真码需与二维码同页展示；失败全部写审计（Q4：MVP 不限流但可统计）。

## R-11 药店身份与授权

**决策**：复用 001 JWT（HS256），新增 `role="PHARMACY"` claim 与 `PharmacyIdExtractor`（镜像 `DoctorIdExtractor`，缺失/角色不符抛 `PHARMACY_ROLE_REQUIRED`）；不引入 Spring Security 与注解式权限。

**理由**：与既有 001/003 一致；避免为单角色引入安全框架（G1）。药店端不签发 Refresh Token（与医生端一致），access token 有效期沿用 7200 s。

**sessionId 口径**：与 003 医生端严格一致——登录成功时用 `ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)` 生成 `long`，仅作为 JWT claim 与 Redis 吊销键（`sessionRevoked(sessionId)`）使用，不落库、不对外暴露。

**被否方案**：引入 `spring-boot-starter-security` + `@PreAuthorize`：与既有「无安全框架」架构冲突，且会改变全部端点的过滤器链行为（高风险）。

## 遗留风险

- **密钥轮换**：`aes-key` 或 RSA 密钥轮换需保留旧密钥 ≥ 3 天，否则有效期内的二维码会失效；在运维手册（`quickstart.md` 第 6 节）记录。
- **截图转发**：不绑定药店（Q2），截图可被他人取药；缓解手段为核销人留痕 + 全量审计 + 后续「患者端二次确认」增强（已列入 spec 第 9 节剩余风险）。
- **存量二维码**：003 未上线，dev 数据在 V7 后需重新签名；旧格式一律拒绝并审计，不做静默兼容。
- **SpotBugs**：`pharmacy` 包会命中 `EI_EXPOSE_REP2`/`OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE`，需把包名加入既有豁免口径（理由同 identity/appointment/doctor）。
