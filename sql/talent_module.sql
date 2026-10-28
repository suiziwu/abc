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
  source       varchar(30)   default ''                 comment '数据来源平台',
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
-- 菜单：人才培养（系统管理 → 首页级菜单）
-- ----------------------------
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
                        `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
                        `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '人才培养', 0, 5, 'talent', 'project/talent/index', NULL,
       '', 1, 0, 'C', '0', '0',
       '', 'chart', 'admin', sysdate(), '', NULL, '岗位动态采集、技能需求趋势、专业匹配预警与培养方案优化建议'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE `component` = 'project/talent/index'
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`menu_id`
FROM `sys_menu` m
WHERE m.`component` = 'project/talent/index'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`menu_id`
  );

SELECT `menu_id`, `menu_name`, `path`, `component` FROM `sys_menu` WHERE `component` = 'project/talent/index';
