-- ============================================================
-- 人才培养模块 · 非 IT 方向课程 / 专业 演示数据（假数据）
--
-- 背景：
--   「课程体系」只读引用知识库 subject / knowledge_point，
--   而知识库原来只有 22 门计算机类课程，导致看板除 IT 方向外
--   全部显示「知识库暂无匹配课程」。
--
-- 本脚本按 fiveEi/08-行业对照表 中实际抓取到的行业与岗位方向，
-- 补充 13 门面向服务业 / 电子通信 / 生产制造 / 销售 / 产品运营的课程，
-- 每门课配套 2~8 个知识点（名称与 talent_job_skill 中的技能标签对齐，
-- 保证技能匹配能命中），并补齐 2 个缺失专业。
--
-- 幂等：按 code 先删后建（删除 subject 会级联删除其知识点）。
-- 目标库：personnel_information
-- 执行：
--   Get-Content -Raw .\sql\seed_talent_fake_courses.sql |
--     & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -uroot -p123456 `
--       --default-character-set=utf8mb4 -D personnel_information
-- ============================================================

SET NAMES utf8mb4;

-- ---------- 1. 清理旧的演示课程 ----------
DELETE FROM `subject` WHERE `code` IN (
  'sub_catering', 'sub_beauty', 'sub_nursing', 'sub_etc', 'sub_housekeeping',
  'sub_fitness', 'sub_tourism', 'sub_logistics', 'sub_mfg', 'sub_teaching',
  'sub_sales', 'sub_pm', 'sub_ui'
);

-- ---------- 2. 课程（subject） ----------
INSERT INTO `subject` (`name`, `code`, `description`, `sort_order`, `status`, `create_by`, `create_time`, `del_flag`) VALUES
('餐饮服务与门店运营', 'sub_catering', '面向餐饮、酒店、民宿门店：顾客接待、点餐收银、后厨备餐、食品安全与门店运营', 200, '0', 'seed', NOW(), '0'),
('美容美发与形象设计', 'sub_beauty', '面向美容、美发、美睫美甲门店：皮肤护理、化妆造型、发型设计与客户沟通', 201, '0', 'seed', NOW(), '0'),
('临床护理与健康服务', 'sub_nursing', '面向医院、口腔诊所、健康管理机构：临床护理、医患沟通、健康评估与院感管理', 202, '0', 'seed', NOW(), '0'),
('电子通信与硬件维修', 'sub_etc', '面向通信设备、智能硬件、弱电工程：硬件维修、设备调试、电路调试与弱电施工', 203, '0', 'seed', NOW(), '0'),
('家政服务与安全操作', 'sub_housekeeping', '面向家政、保洁、家电清洗：家政保洁流程、安全操作规范与客户服务', 204, '0', 'seed', NOW(), '0'),
('健身指导与健康管理', 'sub_fitness', '面向健身房、养生馆：健身指导、私教课程、按摩理疗、健康咨询与会员管理', 205, '0', 'seed', NOW(), '0'),
('旅游产品与婚庆策划', 'sub_tourism', '面向旅行社、景区、婚庆摄影：旅游产品设计、行程策划、婚礼策划与会奖 MICE', 206, '0', 'seed', NOW(), '0'),
('仓储配送与物流管理', 'sub_logistics', '面向电商仓配、快递末端：仓储管理、快递分拣、打包作业与配送调度', 207, '0', 'seed', NOW(), '0'),
('智能制造与质量检验', 'sub_mfg', '面向电子制造、半导体封测产线：产线操作、装配作业、质量检验与设备维护', 208, '0', 'seed', NOW(), '0'),
('学科教学与课堂管理', 'sub_teaching', '面向中小学、培训机构：学科教学、课程设计、课堂管理与家校沟通', 209, '0', 'seed', NOW(), '0'),
('商务谈判与大客户管理', 'sub_sales', '面向 B 端销售、渠道拓展：客户开发、商务谈判、大客户管理、渠道拓展与招投标', 210, '0', 'seed', NOW(), '0'),
('产品运营与需求分析', 'sub_pm', '面向产品经理、运营岗：需求分析、产品规划、数据分析、A/B 测试与内容运营', 211, '0', 'seed', NOW(), '0'),
('UI与视觉设计', 'sub_ui', '面向 UI、视觉、电商美工：视觉设计、交互设计、界面设计、品牌设计与剪辑包装', 212, '0', 'seed', NOW(), '0');

-- ---------- 3. 知识点（knowledge_point） ----------
INSERT INTO `knowledge_point` (`subject_id`, `name`, `content`, `difficulty`, `importance`, `type`, `sort_order`, `status`, `create_by`, `create_time`, `del_flag`)
SELECT s.id, t.name, t.content, t.difficulty, t.importance, '技能', t.sort_order, '0', 'seed', NOW(), '0'
FROM (
  SELECT 'sub_catering' AS code, '顾客接待' AS name, '迎宾、引座、点单引导与顾客投诉处理' AS content, 1 AS difficulty, 3 AS importance, 1 AS sort_order
  UNION ALL SELECT 'sub_catering', '点餐服务', '点餐系统操作、菜品推荐与加菜催菜处理', 1, 3, 2
  UNION ALL SELECT 'sub_catering', '收银结算', '收银系统、移动支付、发票开具与日结对账', 1, 2, 3
  UNION ALL SELECT 'sub_catering', '后厨备餐', '备菜标准、出品流程与出餐速度控制', 2, 3, 4
  UNION ALL SELECT 'sub_catering', '食品安全', '食材储存、交叉污染防控与食安台账', 2, 4, 5
  UNION ALL SELECT 'sub_catering', '门店运营', '排班、物料盘点、成本控制与门店巡检', 2, 3, 6
  UNION ALL SELECT 'sub_catering', '客房服务', '客房清洁标准、布草更换与客诉处理', 1, 2, 7
  UNION ALL SELECT 'sub_catering', '成本控制', '食材成本核算、损耗控制与毛利分析', 3, 2, 8

  UNION ALL SELECT 'sub_beauty', '皮肤护理', '肤质判断、清洁补水流程与仪器操作', 2, 3, 1
  UNION ALL SELECT 'sub_beauty', '化妆造型', '底妆、眼妆、整体造型与场合妆设计', 2, 3, 2
  UNION ALL SELECT 'sub_beauty', '美睫美甲', '嫁接睫毛、甲型修整与美甲款式设计', 2, 3, 3
  UNION ALL SELECT 'sub_beauty', '发型设计', '剪发基础、烫染原理与脸型搭配', 3, 3, 4
  UNION ALL SELECT 'sub_beauty', '客户沟通', '需求问询、效果预期管理与客诉处理', 1, 2, 5
  UNION ALL SELECT 'sub_beauty', '门店销售', '项目套餐推介、会员卡销售与复购运营', 2, 2, 6

  UNION ALL SELECT 'sub_nursing', '临床护理', '基础护理操作、给药、生命体征监测与交接班', 3, 4, 1
  UNION ALL SELECT 'sub_nursing', '医患沟通', '病情告知、家属沟通与投诉应对', 2, 3, 2
  UNION ALL SELECT 'sub_nursing', '健康评估', '健康史采集、风险评估与护理计划制定', 3, 3, 3
  UNION ALL SELECT 'sub_nursing', '院感管理', '手卫生、消毒隔离与医疗废物处置', 2, 4, 4
  UNION ALL SELECT 'sub_nursing', '口腔护理', '口腔四手操作、器械消毒与椅旁配合', 2, 3, 5
  UNION ALL SELECT 'sub_nursing', '验光配镜', '验光流程、镜片选择与配镜售后', 2, 2, 6

  UNION ALL SELECT 'sub_etc', '硬件维修', '手机/主板拆装、故障检测与焊接返修', 3, 3, 1
  UNION ALL SELECT 'sub_etc', '通信设备调试', '基站/光猫/路由设备配置与链路测试', 3, 3, 2
  UNION ALL SELECT 'sub_etc', '电路调试', '原理图识读、万用表/示波器测量与故障定位', 3, 3, 3
  UNION ALL SELECT 'sub_etc', '弱电施工', '综合布线、监控门禁安装与施工规范', 2, 3, 4
  UNION ALL SELECT 'sub_etc', '嵌入式开发', '单片机外设驱动、C 语言编程与板级调试', 4, 3, 5

  UNION ALL SELECT 'sub_housekeeping', '家政保洁', '日常保洁流程、深度清洁与家电清洗', 1, 3, 1
  UNION ALL SELECT 'sub_housekeeping', '安全操作', '清洁剂配比、高空与用电作业安全', 1, 3, 2

  UNION ALL SELECT 'sub_fitness', '健身指导', '体态评估、训练计划编排与动作纠正', 2, 3, 1
  UNION ALL SELECT 'sub_fitness', '私教课程', '体验课转化、课程包设计与训练跟踪', 2, 3, 2
  UNION ALL SELECT 'sub_fitness', '按摩理疗', '经络放松、肩颈腰背手法与理疗禁忌', 3, 3, 3
  UNION ALL SELECT 'sub_fitness', '健康咨询', '体测解读、饮食建议与健康风险提示', 2, 2, 4
  UNION ALL SELECT 'sub_fitness', '会员管理', '会员档案、续费提醒与流失召回', 1, 2, 5

  UNION ALL SELECT 'sub_tourism', '旅游产品设计', '线路组合、报价测算与产品包装', 2, 3, 1
  UNION ALL SELECT 'sub_tourism', '行程策划', '行程编排、地接协调与应急备案', 3, 3, 2
  UNION ALL SELECT 'sub_tourism', '客户接待', '门店咨询、签约收款与出行说明会', 1, 3, 3
  UNION ALL SELECT 'sub_tourism', '婚礼策划', '婚礼主题设计、流程彩排与供应商统筹', 3, 3, 4
  UNION ALL SELECT 'sub_tourism', '会奖MICE', '会奖旅游方案、会场与商旅资源统筹', 3, 2, 5
  UNION ALL SELECT 'sub_tourism', '摄影服务', '人像/儿童摄影布光、引导与选片交付', 2, 2, 6
  UNION ALL SELECT 'sub_tourism', '外语沟通', '出境游常用外语接待与行程讲解', 2, 2, 7
  UNION ALL SELECT 'sub_tourism', '销售推广', '同业分销、线上投放与老客转介绍', 2, 2, 8

  UNION ALL SELECT 'sub_logistics', '仓储管理', '库位规划、出入库单据与库存盘点', 2, 3, 1
  UNION ALL SELECT 'sub_logistics', '快递分拣', '分拣规则、扫描枪操作与异常件处理', 1, 3, 2
  UNION ALL SELECT 'sub_logistics', '打包作业', '包装材料选择、防损加固与耗材控制', 1, 3, 3
  UNION ALL SELECT 'sub_logistics', '配送调度', '线路规划、时效承诺与运力协调', 2, 2, 4

  UNION ALL SELECT 'sub_mfg', '产线操作', '工位作业标准、节拍达成与换线流程', 1, 3, 1
  UNION ALL SELECT 'sub_mfg', '装配作业', '装配顺序、扭力规范与防错点检', 1, 3, 2
  UNION ALL SELECT 'sub_mfg', '质量检验', '首件检验、抽检标准与不良品处理', 2, 4, 3
  UNION ALL SELECT 'sub_mfg', '设备维护', '点检保养、常见故障排除与备件管理', 3, 3, 4
  UNION ALL SELECT 'sub_mfg', '安全生产', '劳保穿戴、危险源识别与应急疏散', 1, 4, 5
  UNION ALL SELECT 'sub_mfg', '焊接工艺', '焊接参数、焊点质量判定与返修', 3, 2, 6

  UNION ALL SELECT 'sub_teaching', '学科教学', '课程标准解读、教案编写与课堂实施', 2, 4, 1
  UNION ALL SELECT 'sub_teaching', '课程设计', '教学目标拆解、活动设计与作业布置', 2, 3, 2
  UNION ALL SELECT 'sub_teaching', '课堂管理', '课堂纪律、分组互动与学习反馈', 1, 3, 3
  UNION ALL SELECT 'sub_teaching', '家校沟通', '学情反馈、家长会组织与续费沟通', 1, 2, 4

  UNION ALL SELECT 'sub_sales', '客户开发', '线索获取、陌拜电话与需求挖掘', 2, 3, 1
  UNION ALL SELECT 'sub_sales', '商务谈判', '报价策略、议价应对与合同条款', 3, 4, 2
  UNION ALL SELECT 'sub_sales', '大客户管理', '客户分级、关系维护与年度框架协议', 3, 3, 3
  UNION ALL SELECT 'sub_sales', '客户关系维护', '拜访计划、客情维护与续约增购', 2, 4, 4
  UNION ALL SELECT 'sub_sales', '渠道拓展', '渠道招募、政策制定与渠道赋能', 3, 3, 5
  UNION ALL SELECT 'sub_sales', '销售管理', '目标拆解、过程指标与团队激励', 3, 3, 6
  UNION ALL SELECT 'sub_sales', '招投标', '标书编制、资质准备与投标答辩', 3, 2, 7
  UNION ALL SELECT 'sub_sales', '会籍销售', '体验邀约、会籍产品设计与转介绍', 1, 2, 8

  UNION ALL SELECT 'sub_pm', '需求分析', '用户调研、需求优先级与 PRD 撰写', 2, 4, 1
  UNION ALL SELECT 'sub_pm', '产品规划', '产品路线图、版本节奏与竞品分析', 3, 3, 2
  UNION ALL SELECT 'sub_pm', '数据分析', '指标体系、漏斗分析与数据看板', 2, 3, 3
  UNION ALL SELECT 'sub_pm', 'A/B测试', '实验设计、分流方案与结果显著性判断', 3, 2, 4
  UNION ALL SELECT 'sub_pm', 'SEO', '关键词布局、站内优化与内容分发', 2, 2, 5
  UNION ALL SELECT 'sub_pm', '用户增长', '拉新渠道、转化路径与留存运营', 2, 3, 6
  UNION ALL SELECT 'sub_pm', '内容运营', '选题策划、短视频脚本与账号矩阵', 2, 3, 7

  UNION ALL SELECT 'sub_ui', '视觉设计', '版式、色彩、字体与视觉规范', 2, 3, 1
  UNION ALL SELECT 'sub_ui', '交互设计', '信息架构、流程设计与可用性测试', 3, 3, 2
  UNION ALL SELECT 'sub_ui', '界面设计', 'App/小程序界面、组件库与适配规范', 2, 3, 3
  UNION ALL SELECT 'sub_ui', '品牌设计', 'LOGO、VI 与品牌视觉延展', 3, 2, 4
  UNION ALL SELECT 'sub_ui', '剪辑包装', '剪辑节奏、字幕动效与封面包装', 2, 2, 5
) t
JOIN `subject` s ON s.`code` = t.code;

-- ---------- 4. 补齐缺失专业（岗位方向有岗位但无专业） ----------
INSERT INTO `talent_major` (`major_code`, `major_name`, `job_family`, `graduate`, `remark`)
SELECT 'PM', '产品运营（AI产品方向）', '产品/运营', '产品经理、产品运营专员', '演示数据：对应产品/运营岗位方向'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `talent_major` WHERE `major_code` = 'PM');

INSERT INTO `talent_major` (`major_code`, `major_name`, `job_family`, `graduate`, `remark`)
SELECT 'UI', '数字媒体艺术设计（UI/视觉方向）', 'UI/视觉设计', 'UI设计师、视觉设计师、电商美工', '演示数据：对应UI/视觉设计岗位方向'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `talent_major` WHERE `major_code` = 'UI');

-- ---------- 5. 校验 ----------
SELECT COUNT(*) AS course_count FROM `subject` WHERE `del_flag` = '0' AND `status` = '0';
SELECT s.code, s.name, COUNT(p.id) AS point_count
FROM `subject` s
LEFT JOIN `knowledge_point` p ON p.subject_id = s.id AND p.del_flag = '0'
WHERE s.code LIKE 'sub\_%' AND s.sort_order >= 200
GROUP BY s.id, s.code, s.name
ORDER BY s.sort_order;
SELECT COUNT(*) AS major_count FROM `talent_major`;
