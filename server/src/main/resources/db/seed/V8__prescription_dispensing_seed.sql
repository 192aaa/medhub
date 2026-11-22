-- 处方取药 演示种子（仅 dev 加载，禁止用于生产）
-- 唯一来源：specs/004-prescription-dispensing/data-model.md §2.4
-- 合成数据，无真实个人/机构信息。
-- 药店账号口令统一为明文 "password"（BCrypt 哈希，与 003 医生种子一致），仅 dev 联调；
-- 生产由运维侧创建，禁止复用此哈希。
-- 主键交由自增决定：测试断言不得硬编码种子账号 ID。

INSERT INTO pharmacy_account (username, password_hash, name_mask, pharmacy_name, status) VALUES
    ('pharmacy_hp', '$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm', '王*',
     '示例大药房（总部店）', 'ACTIVE'),
    ('pharmacy_xh', '$2a$10$pXA36yuoDj/DCbZiz7/4GuUoveZOSekJDD7yy4auEHzZY2.JLznUm', '李*',
     '示例大药房（西湖店）', 'ACTIVE');
