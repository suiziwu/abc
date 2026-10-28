-- 课程课时（lesson）体系：把「一章一视频」升级为「一章多课时」
--
-- 背景：课程视频按目录分层（如 第一章 网络体系结构 / 1.1.0 引言 / 1.1.1 概念和功能），
--       章节应对应目录（章），章下每个视频是一个课时。逐字稿、随堂练习、
--       知识库切片都需要挂到课时上，避免多个课时互相覆盖。
--
-- 可重复执行（幂等）：列变更通过 information_schema 判断后再执行。

SET NAMES utf8mb4;

-- 1. 课时表
CREATE TABLE IF NOT EXISTS `course_lesson` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课时ID',
  `chapter_id` bigint NOT NULL COMMENT '所属章节ID',
  `subject_id` bigint NOT NULL COMMENT '所属学科ID',
  `lesson_no` int NOT NULL DEFAULT 0 COMMENT '课时序号（章内，从1开始）',
  `lesson_code` varchar(40) DEFAULT NULL COMMENT '课时编号（如 1.1.1）',
  `title` varchar(200) DEFAULT NULL COMMENT '课时标题',
  `video_path` varchar(500) DEFAULT NULL COMMENT '课时视频路径（/profile/...）',
  `duration_sec` int DEFAULT NULL COMMENT '视频时长（秒）',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '显示排序',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `test_id` bigint DEFAULT NULL COMMENT '本课时的随堂练习题库ID',
  `transcript_count` int DEFAULT 0 COMMENT '逐字稿句数',
  `point_count` int DEFAULT 0 COMMENT '生成的知识点数',
  `question_count` int DEFAULT 0 COMMENT '生成的题目数',
  `kb_count` int DEFAULT 0 COMMENT '知识库分块数',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lesson_chapter_no` (`chapter_id`, `lesson_no`),
  KEY `idx_lesson_chapter` (`chapter_id`),
  KEY `idx_lesson_subject` (`subject_id`),
  CONSTRAINT `fk_lesson_chapter` FOREIGN KEY (`chapter_id`) REFERENCES `course_chapter` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程课时表（章下多视频）' ROW_FORMAT = Dynamic;

-- 2. course_transcript 增加 lesson_id（逐字稿归属课时；chapter_id 保留，按章查询仍可用）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `course_transcript` ADD COLUMN `lesson_id` bigint NULL DEFAULT NULL COMMENT ''所属课时ID'' AFTER `chapter_id`, ADD KEY `idx_ct_lesson` (`lesson_id`)',
    'SELECT ''course_transcript.lesson_id already exists''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_transcript' AND COLUMN_NAME = 'lesson_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. course_video_import 增加 lesson_id / lesson_code（导入任务归属课时）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `course_video_import` ADD COLUMN `lesson_id` bigint NULL DEFAULT NULL COMMENT ''所属课时ID'' AFTER `chapter_id`, ADD COLUMN `lesson_code` varchar(40) DEFAULT NULL COMMENT ''课时编号'' AFTER `lesson_id`, ADD KEY `idx_cvi_lesson` (`lesson_id`)',
    'SELECT ''course_video_import.lesson_id already exists''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_video_import' AND COLUMN_NAME = 'lesson_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 校验
SELECT 'course_lesson' AS tbl, COUNT(*) AS rows_now FROM course_lesson;
SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE()
   AND ((TABLE_NAME = 'course_transcript' AND COLUMN_NAME = 'lesson_id')
     OR (TABLE_NAME = 'course_video_import' AND COLUMN_NAME IN ('lesson_id','lesson_code')));
