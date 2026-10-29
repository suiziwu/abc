-- =============================================================
-- 合并「平行章」数据到标准章（一次性数据修复，幂等）
--
-- 背景：串行导入驱动用章号「3」「4」上传，与库中「第3章」「第4章」精确匹配失败，
--   产生了平行章 130('3')、132('4') 与测试章 131('第99章')；
--   其课时编号为 '4.1' 一位数格式，与框架课时 '4.01' 不一致。
-- 处理：
--   1) 卡死任务标失败；
--   2) 平行课时 → 框架课时（编号归一化，逐字稿/视频/题库/任务/知识库引用全迁）；
--   3) 章节知识点关联并入标准章；删除平行章与测试章（级联删平行课时）。
--
-- 执行：Get-Content sql\merge_parallel_chapters_20261028.sql -Raw |
--         mysql -u root -p --default-character-set=utf8mb4 -D personnel_information
-- =============================================================
SET NAMES utf8mb4;
SET @sid = 94;

-- ---------- 1. 卡死/中断任务标失败 ----------
UPDATE `course_video_import`
SET `status` = '3', `stage` = 'error',
    `message` = '后端升级重启中断，课时已清理，请重新上传解析'
WHERE `status` IN ('0', '1');

-- ---------- 2. 平行课时 → 框架课时 映射 ----------
DROP TABLE IF EXISTS `tmp_lesson_merge_map`;
CREATE TABLE `tmp_lesson_merge_map` AS
WITH normalized AS (
    SELECT l.*,
           CASE WHEN l.`lesson_code` REGEXP '^[0-9]+\\.[0-9]$'
                THEN CONCAT(SUBSTRING_INDEX(l.`lesson_code`, '.', 1), '.0',
                            SUBSTRING_INDEX(l.`lesson_code`, '.', -1))
                ELSE l.`lesson_code` END AS norm_code
    FROM `course_lesson` l
    JOIN `course_chapter` pch ON pch.id = l.`chapter_id`
    WHERE l.`subject_id` = @sid AND pch.`chapter_no` IN ('3', '4')
),
mapped AS (
    SELECT n.id AS old_id, n.chapter_id AS old_chapter_id, n.norm_code,
           sch.id AS new_chapter_id, fl.id AS new_id,
           ROW_NUMBER() OVER (PARTITION BY n.id
                              ORDER BY COALESCE(t.trans, 0) DESC, fl.id) AS pick_rank,
           ROW_NUMBER() OVER (PARTITION BY n.norm_code, n.chapter_id
                              ORDER BY COALESCE(t.trans, 0) DESC, n.id) AS old_rank
    FROM normalized n
    JOIN `course_chapter` sch
      ON sch.`subject_id` = @sid AND sch.`del_flag` = '0'
     AND sch.`chapter_no` = CONCAT('第', LEFT(n.norm_code, 1), '章')
    JOIN `course_lesson` fl
      ON fl.`chapter_id` = sch.id AND fl.`lesson_code` = n.norm_code
    LEFT JOIN (SELECT lesson_id, COUNT(*) trans FROM `course_transcript`
               WHERE lesson_id IS NOT NULL GROUP BY lesson_id) t
      ON t.lesson_id = n.id
)
SELECT old_id, old_chapter_id, new_id, new_chapter_id
FROM mapped
WHERE pick_rank = 1;

-- 每个框架课时只取一个平行课时作为填充源（优先已有题库的，其次 id 大的）
DROP TABLE IF EXISTS `tmp_lesson_fill`;
CREATE TABLE `tmp_lesson_fill` AS
SELECT new_id, old_id
FROM (
    SELECT m.old_id, m.new_id,
           ROW_NUMBER() OVER (PARTITION BY m.new_id
                              ORDER BY (o.test_id IS NOT NULL) DESC, m.old_id DESC) AS rn
    FROM `tmp_lesson_merge_map` m
    JOIN `course_lesson` o ON o.id = m.old_id
) ranked
WHERE rn = 1;

-- ---------- 3. 逐字稿改指（先记录框架课时已有逐字稿的情况，避免同表子查询 1093） ----------
DROP TABLE IF EXISTS `tmp_new_has_trans`;
CREATE TABLE `tmp_new_has_trans` AS
SELECT DISTINCT t.`lesson_id`
FROM `course_transcript` t
JOIN `tmp_lesson_merge_map` m ON t.`lesson_id` = m.new_id;

UPDATE `course_transcript` t
JOIN `tmp_lesson_merge_map` m ON t.`lesson_id` = m.old_id
SET t.`lesson_id` = m.new_id, t.`chapter_id` = m.new_chapter_id
WHERE NOT EXISTS (SELECT 1 FROM `tmp_new_has_trans` x WHERE x.`lesson_id` = m.new_id);

DROP TABLE IF EXISTS `tmp_new_has_trans`;

-- ---------- 4. 框架课时补齐视频 / 题库 / 时长 ----------
UPDATE `course_lesson` n
JOIN (SELECT f.new_id,
             MAX(o.video_path) AS video_path,
             MAX(o.duration_sec) AS duration_sec,
             MAX(o.test_id) AS test_id
      FROM `tmp_lesson_fill` f
      JOIN `course_lesson` o ON o.id = f.old_id
      GROUP BY f.new_id) src ON src.new_id = n.id
SET n.`video_path` = COALESCE(n.`video_path`, src.video_path),
    n.`duration_sec` = COALESCE(n.`duration_sec`, src.duration_sec),
    n.`test_id` = COALESCE(n.`test_id`, src.test_id),
    n.`update_time` = NOW();

-- ---------- 5. 导入任务改指 ----------
UPDATE `course_video_import` vi
JOIN `tmp_lesson_merge_map` m ON vi.`lesson_id` = m.old_id
SET vi.`lesson_id` = m.new_id, vi.`chapter_id` = m.new_chapter_id;

-- ---------- 6. 章节知识点关联并入标准章（去重） ----------
INSERT INTO `chapter_point` (`chapter_id`, `point_id`, `section`, `sort_order`, `create_time`)
SELECT DISTINCT m.new_chapter_id, cp.`point_id`, cp.`section`, cp.`sort_order`, cp.`create_time`
FROM `chapter_point` cp
JOIN `tmp_lesson_merge_map` m ON cp.`chapter_id` = m.old_chapter_id
WHERE NOT EXISTS (SELECT 1 FROM `chapter_point` x
                  WHERE x.`chapter_id` = m.new_chapter_id AND x.`point_id` = cp.`point_id`);

-- ---------- 7. 知识库分块 / 文档：平行章并入标准章，测试章置空 ----------
UPDATE `kb_chunk` kc
JOIN `tmp_lesson_merge_map` m ON kc.`chapter_id` = m.old_chapter_id
SET kc.`chapter_id` = m.new_chapter_id
WHERE kc.`chapter_id` IN (130, 132);

UPDATE `kb_document` kd
JOIN `tmp_lesson_merge_map` m ON kd.`chapter_id` = m.old_chapter_id
SET kd.`chapter_id` = m.new_chapter_id
WHERE kd.`chapter_id` IN (130, 132);

UPDATE `kb_chunk` SET `chapter_id` = NULL WHERE `chapter_id` IN (131);
UPDATE `kb_document` SET `chapter_id` = NULL WHERE `chapter_id` IN (131);

-- ---------- 8. 删除平行章 / 测试章（级联删其课时与章节知识点） ----------
DELETE FROM `course_transcript` WHERE `chapter_id` IN (130, 131, 132);
DELETE FROM `course_chapter` WHERE `id` IN (130, 131, 132) AND `subject_id` = @sid;

-- 导入任务里残留的已删课时/章节引用置空（这些表无外键）
UPDATE `course_video_import` vi
SET vi.`lesson_id` = NULL
WHERE vi.`lesson_id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `course_lesson` l WHERE l.id = vi.`lesson_id`);
UPDATE `course_video_import` vi
SET vi.`chapter_id` = NULL
WHERE vi.`chapter_id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `course_chapter` c WHERE c.id = vi.`chapter_id`);

-- ---------- 9. 重算框架课时统计 ----------
UPDATE `course_lesson` l
SET l.`transcript_count` = (SELECT COUNT(*) FROM `course_transcript` t WHERE t.`lesson_id` = l.id),
    l.`update_time` = NOW()
WHERE l.`subject_id` = @sid;

DROP TABLE IF EXISTS `tmp_lesson_merge_map`;
DROP TABLE IF EXISTS `tmp_lesson_fill`;

-- ---------- 10. 校验 ----------
SELECT cc.`chapter_no`, cc.`title`,
       COUNT(l.id) AS lessons,
       SUM(CASE WHEN l.video_path IS NOT NULL THEN 1 ELSE 0 END) AS with_video,
       SUM(CASE WHEN l.transcript_count > 0 THEN 1 ELSE 0 END) AS parsed,
       (SELECT COUNT(*) FROM `chapter_point` cp WHERE cp.chapter_id = cc.id) AS points
FROM `course_chapter` cc
LEFT JOIN `course_lesson` l ON l.`chapter_id` = cc.id
WHERE cc.`subject_id` = @sid AND cc.`del_flag` = '0'
GROUP BY cc.id, cc.`chapter_no`, cc.`title`, cc.`sort_order`
ORDER BY cc.`sort_order`;

SELECT COUNT(*) AS orphan_transcripts FROM `course_transcript` t
WHERE NOT EXISTS (SELECT 1 FROM `course_chapter` c WHERE c.id = t.`chapter_id`);

SELECT COUNT(*) AS running_tasks FROM `course_video_import` WHERE `status` IN ('0', '1');
