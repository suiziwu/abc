-- 知识库菜单（可重复执行）
-- 位置：知识架构管理 → 知识库（排在知识点管理与知识点关联之间）
-- 页面：ruoyi-ui/src/views/project/kb/index.vue
-- 接口：/project/kb/list、/project/kb/status、/project/kb/rebuild、/project/kb/search、/project/kb/clear
SET NAMES utf8mb4;

SET @parent_id = (SELECT menu_id FROM `sys_menu` WHERE `menu_name` = '知识架构管理' ORDER BY `menu_id` LIMIT 1);
SET @parent_id = IFNULL(@parent_id, 0);

INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
                        `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
                        `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '知识库', @parent_id, 4, 'kb', 'project/kb/index',
       '', '', 1, 0, 'C', '0', '0',
       'project:kb:list', 'server', 'admin', sysdate(), '', NULL, '知识库分块状态、重建、检索与清空'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE `component` = 'project/kb/index'
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`menu_id`
FROM `sys_menu` m
WHERE m.`component` = 'project/kb/index'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`menu_id`
  );

-- 知识库切片的二级界面（隐藏菜单：不在侧边栏展示，由「知识库」页跳转进入）
-- 路由地址 kb/chunks 挂在「知识架构管理」目录下，最终路由为 /picture/kb/chunks
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
                        `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
                        `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '知识库切片', @parent_id, 5, 'kb/chunks', 'project/kb/chunks',
       NULL, '', 1, 0, 'C', '1', '0',
       'project:kb:list', 'tickets', 'admin', sysdate(), '', NULL, '知识库切片浏览二级界面'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE `component` = 'project/kb/chunks'
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`menu_id`
FROM `sys_menu` m
WHERE m.`component` = 'project/kb/chunks'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`menu_id`
  );
