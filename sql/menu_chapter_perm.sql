-- 课程章节独立权限（可重复执行）
--
-- 背景：章节管理原先复用 project:point:* 权限码，导致「只有知识点权限的人也能改删章节」。
--       本脚本新增 project:chapter:* 四个按钮权限，并授予已拥有章节菜单的角色。
--
-- 执行顺序：先执行本脚本，再发布前端。
--           否则 v-hasPermi 校验不到新权限码，章节页的新增/修改/删除按钮会全部消失。
SET NAMES utf8mb4;

-- 1. 章节菜单的 list 权限码改为独立标识
UPDATE `sys_menu`
SET `perms` = 'project:chapter:list'
WHERE `component` = 'project/chapter/index'
  AND (`perms` IS NULL OR `perms` <> 'project:chapter:list');

-- 2. 新增四个按钮权限（挂在章节菜单下）
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
                        `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
                        `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT t.menu_name, m.`menu_id`, t.order_num, '', NULL, NULL, '', 1, 0, 'F', '0', '0',
       t.perms, '#', 'admin', sysdate(), '', NULL, ''
FROM (
    SELECT 'chapter:list'   AS menu_name, 1 AS order_num, 'project:chapter:list'   AS perms
    UNION ALL SELECT 'chapter:add',    2, 'project:chapter:add'
    UNION ALL SELECT 'chapter:edit',   3, 'project:chapter:edit'
    UNION ALL SELECT 'chapter:remove', 4, 'project:chapter:remove'
) t
JOIN `sys_menu` m ON m.`component` = 'project/chapter/index'
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` x WHERE x.`perms` = t.perms);

-- 3. 授予「已拥有章节菜单」的所有角色
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT rm.`role_id`, perm.`menu_id`
FROM `sys_role_menu` rm
JOIN `sys_menu` chapter ON chapter.`menu_id` = rm.`menu_id`
                       AND chapter.`component` = 'project/chapter/index'
JOIN `sys_menu` perm ON perm.`perms` LIKE 'project:chapter:%'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_role_menu` x
    WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = perm.`menu_id`
);

-- 校验：确认章节权限已登记
SELECT `menu_id`, `menu_name`, `parent_id`, `menu_type`, `perms`
FROM `sys_menu`
WHERE `perms` LIKE 'project:chapter:%'
ORDER BY `menu_id`;
