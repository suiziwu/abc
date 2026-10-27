-- 计算机网络第 1 章随堂练习（可重复执行）
SET NAMES utf8mb4;
DELETE FROM `question` WHERE `test_id` = 102;

INSERT INTO `question` (`test_id`, `question_number`, `question_type`, `content`, `options`,
                         `answer`, `analysis`, `score`, `difficulty`, `sort_order`, `create_time`)
VALUES
(102, 1, 'choice',
 '从逻辑结构上看，计算机网络通常由哪两部分组成？\nA. 资源子网和通信子网\nB. 硬件和软件\nC. 主机和终端\nD. 局域网和广域网',
 NULL, 'A', '计算机网络从逻辑上分为资源子网和通信子网。资源子网负责数据处理，通信子网负责数据通信。', 5, 1, 1, sysdate()),
(102, 2, 'choice',
 '以下哪一项不属于计算机网络的主要功能？\nA. 数据通信\nB. 资源共享\nC. 分布式处理\nD. 自动发电',
 NULL, 'D', '计算机网络的主要功能包括数据通信、资源共享、分布式处理和提高可靠性，不包含自动发电。', 5, 1, 2, sysdate()),
(102, 3, 'choice',
 '按覆盖范围划分，计算机网络通常分为哪三类？\nA. 局域网、城域网、广域网\nB. 总线网、星型网、环型网\nC. 电路交换网、报文交换网、分组交换网\nD. 资源子网、通信子网、接入网',
 NULL, 'A', '按覆盖范围可分为局域网（LAN）、城域网（MAN）和广域网（WAN）。', 5, 1, 3, sysdate()),
(102, 4, 'choice',
 '以下哪一项不是衡量计算机网络性能的常用指标？\nA. 速率\nB. 带宽\nC. 吞吐量\nD. 显示器分辨率',
 NULL, 'D', '网络性能指标包括速率、带宽、吞吐量、时延、往返时延 RTT 和利用率等，显示器分辨率属于显示设备指标。', 5, 1, 4, sysdate()),
(102, 5, 'choice',
 '网络时延通常由哪些部分组成？\nA. 发送时延\nB. 传播时延\nC. 处理时延\nD. 排队时延',
 NULL, 'ABCD', '网络时延通常由发送时延、传播时延、处理时延和排队时延组成。', 5, 2, 5, sysdate()),
(102, 6, 'choice',
 'OSI 参考模型一共有多少层？\nA. 4 层\nB. 7 层\nC. 5 层\nD. 6 层',
 NULL, 'B', 'OSI 参考模型分为物理层、数据链路层、网络层、传输层、会话层、表示层和应用层，共 7 层。', 5, 1, 6, sysdate()),
(102, 7, 'choice',
 'TCP/IP 参考模型通常划分为多少层？\nA. 4 层\nB. 7 层\nC. 5 层\nD. 6 层',
 NULL, 'A', 'TCP/IP 参考模型通常划分为网络接口层、网际层、传输层和应用层，共 4 层。', 5, 1, 7, sysdate()),
(102, 8, 'choice',
 '网络协议的三要素包括哪些？\nA. 语法\nB. 语义\nC. 时序\nD. 拓扑结构',
 NULL, 'ABC', '网络协议的三要素是语法、语义和时序；拓扑结构描述网络的连接方式，不属于协议三要素。', 5, 2, 8, sysdate()),
(102, 9, 'choice',
 '以下哪些是常见的计算机网络标准化组织？\nA. ISO\nB. IEEE\nC. IETF\nD. CPU',
 NULL, 'ABC', 'ISO、IEEE 和 IETF 都是常见的网络标准化组织，CPU 是处理器，不是标准化组织。', 5, 2, 9, sysdate()),
(102, 10, 'judge',
 'Internet 就是互联网的简称，二者完全等同。',
 NULL, 'false', 'Internet 是当前全球最大的特定互联网，使用 TCP/IP 协议族；互联网泛指 interconnected networks，二者不能完全等同。', 5, 2, 10, sysdate());
