-- ----------------------------
-- 人才培养方案智能优化模块（岗位动态采集 → 技能需求趋势 → 专业匹配预警 → 优化建议）
-- 可重复执行：仅重建本模块自有表，不影响业务表
-- ----------------------------
SET NAMES utf8mb4;

drop table if exists talent_job_skill;
drop table if exists talent_job_posting;

-- ----------------------------
-- 招聘岗位原始数据
-- ----------------------------
create table talent_job_posting (
  id           bigint(20)    not null auto_increment    comment '岗位ID',
  batch_date   varchar(20)   default ''                 comment '采集批次日期（yyyy-MM-dd，空表示清洗快照）',
  keyword      varchar(100)  default ''                 comment '岗位关键词/行业',
  city         varchar(50)   default ''                 comment '城市',
  job_title    varchar(200)  default ''                 comment '职位名称',
  description  text                                     comment '职位描述',
  source       varchar(128)  default ''                 comment '数据来源平台（CSV导入:文件名，需容纳较长文件名）',
  skill_names  varchar(1000) default ''                 comment '技能标签（逗号分隔）',
  create_time  datetime                                 comment '入库时间',
  primary key (id),
  key idx_batch_date (batch_date),
  key idx_city (city)
) engine=innodb comment='招聘岗位原始数据';

-- ----------------------------
-- 岗位技能需求明细
-- ----------------------------
create table talent_job_skill (
  id             bigint(20)   not null auto_increment,
  posting_id     bigint(20)   not null                 comment '岗位ID',
  batch_date     varchar(20)  default ''               comment '采集批次日期',
  skill_name     varchar(100) not null                 comment '技能名称',
  skill_category varchar(30)  default ''               comment '技能类别（后端/前端/AI大模型/工程化运维）',
  primary key (id),
  key idx_skill_name (skill_name),
  key idx_batch (batch_date),
  key idx_posting (posting_id)
) engine=innodb comment='岗位技能需求明细';

-- ----------------------------
-- 菜单：人才培养（目录）→ 人才培养看板 / 行业编码对照表
-- 兼容旧版：若已存在顶级「人才培养」菜单（component=project/talent/index），
-- 先将其转换为目录，再补齐两个子菜单；可重复执行。
-- ----------------------------
SET @existing_talent_parent_id = (
    SELECT `menu_id` FROM `sys_menu`
    WHERE `parent_id` = 0 AND `menu_type` = 'M' AND `path` = 'talent'
    ORDER BY `menu_id` LIMIT 1
);

-- 旧版顶级菜单转目录
UPDATE `sys_menu`
SET `menu_type` = 'M',
    `component` = NULL,
    `path` = 'talent',
    `icon` = 'chart',
    `remark` = '人才培养目录'
WHERE `parent_id` = 0
  AND `component` = 'project/talent/index'
  AND `menu_type` = 'C'
  AND @existing_talent_parent_id IS NULL;

-- 目录（不存在则新增）
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
                        `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
                        `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '人才培养', 0, 5, 'talent', NULL, NULL,
       '', 1, 0, 'M', '0', '0',
       '', 'chart', 'admin', sysdate(), '', NULL, '人才培养目录'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu`
    WHERE `parent_id` = 0 AND `menu_type` = 'M' AND `path` = 'talent'
);

SET @talent_parent_id = (
    SELECT `menu_id` FROM `sys_menu`
    WHERE `parent_id` = 0 AND `menu_type` = 'M' AND `path` = 'talent'
    ORDER BY `menu_id` LIMIT 1
);

-- 旧版顶级菜单（若目录已存在）转为「人才培养看板」子菜单
SET @existing_talent_dashboard_id = (
    SELECT `menu_id` FROM `sys_menu`
    WHERE `parent_id` = @talent_parent_id AND `component` = 'project/talent/index'
    ORDER BY `menu_id` LIMIT 1
);

UPDATE `sys_menu`
SET `menu_name` = '人才培养看板',
    `parent_id` = @talent_parent_id,
    `order_num` = 1,
    `path` = 'dashboard',
    `component` = 'project/talent/index',
    `route_name` = 'TalentDashboard',
    `menu_type` = 'C',
    `icon` = 'dashboard',
    `remark` = '人才培养方案智能优化看板'
WHERE `parent_id` = 0
  AND `component` = 'project/talent/index'
  AND `menu_type` = 'C'
  AND @talent_parent_id IS NOT NULL
  AND @existing_talent_dashboard_id IS NULL;

-- 人才培养看板
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
                        `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
                        `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '人才培养看板', @talent_parent_id, 1, 'dashboard', 'project/talent/index', NULL,
       'TalentDashboard', 1, 0, 'C', '0', '0',
       '', 'dashboard', 'admin', sysdate(), '', NULL, '人才培养方案智能优化看板'
WHERE @talent_parent_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `sys_menu`
      WHERE `parent_id` = @talent_parent_id AND `component` = 'project/talent/index'
  );

-- 行业编码对照表
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
                        `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
                        `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '行业编码对照表', @talent_parent_id, 2, 'industry-code', 'project/talent/industry-code/index', NULL,
       'TalentIndustryCode', 1, 0, 'C', '0', '0',
       '', 'table', 'admin', sysdate(), '', NULL, 'BOSS 直聘行业编码对照表'
WHERE @talent_parent_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `sys_menu`
      WHERE `parent_id` = @talent_parent_id AND `component` = 'project/talent/industry-code/index'
  );

-- 角色菜单授权（admin 角色；admin 用户本身拥有全部菜单，这里保证普通角色配置完整）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`menu_id`
FROM `sys_menu` m
WHERE (m.`menu_id` = @talent_parent_id OR m.`parent_id` = @talent_parent_id)
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`menu_id`
  );

SELECT `menu_id`, `menu_name`, `parent_id`, `path`, `component`, `menu_type`, `visible`, `status`
FROM `sys_menu`
WHERE `menu_id` = @talent_parent_id OR `parent_id` = @talent_parent_id
ORDER BY `parent_id`, `order_num`;
