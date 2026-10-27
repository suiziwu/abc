-- 课程学习页面菜单（可重复执行）
SET NAMES utf8mb4;

INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
                        `route_name`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`,
                        `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
SELECT '课程学习', 0, 4, 'course', 'project/course/index', NULL,
       '', 1, 0, 'C', '0', '0',
       '', 'education', 'admin', sysdate(), '', NULL, '课程视频、AI 助教、思维导图与随堂练习'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE `component` = 'project/course/index'
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, m.`menu_id`
FROM `sys_menu` m
WHERE m.`component` = 'project/course/index'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm WHERE rm.`role_id` = 1 AND rm.`menu_id` = m.`menu_id`
  );
