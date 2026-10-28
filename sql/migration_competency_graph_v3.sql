-- 岗位能力图谱 v3 契约字段迁移（一次性执行）
ALTER TABLE `job_competency_graph`
  ADD COLUMN `schema_version` varchar(20) DEFAULT '3.0.0' COMMENT '契约版本' AFTER `source_mode`,
  ADD COLUMN `mode` varchar(10) DEFAULT 'llm' COMMENT '生成模式（kb/llm）' AFTER `schema_version`,
  ADD COLUMN `review_status` varchar(20) DEFAULT 'pending' COMMENT '审核状态（approved/pending/rejected）' AFTER `mode`,
  ADD COLUMN `engine_version` varchar(20) DEFAULT '' COMMENT '引擎版本' AFTER `review_status`;

-- 历史 v2 记录标记（仅当时执行一次，后续新记录默认 3.0.0）
-- UPDATE `job_competency_graph`
-- SET `schema_version` = '2.0.0',
--     `mode` = `source_mode`,
--     `review_status` = CASE WHEN `source_mode` = 'kb' THEN 'approved' ELSE 'pending' END
-- WHERE `schema_version` IS NULL OR `schema_version` = '3.0.0';
