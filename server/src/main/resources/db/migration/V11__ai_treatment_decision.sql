-- 006 AI辅助治疗决策：扩展 003 的 ai_decision_session（Q3 定稿「复用并扩展」）
-- 只加列与索引，不改既有列类型与约束；003 端点 9 读取路径保持兼容。
ALTER TABLE ai_decision_session
    ADD COLUMN trigger_type       VARCHAR(16) NULL AFTER doctor_id,
    ADD COLUMN dialog_version     VARCHAR(64) NULL AFTER trigger_type,
    ADD COLUMN result_json        MEDIUMTEXT  NULL AFTER output,
    ADD COLUMN degraded_reason    VARCHAR(64) NULL AFTER status,
    ADD COLUMN applied_item_count INT         NULL AFTER elapsed_ms,
    ADD COLUMN adopted_at         DATETIME    NULL AFTER applied_item_count,
    ADD INDEX idx_ai_dialog_version (consultation_id, dialog_version);
