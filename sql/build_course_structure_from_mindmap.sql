-- =============================================================
-- 依据思维导图构建课程结构（章 + 课时两级），可重复执行
--
-- 数据源：D:/AAAWork/output/计算机网络课程思维导图.html
--   课程根 → 6 个章 → 74 个讲（视频）
-- 目标表：
--   course_chapter  6 条章（第1章~第6章，sort_order 10/30/50/70/90/110）
--   course_lesson   74 条课时（lesson_code = 1.1.0 / 2.01 / 3.12 …，章内 lesson_no 从 1 递增）
-- 说明：
--   1) 已由「上传视频」自动导入的课时（同一 lesson_code）保留其 video_path 等信息，
--      本脚本不覆盖已有课时，只补齐缺失的；
--   2) 不做视频解析，本脚本只负责「框架」：章节、课时、层级与排序。
--
-- 执行：Get-Content sql\build_course_structure_from_mindmap.sql -Raw |
--         mysql -u root -p --default-character-set=utf8mb4 -D personnel_information
-- =============================================================
SET NAMES utf8mb4;
SET @sid = 94;

-- 备份（回滚参照）
DROP TABLE IF EXISTS `course_chapter_backup_mindmap`;
CREATE TABLE `course_chapter_backup_mindmap` AS
SELECT * FROM `course_chapter` WHERE `subject_id` = @sid;

DROP TABLE IF EXISTS `course_lesson_backup_mindmap`;
CREATE TABLE `course_lesson_backup_mindmap` AS
SELECT * FROM `course_lesson` WHERE `subject_id` = @sid;

UPDATE `course_chapter` SET `title` = '网络体系结构', `course_title` = '计算机网络课程视频讲解', `sort_order` = 10
WHERE `subject_id` = 94 AND `chapter_no` = '第1章' AND `del_flag` = '0';

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 1, '1.1.0', '引言', NULL, NULL, 11, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.1.0');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 2, '1.1.1', '概念和功能', NULL, NULL, 12, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.1.1');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 3, '1.1.2', '组成和分类', NULL, NULL, 13, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.1.2');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 4, '1.1.3', '标准化工作及相关组织', NULL, NULL, 14, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.1.3');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 5, '1.1.4', '性能指标（1）', NULL, NULL, 15, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.1.4');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 6, '1.1.5', '性能指标（2）', NULL, NULL, 16, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.1.5');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 7, '1.2.1', '分层结构、协议、接口和服务', NULL, NULL, 17, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.2.1');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 8, '1.2.2', 'OSI参考模型（1）', NULL, NULL, 18, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.2.2');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 9, '1.2.3', 'OSI参考模型（2）', NULL, NULL, 19, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.2.3');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 10, '1.2.4', 'TCPIP参考模型', NULL, NULL, 20, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.2.4');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 11, '1.2.5', '第一章总结', NULL, NULL, 21, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第1章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '1.2.5');

UPDATE `course_chapter` SET `title` = '物理层', `course_title` = '计算机网络课程视频讲解', `sort_order` = 30
WHERE `subject_id` = 94 AND `chapter_no` = '第2章' AND `del_flag` = '0';

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 1, '2.01', '物理层01', NULL, NULL, 31, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第2章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '2.01');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 2, '2.02', '物理层02', NULL, NULL, 32, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第2章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '2.02');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 3, '2.03', '物理层03', NULL, NULL, 33, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第2章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '2.03');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 4, '2.04', '物理层04', NULL, NULL, 34, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第2章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '2.04');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 5, '2.05', '物理层05', NULL, NULL, 35, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第2章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '2.05');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 6, '2.06', '物理层06', NULL, NULL, 36, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第2章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '2.06');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 7, '2.07', '物理层07', NULL, NULL, 37, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第2章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '2.07');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 8, '2.08', '物理层08', NULL, NULL, 38, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第2章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '2.08');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 9, '2.09', '物理层09', NULL, NULL, 39, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第2章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '2.09');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 10, '2.10', '物理层10', NULL, NULL, 40, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第2章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '2.10');

UPDATE `course_chapter` SET `title` = '数据链路层', `course_title` = '计算机网络课程视频讲解', `sort_order` = 50
WHERE `subject_id` = 94 AND `chapter_no` = '第3章' AND `del_flag` = '0';

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 1, '3.01', '数据链路层1', NULL, NULL, 51, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.01');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 2, '3.02', '数据链路层2', NULL, NULL, 52, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.02');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 3, '3.03', '数据链路层3', NULL, NULL, 53, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.03');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 4, '3.04', '数据链路层4', NULL, NULL, 54, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.04');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 5, '3.05', '数据链路层5', NULL, NULL, 55, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.05');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 6, '3.06', '数据链路层6', NULL, NULL, 56, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.06');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 7, '3.07', '数据链路层7', NULL, NULL, 57, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.07');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 8, '3.08', '数据链路层8', NULL, NULL, 58, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.08');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 9, '3.09', '数据链路层9', NULL, NULL, 59, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.09');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 10, '3.10', '数据链路层10', NULL, NULL, 60, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.10');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 11, '3.11', '数据链路层11', NULL, NULL, 61, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.11');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 12, '3.12', '数据链路层12', NULL, NULL, 62, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.12');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 13, '3.13', '数据链路层13', NULL, NULL, 63, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.13');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 14, '3.14', '数据链路层14', NULL, NULL, 64, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.14');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 15, '3.15', '数据链路层15', NULL, NULL, 65, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.15');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 16, '3.16', '数据链路层16', NULL, NULL, 66, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.16');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 17, '3.17', '数据链路层17', NULL, NULL, 67, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.17');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 18, '3.18', '数据链路层18', NULL, NULL, 68, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.18');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 19, '3.19', '数据链路层19', NULL, NULL, 69, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.19');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 20, '3.20', '数据链路层20', NULL, NULL, 70, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第3章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '3.20');

UPDATE `course_chapter` SET `title` = '网络层', `course_title` = '计算机网络课程视频讲解', `sort_order` = 70
WHERE `subject_id` = 94 AND `chapter_no` = '第4章' AND `del_flag` = '0';

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 1, '4.01', '网络层1【小麦麦资料库】', NULL, NULL, 71, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.01');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 2, '4.02', '网络层2【小麦麦资料库】', NULL, NULL, 72, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.02');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 3, '4.03', '网络层3【小麦麦资料库】', NULL, NULL, 73, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.03');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 4, '4.04', '网络层4【小麦麦资料库】', NULL, NULL, 74, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.04');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 5, '4.05', '网络层5【小麦麦资料库】', NULL, NULL, 75, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.05');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 6, '4.06', '网络层6【小麦麦资料库】', NULL, NULL, 76, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.06');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 7, '4.07', '网络层7【小麦麦资料库】', NULL, NULL, 77, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.07');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 8, '4.08', '网络层8【小麦麦资料库】', NULL, NULL, 78, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.08');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 9, '4.09', '网络层9【小麦麦资料库】', NULL, NULL, 79, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.09');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 10, '4.10', '网络层10【小麦麦资料库】', NULL, NULL, 80, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.10');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 11, '4.11', '网络层11【小麦麦资料库】', NULL, NULL, 81, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.11');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 12, '4.12', '网络层12【小麦麦资料库】', NULL, NULL, 82, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.12');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 13, '4.13', '网络层13【小麦麦资料库】', NULL, NULL, 83, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.13');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 14, '4.14', '网络层14【小麦麦资料库】', NULL, NULL, 84, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.14');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 15, '4.15', '网络层15【小麦麦资料库】', NULL, NULL, 85, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.15');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 16, '4.16', '网络层16【小麦麦资料库】', NULL, NULL, 86, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.16');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 17, '4.17', '网络层17【小麦麦资料库】', NULL, NULL, 87, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.17');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 18, '4.18', '网络层18【小麦麦资料库】', NULL, NULL, 88, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.18');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 19, '4.19', '网络层19【小麦麦资料库】', NULL, NULL, 89, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第4章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '4.19');

UPDATE `course_chapter` SET `title` = '传输层', `course_title` = '计算机网络课程视频讲解', `sort_order` = 90
WHERE `subject_id` = 94 AND `chapter_no` = '第5章' AND `del_flag` = '0';

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 1, '5.01', '传输层1', NULL, NULL, 91, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第5章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '5.01');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 2, '5.02', '传输层2', NULL, NULL, 92, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第5章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '5.02');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 3, '5.03', '传输层3', NULL, NULL, 93, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第5章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '5.03');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 4, '5.04', '传输层4', NULL, NULL, 94, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第5章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '5.04');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 5, '5.05', '传输层5', NULL, NULL, 95, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第5章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '5.05');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 6, '5.06', '传输层6', NULL, NULL, 96, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第5章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '5.06');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 7, '5.07', '传输层7', NULL, NULL, 97, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第5章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '5.07');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 8, '5.08', '传输层8', NULL, NULL, 98, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第5章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '5.08');

UPDATE `course_chapter` SET `title` = '应用层', `course_title` = '计算机网络课程视频讲解', `sort_order` = 110
WHERE `subject_id` = 94 AND `chapter_no` = '第6章' AND `del_flag` = '0';

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 1, '6.01', '应用层1', NULL, NULL, 111, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第6章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '6.01');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 2, '6.02', '应用层2', NULL, NULL, 112, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第6章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '6.02');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 3, '6.03', '应用层3', NULL, NULL, 113, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第6章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '6.03');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 4, '6.04', '应用层4', NULL, NULL, 114, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第6章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '6.04');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 5, '6.05', '应用层5', NULL, NULL, 115, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第6章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '6.05');

INSERT INTO `course_lesson`
  (`chapter_id`, `subject_id`, `lesson_no`, `lesson_code`, `title`,
   `video_path`, `duration_sec`, `sort_order`, `status`, `create_by`, `create_time`)
SELECT c.id, 94, 6, '6.06', '应用层6', NULL, NULL, 116, '0', 'admin', NOW()
FROM `course_chapter` c
WHERE c.`subject_id` = 94 AND c.`chapter_no` = '第6章' AND c.`del_flag` = '0'
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l
                  WHERE l.`chapter_id` = c.id AND l.`lesson_code` = '6.06');

-- 清理：自动导入产生的临时章（chapter_no 不是「第N章」形式），其课时先并入对应章
UPDATE `course_lesson` l
JOIN `course_chapter` cc ON cc.id = l.`chapter_id`
SET l.`chapter_id` = (SELECT id FROM `course_chapter`
                      WHERE `subject_id` = @sid AND `chapter_no` = CONCAT('第', LEFT(cc.`chapter_no`, 1), '章')
                      AND `del_flag` = '0' LIMIT 1)
WHERE cc.`subject_id` = @sid AND cc.`chapter_no` NOT LIKE '第%章';

DELETE FROM `course_chapter`
WHERE `subject_id` = @sid AND `chapter_no` NOT LIKE '第%章';

-- 逐字稿 / 知识库分块 归到标准章
UPDATE `course_transcript` t
JOIN `course_chapter` cc ON cc.id = t.`chapter_id`
SET t.`chapter_id` = (SELECT id FROM `course_chapter`
                      WHERE `subject_id` = @sid AND `chapter_no` = CONCAT('第', LEFT(cc.`chapter_no`, 1), '章')
                      AND `del_flag` = '0' LIMIT 1)
WHERE cc.`subject_id` = @sid AND cc.`chapter_no` NOT LIKE '第%章';

UPDATE `kb_chunk` kc
JOIN `course_chapter` cc ON cc.id = kc.`chapter_id`
SET kc.`chapter_id` = (SELECT id FROM `course_chapter`
                      WHERE `subject_id` = @sid AND `chapter_no` = CONCAT('第', LEFT(cc.`chapter_no`, 1), '章')
                      AND `del_flag` = '0' LIMIT 1)
WHERE cc.`subject_id` = @sid AND cc.`chapter_no` NOT LIKE '第%章';

UPDATE `kb_document` kd
JOIN `course_chapter` cc ON cc.id = kd.`chapter_id`
SET kd.`chapter_id` = (SELECT id FROM `course_chapter`
                      WHERE `subject_id` = @sid AND `chapter_no` = CONCAT('第', LEFT(cc.`chapter_no`, 1), '章')
                      AND `del_flag` = '0' LIMIT 1)
WHERE cc.`subject_id` = @sid AND cc.`chapter_no` NOT LIKE '第%章';

UPDATE `course_video_import` vi
SET vi.`chapter_id` = NULL
WHERE vi.`chapter_id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `course_chapter` c WHERE c.id = vi.`chapter_id`);

-- 校验：章 + 课时
SELECT cc.`chapter_no`, cc.`title`, cc.`sort_order`,
       (SELECT COUNT(*) FROM `course_lesson` l WHERE l.`chapter_id` = cc.id) AS lessons,
       (SELECT COUNT(*) FROM `course_lesson` l WHERE l.`chapter_id` = cc.id AND l.`video_path` IS NOT NULL) AS with_video,
       (SELECT COUNT(*) FROM `chapter_point` cp WHERE cp.`chapter_id` = cc.id) AS points
FROM `course_chapter` cc
WHERE cc.`subject_id` = @sid AND cc.`del_flag` = '0'
ORDER BY cc.`sort_order`;
