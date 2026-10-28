-- 逐字稿唯一键从「章 + 序号」改为「课时 + 序号」
-- 一章多课时后，同一章下多个课时的逐字稿序号都会从 1 开始，原唯一键会冲突。
SET NAMES utf8mb4;

SET @ddl = (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `course_transcript` DROP INDEX `uk_ct_chapter_seq`, ADD UNIQUE KEY `uk_ct_lesson_seq` (`lesson_id`, `seq`)',
    'SELECT ''uk_ct_chapter_seq absent''')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_transcript' AND INDEX_NAME = 'uk_ct_chapter_seq');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SHOW INDEX FROM course_transcript WHERE Key_name IN ('uk_ct_chapter_seq', 'uk_ct_lesson_seq');
