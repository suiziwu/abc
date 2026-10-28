-- 岗位产业链与岗位群映射（数据取自公开分类标准，不含主观推断）
CREATE TABLE IF NOT EXISTS `job_industry_chain` (
  `id`           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `job_code`     varchar(20)  DEFAULT NULL COMMENT '职业编码（职业分类大典）',
  `job_name`     varchar(100) NOT NULL COMMENT '职业名称',
  `chain_name`   varchar(100) NOT NULL COMMENT '产业链/行业（国民经济行业分类）',
  `chain_code`   varchar(30)  DEFAULT NULL COMMENT '行业分类编码',
  `chain_desc`   varchar(500) DEFAULT NULL COMMENT '产业链说明',
  `stage_name`   varchar(100) DEFAULT NULL COMMENT '产业链环节',
  `stage_desc`   varchar(500) DEFAULT NULL COMMENT '环节说明',
  `cluster_code` varchar(20)  DEFAULT NULL COMMENT '岗位群编码（大典小类）',
  `cluster_name` varchar(100) DEFAULT NULL COMMENT '岗位群名称',
  `cluster_desc` varchar(500) DEFAULT NULL COMMENT '岗位群说明',
  `source`       varchar(200) DEFAULT NULL COMMENT '数据来源',
  `source_url`   varchar(300) DEFAULT NULL COMMENT '来源链接',
  `create_time`  datetime     DEFAULT NULL COMMENT '入库时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_name` (`job_name`),
  KEY `idx_job_code` (`job_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位产业链与岗位群映射';
