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

 Date: 23/06/2026 18:43:20
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
) ENGINE = InnoDB AUTO_INCREMENT = 44 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识点关联表（拓扑图边）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of knowledge_point_relation
-- ----------------------------
INSERT INTO `knowledge_point_relation` VALUES (1, 1, 2, 'prerequisite', '先搭好环境才能写代码', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (2, 2, 3, 'prerequisite', '掌握数据类型后才能学习流程控制', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (3, 3, 4, 'prerequisite', '流程控制是数组遍历的基础', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (4, 2, 5, 'prerequisite', '数据类型是定义类中属性的基础', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (5, 4, 5, 'prerequisite', '数组作为对象，需要先理解类与对象', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (6, 5, 6, 'prerequisite', '先理解类与对象才能学习继承', 3, NULL);
INSERT INTO `knowledge_point_relation` VALUES (7, 6, 7, 'prerequisite', '继承是多态的基础', 3, NULL);
INSERT INTO `knowledge_point_relation` VALUES (8, 7, 8, 'prerequisite', '理解多态后才能更好理解Lambda', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (9, 5, 9, 'prerequisite', '面向对象知识对理解异常类层次很重要', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (10, 2, 10, 'prerequisite', '基本语法是使用常用类库的前提', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (11, 7, 11, 'prerequisite', '接口知识对理解集合框架至关重要', 3, NULL);
INSERT INTO `knowledge_point_relation` VALUES (12, 7, 12, 'prerequisite', '多态与接口是泛型的基础', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (13, 11, 12, 'related', '集合框架大量使用泛型，互相强化', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (14, 9, 13, 'related', '异常处理在I/O操作中非常重要', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (15, 8, 14, 'related', 'Lambda简化了多线程中的匿名内部类', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (16, 10, 14, 'related', '常用类库中的并发包为多线程提供支持', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (17, 14, 15, 'prerequisite', '多线程是网络编程中并发处理的基础', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (18, 10, 16, 'prerequisite', '字符串处理等基础是SQL拼接的前提', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (19, 11, 17, 'related', 'Maven管理集合框架等依赖', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (20, 7, 18, 'prerequisite', '理解接口和DI思想是学习Spring的前提', 3, NULL);
INSERT INTO `knowledge_point_relation` VALUES (21, 18, 19, 'prerequisite', 'Spring是Spring Boot的核心基础', 3, NULL);
INSERT INTO `knowledge_point_relation` VALUES (22, 16, 19, 'related', 'Spring Boot集成JDBC/MyBatis', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (23, 17, 19, 'related', 'Maven是Spring Boot项目的构建工具', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (24, 20, 21, 'related', '质量模型是测试理论的重要补充', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (25, 20, 22, 'prerequisite', '先理解测试基础才能区分测试分类', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (26, 20, 23, 'prerequisite', '测试理论是设计测试用例的前提', 3, NULL);
INSERT INTO `knowledge_point_relation` VALUES (27, 20, 30, 'prerequisite', '理解测试流程才能做好缺陷管理', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (28, 23, 24, 'prerequisite', '等价类/边界值是黑盒测试的核心方法', 3, NULL);
INSERT INTO `knowledge_point_relation` VALUES (29, 23, 25, 'prerequisite', '路径覆盖等方法属于白盒测试', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (30, 22, 24, 'related', '系统测试阶段大量使用黑盒测试', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (31, 22, 25, 'related', '单元测试阶段大量使用白盒测试', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (32, 24, 26, 'extension', '黑盒测试方法论可延伸到接口测试', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (33, 24, 27, 'extension', '黑盒测试是UI自动化的基础', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (34, 25, 27, 'related', '白盒测试知识有助于理解自动化框架', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (35, 22, 28, 'prerequisite', '理解测试分类后才能定位性能测试', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (36, 24, 28, 'related', '性能测试中的功能正确性验证', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (37, 26, 28, 'extension', '接口测试经验有助于性能脚本编写', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (38, 24, 29, 'extension', '安全测试可看作黑盒测试的专项领域', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (39, 30, 31, 'prerequisite', '缺陷管理是测试计划的重要组成部分', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (40, 22, 31, 'prerequisite', '了解测试分类才能制定合理的测试策略', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (41, 27, 32, 'extension', '自动化测试脚本接入CI/CD流水线', 2, NULL);
INSERT INTO `knowledge_point_relation` VALUES (42, 28, 32, 'extension', '性能测试可集成到CI/CD质量门禁', 1, NULL);
INSERT INTO `knowledge_point_relation` VALUES (43, 31, 32, 'related', '测试计划指导CI/CD中的测试策略', 1, NULL);

SET FOREIGN_KEY_CHECKS = 1;
