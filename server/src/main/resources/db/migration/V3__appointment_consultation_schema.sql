-- 预约挂号与图文问诊 表结构（功能 002）
-- 唯一来源：specs/002-appointment-consultation/data-model.md §2
-- 依赖 001 的 patient_account(id) 与 department 外键由本脚本引用

CREATE TABLE department (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    icon_url    VARCHAR(255),
    sort_order  INT          NOT NULL DEFAULT 0,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE doctor (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    department_id        BIGINT       NOT NULL,
    name                 VARCHAR(50)  NOT NULL,                 -- 脱敏姓名，禁止真实姓名
    title                VARCHAR(50),
    hospital             VARCHAR(100),
    years_of_experience  INT,
    good_at              VARCHAR(255),
    consultation_fee     DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    rating               DECIMAL(3, 1),
    consultation_count   INT          NOT NULL DEFAULT 0,
    online_status        VARCHAR(20)  NOT NULL,
    avatar_url           VARCHAR(255),
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_doctor_department FOREIGN KEY (department_id) REFERENCES department (id),
    INDEX idx_doctor_department (department_id),
    INDEX idx_doctor_online (online_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE doctor_schedule (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    doctor_id    BIGINT      NOT NULL,
    work_date    DATE        NOT NULL,
    period       VARCHAR(20) NOT NULL,
    slots_total  INT         NOT NULL DEFAULT 0,
    slots_booked INT         NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id),
    INDEX idx_schedule_doctor (doctor_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE department_disease_kb (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    department_id   BIGINT      NOT NULL,
    disease_keyword VARCHAR(100) NOT NULL,
    disease_name    VARCHAR(100) NOT NULL,
    source          VARCHAR(20) NOT NULL,
    kb_version      VARCHAR(20) NOT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_kb_department FOREIGN KEY (department_id) REFERENCES department (id),
    INDEX idx_kb_keyword (disease_keyword),
    INDEX idx_kb_department (department_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE patient_history_visit (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    patient_id     BIGINT UNSIGNED NOT NULL,
    visit_date     DATE        NOT NULL,
    department_id  BIGINT,
    doctor_id      BIGINT,
    summary        VARCHAR(255),
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_history_patient FOREIGN KEY (patient_id) REFERENCES patient_account (id),
    INDEX idx_history_patient (patient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE appointment (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    patient_id         BIGINT UNSIGNED NOT NULL,
    doctor_id          BIGINT      NOT NULL,
    department_id      BIGINT      NOT NULL,
    schedule_id        BIGINT      NOT NULL,
    appointment_date   DATE        NOT NULL,
    period             VARCHAR(20) NOT NULL,
    fee_amount         DECIMAL(10, 2) NOT NULL,
    payment_status     VARCHAR(20) NOT NULL,
    appointment_status VARCHAR(20) NOT NULL,
    patient_note       VARCHAR(2000),
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version            INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id) REFERENCES patient_account (id),
    CONSTRAINT fk_appointment_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (id),
    CONSTRAINT fk_appointment_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT fk_appointment_schedule FOREIGN KEY (schedule_id) REFERENCES doctor_schedule (id),
    UNIQUE KEY uk_appointment_patient_schedule (patient_id, schedule_id),
    INDEX idx_appointment_patient (patient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE payment_record (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT      NOT NULL,
    channel        VARCHAR(20) NOT NULL,
    amount         DECIMAL(10, 2) NOT NULL,
    out_trade_no   VARCHAR(64) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    paid_at        DATETIME,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_appointment FOREIGN KEY (appointment_id) REFERENCES appointment (id),
    UNIQUE KEY uk_payment_appointment (appointment_id),
    INDEX idx_payment_appointment (appointment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE consultation (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    appointment_id  BIGINT      NOT NULL,
    patient_id      BIGINT UNSIGNED NOT NULL,
    doctor_id       BIGINT      NOT NULL,
    department_id   BIGINT      NOT NULL,
    complaint       VARCHAR(500) NOT NULL,
    description     VARCHAR(2000),
    summary         VARCHAR(1000),
    status          VARCHAR(20) NOT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version         INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_consultation_appointment FOREIGN KEY (appointment_id) REFERENCES appointment (id),
    CONSTRAINT fk_consultation_patient FOREIGN KEY (patient_id) REFERENCES patient_account (id),
    UNIQUE KEY uk_consultation_appointment (appointment_id),
    INDEX idx_consultation_patient (patient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE consultation_image (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    consultation_id BIGINT,
    image_url       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(50),
    size_bytes      BIGINT      NOT NULL DEFAULT 0,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_image_consultation FOREIGN KEY (consultation_id) REFERENCES consultation (id) ON DELETE SET NULL,
    INDEX idx_image_consultation (consultation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;

CREATE TABLE ai_triage_session (
    id                        BIGINT      NOT NULL AUTO_INCREMENT,
    patient_id                BIGINT UNSIGNED,
    complaint                 VARCHAR(500) NOT NULL,
    recommended_department_ids VARCHAR(255),
    model_used                VARCHAR(50),
    elapsed_ms                BIGINT      NOT NULL DEFAULT 0,
    status                    VARCHAR(20) NOT NULL,
    kb_version                VARCHAR(20),
    created_at                DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_triage_patient FOREIGN KEY (patient_id) REFERENCES patient_account (id) ON DELETE SET NULL,
    INDEX idx_triage_patient (patient_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
