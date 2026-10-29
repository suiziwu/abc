-- =============================================================
-- 计算机网络课程章节重构（按视频目录重建，可重复执行）
--
-- 依据：C:\xinxinxin\视频\7-计算机网络课程视频讲解 下的 6 个章节目录
--   第一章 网络体系结构（11 个视频）
--   第二章 物理层（10 个视频）
--   第三章 数据链路层（20 个视频）
--   第四章 网络层（19 个视频）
--   第五章 传输层（8 个视频）
--   第六章 应用层（6 个视频）
--
-- 学科：94 计算机网络原理与实践（完整图谱）
-- 目标：把「知识架构管理 → 课程章节」统一为上述 6 章，
--       章号固定为 第1章 ~ 第6章，排序 10/20/30/40/50/60。
--
-- 旧章节的知识点关联 / 逐字稿 / 知识库分块会按“章”语义迁移到新章节，
-- 避免删除章节时级联丢失教材知识点与 ASR 逐字稿。
--
-- 执行：Get-Content sql\restructure_cn_course_chapters.sql -Raw |
--         mysql -u root -p --default-character-set=utf8mb4 -D personnel_information
-- =============================================================
SET NAMES utf8mb4;
SET @subject_id = 94;
SET @course_title = '计算机网络课程视频讲解';

-- ---------- 1. 备份旧章节与关联（回滚参照） ----------
DROP TABLE IF EXISTS `course_chapter_backup_20261028`;
CREATE TABLE `course_chapter_backup_20261028` AS
SELECT * FROM `course_chapter` WHERE `subject_id` = @subject_id;

DROP TABLE IF EXISTS `chapter_point_backup_20261028`;
CREATE TABLE `chapter_point_backup_20261028` AS
SELECT cp.* FROM `chapter_point` cp
JOIN `course_chapter` cc ON cc.id = cp.chapter_id
WHERE cc.subject_id = @subject_id;

-- ---------- 2. 补齐缺失章节（已存在则跳过） ----------
INSERT INTO `course_chapter`
  (`subject_id`, `chapter_no`, `title`, `course_title`, `description`,
   `sort_order`, `status`, `create_by`, `create_time`, `del_flag`)
SELECT @subject_id, t.no, t.name, @course_title, t.descr, t.ord, '0', 'admin', sysdate(), '0'
FROM (
  SELECT '第1章' AS no, '网络体系结构' AS name, 10 AS ord,
         '对应「第一章 网络体系结构」目录（11 个视频）：1.1.0 引言、1.1.1 概念和功能、1.1.2 组成和分类、1.1.3 标准化工作及相关组织、1.1.4 性能指标（1）、1.1.5 性能指标（2）、1.2.1 分层结构、协议、接口和服务、1.2.2 OSI参考模型（1）、1.2.3 OSI参考模型（2）、1.2.4 TCPIP参考模型、1.2.5 第一章总结。' AS descr
  UNION ALL SELECT '第2章', '物理层', 20,
         '对应「第二章 物理层」目录（10 个视频：第二章 物理层01~10）：物理层接口特性、数据通信术语与传输方式、码元与带宽、奈氏准则与香农定理、编码与调制、数据交换方式、传输介质、物理层设备与本章总结。'
  UNION ALL SELECT '第3章', '数据链路层', 30,
         '对应「第三章 数据链路层」目录（20 个视频：第三章 数据链路层1~20）：概述、封装成帧、差错控制与编码、流量控制与可靠传输（停等/GBN/SR）、介质访问控制（ALOHA/CSMA/CSMA-CD/CSMA-CA/令牌）、局域网与以太网、无线局域网、广域网 PPP/HDLC、链路层扩展与本章总结。'
  UNION ALL SELECT '第4章', '网络层', 40,
         '对应「第四章 网络层」目录（19 个视频：第四章 网络层1~19）：网络层功能、路由算法、IPv4 数据报与分片、分类地址/子网划分/CIDR、NAT、ARP、DHCP、ICMP、IPv6、RIP/OSPF/BGP、IP 组播、移动 IP、路由器与本章总结。'
  UNION ALL SELECT '第5章', '传输层', 50,
         '对应「第五章 传输层」目录（8 个视频：第五章 传输层1~8）：传输层功能与端口复用分用、UDP 概述与校验、TCP 特点与首部、TCP 连接管理、可靠传输、流量控制、拥塞控制与本章总结。'
  UNION ALL SELECT '第6章', '应用层', 60,
         '对应「第六章 应用层」目录（6 个视频：第六章 应用层1~6）：应用层概述与 C/S、P2P 模型、DNS、FTP、电子邮件（SMTP/POP3/IMAP）、万维网与 HTTP、本章总结。'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM `course_chapter` c
  WHERE c.`subject_id` = @subject_id AND c.`chapter_no` = t.no AND c.`del_flag` = '0');

-- ---------- 3. 统一标题 / 描述 / 排序（覆盖历史命名） ----------
UPDATE `course_chapter` SET `title` = '网络体系结构', `course_title` = @course_title, `sort_order` = 10,
  `description` = '对应「第一章 网络体系结构」目录（11 个视频）：1.1.0 引言、1.1.1 概念和功能、1.1.2 组成和分类、1.1.3 标准化工作及相关组织、1.1.4 性能指标（1）、1.1.5 性能指标（2）、1.2.1 分层结构、协议、接口和服务、1.2.2 OSI参考模型（1）、1.2.3 OSI参考模型（2）、1.2.4 TCPIP参考模型、1.2.5 第一章总结。'
WHERE `subject_id` = @subject_id AND `chapter_no` = '第1章' AND `del_flag` = '0';

UPDATE `course_chapter` SET `title` = '物理层', `course_title` = @course_title, `sort_order` = 20,
  `description` = '对应「第二章 物理层」目录（10 个视频：第二章 物理层01~10）：物理层接口特性、数据通信术语与传输方式、码元与带宽、奈氏准则与香农定理、编码与调制、数据交换方式、传输介质、物理层设备与本章总结。'
WHERE `subject_id` = @subject_id AND `chapter_no` = '第2章' AND `del_flag` = '0';

UPDATE `course_chapter` SET `title` = '数据链路层', `course_title` = @course_title, `sort_order` = 30,
  `description` = '对应「第三章 数据链路层」目录（20 个视频：第三章 数据链路层1~20）：概述、封装成帧、差错控制与编码、流量控制与可靠传输（停等/GBN/SR）、介质访问控制（ALOHA/CSMA/CSMA-CD/CSMA-CA/令牌）、局域网与以太网、无线局域网、广域网 PPP/HDLC、链路层扩展与本章总结。'
WHERE `subject_id` = @subject_id AND `chapter_no` = '第3章' AND `del_flag` = '0';

UPDATE `course_chapter` SET `title` = '网络层', `course_title` = @course_title, `sort_order` = 40,
  `description` = '对应「第四章 网络层」目录（19 个视频：第四章 网络层1~19）：网络层功能、路由算法、IPv4 数据报与分片、分类地址/子网划分/CIDR、NAT、ARP、DHCP、ICMP、IPv6、RIP/OSPF/BGP、IP 组播、移动 IP、路由器与本章总结。'
WHERE `subject_id` = @subject_id AND `chapter_no` = '第4章' AND `del_flag` = '0';

UPDATE `course_chapter` SET `title` = '传输层', `course_title` = @course_title, `sort_order` = 50,
  `description` = '对应「第五章 传输层」目录（8 个视频：第五章 传输层1~8）：传输层功能与端口复用分用、UDP 概述与校验、TCP 特点与首部、TCP 连接管理、可靠传输、流量控制、拥塞控制与本章总结。'
WHERE `subject_id` = @subject_id AND `chapter_no` = '第5章' AND `del_flag` = '0';

UPDATE `course_chapter` SET `title` = '应用层', `course_title` = @course_title, `sort_order` = 60,
  `description` = '对应「第六章 应用层」目录（6 个视频：第六章 应用层1~6）：应用层概述与 C/S、P2P 模型、DNS、FTP、电子邮件（SMTP/POP3/IMAP）、万维网与 HTTP、本章总结。'
WHERE `subject_id` = @subject_id AND `chapter_no` = '第6章' AND `del_flag` = '0';

-- ---------- 4. 取新章节 ID ----------
SET @ch1 = (SELECT id FROM `course_chapter` WHERE `subject_id` = @subject_id AND `chapter_no` = '第1章' AND `del_flag` = '0' ORDER BY id LIMIT 1);
SET @ch2 = (SELECT id FROM `course_chapter` WHERE `subject_id` = @subject_id AND `chapter_no` = '第2章' AND `del_flag` = '0' ORDER BY id LIMIT 1);
SET @ch3 = (SELECT id FROM `course_chapter` WHERE `subject_id` = @subject_id AND `chapter_no` = '第3章' AND `del_flag` = '0' ORDER BY id LIMIT 1);
SET @ch4 = (SELECT id FROM `course_chapter` WHERE `subject_id` = @subject_id AND `chapter_no` = '第4章' AND `del_flag` = '0' ORDER BY id LIMIT 1);
SET @ch5 = (SELECT id FROM `course_chapter` WHERE `subject_id` = @subject_id AND `chapter_no` = '第5章' AND `del_flag` = '0' ORDER BY id LIMIT 1);
SET @ch6 = (SELECT id FROM `course_chapter` WHERE `subject_id` = @subject_id AND `chapter_no` = '第6章' AND `del_flag` = '0' ORDER BY id LIMIT 1);

-- ---------- 5. 旧章节知识点关联按章迁移 ----------
INSERT INTO `chapter_point` (`chapter_id`, `point_id`, `section`, `sort_order`, `create_time`)
SELECT @ch1, cp.point_id, cp.section, cp.sort_order, cp.create_time
FROM `chapter_point` cp JOIN `course_chapter` cc ON cc.id = cp.chapter_id
WHERE cc.subject_id = @subject_id AND cc.id <> @ch1
  AND (cc.chapter_no IN ('1', '第1章') OR cc.title LIKE '%网络体系结构%' OR cc.title LIKE '%概念和功能%')
  AND NOT EXISTS (SELECT 1 FROM `chapter_point` x WHERE x.chapter_id = @ch1 AND x.point_id = cp.point_id);

INSERT INTO `chapter_point` (`chapter_id`, `point_id`, `section`, `sort_order`, `create_time`)
SELECT @ch2, cp.point_id, cp.section, cp.sort_order, cp.create_time
FROM `chapter_point` cp JOIN `course_chapter` cc ON cc.id = cp.chapter_id
WHERE cc.subject_id = @subject_id AND cc.id <> @ch2
  AND (cc.chapter_no IN ('2', '第2章') OR cc.title LIKE '%物理层%' OR cc.title LIKE '%数据通信%')
  AND NOT EXISTS (SELECT 1 FROM `chapter_point` x WHERE x.chapter_id = @ch2 AND x.point_id = cp.point_id);

INSERT INTO `chapter_point` (`chapter_id`, `point_id`, `section`, `sort_order`, `create_time`)
SELECT @ch3, cp.point_id, cp.section, cp.sort_order, cp.create_time
FROM `chapter_point` cp JOIN `course_chapter` cc ON cc.id = cp.chapter_id
WHERE cc.subject_id = @subject_id AND cc.id <> @ch3
  AND (cc.chapter_no IN ('3', '第3章') OR cc.title LIKE '%数据链路层%' OR cc.title LIKE '%直接连接%')
  AND NOT EXISTS (SELECT 1 FROM `chapter_point` x WHERE x.chapter_id = @ch3 AND x.point_id = cp.point_id);

INSERT INTO `chapter_point` (`chapter_id`, `point_id`, `section`, `sort_order`, `create_time`)
SELECT @ch4, cp.point_id, cp.section, cp.sort_order, cp.create_time
FROM `chapter_point` cp JOIN `course_chapter` cc ON cc.id = cp.chapter_id
WHERE cc.subject_id = @subject_id AND cc.id <> @ch4
  AND (cc.chapter_no = '第4章' OR cc.title LIKE '%网络互联%')
  AND NOT EXISTS (SELECT 1 FROM `chapter_point` x WHERE x.chapter_id = @ch4 AND x.point_id = cp.point_id);

INSERT INTO `chapter_point` (`chapter_id`, `point_id`, `section`, `sort_order`, `create_time`)
SELECT @ch5, cp.point_id, cp.section, cp.sort_order, cp.create_time
FROM `chapter_point` cp JOIN `course_chapter` cc ON cc.id = cp.chapter_id
WHERE cc.subject_id = @subject_id AND cc.id <> @ch5
  AND (cc.chapter_no = '第5章' OR cc.title LIKE '%端到端%')
  AND NOT EXISTS (SELECT 1 FROM `chapter_point` x WHERE x.chapter_id = @ch5 AND x.point_id = cp.point_id);

INSERT INTO `chapter_point` (`chapter_id`, `point_id`, `section`, `sort_order`, `create_time`)
SELECT @ch6, cp.point_id, cp.section, cp.sort_order, cp.create_time
FROM `chapter_point` cp JOIN `course_chapter` cc ON cc.id = cp.chapter_id
WHERE cc.subject_id = @subject_id AND cc.id <> @ch6
  AND (cc.chapter_no IN ('第6章', '第7章') OR cc.title LIKE '%网络应用%' OR cc.title LIKE '%网络安全%')
  AND NOT EXISTS (SELECT 1 FROM `chapter_point` x WHERE x.chapter_id = @ch6 AND x.point_id = cp.point_id);

-- ---------- 6. 逐字稿 / 导入任务 / 知识库分块 改指新章节 ----------
UPDATE `course_transcript` ct JOIN `course_chapter` cc ON cc.id = ct.chapter_id
SET ct.chapter_id = CASE
    WHEN cc.chapter_no IN ('1', '第1章') OR cc.title LIKE '%网络体系结构%' THEN @ch1
    WHEN cc.chapter_no IN ('2', '第2章') OR cc.title LIKE '%物理层%' THEN @ch2
    WHEN cc.chapter_no IN ('3', '第3章') OR cc.title LIKE '%数据链路层%' THEN @ch3
    ELSE ct.chapter_id END
WHERE cc.subject_id = @subject_id AND cc.id NOT IN (@ch1, @ch2, @ch3);

UPDATE `course_video_import` vi JOIN `course_chapter` cc ON cc.id = vi.chapter_id
SET vi.chapter_id = CASE
    WHEN cc.chapter_no IN ('1', '第1章') OR cc.title LIKE '%网络体系结构%' THEN @ch1
    WHEN cc.chapter_no IN ('2', '第2章') OR cc.title LIKE '%物理层%' THEN @ch2
    WHEN cc.chapter_no IN ('3', '第3章') OR cc.title LIKE '%数据链路层%' THEN @ch3
    ELSE vi.chapter_id END
WHERE cc.subject_id = @subject_id AND cc.id NOT IN (@ch1, @ch2, @ch3);

UPDATE `kb_chunk` kc JOIN `course_chapter` cc ON cc.id = kc.chapter_id
SET kc.chapter_id = CASE
    WHEN cc.chapter_no IN ('1', '第1章') OR cc.title LIKE '%网络体系结构%' THEN @ch1
    WHEN cc.chapter_no IN ('2', '第2章') OR cc.title LIKE '%物理层%' OR cc.title LIKE '%数据通信%' THEN @ch2
    WHEN cc.chapter_no IN ('3', '第3章') OR cc.title LIKE '%数据链路层%' OR cc.title LIKE '%直接连接%' THEN @ch3
    WHEN cc.chapter_no = '第4章' OR cc.title LIKE '%网络互联%' THEN @ch4
    WHEN cc.chapter_no = '第5章' OR cc.title LIKE '%端到端%' THEN @ch5
    WHEN cc.chapter_no IN ('第6章', '第7章') OR cc.title LIKE '%网络应用%' THEN @ch6
    ELSE kc.chapter_id END
WHERE cc.subject_id = @subject_id AND cc.id NOT IN (@ch1, @ch2, @ch3, @ch4, @ch5, @ch6);

UPDATE `kb_document` kd JOIN `course_chapter` cc ON cc.id = kd.chapter_id
SET kd.chapter_id = CASE
    WHEN cc.chapter_no IN ('1', '第1章') OR cc.title LIKE '%网络体系结构%' THEN @ch1
    WHEN cc.chapter_no IN ('2', '第2章') OR cc.title LIKE '%物理层%' OR cc.title LIKE '%数据通信%' THEN @ch2
    WHEN cc.chapter_no IN ('3', '第3章') OR cc.title LIKE '%数据链路层%' OR cc.title LIKE '%直接连接%' THEN @ch3
    WHEN cc.chapter_no = '第4章' OR cc.title LIKE '%网络互联%' THEN @ch4
    WHEN cc.chapter_no = '第5章' OR cc.title LIKE '%端到端%' THEN @ch5
    WHEN cc.chapter_no IN ('第6章', '第7章') OR cc.title LIKE '%网络应用%' THEN @ch6
    ELSE kd.chapter_id END
WHERE cc.subject_id = @subject_id AND cc.id NOT IN (@ch1, @ch2, @ch3, @ch4, @ch5, @ch6);

-- 清理已不存在的章节遗留的导入任务指向
UPDATE `course_video_import` vi
SET vi.chapter_id = NULL
WHERE vi.chapter_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `course_chapter` c WHERE c.id = vi.chapter_id);

-- ---------- 7. 删除该学科的其它章节（含历史重复章） ----------
DELETE FROM `course_chapter`
WHERE `subject_id` = @subject_id
  AND `id` NOT IN (@ch1, @ch2, @ch3, @ch4, @ch5, @ch6);

-- ---------- 8. 校验 ----------
SELECT cc.chapter_no, cc.title, cc.sort_order,
       (SELECT COUNT(*) FROM `chapter_point` cp WHERE cp.chapter_id = cc.id) AS point_count,
       (SELECT COUNT(*) FROM `course_transcript` t WHERE t.chapter_id = cc.id) AS transcript_count
FROM `course_chapter` cc
WHERE cc.subject_id = @subject_id AND cc.del_flag = '0'
ORDER BY cc.sort_order;
