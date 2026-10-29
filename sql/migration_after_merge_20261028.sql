-- ============================================================
-- 合并远程 origin/main（1ea037a..3e7a017）后的增量迁移
--
-- 背景：
--   远端新增「岗位管理 / 岗位能力图谱 / 人才培养」等模块，并把
--   course_chapter、knowledge_point、ai_chat_history 的实体与 Mapper
--   扩展到新列。本地库还没有这些列，合并后「课程章节」「知识点」等
--   页面会直接 500（Unknown column）。本脚本只做增量：加列、建表、回填，
--   不删除任何已有数据，可重复执行。
--
-- 用法：
--   mysql -u root -p --default-character-set=utf8mb4 -D personnel_information < sql/migration_after_merge_20261028.sql
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. course_chapter 增加 position_id（章节关联岗位）
-- ------------------------------------------------------------
SET @col_exists = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'course_chapter' AND column_name = 'position_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `course_chapter` ADD COLUMN `position_id` bigint DEFAULT NULL COMMENT ''关联岗位ID'' AFTER `subject_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'course_chapter' AND index_name = 'idx_cc_position_id'
);
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE `course_chapter` ADD INDEX `idx_cc_position_id` (`position_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. knowledge_point 增加 chapter_id（知识点归属章）
-- ------------------------------------------------------------
SET @col_exists = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'knowledge_point' AND column_name = 'chapter_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `knowledge_point` ADD COLUMN `chapter_id` bigint DEFAULT NULL COMMENT ''所属章ID'' AFTER `subject_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(1) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'knowledge_point' AND index_name = 'idx_kp_chapter_id'
);
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE `knowledge_point` ADD INDEX `idx_kp_chapter_id` (`chapter_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. ai_chat_history 增加 position_id（AI 助教按岗位留存）
-- ------------------------------------------------------------
SET @col_exists = (
  SELECT COUNT(1) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_chat_history' AND column_name = 'position_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `ai_chat_history` ADD COLUMN `position_id` bigint DEFAULT NULL COMMENT ''岗位ID'' AFTER `user_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4. position 岗位表（远端 PositionMapper 依赖）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `position` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `name` varchar(200) NOT NULL COMMENT '岗位名称',
  `description` varchar(1000) DEFAULT NULL COMMENT '岗位描述',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `sort_order` int DEFAULT '0' COMMENT '显示排序',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0正常 1已删除）',
  PRIMARY KEY (`id`),
  KEY `idx_position_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位表';

-- ------------------------------------------------------------
-- 5. 回填 knowledge_point.chapter_id
--    按「章-知识点」关联回填（一个知识点属于多章时取 id 最小的一章），
--    与远端 sql/migration_merge_abc_frontend.sql 的做法一致。
-- ------------------------------------------------------------
UPDATE `knowledge_point` kp
JOIN (
  SELECT cp.point_id, MIN(cp.chapter_id) AS chapter_id
  FROM `chapter_point` cp
  GROUP BY cp.point_id
) t ON t.point_id = kp.id
SET kp.chapter_id = t.chapter_id
WHERE kp.chapter_id IS NULL OR kp.chapter_id = 0;

-- ------------------------------------------------------------
-- 6. 校验输出
-- ------------------------------------------------------------
SELECT 'course_chapter.position_id' AS item, COUNT(*) AS total,
       SUM(position_id IS NOT NULL) AS filled FROM `course_chapter`
UNION ALL
SELECT 'knowledge_point.chapter_id', COUNT(*), SUM(chapter_id IS NOT NULL) FROM `knowledge_point`
UNION ALL
SELECT 'position', COUNT(*), SUM(status = '0') FROM `position`;
