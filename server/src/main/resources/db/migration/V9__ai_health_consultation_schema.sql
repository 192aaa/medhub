-- 005 AI健康咨询：会话、消息、紧急判定留痕三张表
-- 唯一来源：specs/005-ai-health-consultation/data-model.md 第 2 节
-- 注意：本脚本只建新表，不修改 002/003 既有表结构（只读依赖）

CREATE TABLE ai_health_session (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    patient_id     BIGINT UNSIGNED NOT NULL,
    title          VARCHAR(64)     NOT NULL,
    last_active_at DATETIME        NOT NULL,
    message_count  INT             NOT NULL DEFAULT 0,
    status         VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_session_patient FOREIGN KEY (patient_id) REFERENCES patient_account (id),
    INDEX idx_ai_session_patient (patient_id, last_active_at),
    UNIQUE KEY uk_ai_session_patient_id (patient_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_health_message (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id    BIGINT UNSIGNED NOT NULL,
    role          VARCHAR(16)     NOT NULL,
    content       TEXT            NOT NULL,
    disclaimer    VARCHAR(64)     NULL,
    emergency     TINYINT(1)      NOT NULL DEFAULT 0,
    rule_code     VARCHAR(32)     NULL,
    stream_status VARCHAR(16)     NOT NULL DEFAULT 'COMPLETED',
    model_used    VARCHAR(64)     NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_message_session FOREIGN KEY (session_id) REFERENCES ai_health_session (id),
    INDEX idx_ai_message_session (session_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_health_emergency_decision (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id    BIGINT UNSIGNED NOT NULL,
    message_id    BIGINT UNSIGNED NULL,
    rule_code     VARCHAR(32)     NOT NULL,
    model_verdict VARCHAR(16)     NOT NULL,
    warned        TINYINT(1)      NOT NULL,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_emergency_session FOREIGN KEY (session_id) REFERENCES ai_health_session (id),
    CONSTRAINT fk_ai_emergency_message FOREIGN KEY (message_id) REFERENCES ai_health_message (id),
    INDEX idx_ai_emergency_session (session_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- 本表禁止写入患者原话、症状描述等病情明文（宪法原则 IV）
