-- 用户学习记录表添加 point_quiz_score 字段
ALTER TABLE `user_learn_record` 
ADD COLUMN `point_quiz_score` tinyint DEFAULT NULL COMMENT '知识点测评分数（0-100）' AFTER `progress`;
