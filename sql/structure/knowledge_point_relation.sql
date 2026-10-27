/*
 Navicat Premium Dump SQL

 Source Server         : MySQL
 Source Server Type    : MySQL
 Source Server Version : 80040 (8.0.40)
 Source Host           : localhost:3306
 Source Schema         : knowledge

 Target Server Type    : MySQL
 Target Server Version : 80040 (8.0.40)
 File Encoding         : 65001

 Date: 03/07/2026 16:00:08
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for knowledge_point_relation
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_point_relation`;
CREATE TABLE `knowledge_point_relation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `source_id` bigint NOT NULL COMMENT '源知识点ID',
  `target_id` bigint NOT NULL COMMENT '目标知识点ID',
  `relation_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'prerequisite' COMMENT '关联类型（prerequisite-前置依赖 related-相关参考 extension-扩展延伸）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '关联描述',
  `weight` int NULL DEFAULT 1 COMMENT '关联权重/强度（用于拓扑图连线粗细）',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_source_target`(`source_id` ASC, `target_id` ASC) USING BTREE,
  INDEX `idx_source_id`(`source_id` ASC) USING BTREE,
  INDEX `idx_target_id`(`target_id` ASC) USING BTREE,
  CONSTRAINT `fk_kpr_source` FOREIGN KEY (`source_id`) REFERENCES `knowledge_point` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_kpr_target` FOREIGN KEY (`target_id`) REFERENCES `knowledge_point` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 45 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识点关联表（拓扑图边）' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
