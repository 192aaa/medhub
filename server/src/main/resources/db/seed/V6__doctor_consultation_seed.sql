-- 医生接诊 演示种子（仅 dev 加载，禁止用于生产）
-- 唯一来源：specs/003-doctor-consultation/data-model.md §2
-- 合成数据，无真实个人/机构信息。
-- 医生账号密码统一为明文 "password"（BCrypt 哈希），仅 dev 演示；生产由运维侧创建，禁止复用。

-- 医生账号（doctor_id 关联 002 种子 V4 中已插入的 doctor 101/103/105）
INSERT INTO doctor_account (id, username, password_hash, doctor_id, name_mask, status) VALUES
    (1, 'doctor_zhang', '$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm', 101, '张*', 'ACTIVE'),
    (2, 'doctor_wang',  '$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm', 103, '王*', 'ACTIVE'),
    (3, 'doctor_sun',   '$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm', 105, '孙*', 'ACTIVE');

-- 药品目录（PRESET 只读）
INSERT INTO drug_catalog (id, name, spec, manufacturer, dosage_form, unit, prescription_only, enabled, source) VALUES
    (901, '阿莫西林胶囊', '0.25g*20', '示例制药', '胶囊', '盒', 1, 1, 'PRESET'),
    (902, '布洛芬缓释胶囊', '0.3g*20', '示例制药', '胶囊', '盒', 1, 1, 'PRESET'),
    (903, '对乙酰氨基酚片', '0.5g*10', '示例制药', '片剂', '盒', 1, 1, 'PRESET'),
    (904, '感冒灵颗粒', '10g*9', '示例制药', '颗粒', '盒', 0, 1, 'PRESET'),
    (905, '头孢克洛胶囊', '0.25g*12', '示例制药', '胶囊', '盒', 1, 1, 'PRESET');
