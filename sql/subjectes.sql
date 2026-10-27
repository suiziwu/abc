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

 Date: 03/07/2026 16:12:54
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for subject
-- ----------------------------
DROP TABLE IF EXISTS `subject`;
CREATE TABLE `subject`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '学科ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '学科名称',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '学科编码',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '学科描述',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '图标',
  `sort_order` int NULL DEFAULT 0 COMMENT '显示排序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '0' COMMENT '删除标志（0-正常 1-已删除）',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 92 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学科表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of subject
-- ----------------------------
INSERT INTO `subject` VALUES (1, '数据结构与算法', 'sub_algorithm', '计算机专业核心基础，涵盖线性表、树、图、排序、查找及算法复杂度分析', NULL, 1, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (2, '操作系统原理', 'sub_os', '进程管理、内存管理、文件系统、I/O系统及并发编程基础', NULL, 2, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (3, '计算机网络', 'sub_network', 'OSI七层模型、TCP/IP协议栈、HTTP/HTTPS、路由交换及网络安全基础', NULL, 3, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (4, '计算机组成原理', 'sub_computer_org', 'CPU指令系统、数据表示、存储体系、总线及输入输出系统', NULL, 4, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (5, 'Java编程语言', 'sub_java', '面向对象编程、JVM内存模型、集合框架、多线程并发、IO/NIO及新特性', NULL, 5, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (6, 'Python编程语言', 'sub_python', '简洁高效的脚本语言，涵盖基础语法、高级特性、数据分析及自动化脚本', NULL, 6, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (7, 'Go编程语言', 'sub_golang', '高并发、高性能的编译型语言，擅长微服务与云原生开发', NULL, 7, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (8, 'Spring Boot框架', 'sub_springboot', 'Spring生态核心框架，包含自动配置、Starter依赖、Actuator及微服务基础', NULL, 8, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (9, 'Vue.js前端框架', 'sub_vue', '渐进式JavaScript框架，组件化开发、响应式数据绑定、Vue Router及状态管理', NULL, 9, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (10, 'MySQL数据库', 'sub_mysql', '主流关系型数据库，涵盖SQL优化、索引原理、事务隔离级别、主从复制及分库分表', NULL, 10, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (11, 'Redis缓存数据库', 'sub_redis', '高性能内存数据结构存储，支持持久化、主从哨兵、集群模式及缓存穿透/雪崩解决方案', NULL, 11, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (12, '软件测试技术', 'sub_testing', '涵盖白盒/黑盒测试、单元测试(JUnit/TestNG)、集成测试、自动化测试及性能测试', NULL, 12, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (13, '设计模式', 'sub_design_pattern', '面向对象设计原则，包含创建型、结构型和行为型共23种经典设计模式', NULL, 13, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (14, 'Linux系统运维', 'sub_linux', 'Linux基础命令、Shell脚本编程、系统性能调优、权限管理及服务部署', NULL, 14, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (15, 'Git版本控制', 'sub_git', '分布式版本控制系统，涵盖工作区/暂存区/仓库、分支管理、冲突解决及CI/CD流水线集成', NULL, 15, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (16, 'Docker容器技术', 'sub_docker', '应用容器化引擎，镜像构建、容器生命周期管理、Docker Compose及容器网络', NULL, 16, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (17, '微服务架构设计', 'sub_microservice', '微服务拆分原则、服务注册发现、配置中心、网关路由、熔断降级及分布式事务', NULL, 17, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (18, '消息中间件', 'sub_mq', '异步通信与系统解耦，涵盖RabbitMQ、Kafka等主流消息队列的使用及高级特性', NULL, 18, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (19, '网络安全基础', 'sub_security', '加密解密算法、数字签名、SSL/TLS、Web安全漏洞（XSS/CSRF/SQL注入）及防火墙策略', NULL, 19, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');
INSERT INTO `subject` VALUES (20, '大数据技术基础', 'sub_bigdata', '大数据生态入门，涵盖Hadoop HDFS、MapReduce、Spark核心及数据湖概念', NULL, 20, '0', 'System', '2026-07-03 16:09:54', 'System', '2026-07-03 16:09:54', '0');

SET FOREIGN_KEY_CHECKS = 1;
