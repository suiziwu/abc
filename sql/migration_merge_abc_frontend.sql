-- ============================================================
-- abc 前端页面接入 new2 后端所需的增量迁移（可重复执行）
--
-- 背景：
--   abc 版「知识点管理 / 课程学习 / 学习路径与画像」页面依赖：
--   1) 章节上有关联岗位 position_id；
--   2) 知识点上有 chapter_id（用于按章分组、视频学习课程）；
--   3) 岗位 position 表（岗位 CRUD）。
--
-- 本脚本只做增量：加列、回填，不删除任何已有数据。
-- 用法：
--   mysql -u root -p --default-character-set=utf8mb4 -D knowledge < sql/migration_merge_abc_frontend.sql
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. course_chapter 增加 position_id（abc 章管理里的「所属岗位」）
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
-- 2. 回填 knowledge_point.chapter_id
--    new2 的章节归属保存在 chapter_point，abc 页面直接读 knowledge_point.chapter_id，
--    这里按“章-知识点”关联回填（一个知识点属于多章时取排序最靠前的一章）。
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
-- 3. 回填 course_chapter.course_title（章节页“课程名称”列）
-- ------------------------------------------------------------
UPDATE `course_chapter` cc
JOIN `subject` s ON s.id = cc.subject_id
SET cc.course_title = s.name
WHERE (cc.course_title IS NULL OR cc.course_title = '')
  AND cc.del_flag = '0';

-- ------------------------------------------------------------
-- 4. 校验输出
-- ------------------------------------------------------------
SELECT 'chapter_position' AS item, COUNT(*) AS total,
       SUM(position_id IS NOT NULL) AS filled
FROM `course_chapter`
UNION ALL
SELECT 'point_chapter', COUNT(*), SUM(chapter_id IS NOT NULL)
FROM `knowledge_point`;
