SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for chapter_point
-- ----------------------------
DROP TABLE IF EXISTS `chapter_point`;
CREATE TABLE `chapter_point`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '章节-知识点关联表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;