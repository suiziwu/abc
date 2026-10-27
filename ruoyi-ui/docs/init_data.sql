-- =============================================
-- 学科拓扑图 - 初始示例数据
-- =============================================

-- 若还未添加 video 字段，先执行以下 ALTER TABLE
-- ALTER TABLE `knowledge_point`
--   ADD COLUMN `video` varchar(500) DEFAULT NULL COMMENT '视频路径';

-- ----------------------------
-- 1. 插入学科数据
-- ----------------------------
INSERT INTO `subject` (`id`, `name`, `code`, `description`, `icon`, `sort_order`, `status`, `create_by`, `create_time`, `del_flag`)
VALUES
(1, 'Java编程', 'JAVA', 'Java面向对象编程语言，涵盖从基础语法到企业级框架的完整知识体系', 'java-icon', 1, '0', 'admin', NOW(), '0'),
(2, '软件测试', 'TEST', '软件质量保障体系，包含黑盒/白盒测试、自动化测试、性能测试等', 'test-icon', 2, '0', 'admin', NOW(), '0');


-- ----------------------------
-- 2. 插入知识点数据
-- ----------------------------

-- ===== Java 学科知识点 =====
INSERT INTO `knowledge_point` (`id`, `subject_id`, `name`, `content`, `difficulty`, `importance`, `type`, `sort_order`, `status`, `create_by`, `create_time`, `del_flag`)
VALUES
-- Java基础模块
(1,  1, 'Java开发环境搭建',  'JDK安装、IDE配置、环境变量设置、第一个HelloWorld程序',              1, 1, '工具',    1, '0', 'admin', NOW(), '0'),
(2,  1, '基本语法与数据类型', '变量、常量、基本数据类型（int/double/boolean等）、类型转换',      1, 2, '概念',    2, '0', 'admin', NOW(), '0'),
(3,  1, '流程控制语句',      'if-else、switch、for、while、break、continue',                    1, 2, '语法',    3, '0', 'admin', NOW(), '0'),
(4,  1, '数组',              '一维数组、二维数组、Arrays工具类、数组排序',                       2, 2, '语法',    4, '0', 'admin', NOW(), '0'),

-- 面向对象
(5,  1, '类与对象',          '类的定义、对象的创建、构造方法、this关键字',                       2, 3, '概念',    5, '0', 'admin', NOW(), '0'),
(6,  1, '封装与继承',        '访问修饰符、extends、super、方法重写',                             3, 3, '概念',    6, '0', 'admin', NOW(), '0'),
(7,  1, '多态与接口',        '向上转型、instanceof、抽象类、interface、default方法',             3, 4, '概念',    7, '0', 'admin', NOW(), '0'),
(8,  1, '内部类与Lambda',    '成员内部类、匿名内部类、函数式接口、Lambda表达式',                4, 3, '语法',    8, '0', 'admin', NOW(), '0'),

-- 核心API
(9,  1, '异常处理机制',      'try-catch-finally、throws、自定义异常、try-with-resources',        2, 2, '概念',    9, '0', 'admin', NOW(), '0'),
(10, 1, '常用类库',          'String/StringBuilder、Math、Date/LocalDateTime、Optional',         2, 2, '工具',    10, '0', 'admin', NOW(), '0'),
(11, 1, '集合框架',          'List、Set、Map、Collections工具类、泛型、Comparable/Comparator',   3, 4, '框架',    11, '0', 'admin', NOW(), '0'),
(12, 1, '泛型',              '泛型类、泛型方法、类型擦除、通配符 ? extends/super',              4, 3, '语法',    12, '0', 'admin', NOW(), '0'),

-- 进阶技术
(13, 1, 'I/O流与文件操作',   '字节流、字符流、缓冲流、转换流、ObjectStream、NIO',               3, 2, '工具',    13, '0', 'admin', NOW(), '0'),
(14, 1, '多线程与并发',      'Thread、Runnable、线程池、synchronized、Lock、volatile',           4, 3, '概念',    14, '0', 'admin', NOW(), '0'),
(15, 1, '网络编程',          'Socket、ServerSocket、URLConnection、HTTPURLConnection',           4, 2, '工具',    15, '0', 'admin', NOW(), '0'),
(16, 1, 'JDBC与数据库操作',  'DriverManager、PreparedStatement、ResultSet、事务管理',            3, 3, '工具',    16, '0', 'admin', NOW(), '0'),

-- 框架生态
(17, 1, 'Maven项目管理',     'pom.xml、依赖管理、生命周期、私服配置',                           3, 3, '工具',    17, '0', 'admin', NOW(), '0'),
(18, 1, 'Spring基础',        'IoC容器、DI依赖注入、Bean生命周期、配置文件',                     4, 4, '框架',    18, '0', 'admin', NOW(), '0'),
(19, 1, 'Spring Boot',       '自动配置、Starter、application.yml、嵌入式Tomcat',                5, 4, '框架',    19, '0', 'admin', NOW(), '0');


-- ===== 软件测试 学科知识点 =====
INSERT INTO `knowledge_point` (`id`, `subject_id`, `name`, `content`, `difficulty`, `importance`, `type`, `sort_order`, `status`, `create_by`, `create_time`, `del_flag`)
VALUES
-- 测试基础
(20, 2, '软件测试基础理论',   '测试定义、测试目的、测试原则、测试生命周期、V模型与敏捷测试',        1, 2, '概念', 1, '0', 'admin', NOW(), '0'),
(21, 2, '软件质量模型',       'ISO/IEC 25010质量模型、功能性/可靠性/易用性/效率等六大特性',         1, 1, '概念', 2, '0', 'admin', NOW(), '0'),
(22, 2, '测试分类',           '单元测试、集成测试、系统测试、验收测试；静态测试与动态测试',          2, 2, '概念', 3, '0', 'admin', NOW(), '0'),

-- 测试设计
(23, 2, '测试用例设计方法',   '等价类划分、边界值分析、因果图法、正交实验法、场景法',              3, 4, '方法', 4, '0', 'admin', NOW(), '0'),
(24, 2, '黑盒测试',           '功能测试、UI测试、兼容性测试、探索性测试',                          2, 3, '方法', 5, '0', 'admin', NOW(), '0'),
(25, 2, '白盒测试',           '语句覆盖、分支覆盖、条件覆盖、路径覆盖、McCabe圈复杂度',            4, 3, '方法', 6, '0', 'admin', NOW(), '0'),

-- 专项测试
(26, 2, '接口测试',           'HTTP协议、RESTful API测试、Postman使用、响应断言、参数化',          3, 4, '工具', 7, '0', 'admin', NOW(), '0'),
(27, 2, '自动化测试基础',     'Selenium WebDriver、元素定位、Page Object模式、测试框架搭建',       4, 3, '工具', 8, '0', 'admin', NOW(), '0'),
(28, 2, '性能测试',           'JMeter使用、并发/吞吐量/响应时间、性能瓶颈分析、调优建议',          4, 3, '工具', 9, '0', 'admin', NOW(), '0'),
(29, 2, '安全测试基础',       'SQL注入、XSS、CSRF、OWASP Top 10、渗透测试工具',                   4, 2, '方法', 10, '0', 'admin', NOW(), '0'),

-- 测试管理
(30, 2, '缺陷管理',           'Bug生命周期、严重程度与优先级、Bug报告编写、Jira使用',             2, 3, '管理', 11, '0', 'admin', NOW(), '0'),
(31, 2, '测试计划与管理',     '测试策略制定、工作量估算、风险分析、测试报告编写',                 3, 3, '管理', 12, '0', 'admin', NOW(), '0'),
(32, 2, '持续集成与DevOps',   'Jenkins流水线、GitLab CI、自动化测试集成、质量门禁',               5, 3, '工具', 13, '0', 'admin', NOW(), '0');


-- ----------------------------
-- 3. 插入知识点关联（拓扑图边）
-- ----------------------------

-- ===== Java 知识点关联 =====
INSERT INTO `knowledge_point_relation` (`source_id`, `target_id`, `relation_type`, `description`, `weight`)
VALUES
-- 基础语法系列（前置依赖链）
(1,  2,  'prerequisite', '先搭好环境才能写代码',               2),
(2,  3,  'prerequisite', '掌握数据类型后才能学习流程控制',     2),
(3,  4,  'prerequisite', '流程控制是数组遍历的基础',           1),
(2,  5,  'prerequisite', '数据类型是定义类中属性的基础',       2),
(4,  5,  'prerequisite', '数组作为对象，需要先理解类与对象',   1),

-- 面向对象系列
(5,  6,  'prerequisite', '先理解类与对象才能学习继承',         3),
(6,  7,  'prerequisite', '继承是多态的基础',                   3),
(7,  8,  'prerequisite', '理解多态后才能更好理解Lambda',       1),

-- 核心API依赖
(5,  9,  'prerequisite', '面向对象知识对理解异常类层次很重要', 1),
(2,  10, 'prerequisite', '基本语法是使用常用类库的前提',       1),
(7,  11, 'prerequisite', '接口知识对理解集合框架至关重要',     3),
(7,  12, 'prerequisite', '多态与接口是泛型的基础',             2),
(11, 12, 'related',      '集合框架大量使用泛型，互相强化',     2),

-- 进阶技术
(9,  13, 'related',      '异常处理在I/O操作中非常重要',        1),
(8,  14, 'related',      'Lambda简化了多线程中的匿名内部类',   2),
(10, 14, 'related',      '常用类库中的并发包为多线程提供支持', 1),
(14, 15, 'prerequisite', '多线程是网络编程中并发处理的基础',   2),
(10, 16, 'prerequisite', '字符串处理等基础是SQL拼接的前提',    1),

-- 框架生态
(11, 17, 'related',      'Maven管理集合框架等依赖',            1),
(7,  18, 'prerequisite', '理解接口和DI思想是学习Spring的前提',  3),
(18, 19, 'prerequisite', 'Spring是Spring Boot的核心基础',      3),
(16, 19, 'related',      'Spring Boot集成JDBC/MyBatis',        1),
(17, 19, 'related',      'Maven是Spring Boot项目的构建工具',   2);


-- ===== 软件测试 知识点关联 =====
INSERT INTO `knowledge_point_relation` (`source_id`, `target_id`, `relation_type`, `description`, `weight`)
VALUES
-- 基础系列
(20, 21, 'related',      '质量模型是测试理论的重要补充',       1),
(20, 22, 'prerequisite', '先理解测试基础才能区分测试分类',     2),
(20, 23, 'prerequisite', '测试理论是设计测试用例的前提',       3),
(20, 30, 'prerequisite', '理解测试流程才能做好缺陷管理',       1),

-- 测试设计
(23, 24, 'prerequisite', '等价类/边界值是黑盒测试的核心方法',  3),
(23, 25, 'prerequisite', '路径覆盖等方法属于白盒测试',         2),
(22, 24, 'related',      '系统测试阶段大量使用黑盒测试',       2),
(22, 25, 'related',      '单元测试阶段大量使用白盒测试',       2),

-- 专项测试
(24, 26, 'extension',    '黑盒测试方法论可延伸到接口测试',     2),
(24, 27, 'extension',    '黑盒测试是UI自动化的基础',           2),
(25, 27, 'related',      '白盒测试知识有助于理解自动化框架',   1),
(22, 28, 'prerequisite', '理解测试分类后才能定位性能测试',     1),
(24, 28, 'related',      '性能测试中的功能正确性验证',         1),
(26, 28, 'extension',    '接口测试经验有助于性能脚本编写',     2),
(24, 29, 'extension',    '安全测试可看作黑盒测试的专项领域',   1),

-- 测试管理
(30, 31, 'prerequisite', '缺陷管理是测试计划的重要组成部分',   2),
(22, 31, 'prerequisite', '了解测试分类才能制定合理的测试策略', 1),
(27, 32, 'extension',    '自动化测试脚本接入CI/CD流水线',      2),
(28, 32, 'extension',    '性能测试可集成到CI/CD质量门禁',      1),
(31, 32, 'related',      '测试计划指导CI/CD中的测试策略',      1);
