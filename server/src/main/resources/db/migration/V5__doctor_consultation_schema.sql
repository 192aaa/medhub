-- 医生接诊 表结构（功能 003）
-- 唯一来源：specs/003-doctor-consultation/data-model.md §2
-- 依赖 002 的 doctor(id)/consultation(id) 与 001 的 patient_account(id) 外键。
-- 说明：doctor/consultation 主键在既有脚本中为 signed BIGINT，故引用列保持一致（signed）；
--       patient_account(id) 为 BIGINT UNSIGNED，引用列保持一致（unsigned）。

CREATE TABLE doctor_account (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    doctor_id     BIGINT       NOT NULL,
    name_mask     VARCHAR(32)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_doctor_account_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id),
    UNIQUE KEY uk_doctor_account_username (username),
    UNIQUE KEY uk_doctor_account_doctor (doctor_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

ALTER TABLE consultation
    ADD COLUMN accepted_at             DATETIME    NULL,
    ADD COLUMN accepted_by_doctor_id   BIGINT      NULL,
    ADD COLUMN timeout_status          VARCHAR(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN timeout_handled_at      DATETIME    NULL,
    ADD COLUMN transferred_to_doctor_id BIGINT      NULL;

CREATE TABLE consultation_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    consultation_id BIGINT       NOT NULL,
    sender_type     VARCHAR(16)  NOT NULL,
    sender_id       BIGINT       NULL,
    content         VARCHAR(1000) NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_msg_consultation FOREIGN KEY (consultation_id) REFERENCES consultation (id),
    INDEX idx_consultation (consultation_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE drug_catalog (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    name              VARCHAR(128) NOT NULL,
    spec              VARCHAR(128) NULL,
    manufacturer      VARCHAR(128) NULL,
    dosage_form       VARCHAR(32)  NULL,
    unit              VARCHAR(16)  NULL,
    prescription_only TINYINT(1)   NOT NULL DEFAULT 1,
    enabled           TINYINT(1)   NOT NULL DEFAULT 1,
    source            VARCHAR(32)  NOT NULL DEFAULT 'PRESET',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE prescription (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    consultation_id BIGINT       NOT NULL,
    doctor_id       BIGINT       NOT NULL,
    patient_id      BIGINT UNSIGNED NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    signature       VARCHAR(512) NULL,
    qr_code         VARCHAR(512) NULL,
    watermark       VARCHAR(512) NULL,
    signed_at       DATETIME     NULL,
    valid_until     DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_prescription_consultation FOREIGN KEY (consultation_id) REFERENCES consultation (id),
    CONSTRAINT fk_prescription_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id),
    CONSTRAINT fk_prescription_patient FOREIGN KEY (patient_id) REFERENCES patient_account (id),
    UNIQUE KEY uk_prescription_consultation (consultation_id),
    INDEX idx_prescription_doctor (doctor_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE prescription_item (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT       NOT NULL,
    drug_id         BIGINT       NOT NULL,
    spec            VARCHAR(128) NULL,
    dosage          VARCHAR(255) NOT NULL,
    caution         VARCHAR(500) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_item_prescription FOREIGN KEY (prescription_id) REFERENCES prescription (id),
    CONSTRAINT fk_item_drug FOREIGN KEY (drug_id) REFERENCES drug_catalog (id),
    INDEX idx_item_prescription (prescription_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_decision_session (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    consultation_id BIGINT        NOT NULL,
    doctor_id       BIGINT        NOT NULL,
    input_summary   VARCHAR(2000) NULL,
    output          VARCHAR(2000) NULL,
    model_used      VARCHAR(64)   NULL,
    status          VARCHAR(16)   NULL,
    elapsed_ms      INT           NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_consultation FOREIGN KEY (consultation_id) REFERENCES consultation (id),
    CONSTRAINT fk_ai_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id),
    INDEX idx_ai_consultation (consultation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
