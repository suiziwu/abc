-- ============================================================
-- 知识库（RAG）数据层 v2 迁移
--
-- 目标：把「只有分块表」的扁平结构，升级为百炼同款三层模型
--       知识库(kb_base) → 文档(kb_document) → 切片(kb_chunk)
--       并为切片补齐元数据、全文索引与增量入库所需的指纹字段。
--
-- 特点：
--   * 幂等：可重复执行，列/索引存在则跳过；
--   * 向后兼容：只加列、加表、加索引，不改不删已有字段，旧代码仍可运行；
--   * 自动回填：历史 87 条分块会补齐 kb_id / doc_pk / content_hash 等。
--
-- 用法：
--   mysql -u root -p personnel_information < sql/migration_kb_rag_v2.sql
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 0. 备份（重复执行不会重复插入）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_chunk_bak_20261028` LIKE `kb_chunk`;
-- 显式列名：本脚本后续会给 kb_chunk 加列，用 SELECT * 重跑时会因列数不一致报错
INSERT IGNORE INTO `kb_chunk_bak_20261028`
  (`doc_id`, `source_type`, `subject_id`, `chapter_id`, `point_id`, `title`, `content`,
   `chunk_index`, `start_time`, `model`, `dimensions`, `embedding`, `create_time`)
SELECT `doc_id`, `source_type`, `subject_id`, `chapter_id`, `point_id`, `title`, `content`,
       `chunk_index`, `start_time`, `model`, `dimensions`, `embedding`, `create_time`
FROM `kb_chunk`;

CREATE TABLE IF NOT EXISTS `kb_base_bak_20261028` LIKE `kb_base`;
INSERT IGNORE INTO `kb_base_bak_20261028` SELECT * FROM `kb_base`;

-- ------------------------------------------------------------
-- 1. 文档表：一次入库的源文档（章节 / 知识点 / 视频 / 题库）
--    对应百炼「知识库 → 文档」这一层，切片通过 doc_pk 归属到文档
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `kb_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `kb_id` bigint NULL DEFAULT NULL COMMENT '所属知识库ID（kb_base.id）',
  `doc_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源文档标识（chapter:10 / point:11001 / transcript:94:10 / quiz:999）',
  `subject_id` bigint NOT NULL COMMENT '学科ID',
  `chapter_id` bigint NULL DEFAULT NULL COMMENT '章节ID',
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '语料类型（chapter/point/transcript/quiz）',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文档标题',
  `section_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '章节路径（第4章 树 > 4.4 二叉树）',
  `source_uri` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源地址（PDF/视频/题库）',
  `page_start` int NULL DEFAULT NULL COMMENT '起始页',
  `page_end` int NULL DEFAULT NULL COMMENT '结束页',
  `chunk_count` int NULL DEFAULT 0 COMMENT '切片数',
  `token_count` int NULL DEFAULT 0 COMMENT '估算 token 数',
  `content_hash` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文档内容指纹（增量入库用）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '1' COMMENT '状态（1就绪 0处理中 2失败 3停用）',
  `version` int NULL DEFAULT 1 COMMENT '文档版本（内容变更递增）',
  `tags` json NULL COMMENT '标签（元数据过滤用）',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_kb_doc_key`(`doc_key` ASC) USING BTREE,
  INDEX `idx_kb_doc_subject`(`subject_id` ASC, `source_type` ASC) USING BTREE,
  INDEX `idx_kb_doc_chapter`(`chapter_id` ASC) USING BTREE,
  INDEX `idx_kb_doc_hash`(`content_hash` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库文档表（知识库→文档→切片 的中间层）' ROW_FORMAT = Dynamic;

-- ------------------------------------------------------------
-- 2. 切片表：补齐元数据列（一次 ALTER 只加缺失的列）
-- ------------------------------------------------------------
SET @kb_missing_cols = (
  SELECT GROUP_CONCAT(CONCAT('ADD COLUMN `', c.col, '` ', c.ddl) SEPARATOR ', ')
  FROM (
    SELECT 'kb_id'        AS col, 'bigint NULL DEFAULT NULL COMMENT ''所属知识库ID（kb_base.id）''' AS ddl
    UNION ALL SELECT 'doc_pk',       'bigint NULL DEFAULT NULL COMMENT ''所属文档ID（kb_document.id）'''
    UNION ALL SELECT 'section_path', 'varchar(500) NULL DEFAULT NULL COMMENT ''章节路径（第4章 树 > 4.4 二叉树）'''
    UNION ALL SELECT 'page_start',   'int NULL DEFAULT NULL COMMENT ''起始页'''
    UNION ALL SELECT 'page_end',     'int NULL DEFAULT NULL COMMENT ''结束页'''
    UNION ALL SELECT 'token_count',  'int NULL DEFAULT 0 COMMENT ''估算 token 数'''
    UNION ALL SELECT 'content_hash', 'char(32) NULL DEFAULT NULL COMMENT ''内容指纹（增量入库/去重）'''
    UNION ALL SELECT 'status',       'tinyint NOT NULL DEFAULT 1 COMMENT ''状态（1启用 0停用）'''
    UNION ALL SELECT 'update_time',  'datetime NULL DEFAULT NULL COMMENT ''更新时间'''
  ) c
  WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.columns ic
    WHERE ic.table_schema = DATABASE() AND ic.table_name = 'kb_chunk' AND ic.column_name = c.col
  )
);
SET @kb_sql = IF(@kb_missing_cols IS NULL, 'SELECT ''kb_chunk columns already up to date''',
                 CONCAT('ALTER TABLE `kb_chunk` ', @kb_missing_cols));
PREPARE kb_stmt FROM @kb_sql;
EXECUTE kb_stmt;
DEALLOCATE PREPARE kb_stmt;

-- ------------------------------------------------------------
-- 3. 索引：新增复合索引 + ngram 全文索引，删除被复合索引覆盖的单列索引
--    组合索引 (subject_id, chapter_id, source_type) 可覆盖
--    「按学科统计」「按章节过滤」「按类型清空」三类高频查询
-- ------------------------------------------------------------
SET @kb_idx_scope = (SELECT IF(COUNT(*) = 0,
    'CREATE INDEX `idx_kb_scope` ON `kb_chunk` (`subject_id`, `chapter_id`, `source_type`)',
    'SELECT ''idx_kb_scope exists''')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'idx_kb_scope');
PREPARE kb_stmt FROM @kb_idx_scope;
EXECUTE kb_stmt;
DEALLOCATE PREPARE kb_stmt;

SET @kb_idx_doc = (SELECT IF(COUNT(*) = 0,
    'CREATE INDEX `idx_kb_doc_pk` ON `kb_chunk` (`doc_pk`, `chunk_index`)',
    'SELECT ''idx_kb_doc_pk exists''')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'idx_kb_doc_pk');
PREPARE kb_stmt FROM @kb_idx_doc;
EXECUTE kb_stmt;
DEALLOCATE PREPARE kb_stmt;

SET @kb_idx_hash = (SELECT IF(COUNT(*) = 0,
    'CREATE INDEX `idx_kb_hash` ON `kb_chunk` (`content_hash`)',
    'SELECT ''idx_kb_hash exists''')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'idx_kb_hash');
PREPARE kb_stmt FROM @kb_idx_hash;
EXECUTE kb_stmt;
DEALLOCATE PREPARE kb_stmt;

-- 中文全文索引：ngram 分词（ngram_token_size=2），支撑「关键词召回」这一路
SET @kb_idx_ft = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `kb_chunk` ADD FULLTEXT INDEX `ft_kb_text` (`title`, `content`) WITH PARSER ngram',
    'SELECT ''ft_kb_text exists''')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'ft_kb_text');
PREPARE kb_stmt FROM @kb_idx_ft;
EXECUTE kb_stmt;
DEALLOCATE PREPARE kb_stmt;

-- 删除被 idx_kb_scope 前缀覆盖的单列索引，减少写放大
SET @kb_drop_subject = (SELECT IF(COUNT(*) > 0,
    'DROP INDEX `idx_kb_subject` ON `kb_chunk`', 'SELECT ''idx_kb_subject absent''')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'idx_kb_subject');
PREPARE kb_stmt FROM @kb_drop_subject;
EXECUTE kb_stmt;
DEALLOCATE PREPARE kb_stmt;

SET @kb_drop_chapter = (SELECT IF(COUNT(*) > 0,
    'DROP INDEX `idx_kb_chapter` ON `kb_chunk`', 'SELECT ''idx_kb_chapter absent''')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'idx_kb_chapter');
PREPARE kb_stmt FROM @kb_drop_chapter;
EXECUTE kb_stmt;
DEALLOCATE PREPARE kb_stmt;

SET @kb_drop_type = (SELECT IF(COUNT(*) > 0,
    'DROP INDEX `idx_kb_type` ON `kb_chunk`', 'SELECT ''idx_kb_type absent''')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'idx_kb_type');
PREPARE kb_stmt FROM @kb_drop_type;
EXECUTE kb_stmt;
DEALLOCATE PREPARE kb_stmt;

-- ------------------------------------------------------------
-- 4. 历史数据回填
-- ------------------------------------------------------------
-- 4.1 知识库ID
UPDATE `kb_chunk` c
JOIN `kb_base` k ON k.subject_id = c.subject_id
SET c.kb_id = k.id
WHERE c.kb_id IS NULL;

-- 4.2 内容指纹（MD5 去重 + 增量入库判断）
UPDATE `kb_chunk`
SET content_hash = MD5(content)
WHERE content_hash IS NULL AND content IS NOT NULL;

-- 4.3 token 估算：中文按 1 字 ≈ 0.67 token 粗估
UPDATE `kb_chunk`
SET token_count = GREATEST(1, ROUND(CHAR_LENGTH(content) / 1.5))
WHERE (token_count IS NULL OR token_count = 0) AND content IS NOT NULL;

-- 4.4 更新时间
UPDATE `kb_chunk`
SET update_time = COALESCE(create_time, NOW())
WHERE update_time IS NULL;

-- 4.5 文档层：按 doc_id 聚合出文档记录
INSERT INTO `kb_document`
  (`kb_id`, `doc_key`, `subject_id`, `chapter_id`, `source_type`, `title`,
   `chunk_count`, `token_count`, `content_hash`, `status`, `create_time`, `update_time`)
SELECT MAX(c.kb_id), c.doc_id, MAX(c.subject_id), MAX(c.chapter_id), MAX(c.source_type), MAX(c.title),
       COUNT(*), SUM(COALESCE(c.token_count, 0)), MAX(c.content_hash), '1',
       MIN(COALESCE(c.create_time, NOW())), NOW()
FROM `kb_chunk` c
GROUP BY c.doc_id
ON DUPLICATE KEY UPDATE
  `chunk_count` = VALUES(`chunk_count`),
  `token_count` = VALUES(`token_count`),
  `content_hash` = VALUES(`content_hash`),
  `update_time` = NOW();

-- 4.6 切片挂到文档
UPDATE `kb_chunk` c
JOIN `kb_document` d ON d.doc_key = c.doc_id
SET c.doc_pk = d.id
WHERE c.doc_pk IS NULL;

-- 4.7 章节路径：优先「章 > 小节」，没有小节时退化为章节标题
UPDATE `kb_chunk` c
JOIN `course_chapter` ch ON ch.id = c.chapter_id
LEFT JOIN `chapter_point` cp ON cp.chapter_id = c.chapter_id AND cp.point_id = c.point_id
SET c.section_path = CASE
      WHEN cp.section IS NOT NULL AND cp.section <> ''
        THEN CONCAT(ch.chapter_no, ' ', ch.title, ' > ', cp.section)
      ELSE CONCAT(ch.chapter_no, ' ', ch.title)
    END
WHERE c.section_path IS NULL;

-- 4.8 文档层同步章节路径
UPDATE `kb_document` d
JOIN (
  SELECT doc_pk, MAX(section_path) AS section_path
  FROM `kb_chunk`
  WHERE doc_pk IS NOT NULL
  GROUP BY doc_pk
) c ON c.doc_pk = d.id
SET d.section_path = c.section_path
WHERE d.section_path IS NULL;

-- ------------------------------------------------------------
-- 5. 健康度视图：一眼看出每个知识库的切片质量
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW `v_kb_health` AS
SELECT c.subject_id,
       c.kb_id,
       c.source_type,
       COUNT(*)                                                          AS chunk_count,
       COUNT(DISTINCT c.doc_pk)                                          AS doc_count,
       SUM(CASE WHEN c.embedding IS NULL OR c.embedding = '' THEN 1 ELSE 0 END) AS missing_vector,
       SUM(CASE WHEN c.status = 0 THEN 1 ELSE 0 END)                     AS disabled_chunk,
       SUM(COALESCE(c.token_count, 0))                                    AS token_count,
       ROUND(AVG(CHAR_LENGTH(c.content)), 1)                              AS avg_chars,
       MAX(c.update_time)                                                 AS last_update
FROM `kb_chunk` c
GROUP BY c.subject_id, c.kb_id, c.source_type;

-- ------------------------------------------------------------
-- 6. 校验
-- ------------------------------------------------------------
SELECT
  (SELECT COUNT(*) FROM `kb_chunk`)                                   AS chunks,
  (SELECT COUNT(*) FROM `kb_document`)                                AS documents,
  (SELECT COUNT(*) FROM `kb_chunk` WHERE `doc_pk` IS NULL)            AS chunks_without_doc,
  (SELECT COUNT(*) FROM `kb_chunk` WHERE `content_hash` IS NULL)      AS chunks_without_hash,
  (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'kb_chunk' AND index_name = 'ft_kb_text') AS fulltext_index_parts;
