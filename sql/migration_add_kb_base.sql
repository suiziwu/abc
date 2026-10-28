-- 知识库主表：与学科一对一（每个学科一个知识库）
-- 可重复执行；会为已存在的学科补齐知识库记录
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `kb_base` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
  `subject_id` bigint NOT NULL COMMENT '所属学科ID',
  `name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识库名称（默认同科学科名称）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '删除标志（0-正常 1-已删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_base_subject` (`subject_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库表（与学科一对一）' ROW_FORMAT = Dynamic;

-- 为已有学科补齐知识库
INSERT INTO `kb_base` (`subject_id`, `name`, `status`, `create_by`, `create_time`, `del_flag`)
SELECT s.`id`, s.`name`, '0', 'system', NOW(), '0'
FROM `subject` s
WHERE NOT EXISTS (
    SELECT 1 FROM `kb_base` k WHERE k.`subject_id` = s.`id`
);
