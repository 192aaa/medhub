-- 处方取药 表结构（功能 004）
-- 唯一来源：specs/004-prescription-dispensing/data-model.md §2
-- 说明：本迁移为纯新增 + 一处列扩容，无数据破坏性变更（可回滚：下线端点即可，列扩容保留）。

CREATE TABLE pharmacy_account (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name_mask     VARCHAR(64)  NOT NULL,
    pharmacy_name VARCHAR(128) NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pharmacy_account_username (username),
    INDEX idx_pharmacy_account_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- 一处方最多一条核销记录：并发幂等的最终保证（research.md R-08）
CREATE TABLE prescription_dispense_record (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    prescription_id  BIGINT      NOT NULL,
    pharmacy_id      BIGINT      NOT NULL,
    dispensed_at     DATETIME    NOT NULL,
    qr_token_version VARCHAR(8)  NOT NULL,
    proof_digest     CHAR(64)    NOT NULL,
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispense_prescription (prescription_id),
    INDEX idx_dispense_pharmacy (pharmacy_id, dispensed_at),
    CONSTRAINT fk_dispense_prescription FOREIGN KEY (prescription_id) REFERENCES prescription (id),
    CONSTRAINT fk_dispense_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacy_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

-- v1 令牌 URL 实测约 470-520 字符，原 512 不足
ALTER TABLE prescription MODIFY COLUMN qr_code VARCHAR(1024) NULL;

-- 支撑「患者 + 开方时间倒序」分页（端点 4）
CREATE INDEX idx_prescription_patient ON prescription (patient_id, signed_at);
