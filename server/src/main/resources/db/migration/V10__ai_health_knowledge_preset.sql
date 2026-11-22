-- 005 AI健康咨询：医学知识与医院信息预置（58 条）
-- 唯一来源：specs/005-ai-health-consultation/plan.md M3
-- 由 005 引入，业务必需数据，禁止移入 db/seed（test/prod 同样需要）
-- 只 INSERT，不 UPDATE / DELETE 既有行；source='PRESET'、kb_version='2026.11'
-- 已知缺口：department_disease_kb 无「康复/用药」专用列，本阶段以 disease_name 承载一句话要点

-- 兜底科室：department 无 name 唯一约束，故用 WHERE NOT EXISTS 保证可重跑
INSERT INTO department (name, description, sort_order, enabled)
SELECT '全科医学科', 'AI健康咨询知识预置兜底科室', 99, 1
WHERE NOT EXISTS (SELECT 1 FROM department WHERE name = '全科医学科');

INSERT INTO department_disease_kb (department_id, disease_keyword, disease_name, source, kb_version)
SELECT d.id, v.kw, v.point, 'PRESET', '2026.11'
FROM department d
JOIN (
    /* ---- 常见症状与处置要点（20 条） ---- */
    SELECT '发热' AS kw, '发热：低于38.5℃可物理降温并多饮水，持续高热超过3天请及时就医。' AS point
    UNION ALL SELECT '咳嗽', '咳嗽：保持室内湿度、避免烟尘刺激，干咳可少量多次饮水润喉。'
    UNION ALL SELECT '咳痰', '咳痰：多饮温水帮助排痰，避免自行使用强力镇咳药。'
    UNION ALL SELECT '咽痛', '咽痛：温盐水漱口、少量多次饮水，伴高热或吞咽困难需就医。'
    UNION ALL SELECT '流鼻涕', '流鼻涕：多为病毒感染且可自限，注意休息与保暖。'
    UNION ALL SELECT '鼻塞', '鼻塞：可用生理盐水冲洗鼻腔，避免长期使用血管收缩剂。'
    UNION ALL SELECT '头痛', '头痛：安静休息、避免强光，突发剧烈头痛请立即就医。'
    UNION ALL SELECT '头晕', '头晕：先坐下防止跌倒并监测血压，伴言语不清或肢体无力需急诊。'
    UNION ALL SELECT '腹痛', '腹痛：记录部位与性质，避免自行服用止痛药掩盖病情。'
    UNION ALL SELECT '腹泻', '腹泻：补充口服补液盐并注意手卫生，出现血便或高热需就医。'
    UNION ALL SELECT '恶心呕吐', '恶心呕吐：少量多次补水，呕吐后暂缓进食油腻食物。'
    UNION ALL SELECT '便秘', '便秘：增加膳食纤维与饮水量，保持规律活动。'
    UNION ALL SELECT '皮疹', '皮疹：避免抓挠与热水烫洗，记录可疑接触物与出现时间。'
    UNION ALL SELECT '瘙痒', '瘙痒：注意保湿、避免过度清洁，泛发或伴呼吸困难需就医。'
    UNION ALL SELECT '腰痛', '腰痛：急性期减少负重、避免久坐久站。'
    UNION ALL SELECT '关节痛', '关节痛：急性期减少活动，红肿热痛明显需就医。'
    UNION ALL SELECT '失眠', '失眠：固定作息、睡前减少屏幕使用与咖啡因摄入。'
    UNION ALL SELECT '乏力', '乏力：保证睡眠与营养，持续两周以上需排查原因。'
    UNION ALL SELECT '心悸', '心悸：静坐休息并记录发作时间，伴胸痛或晕厥需急诊。'
    UNION ALL SELECT '水肿', '水肿：抬高患肢并限盐，单侧突发肿胀需排查血栓。'
    /* ---- 用药注意事项（10 条） ---- */
    UNION ALL SELECT '抗菌药', '抗菌药：需遵医嘱足疗程使用，不可自行停药或加量。'
    UNION ALL SELECT '解热镇痛药', '解热镇痛药：避免多种复方感冒药叠加使用导致成分过量。'
    UNION ALL SELECT '布洛芬', '布洛芬：胃病患者慎用，建议餐后服用。'
    UNION ALL SELECT '对乙酰氨基酚', '对乙酰氨基酚：每日总量有上限，肝功能异常者慎用。'
    UNION ALL SELECT '阿莫西林', '阿莫西林：青霉素过敏者禁用，使用前须确认过敏史。'
    UNION ALL SELECT '抗过敏药', '抗过敏药：部分药物引起嗜睡，服药后避免驾驶与高空作业。'
    UNION ALL SELECT '激素类药物', '激素类药物：不可自行突然停药，需按医嘱逐渐减量。'
    UNION ALL SELECT '降压药', '降压药：规律服药并监测血压，不可自行停药。'
    UNION ALL SELECT '降糖药', '降糖药：注意低血糖表现，随身携带含糖食品。'
    UNION ALL SELECT '中药汤剂', '中药汤剂：与西药间隔服用，避免自行叠加多种中成药。'
    /* ---- 康复与生活指导（8 条） ---- */
    UNION ALL SELECT '上感康复', '上感康复：恢复期保证休息与饮水，逐步恢复日常活动。'
    UNION ALL SELECT '术后康复', '术后康复：遵医嘱换药，前两周避免负重与剧烈运动。'
    UNION ALL SELECT '骨折康复', '骨折康复：固定期做肌肉等长收缩，避免早期负重。'
    UNION ALL SELECT '饮食调理', '饮食调理：清淡均衡，减少高盐、高脂与酒精摄入。'
    UNION ALL SELECT '运动建议', '运动建议：每周中等强度有氧约150分钟，循序渐进。'
    UNION ALL SELECT '体重管理', '体重管理：以每周0.5-1公斤的速度减重更安全。'
    UNION ALL SELECT '戒烟限酒', '戒烟限酒：戒烟可显著降低呼吸道与心血管疾病风险。'
    UNION ALL SELECT '睡眠卫生', '睡眠卫生：卧室安静昏暗，固定起床时间。'
    /* ---- 就医指征（8 条） ---- */
    UNION ALL SELECT '持续高热', '持续高热：体温超过39℃或持续3天以上应及时就医。'
    UNION ALL SELECT '呼吸困难', '呼吸困难：静息状态下气促、口唇发紫需立即急诊。'
    UNION ALL SELECT '意识改变', '意识改变：嗜睡、意识模糊、抽搐需立即拨打120。'
    UNION ALL SELECT '胸痛', '胸痛：压榨性胸痛持续不缓解需立即拨打120。'
    UNION ALL SELECT '剧烈腹痛', '剧烈腹痛：持续加重、腹肌紧张或伴发热需急诊。'
    UNION ALL SELECT '出血不止', '出血不止：压迫止血无效，或呕血、咯血需立即急诊。'
    UNION ALL SELECT '严重过敏', '严重过敏：皮疹伴喉头发紧、呼吸困难需立即急诊。'
    UNION ALL SELECT '婴幼儿高热', '婴幼儿高热：3个月以下婴儿出现发热应立即就医。'
    /* ---- 医院信息知识（12 条） ---- */
    UNION ALL SELECT '门诊时间', '门诊时间：周一至周日08:00-17:30，节假日以院内公告为准。'
    UNION ALL SELECT '急诊位置', '急诊位置：急诊位于门诊楼一层东侧，24小时开放。'
    UNION ALL SELECT '发热门诊', '发热门诊：发热患者请由门诊楼北侧独立通道进入发热门诊。'
    UNION ALL SELECT '儿科门诊', '儿科门诊：位于门诊楼二层西侧，设有儿童输液区。'
    UNION ALL SELECT '检验科', '检验科：位于门诊楼三层，需空腹的项目请上午前往。'
    UNION ALL SELECT '空腹检查', '空腹检查：肝功能、血脂、血糖等项目需空腹8-12小时。'
    UNION ALL SELECT '取药窗口', '取药窗口：门诊药房位于门诊楼一层大厅西侧，凭处方取药。'
    UNION ALL SELECT '医保结算', '医保结算：窗口位于门诊楼一层大厅，支持医保电子凭证。'
    UNION ALL SELECT '住院办理', '住院办理：住院处位于住院部一层，请携带身份证与住院证。'
    UNION ALL SELECT '停车指引', '停车指引：院内停车场由南门进入，车位紧张建议公共交通。'
    UNION ALL SELECT '咨询电话', '咨询电话：门诊咨询电话见医院官网，急救请拨打120。'
    UNION ALL SELECT '报告领取', '报告领取：检验报告可在自助机打印，也可在官网查询电子报告。'
) v
WHERE d.name = '全科医学科'
  AND NOT EXISTS (
      SELECT 1 FROM department_disease_kb k
      WHERE k.source = 'PRESET'
        AND k.kb_version = '2026.11'
        AND k.disease_keyword = v.kw
  );
