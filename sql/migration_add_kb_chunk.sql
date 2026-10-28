-- ============================================================
-- 知识库分块表：建表脚本（幂等）
--
-- 用途：存放向量化后的课程语料，供「视频 AI 助教」做 RAG 检索。
--   * provider=local 时，向量存 embedding 字段（JSON 浮点数组），由 Java 侧算余弦；
--   * provider=milvus 时，向量写入 Milvus，本表仍保留原文用于管理与回放。
--
-- 用法：
--   mysql -u root -D personnel_information < sql/migration_add_kb_chunk.sql
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `kb_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分块ID',
  `doc_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源文档块标识（如 transcript:94:10:3）',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '语料类型（transcript-逐字稿 point-知识点 chapter-章节 quiz-题库）',
  `subject_id` bigint NULL DEFAULT NULL COMMENT '所属学科ID',
  `chapter_id` bigint NULL DEFAULT NULL COMMENT '所属章节ID',
  `point_id` bigint NULL DEFAULT NULL COMMENT '关联知识点ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '分块标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '分块正文',
  `chunk_index` int NULL DEFAULT 0 COMMENT '同一文档内的分块序号',
  `start_time` int NULL DEFAULT NULL COMMENT '视频时间戳（秒，仅逐字稿）',
  `model` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '向量化模型',
  `dimensions` int NULL DEFAULT NULL COMMENT '向量维度',
  `embedding` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '向量（归一化后的浮点数组 JSON）',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_doc_chunk`(`doc_id` ASC, `chunk_index` ASC) USING BTREE,
  INDEX `idx_kb_subject`(`subject_id` ASC) USING BTREE,
  INDEX `idx_kb_chapter`(`chapter_id` ASC) USING BTREE,
  INDEX `idx_kb_point`(`point_id` ASC) USING BTREE,
  INDEX `idx_kb_type`(`source_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库分块表' ROW_FORMAT = Dynamic;

-- 校验
SELECT COUNT(*) AS kb_chunk_ready FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'kb_chunk';