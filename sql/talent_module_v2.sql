-- ----------------------------
-- 人才培养方案智能优化 模块 v2
-- 1) 从已采集岗位中提取岗位方向（不再读取外部文件夹）
-- 2) 建立模块自有的课程 / 专业 / 培养方案表（不依赖 subject、knowledge_point）
-- 可重复执行
-- ----------------------------
SET NAMES utf8mb4;

-- ---------- 1. 岗位表扩展：行业 / 岗位方向 ----------
SET @has_job_family = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'talent_job_posting' AND COLUMN_NAME = 'job_family');
SET @ddl = IF(@has_job_family = 0,
    'ALTER TABLE talent_job_posting ADD COLUMN job_family varchar(50) DEFAULT '''' COMMENT ''岗位方向''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_industry = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'talent_job_posting' AND COLUMN_NAME = 'industry');
SET @ddl = IF(@has_industry = 0,
    'ALTER TABLE talent_job_posting ADD COLUMN industry varchar(50) DEFAULT ''信息技术'' COMMENT ''所属行业''',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'talent_job_posting' AND INDEX_NAME = 'idx_job_family');
SET @ddl = IF(@has_idx = 0,
    'ALTER TABLE talent_job_posting ADD INDEX idx_job_family (job_family)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 从已入库岗位提取岗位方向 ----------
UPDATE talent_job_posting
SET job_family = CASE
        WHEN keyword IN ('后端', 'Java', 'Python') THEN '后端开发'
        WHEN keyword = '前端' THEN '前端开发'
        WHEN keyword = 'C++工程师' THEN 'C++/嵌入式开发'
        WHEN keyword IN ('算法工程师', 'AI') THEN '算法工程师'
        WHEN keyword = '软件测试' THEN '软件测试'
        ELSE CASE
            WHEN job_title LIKE '%产品%' OR job_title LIKE '%运营%' THEN '产品/运营'
            WHEN job_title LIKE '%测试%' THEN '软件测试'
            WHEN job_title LIKE '%算法%' THEN '算法工程师'
            WHEN job_title LIKE '%前端%' THEN '前端开发'
            WHEN job_title LIKE '%后端%' THEN '后端开发'
            WHEN job_title LIKE '%数据%' THEN '数据开发'
            WHEN job_title LIKE '%设计%' OR job_title LIKE '%视觉%' OR job_title LIKE '%UI%' THEN 'UI/视觉设计'
            WHEN job_title LIKE '%运维%' OR job_title LIKE '%实施%' OR job_title LIKE '%网络%' THEN '运维/DevOps'
            WHEN job_title LIKE '%C++%' OR job_title LIKE '%嵌入式%' THEN 'C++/嵌入式开发'
            ELSE '全栈/AI应用开发'
        END
    END
WHERE job_family = '' OR job_family IS NULL;

-- ---------- 3. 模块自有：课程库 ----------
drop table if exists talent_major_course;
drop table if exists talent_course;
drop table if exists talent_major;

create table talent_course (
  id           bigint(20)   not null auto_increment,
  course_code  varchar(20)  not null                comment '课程编码',
  course_name  varchar(100) not null                comment '课程名称',
  course_level varchar(10)  default '基础'          comment '课程级别：基础/中级/高级',
  course_type  varchar(10)  default '必修'          comment '课程属性：必修/选修',
  course_group varchar(20)  default '专业核心课'     comment '课程分组：专业基础课/专业核心课/方向选修课',
  job_family   varchar(50)  default ''              comment '面向岗位方向',
  credit       decimal(3,1) default 3.0             comment '学分',
  skill_tags   varchar(500) default ''              comment '覆盖技能标签（逗号分隔）',
  description  varchar(300) default ''              comment '课程简介',
  primary key (id),
  unique key uk_course_code (course_code)
) engine=innodb comment='人才培养课程库（内置课程标准）';

create table talent_major (
  id          bigint(20)   not null auto_increment,
  major_code  varchar(20)  not null,
  major_name  varchar(100) not null                 comment '专业名称',
  job_family  varchar(50)  default ''               comment '面向岗位方向',
  graduate    varchar(100) default ''               comment '主要就业岗位',
  remark      varchar(300) default '',
  primary key (id),
  unique key uk_major_code (major_code)
) engine=innodb comment='专业（培养方案）';

create table talent_major_course (
  id        bigint(20) not null auto_increment,
  major_id  bigint(20) not null,
  course_id bigint(20) not null,
  primary key (id),
  unique key uk_major_course (major_id, course_id)
) engine=innodb comment='专业-课程关联';

-- ---------- 4. 专业基础课（全体专业必修） ----------
INSERT INTO talent_course (course_code, course_name, course_level, course_type, course_group, job_family, credit, skill_tags, description) VALUES
('CS101','程序设计基础','基础','必修','专业基础课','',4.0,'C语言,变量,流程控制,数组,函数','面向零基础的编程入门课程'),
('CS102','数据结构与算法','基础','必修','专业基础课','',4.0,'数据结构,算法,复杂度,链表,树,排序','培养抽象建模与算法设计能力'),
('CS103','操作系统原理','基础','必修','专业基础课','',4.0,'操作系统,进程管理,内存管理,Linux','理解操作系统核心机制'),
('CS104','计算机网络','基础','必修','专业基础课','',3.5,'计算机网络,TCP/IP,HTTP,网络协议','掌握网络体系结构与协议栈'),
('CS105','计算机组成原理','基础','必修','专业基础课','',3.5,'计算机组成,CPU,存储系统,指令','理解计算机硬件组成与工作原理'),
('CS106','数据库系统原理','基础','必修','专业基础课','',3.5,'数据库,SQL,事务,索引,范式','掌握关系型数据库理论与设计'),
('CS107','软件工程','基础','必修','专业基础课','',3.0,'软件工程,需求分析,版本控制,Git,项目管理','掌握软件全生命周期管理方法'),
('CS108','设计模式','基础','必修','专业基础课','',3.0,'设计模式,面向对象,架构设计','掌握常用设计模式与架构思想');

-- ---------- 5. 专业与专业核心课 ----------
INSERT INTO talent_major (major_code, major_name, job_family, graduate, remark) VALUES
('SE','软件工程（全栈开发方向）','全栈/AI应用开发','全栈开发工程师、AI应用开发工程师','面向AI全栈与全栈开发岗位'),
('CS','计算机科学与技术（后端开发方向）','后端开发','Java开发工程师、后端开发工程师','面向服务端开发岗位'),
('FE','软件技术（前端开发方向）','前端开发','前端开发工程师、Web开发工程师','面向Web前端与跨端开发岗位'),
('AI','人工智能技术应用（算法方向）','算法工程师','算法工程师、AI应用工程师','面向算法与大模型应用岗位'),
('BD','大数据技术（数据开发方向）','数据开发','数据开发工程师、数据分析师','面向数据采集、治理与分析岗位'),
('ST','软件与信息服务（测试方向）','软件测试','软件测试工程师、测试开发工程师','面向质量保障与测试开发岗位'),
('NET','计算机网络技术（运维方向）','运维/DevOps','运维工程师、云平台工程师','面向系统与云平台运维岗位'),
('EE','嵌入式技术应用（C++方向）','C++/嵌入式开发','C++开发工程师、嵌入式开发工程师','面向底层与嵌入式开发岗位');

INSERT INTO talent_course (course_code, course_name, course_level, course_type, course_group, job_family, credit, skill_tags, description) VALUES
-- 软件工程（全栈）
('SE201','Java程序设计','中级','必修','专业核心课','全栈/AI应用开发',4.0,'Java,面向对象,RESTful','面向对象程序设计与Java语言基础'),
('SE202','Web前端开发','中级','必修','专业核心课','全栈/AI应用开发',4.0,'JavaScript,TypeScript,HTML,CSS','前后端一体的前端基础能力'),
('SE203','Spring Boot企业级开发','高级','必修','专业核心课','全栈/AI应用开发',4.0,'Spring Boot,Spring Cloud,微服务,MySQL,RESTful','企业级服务端开发与接口设计'),
('SE204','全栈项目实战','高级','必修','专业核心课','全栈/AI应用开发',4.0,'React,Node.js,Next.js,全栈','以真实项目贯穿前后端与部署'),
('SE205','大模型应用开发','高级','选修','方向选修课','全栈/AI应用开发',3.0,'大模型,LLM,Prompt工程,Agent,RAG,LangChain','大模型API接入与智能体应用开发'),
('SE206','云原生与容器技术','高级','选修','方向选修课','全栈/AI应用开发',3.0,'Docker,K8s,Kubernetes,容器化,CI/CD','容器化部署与持续集成'),
('SE207','Redis缓存与性能优化','中级','选修','方向选修课','全栈/AI应用开发',3.0,'Redis,缓存,性能优化','缓存设计与性能调优'),
-- 计算机科学与技术（后端）
('CS201','Java程序设计','中级','必修','专业核心课','后端开发',4.0,'Java,面向对象,RESTful','面向对象程序设计与Java语言基础'),
('CS202','JVM与并发编程','高级','必修','专业核心课','后端开发',4.0,'Java,高并发,性能优化','JVM原理、多线程与并发编程'),
('CS203','分布式系统与微服务','高级','必修','专业核心课','后端开发',4.0,'微服务,Spring Cloud,分布式,消息队列,高可用','服务拆分、治理与高可用设计'),
('CS204','MySQL数据库开发','中级','必修','专业核心课','后端开发',3.5,'MySQL,PostgreSQL,索引,事务','关系型数据库设计与优化'),
('CS205','Go语言开发','中级','选修','方向选修课','后端开发',3.0,'Go,Gin,微服务','Go语言与高并发服务开发'),
('CS206','Python程序设计','中级','选修','方向选修课','后端开发',3.0,'Python,Django,Flask,FastAPI','Python后端与脚本开发'),
('CS207','消息队列实战','高级','选修','方向选修课','后端开发',3.0,'Kafka,RabbitMQ,MQ,消息队列','异步解耦与消息中间件实践'),
-- 软件技术（前端）
('FE201','Web前端基础','中级','必修','专业核心课','前端开发',4.0,'JavaScript,TypeScript,HTML,CSS','前端基础语法与页面结构'),
('FE202','Vue框架应用','中级','必修','专业核心课','前端开发',3.5,'Vue,JavaScript,组件化','Vue工程化开发与组件设计'),
('FE203','React框架应用','中级','必修','专业核心课','前端开发',3.5,'React,Next.js,JavaScript','React生态与Hooks开发'),
('FE204','前端工程化','高级','选修','方向选修课','前端开发',3.0,'TypeScript,Node.js,单元测试','构建、打包与工程规范'),
('FE205','跨端与小程序开发','中级','选修','方向选修课','前端开发',3.0,'小程序,React,UniApp','多端适配与小程序开发'),
('FE206','数据可视化开发','高级','选修','方向选修课','前端开发',3.0,'JavaScript,Node.js,监控','可视化图表与大屏实现'),
-- 人工智能（算法）
('AI201','Python程序设计','中级','必修','专业核心课','算法工程师',3.5,'Python,Flask,FastAPI','算法工程化基础'),
('AI202','机器学习基础','中级','必修','专业核心课','算法工程师',4.0,'PyTorch,模型部署,算法','经典机器学习算法与实践'),
('AI203','深度学习','高级','必修','专业核心课','算法工程师',4.0,'PyTorch,TensorFlow,模型部署,微调','神经网络与模型训练'),
('AI204','自然语言处理','高级','选修','方向选修课','算法工程师',3.0,'LLM,GPT,微调,NLP','文本建模与大模型微调'),
('AI205','计算机视觉','高级','选修','方向选修课','算法工程师',3.0,'PyTorch,模型部署,多模态','图像识别与视觉模型'),
('AI206','大模型应用与微调','高级','必修','专业核心课','算法工程师',4.0,'大模型,LLM,RAG,Agent,LangChain,LangGraph,Prompt工程,微调','大模型应用链路与检索增强'),
-- 大数据（数据开发）
('BD201','Python数据分析','中级','必修','专业核心课','数据开发',3.5,'Python,Pandas,数据分析','数据处理与分析基础'),
('BD202','大数据平台技术','高级','必修','专业核心课','数据开发',4.0,'分布式,监控,高可用','Spark/Flink数据处理与平台运维'),
('BD203','数据采集与清洗','中级','必修','专业核心课','数据开发',3.5,'Python,MySQL,数据采集,数据清洗','爬取、清洗与入库全流程'),
('BD204','SQL数据分析','中级','选修','方向选修课','数据开发',3.0,'MySQL,PostgreSQL,MongoDB','SQL查询与数据建模'),
('BD205','数据可视化','中级','选修','方向选修课','数据开发',3.0,'Python,MySQL,数据可视化','报表与可视化呈现'),
-- 软件与信息服务（测试）
('ST201','软件测试技术','中级','必修','专业核心课','软件测试',3.5,'单元测试,测试用例,缺陷管理','测试流程与用例设计'),
('ST202','自动化测试','中级','必修','专业核心课','软件测试',3.5,'Python,单元测试,CI/CD','自动化测试框架与脚本'),
('ST203','接口与性能测试','高级','选修','方向选修课','软件测试',3.0,'RESTful,监控,性能优化','接口测试与性能压测'),
('ST204','测试开发与CI','高级','选修','方向选修课','软件测试',3.0,'CI/CD,单元测试,MySQL','测试平台与持续集成'),
-- 计算机网络（运维）
('NET201','Linux系统管理','中级','必修','专业核心课','运维/DevOps',3.5,'Docker,监控,Linux','Linux运维与系统管理'),
('NET202','网络构建与维护','中级','必修','专业核心课','运维/DevOps',3.5,'高可用,监控,网络协议','网络规划与故障排查'),
('NET203','云平台与容器运维','高级','选修','方向选修课','运维/DevOps',3.0,'Docker,K8s,Kubernetes,容器化,CI/CD,高可用','云平台与容器编排'),
('NET204','自动化运维','高级','选修','方向选修课','运维/DevOps',3.0,'Python,监控,容器化','自动化脚本与监控告警'),
-- 嵌入式（C++）
('EE201','C++程序设计','中级','必修','专业核心课','C++/嵌入式开发',4.0,'C++,面向对象,STL','C++语言与系统级编程'),
('EE202','嵌入式系统开发','高级','必修','专业核心课','C++/嵌入式开发',4.0,'C++,嵌入式,模型部署','嵌入式系统与实时操作系统'),
('EE203','硬件接口与驱动','高级','选修','方向选修课','C++/嵌入式开发',3.0,'C++,驱动开发,串口','外设驱动与硬件接口');

-- ---------- 6. 培养方案关联 ----------
-- 所有专业必修 8 门专业基础课
INSERT INTO talent_major_course (major_id, course_id)
SELECT m.id, c.id FROM talent_major m JOIN talent_course c ON c.course_group = '专业基础课';

-- 各专业核心课与方向选修课
INSERT INTO talent_major_course (major_id, course_id)
SELECT m.id, c.id FROM talent_major m JOIN talent_course c
WHERE c.course_group <> '专业基础课' AND c.job_family = m.job_family;

-- ---------- 7. 校验 ----------
SELECT job_family, COUNT(*) AS jobs FROM talent_job_posting GROUP BY job_family ORDER BY jobs DESC;
SELECT COUNT(*) AS courses FROM talent_course;
SELECT COUNT(*) AS majors FROM talent_major;
SELECT COUNT(*) AS major_course_links FROM talent_major_course;
