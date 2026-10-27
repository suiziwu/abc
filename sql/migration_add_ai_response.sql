-- 用户知识点测评记录表添加 ai_response 字段
ALTER TABLE `user_knowledge_assessment` 
ADD COLUMN `ai_response` json DEFAULT NULL COMMENT 'AI生成的针对性学习路径（JSON）' AFTER `overall_analysis`;
