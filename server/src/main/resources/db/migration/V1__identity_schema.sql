-- 患者注册与实名认证：结构脚本（dev/test/prod 共用）
-- 表与索引唯一来源：specs/001-patient-identity/data-model.md 第 3 节
-- 引擎 InnoDB / 字符集 utf8mb4 / COLLATE utf8mb4_general_ci

CREATE TABLE IF NOT EXISTS patient_account (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    phone_hash CHAR(64) NOT NULL,
    phone_cipher VARBINARY(256) NOT NULL,
    phone_mask VARCHAR(16) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    auth_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
    account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    registered_at DATETIME NOT NULL,
    verified_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_phone_hash UNIQUE (phone_hash),
    KEY idx_auth_status (auth_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS patient_identity (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT UNSIGNED NOT NULL,
    real_name_cipher VARBINARY(512) NOT NULL,
    real_name_mask VARCHAR(64) NOT NULL,
    id_card_hash CHAR(64) NOT NULL,
    id_card_cipher VARBINARY(512) NOT NULL,
    id_card_mask VARCHAR(32) NOT NULL,
    verified_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_patient_id UNIQUE (patient_id),
    CONSTRAINT uk_id_card_hash UNIQUE (id_card_hash),
    CONSTRAINT fk_identity_patient FOREIGN KEY (patient_id) REFERENCES patient_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS patient_agreement_consent (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT UNSIGNED NOT NULL,
    agreement_version VARCHAR(32) NOT NULL,
    consent_status VARCHAR(16) NOT NULL,
    agreed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_patient_version UNIQUE (patient_id, agreement_version),
    CONSTRAINT fk_consent_patient FOREIGN KEY (patient_id) REFERENCES patient_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS patient_session (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT UNSIGNED NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL,
    session_status VARCHAR(16) NOT NULL,
    client_ip_hash CHAR(64) NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_refresh_token_hash UNIQUE (refresh_token_hash),
    KEY idx_patient_id (patient_id),
    CONSTRAINT fk_session_patient FOREIGN KEY (patient_id) REFERENCES patient_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS hospital_identity_whitelist (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    real_name VARCHAR(64) NOT NULL,
    id_card_hash CHAR(64) NOT NULL,
    id_card_cipher VARBINARY(512) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    source VARCHAR(32) NOT NULL DEFAULT 'PRESET',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_whitelist_id_card_hash UNIQUE (id_card_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS security_audit_event (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    result VARCHAR(16) NOT NULL,
    actor_patient_id BIGINT UNSIGNED NULL,
    masked_phone VARCHAR(16) NULL,
    masked_id_card VARCHAR(32) NULL,
    reason_code VARCHAR(32) NULL,
    trace_id VARCHAR(64) NULL,
    occurred_at DATETIME NOT NULL,
    KEY idx_event_type_time (event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
