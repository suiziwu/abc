-- 用户知识点测评记录表添加 point_count 字段
-- 记录生成学习路径时的候选知识点池大小，用于判断已保存的学习路径是否已过期（知识点变多/失效）
ALTER TABLE `user_knowledge_assessment`
ADD COLUMN `point_count` int DEFAULT NULL COMMENT '生成学习路径时的候选知识点池大小' AFTER `ai_response`;
