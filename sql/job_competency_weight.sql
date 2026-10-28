-- 国家职业标准 4.2 技能要求权重表（按职业 × 等级 × 能力域存储）
CREATE TABLE IF NOT EXISTS `job_competency_weight` (
  `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_code`      varchar(20)  NOT NULL COMMENT '职业编码，如 4-04-05-01',
  `job_name`      varchar(100) NOT NULL COMMENT '职业名称',
  `level`         varchar(20)  NOT NULL COMMENT '技能等级：五级/四级/三级/二级/一级',
  `domain_name`   varchar(200) NOT NULL COMMENT '职业功能/能力域名称',
  `weight`        int(11)      DEFAULT NULL COMMENT '权重百分比，NULL 表示该等级不设此项',
  `source`        varchar(40)  NOT NULL DEFAULT 'national_standard' COMMENT '数据来源',
  `source_file`   varchar(200) DEFAULT NULL COMMENT '标准 PDF 文件名',
  `source_page`   varchar(40)  DEFAULT NULL COMMENT '原文页码',
  `issue_number`  varchar(100) DEFAULT NULL COMMENT '颁布文号',
  `create_time`   datetime     DEFAULT NULL COMMENT '入库时间',
  PRIMARY KEY (`id`),
  KEY `idx_code_level` (`job_code`, `level`),
  KEY `idx_name` (`job_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='国家职业标准技能要求权重表';
