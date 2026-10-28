-- ----------------------------
-- 岗位能力图谱：表结构 + 菜单
-- 数据库：personnel_information
-- ----------------------------

CREATE TABLE IF NOT EXISTS `job_competency_graph` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_name` varchar(100) NOT NULL COMMENT '岗位名称',
  `job_code` varchar(32) DEFAULT '' COMMENT '职业编码',
  `level` varchar(50) DEFAULT '' COMMENT '职业技能等级',
  `source_mode` varchar(20) DEFAULT 'llm' COMMENT '生成方式（llm/kb）',
  `schema_version` varchar(20) DEFAULT '3.0.0' COMMENT '契约版本',
  `mode` varchar(10) DEFAULT 'llm' COMMENT '生成模式（kb/llm）',
  `review_status` varchar(20) DEFAULT 'pending' COMMENT '审核状态（approved/pending/rejected）',
  `engine_version` varchar(20) DEFAULT '' COMMENT '引擎版本',
  `confidence` decimal(4,2) DEFAULT NULL COMMENT '置信度',
  `coverage_json` text COMMENT '覆盖率等统计 JSON',
  `graph_json` longtext COMMENT '图谱全文 JSON',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0-正常 1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_job_name` (`job_name`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '岗位能力图谱' ROW_FORMAT = Dynamic;

-- 菜单：岗位能力图谱（挂在“岗位能力管理”目录下）
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
SELECT 3000, '岗位能力图谱', 3001, 0, 'competency', 'project/competency/index', '', 1, 0, 'C', '0', '0', 'project:competency:list', 'chart', 'admin', now(), '岗位能力图谱工作流'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `menu_id` = 3000);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, 3000
WHERE NOT EXISTS (SELECT 1 FROM `sys_role_menu` WHERE `role_id` = 1 AND `menu_id` = 3000);
