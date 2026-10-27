-- 课程章节管理菜单（可重复执行）
--
-- 说明：
--   * 页面挂在「知识架构管理」(menu_id=2000) 下，component = project/chapter/index；
--   * 复用知识点管理已有的权限标识（project:point:list/add/edit/remove），
--     因此这里只登记菜单本身，不再新增 F 类型按钮权限，避免权限碎片化；
--   * 菜单授予当前已拥有「知识架构管理」的角色。
SET NAMES utf8mb4;

INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
                        `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
                        `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '课程章节', 2000, 2, 'chapter', 'project/chapter/index', NULL,
       '', 1, 0, 'C', '0', '0',
       'project:point:list', 'documentation', 'admin', sysdate(), '', NULL, '课程章节与知识点关联维护'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE `component` = 'project/chapter/index'
);

-- 菜单授予：
--   1) 超级管理员（role_id=1）：admin 角色本身可见全部菜单，这里显式登记以便角色管理页可勾选；
--   2) 与「知识点管理」(2003) 可见范围保持一致的角色。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`menu_id`
FROM `sys_menu` m
WHERE m.`component` = 'project/chapter/index'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = 1 AND x.`menu_id` = m.`menu_id`
  );

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT rm.`role_id`, m.`menu_id`
FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`component` = 'project/chapter/index'
WHERE rm.`menu_id` = 2003
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`menu_id`
  );

-- 校验：查看菜单是否登记成功
SELECT `menu_id`, `menu_name`, `parent_id`, `path`, `component`, `perms`
FROM `sys_menu`
WHERE `component` = 'project/chapter/index';