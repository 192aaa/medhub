# API 契约：AI辅助治疗决策

**Branch**: `006-ai-treatment-decision` | **Date**: 2026-11-08
**依据**：[spec.md](../spec.md) FR-001–FR-024、[data-model.md](../data-model.md) §1/§5/§7

## 通用约定

- 前缀：`/api/v1/workbench`（与 003 医生工作台一致）。
- 认证：`Authorization: Bearer <doctorAccessToken>`；写操作必须携带 CSRF 双 Cookie（`sdas_csrf` Cookie + `X-CSRF-Token` Header），缺失或失配返回 `403 CSRF_TOKEN_MISMATCH`。
- 统一响应：`{ "code": 200, "message": "成功", "data": {...} }`；分页 `{ "list": [], "total": 0, "page": 1, "pageSize": 20 }`。
- 时间格式：`yyyy-MM-dd'T'HH:mm:ss`（无时区后缀）。
- **AI 失败不返回 5xx**：统一 `200` + `data.status` 降级枚举（见端点 1 示例 2）。
- 响应体**禁止**包含：患者姓名、手机号、身份证号、完整病情明文（仅结构化摘要）、提示词原文、医生真实姓名。

---

## 端点 1：生成或复用治疗决策辅助

`POST /api/v1/workbench/consultations/{consultationId}/treatment-decision`

- 认证：是 | CSRF：是 | 需求：FR-001、FR-002、FR-021、SC-001
- 请求体：无（允许空 body）
- 语义：
  1. 校验状态与归属（见 data-model §8）；
  2. 计算 `dialog_version`，若 30 分钟内存在同医生+同问诊单+同指纹且 `SUCCESS` 的记录，直接返回并置 `reused=true`（不调用模型）；
  3. 否则抢锁 → 组装上下文 → 调用模型 → 解析 → 落留痕 → 返回。

**响应 200（示例 1，首次生成成功）**

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "decisionId": 1201,
    "consultationId": 501,
    "status": "SUCCESS",
    "triggerType": "AUTO",
    "reused": false,
    "summary": {
      "chiefComplaint": "咳嗽伴咳痰 3 天",
      "presentIllness": "3 天前受凉后出现咳嗽，咳白色黏痰，无发热、无胸痛。",
      "dialogHighlights": ["患者自述夜间咳嗽加重", "已服用阿莫西林 2 天效果一般"],
      "riskAlerts": ["既往青霉素过敏史待确认"],
      "evidenceLimited": false,
      "generatedAt": "2026-11-08T10:20:00",
      "modelUsed": "DeepSeek-Flash-1017"
    },
    "suggestions": [
      {
        "diagnosis": "疑似上呼吸道感染",
        "likelihood": "MEDIUM",
        "basis": ["主诉咳嗽咳痰 3 天", "无发热与胸痛"],
        "workup": ["必要时血常规"],
        "riskAlert": null
      }
    ],
    "medicationDrafts": [
      {
        "drugId": 901,
        "drugName": "阿莫西林胶囊",
        "spec": "0.25g*20",
        "dosage": "口服，一次 2 粒，一日 3 次",
        "caution": "青霉素过敏者禁用",
        "needsCompletion": false
      }
    ],
    "ignoredMedications": [],
    "disclaimer": "AI 生成，仅供参考，最终诊疗决策由医生负责",
    "degradedReason": "NONE",
    "elapsedMs": 3400,
    "generatedAt": "2026-11-08T10:20:00"
  }
}
```

**响应 200（示例 2，降级）**

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "decisionId": 1202,
    "consultationId": 501,
    "status": "TIMEOUT_FALLBACK",
    "triggerType": "AUTO",
    "reused": false,
    "summary": null,
    "suggestions": [],
    "medicationDrafts": [],
    "ignoredMedications": [],
    "disclaimer": "AI 生成，仅供参考，最终诊疗决策由医生负责",
    "degradedReason": "TIMEOUT",
    "elapsedMs": 15000,
    "generatedAt": "2026-11-08T10:22:00"
  }
}
```

**响应 200（示例 3，复用）**：字段同上，`"reused": true`、`"elapsedMs": 0`、`decisionId` 为被复用的记录。

**异常**

| HTTP | `code` | 触发 |
|------|--------|------|
| 400 | `INVALID_CONSULTATION_STATUS` | 问诊单非 `IN_PROGRESS` |
| 401 | `UNAUTHORIZED` | 未登录/令牌失效 |
| 403 | `DOCTOR_ROLE_REQUIRED` / `CONSULTATION_FORBIDDEN` / `CSRF_TOKEN_MISMATCH` | 角色/归属/CSRF |
| 404 | `CONSULTATION_NOT_FOUND` | 问诊单不存在 |
| 409 | `PRESCRIPTION_ALREADY_EXISTS` | 已开具处方 |
| 409 | `TREATMENT_DECISION_BUSY` | 并发生成未获锁 |
| 503 | `SERVICE_UNAVAILABLE` | 非 AI 依赖故障（如数据库不可用） |

---

## 端点 2：查询最近一次辅助记录

`GET /api/v1/workbench/consultations/{consultationId}/treatment-decision`

- 认证：是 | CSRF：否 | 需求：FR-001（刷新恢复）、SC-007
- 语义：返回该问诊单**本医生**最近一条记录（无论状态），供开方页刷新/回退时展示，避免重复触发模型。
- 响应 200：结构同端点 1。

**异常**

| HTTP | `code` | 触发 |
|------|--------|------|
| 404 | `CONSULTATION_NOT_FOUND` | 问诊单不存在 |
| 404 | `TREATMENT_DECISION_NOT_FOUND` | 该问诊单暂无任何记录 |
| 403 | `DOCTOR_ROLE_REQUIRED` / `CONSULTATION_FORBIDDEN` | 角色/归属 |

---

## 端点 3：强制重新生成

`POST /api/v1/workbench/consultations/{consultationId}/treatment-decision/regenerate`

- 认证：是 | CSRF：是 | 需求：FR-014、FR-021
- 请求体：无
- 语义：跳过幂等复用，强制调用模型；`trigger_type=REGENERATE`；**每次调用消耗一次频控额度**（3 次/小时/问诊单）。
- 响应 200：结构同端点 1，`"triggerType": "REGENERATE"`、`"reused": false`。

**异常**（在端点 1 基础上追加）

| HTTP | `code` | 触发 |
|------|--------|------|
| 429 | `TREATMENT_DECISION_RATE_LIMITED` | 超过 3 次/小时 |
| 409 | `TREATMENT_DECISION_BUSY` | 并发生成未获锁 |

---

## 端点 4：回填采纳数量

`POST /api/v1/workbench/consultations/{consultationId}/treatment-decision/adoptions`

- 认证：是 | CSRF：是 | 需求：FR-016、SC-009
- 请求体：

```json
{ "appliedItemCount": 2 }
```

- 字段约束：`appliedItemCount` 必填，整数 `0`–`20`（超过 20 视为非法）。
- 语义：更新该问诊单**最近一条**记录的 `applied_item_count` 与 `adopted_at`，并写审计 `AI_TREATMENT_DECISION_ADOPTED`；**不影响处方提交**，失败也不阻断开方（前端失败静默）。
- **回溯口径（SC-007）**：同一问诊单多次生成时，**以最后一次回填为准**；审计回溯按「`adopted_at` 最近的一条记录」关联处方签名时间（±10 分钟窗口），该口径在 `testsdoc/ai-treatment-decision/test-report.md` 中固定记录。
- 响应 200：

```json
{ "code": 200, "message": "成功", "data": { "decisionId": 1201, "appliedItemCount": 2 } }
```

**异常**

| HTTP | `code` | 触发 |
|------|--------|------|
| 400 | `INVALID_TREATMENT_ADOPTION` | 数量缺省或越界 |
| 404 | `TREATMENT_DECISION_NOT_FOUND` | 无记录可回填 |

> `INVALID_TREATMENT_ADOPTION` 为 006 新增第 4 个错误码（400，「采纳数量不合法」），在 data-model §5 同步登记。

---

## 端点 5：药品目录查询（伴随改动，R-09）

`GET /api/v1/workbench/drugs?keyword=&page=1&pageSize=20`

- 认证：是 | CSRF：否 | 需求：支撑 FR-008/FR-009 的草稿展示与手工替换
- 语义：查询 `drug_catalog` 中 `enabled=1` 的药品，`keyword` 对 `name` 模糊匹配（≤50 字符），按 `id ASC` 排序。
- 响应 200：

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "list": [
      { "drugId": 901, "drugName": "阿莫西林胶囊", "spec": "0.25g*20", "unit": "盒", "prescriptionOnly": true }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

**异常**：401 `UNAUTHORIZED`；403 `DOCTOR_ROLE_REQUIRED`；400 `BAD_REQUEST`（分页越界或 `keyword` 超长，复用既有码，不新增专属码）。

---

## 端点编号汇总

| # | 方法 | 路径 | 说明 |
|---|------|------|------|
| 1 | POST | `/v1/workbench/consultations/{id}/treatment-decision` | 生成或复用 |
| 2 | GET | `/v1/workbench/consultations/{id}/treatment-decision` | 最近一次 |
| 3 | POST | `/v1/workbench/consultations/{id}/treatment-decision/regenerate` | 强制重新生成 |
| 4 | POST | `/v1/workbench/consultations/{id}/treatment-decision/adoptions` | 回填采纳数量 |
| 5 | GET | `/v1/workbench/drugs` | 药品目录分页 |

---

## 契约一致性检查清单（SC-010）

- [ ] 端点路径、方法、认证/CSRF 要求与 003 风格一致；
- [ ] `code` 与 `message` 逐字来自 `ErrorCode`；
- [ ] 枚举取值与 `data-model.md` §1 完全一致；
- [ ] 时间格式统一 `yyyy-MM-dd'T'HH:mm:ss`；
- [ ] 降级响应为 `200` + 空数组 + `degradedReason`，与 003 端点 9 的降级口径不冲突；
- [ ] 响应体不含患者身份信息与完整病情明文；
- [ ] `disclaimer` 文案为「AI 生成，仅供参考，最终诊疗决策由医生负责」。
