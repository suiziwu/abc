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

 Date: 23/06/2026 18:43:15
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for knowledge_point
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_point`;
CREATE TABLE `knowledge_point`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识点ID',
  `subject_id` bigint NOT NULL COMMENT '所属学科ID',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识点名称',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '知识点内容/描述',
  `difficulty` tinyint NULL DEFAULT 1 COMMENT '难度等级（1-简单 2-较易 3-中等 4-较难 5-困难）',
  `importance` tinyint NULL DEFAULT 1 COMMENT '重要程度（1-了解 2-掌握 3-重点 4-核心）',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '知识点类型（如：概念、公式、方法、题型等）',
  `sort_order` int NULL DEFAULT 0 COMMENT '显示排序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志（0-正常 1-已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_subject_id`(`subject_id` ASC) USING BTREE,
  CONSTRAINT `fk_kp_subject` FOREIGN KEY (`subject_id`) REFERENCES `subject` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 33 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of knowledge_point
-- ----------------------------
INSERT INTO `knowledge_point` VALUES (1, 1, 'Java开发环境搭建', 'JDK安装、IDE配置、环境变量设置、第一个HelloWorld程序', 1, 1, '工具', 1, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (2, 1, '基本语法与数据类型', '变量、常量、基本数据类型（int/double/boolean等）、类型转换', 1, 2, '概念', 2, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (3, 1, '流程控制语句', 'if-else、switch、for、while、break、continue', 1, 2, '语法', 3, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (4, 1, '数组', '一维数组、二维数组、Arrays工具类、数组排序', 2, 2, '语法', 4, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (5, 1, '类与对象', '类的定义、对象的创建、构造方法、this关键字', 2, 3, '概念', 5, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (6, 1, '封装与继承', '访问修饰符、extends、super、方法重写', 3, 3, '概念', 6, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (7, 1, '多态与接口', '向上转型、instanceof、抽象类、interface、default方法', 3, 4, '概念', 7, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (8, 1, '内部类与Lambda', '成员内部类、匿名内部类、函数式接口、Lambda表达式', 4, 3, '语法', 8, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (9, 1, '异常处理机制', 'try-catch-finally、throws、自定义异常、try-with-resources', 2, 2, '概念', 9, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (10, 1, '常用类库', 'String/StringBuilder、Math、Date/LocalDateTime、Optional', 2, 2, '工具', 10, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (11, 1, '集合框架', 'List、Set、Map、Collections工具类、泛型、Comparable/Comparator', 3, 4, '框架', 11, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (12, 1, '泛型', '泛型类、泛型方法、类型擦除、通配符 ? extends/super', 4, 3, '语法', 12, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (13, 1, 'I/O流与文件操作', '字节流、字符流、缓冲流、转换流、ObjectStream、NIO', 3, 2, '工具', 13, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (14, 1, '多线程与并发', 'Thread、Runnable、线程池、synchronized、Lock、volatile', 4, 3, '概念', 14, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (15, 1, '网络编程', 'Socket、ServerSocket、URLConnection、HTTPURLConnection', 4, 2, '工具', 15, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (16, 1, 'JDBC与数据库操作', 'DriverManager、PreparedStatement、ResultSet、事务管理', 3, 3, '工具', 16, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (17, 1, 'Maven项目管理', 'pom.xml、依赖管理、生命周期、私服配置', 3, 3, '工具', 17, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (18, 1, 'Spring基础', 'IoC容器、DI依赖注入、Bean生命周期、配置文件', 4, 4, '框架', 18, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (19, 1, 'Spring Boot', '自动配置、Starter、application.yml、嵌入式Tomcat', 5, 4, '框架', 19, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (20, 2, '软件测试基础理论', '测试定义、测试目的、测试原则、测试生命周期、V模型与敏捷测试', 1, 2, '概念', 1, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (21, 2, '软件质量模型', 'ISO/IEC 25010质量模型、功能性/可靠性/易用性/效率等六大特性', 1, 1, '概念', 2, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (22, 2, '测试分类', '单元测试、集成测试、系统测试、验收测试；静态测试与动态测试', 2, 2, '概念', 3, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (23, 2, '测试用例设计方法', '等价类划分、边界值分析、因果图法、正交实验法、场景法', 3, 4, '方法', 4, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (24, 2, '黑盒测试', '功能测试、UI测试、兼容性测试、探索性测试', 2, 3, '方法', 5, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (25, 2, '白盒测试', '语句覆盖、分支覆盖、条件覆盖、路径覆盖、McCabe圈复杂度', 4, 3, '方法', 6, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (26, 2, '接口测试', 'HTTP协议、RESTful API测试、Postman使用、响应断言、参数化', 3, 4, '工具', 7, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (27, 2, '自动化测试基础', 'Selenium WebDriver、元素定位、Page Object模式、测试框架搭建', 4, 3, '工具', 8, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (28, 2, '性能测试', 'JMeter使用、并发/吞吐量/响应时间、性能瓶颈分析、调优建议', 4, 3, '工具', 9, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (29, 2, '安全测试基础', 'SQL注入、XSS、CSRF、OWASP Top 10、渗透测试工具', 4, 2, '方法', 10, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (30, 2, '缺陷管理', 'Bug生命周期、严重程度与优先级、Bug报告编写、Jira使用', 2, 3, '管理', 11, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (31, 2, '测试计划与管理', '测试策略制定、工作量估算、风险分析、测试报告编写', 3, 3, '管理', 12, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');
INSERT INTO `knowledge_point` VALUES (32, 2, '持续集成与DevOps', 'Jenkins流水线、GitLab CI、自动化测试集成、质量门禁', 5, 3, '工具', 13, '0', 'admin', '2026-06-23 09:50:25', '', NULL, '0');

SET FOREIGN_KEY_CHECKS = 1;
