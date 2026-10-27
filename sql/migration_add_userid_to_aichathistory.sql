-- AI对话历史记录添加userId字段
ALTER TABLE `ai_chat_history` 
ADD COLUMN `user_id` BIGINT DEFAULT NULL COMMENT '用户ID' AFTER `id`;
