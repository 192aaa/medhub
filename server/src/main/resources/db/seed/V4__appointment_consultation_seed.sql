-- 预约挂号与图文问诊 演示种子（仅 dev 加载，禁止用于生产）
-- 唯一来源：specs/002-appointment-consultation/data-model.md §2
-- 合成数据，无真实个人/机构信息；知识库为数据库预置（R-03，无管理界面）

-- 科室
INSERT INTO department (id, name, description, icon_url, sort_order, enabled) VALUES
    (1, '内科', '常见内科疾病诊疗', NULL, 1, 1),
    (2, '外科', '外科手术与创伤诊疗', NULL, 2, 1),
    (3, '儿科', '儿童常见病诊疗', NULL, 3, 1),
    (4, '皮肤科', '皮肤疾病诊疗', NULL, 4, 1);

-- 医生（name 为脱敏展示值）
INSERT INTO doctor (id, department_id, name, title, hospital, years_of_experience, good_at,
                    consultation_fee, rating, consultation_count, online_status, avatar_url) VALUES
    (101, 1, '张*', '主任医师', '示例医院', 15, '呼吸系统疾病', 30.00, 4.8, 1200, 'ONLINE', NULL),
    (102, 1, '李*', '副主任医师', '示例医院', 10, '消化内科', 25.00, 4.6, 800, 'BUSY', NULL),
    (103, 2, '王*', '主任医师', '示例医院', 20, '骨科创伤', 50.00, 4.9, 2000, 'ONLINE', NULL),
    (104, 3, '赵*', '主治医师', '示例医院', 8, '儿童呼吸道感染', 20.00, 4.5, 600, 'OFFLINE', NULL),
    (105, 4, '孙*', '副主任医师', '示例医院', 12, '过敏性皮肤病', 35.00, 4.7, 900, 'ONLINE', NULL);

-- 排班
INSERT INTO doctor_schedule (id, doctor_id, work_date, period, slots_total, slots_booked) VALUES
    (201, 101, '2026-11-10', 'MORNING', 20, 5),
    (202, 101, '2026-11-11', 'AFTERNOON', 20, 0),
    (203, 103, '2026-11-10', 'MORNING', 15, 3),
    (204, 105, '2026-11-12', 'EVENING', 10, 0);

-- 科室-疾病知识库（PRESET，供导诊兜底匹配）
INSERT INTO department_disease_kb (department_id, disease_keyword, disease_name, source, kb_version) VALUES
    (1, '咳嗽', '咳嗽', 'PRESET', 'PRESET'),
    (1, '发热', '发热', 'PRESET', 'PRESET'),
    (1, '头痛', '头痛', 'PRESET', 'PRESET'),
    (1, '感冒', '感冒', 'PRESET', 'PRESET'),
    (2, '骨折', '骨折', 'PRESET', 'PRESET'),
    (2, '外伤', '外伤', 'PRESET', 'PRESET'),
    (2, '扭伤', '扭伤', 'PRESET', 'PRESET'),
    (3, '儿童', '儿科疾病', 'PRESET', 'PRESET'),
    (3, '小儿', '儿科疾病', 'PRESET', 'PRESET'),
    (4, '皮疹', '皮疹', 'PRESET', 'PRESET'),
    (4, '过敏', '过敏', 'PRESET', 'PRESET');

-- 复诊资格演示数据（R-08）：手动插入历史就诊记录后即可测试挂号门禁。
-- 先注册/登录一个患者，记下其 patient_account.id，再执行（patient_id 替换为实际值）：
-- INSERT INTO patient_history_visit (patient_id, visit_date, department_id, doctor_id, summary)
-- VALUES (<实际 patient_id>, '2026-10-01', 1, 101, '既往内科就诊');
-- 未插入该记录时，挂号端点将返回 REVISIT_NOT_ELIGIBLE（符合 FR-007 复诊定位）。
