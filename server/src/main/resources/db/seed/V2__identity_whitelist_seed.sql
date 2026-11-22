-- 禁止用于生产 / FOR LOCAL DEMO ONLY
-- 本文件位于 db/seed/，仅 dev 的 spring.flyway.locations 追加加载；test/prod 不加载。
-- 数据为合成演示数据：虚构姓名 + 算法构造的占位证件号摘要，不含任何真实身份信息。
-- 生产白名单由 DBA 以 source='IMPORT' 受控导入；测试数据由 IdentityFixture 动态写入。

INSERT INTO hospital_identity_whitelist
    (real_name, id_card_hash, id_card_cipher, enabled, source, created_at, updated_at, version)
VALUES
    ('测试姓名甲', '0000000000000000000000000000000000000000000000000000000000000001',
     UNHEX('44454D4F'), 1, 'PRESET', NOW(), NOW(), 0),
    ('测试姓名乙', '0000000000000000000000000000000000000000000000000000000000000002',
     UNHEX('44454D4F'), 1, 'PRESET', NOW(), NOW(), 0),
    ('测试姓名丙', '0000000000000000000000000000000000000000000000000000000000000003',
     UNHEX('44454D4F'), 1, 'PRESET', NOW(), NOW(), 0);
