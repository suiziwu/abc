-- 视频课程自动分析：逐字稿分句 + 导入任务（可重复执行）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `course_transcript` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `chapter_id` bigint NOT NULL COMMENT '章节ID',
  `seq` int NOT NULL COMMENT '句子序号',
  `start_ms` int DEFAULT NULL COMMENT '开始时间（毫秒）',
  `end_ms` int DEFAULT NULL COMMENT '结束时间（毫秒）',
  `section` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '所属小节',
  `text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '句子文本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ct_chapter_seq` (`chapter_id`, `seq`),
  KEY `idx_ct_chapter` (`chapter_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '课程视频逐字稿（分句）' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `course_video_import` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `subject_id` bigint NOT NULL COMMENT '学科ID',
  `chapter_id` bigint DEFAULT NULL COMMENT '生成/关联的章节ID',
  `chapter_no` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '章号',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '课程标题',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '原始文件名',
  `video_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '视频相对路径（/profile/...）',
  `duration_sec` int DEFAULT NULL COMMENT '视频时长（秒）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '状态（0待处理 1处理中 2成功 3失败）',
  `stage` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '当前阶段',
  `progress` int DEFAULT '0' COMMENT '进度百分比',
  `message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '提示/错误信息',
  `transcript_count` int DEFAULT '0' COMMENT '逐字稿句数',
  `point_count` int DEFAULT '0' COMMENT '生成知识点数',
  `question_count` int DEFAULT '0' COMMENT '生成题目数',
  `kb_count` int DEFAULT '0' COMMENT '知识库分块数',
  `test_id` bigint DEFAULT NULL COMMENT '题库ID',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cvi_subject` (`subject_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '视频课程导入任务' ROW_FORMAT = Dynamic;
