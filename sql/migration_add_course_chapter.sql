-- ============================================================
-- 课程章节结构化：建表 + 从 knowledge_point.content 回填
--
-- 背景：
--   现有知识点只有扁平表 knowledge_point，章节归属藏在 content 文本里，
--   形如：【位置】第1章 计算机网络概论 · 1.1 计算机网络的定义
--   课程学习页（图一）只能靠 content LIKE '%第1章%' 猜，知识点改名/改文案就会丢。
--
-- 本脚本：
--   1) 新建 course_chapter（章节）与 chapter_point（章节-知识点关联）两张表；
--   2) 解析 content 中的【位置】行，回填章节与“章-知识点”关联；
--   3) 可重复执行（幂等）。
--
-- 用法（MySQL 8.0+，PowerShell 示例）：
--   $env:MYSQL_PWD='你的密码'
--   & "C:\mysql\MySQL\MySQL Server 8.0\bin\mysql.exe" -h 127.0.0.1 -u root `
--       --default-character-set=utf8mb4 -D knowledge `
--       -e "source D:/AAAWork/Knowledge/Knowledge/sql/migration_add_course_chapter.sql"
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------- 1. 建表 ----------
CREATE TABLE IF NOT EXISTS `course_chapter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '章节ID',
  `subject_id` bigint NOT NULL COMMENT '所属学科ID',
  `chapter_no` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '章号（如：第1章）',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '章节名称',
  `course_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属课程名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '章节描述',
  `video` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '章节视频路径',
  `start_time` int NULL DEFAULT NULL COMMENT '视频起始时间（秒）',
  `end_time` int NULL DEFAULT NULL COMMENT '视频结束时间（秒）',
  `sort_order` int NULL DEFAULT 0 COMMENT '显示排序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志（0-正常 1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_subject_chapter`(`subject_id` ASC, `chapter_no` ASC) USING BTREE,
  INDEX `idx_cc_subject_id`(`subject_id` ASC) USING BTREE,
  CONSTRAINT `fk_cc_subject` FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程章节表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `chapter_point` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `chapter_id` bigint NOT NULL COMMENT '章节ID',
  `point_id` bigint NOT NULL COMMENT '知识点ID',
  `section` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '所属小节（如：1.1 计算机网络的定义）',
  `sort_order` int NULL DEFAULT 0 COMMENT '显示排序',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_chapter_point`(`chapter_id` ASC, `point_id` ASC) USING BTREE,
  INDEX `idx_cp_chapter_id`(`chapter_id` ASC) USING BTREE,
  INDEX `idx_cp_point_id`(`point_id` ASC) USING BTREE,
  CONSTRAINT `fk_cp_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `course_chapter` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_cp_point` FOREIGN KEY (`point_id`) REFERENCES `knowledge_point` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '章节-知识点关联表' ROW_FORMAT = Dynamic;

-- ---------- 2. 解析 content 中的【位置】行 ----------
-- 形如：第1章 计算机网络概论 · 1.1 计算机网络的定义
DROP TEMPORARY TABLE IF EXISTS tmp_kp_loc;
CREATE TEMPORARY TABLE tmp_kp_loc AS
SELECT kp.id AS point_id,
       kp.subject_id,
       SUBSTRING_INDEX(SUBSTRING_INDEX(kp.content, '【位置】', -1), '\n', 1) AS loc
FROM knowledge_point kp
WHERE kp.content LIKE '%【位置】%';

DROP TEMPORARY TABLE IF EXISTS tmp_kp_after;
CREATE TEMPORARY TABLE tmp_kp_after AS
SELECT point_id,
       subject_id,
       REGEXP_SUBSTR(loc, '第[0-9]+章') AS chapter_no,
       TRIM(SUBSTRING(loc, CHAR_LENGTH(REGEXP_SUBSTR(loc, '第[0-9]+章')) + 1)) AS after_chapter
FROM tmp_kp_loc
WHERE REGEXP_SUBSTR(loc, '第[0-9]+章') IS NOT NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_kp_parsed;
CREATE TEMPORARY TABLE tmp_kp_parsed AS
SELECT point_id,
       subject_id,
       chapter_no,
       TRIM(SUBSTRING_INDEX(after_chapter, '·', 1)) AS chapter_title,
       CASE WHEN LOCATE('·', after_chapter) > 0
            THEN TRIM(SUBSTRING_INDEX(after_chapter, '·', -1))
            ELSE NULL END AS section
FROM tmp_kp_after;

-- ---------- 3. 回填章节 ----------
INSERT INTO course_chapter (subject_id, chapter_no, title, sort_order, status, create_by, create_time, del_flag)
SELECT subject_id,
       chapter_no,
       MAX(NULLIF(chapter_title, '')),
       CAST(REGEXP_SUBSTR(chapter_no, '[0-9]+') AS UNSIGNED) * 10,
       '0', 'system', NOW(), '0'
FROM tmp_kp_parsed
GROUP BY subject_id, chapter_no
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  sort_order = VALUES(sort_order),
  update_time = NOW();

-- ---------- 4. 回填章节-知识点关联 ----------
INSERT INTO chapter_point (chapter_id, point_id, section, sort_order, create_time)
SELECT cc.id, tp.point_id, tp.section, COALESCE(kp.sort_order, 0), NOW()
FROM tmp_kp_parsed tp
JOIN course_chapter cc
  ON cc.subject_id = tp.subject_id AND cc.chapter_no = tp.chapter_no
JOIN knowledge_point kp
  ON kp.id = tp.point_id
WHERE tp.chapter_no IS NOT NULL AND tp.chapter_no <> ''
ON DUPLICATE KEY UPDATE
  section = VALUES(section),
  sort_order = VALUES(sort_order);

-- ---------- 5. 清理临时表 ----------
DROP TEMPORARY TABLE IF EXISTS tmp_kp_loc;
DROP TEMPORARY TABLE IF EXISTS tmp_kp_after;
DROP TEMPORARY TABLE IF EXISTS tmp_kp_parsed;

-- ---------- 6. 结果校验 ----------
SELECT cc.subject_id, cc.chapter_no, cc.title, COUNT(cp.id) AS point_count
FROM course_chapter cc
LEFT JOIN chapter_point cp ON cp.chapter_id = cc.id
GROUP BY cc.id
ORDER BY cc.subject_id, cc.sort_order;