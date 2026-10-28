-- 岗位能力管理菜单（岗位能力图谱、岗位能力权重、结构视图管理、产业链管理）
SET @parent_id = (SELECT menu_id FROM sys_menu WHERE menu_name = '岗位能力管理' AND parent_id = 0 LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '岗位能力管理', 0, 6, 'competencyAdmin', NULL, 1, 0, 'M', '0', '0', '', 'chart',
       'admin', now(), '岗位能力管理目录'
FROM DUAL WHERE @parent_id IS NULL;

SET @parent_id = (SELECT menu_id FROM sys_menu WHERE menu_name = '岗位能力管理' AND parent_id = 0 LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '岗位能力权重', @parent_id, 1, 'weight', 'project/weight/index', 1, 0, 'C', '0', '0',
       'project:weight:list', 'list', 'admin', now(), '国家职业标准技能要求权重维护'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id, menu_name, parent_id FROM sys_menu) t
                  WHERE t.menu_name = '岗位能力权重' AND t.parent_id = @parent_id);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '结构视图管理', @parent_id, 2, 'structure', 'project/structure/index', 1, 0, 'C', '0', '0',
       'project:competency:list', 'tree', 'admin', now(), '岗位能力图谱结构视图管理'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id, menu_name, parent_id FROM sys_menu) t
                  WHERE t.menu_name = '结构视图管理' AND t.parent_id = @parent_id);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, is_frame, is_cache,
                      menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '产业链管理', @parent_id, 3, 'chain', 'project/chain/index', 1, 0, 'C', '0', '0',
       'project:chain:list', 'guide', 'admin', now(), '岗位产业链与岗位群映射维护'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT menu_id, menu_name, parent_id FROM sys_menu) t
                  WHERE t.menu_name = '产业链管理' AND t.parent_id = @parent_id);

-- 迁移：岗位能力图谱从顶栏移到“岗位能力管理”目录下
UPDATE sys_menu SET parent_id = @parent_id, order_num = 0, path = 'competency'
WHERE component = 'project/competency/index' AND menu_type = 'C';

-- 迁移：“权重管理”更名为“岗位能力权重”
UPDATE sys_menu SET menu_name = '岗位能力权重'
WHERE menu_name = '权重管理' AND parent_id = @parent_id;

-- 兜底去重：同一目录下同名菜单只保留 menu_id 最小的一条，避免脚本重复执行后出现两个“岗位能力权重”
DELETE m FROM sys_menu m
JOIN sys_menu keep
  ON keep.parent_id = m.parent_id
 AND keep.menu_name = m.menu_name
 AND keep.menu_id < m.menu_id
WHERE m.parent_id = @parent_id;
