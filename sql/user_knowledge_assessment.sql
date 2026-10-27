SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for user_knowledge_assessment
-- ----------------------------
DROP TABLE IF EXISTS `user_knowledge_assessment`;
CREATE TABLE `user_knowledge_assessment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '测评记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `history_id` bigint DEFAULT NULL COMMENT '关联的AI查询记录ID（目标岗位）',
  `assess_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '测评标题/描述',
  `questions` json DEFAULT NULL COMMENT 'AI生成的题目列表（JSON数组）',
  `answers` json DEFAULT NULL COMMENT '用户提交的答案（JSON对象）',
  `weak_points` json DEFAULT NULL COMMENT '分析出的薄弱知识点（JSON数组，含point_id/name/weak_score）',
  `weak_subjects` json DEFAULT NULL COMMENT '分析出的薄弱学科列表（JSON数组，含id/name/weak_score）',
  `overall_analysis` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT 'AI总体分析建议',
  `ai_response` json DEFAULT NULL COMMENT 'AI生成的针对性学习路径（JSON）',
  `total_score` tinyint DEFAULT 0 COMMENT '总体得分（0-100）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '状态（0-待答题 1-已完成答题 2-已分析出结果）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '0' COMMENT '删除标志（0-正常 1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_history_id` (`history_id`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE,
  CONSTRAINT `fk_uka_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_uka_history` FOREIGN KEY (`history_id`) REFERENCES `ai_chat_history` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户知识点测评记录表';

SET FOREIGN_KEY_CHECKS = 1;
