-- ============================================================
-- Sprint 1 种子：演示账号 + 第22组真实团队 + 自举项目
-- 密码用 {plain} 前缀，应用启动后刷成 BCrypt，库里不长期留明文
-- ============================================================

USE aiguanli;
SET NAMES utf8mb4;

-- 角色
INSERT INTO sys_role (id, role_code, role_name, description, is_system) VALUES
(1, 'ADMIN',  '系统管理员', '管账号与角色权限', 1),
(2, 'PM',     '项目经理',   '建项目、派任务、看看板和趋势', 1),
(3, 'MEMBER', '团队成员',   '看自己的权限，更新自己的任务状态', 1);

-- 权限（epic 用于权限地图分组）
INSERT INTO sys_permission (id, perm_code, perm_name, epic, description, sort_order) VALUES
(1,  'user:manage',     '管理用户账号',     '用户管理与权限', 'E1-01', 10),
(2,  'role:manage',     '管理角色权限',     '用户管理与权限', 'E1-02', 20),
(3,  'project:create',  '创建项目',         '任务与协作',     'E1-04', 30),
(4,  'project:view',    '查看项目',         '任务与协作',     'E1-04', 40),
(5,  'task:create',     '创建任务',         '任务与协作',     'E1-04', 50),
(6,  'task:assign',     '指定负责人',       '任务与协作',     'E1-05', 60),
(7,  'task:update_any', '改任意任务状态',   '任务与协作',     'E1-05', 70),
(8,  'task:update_own', '改自己的任务状态', '任务与协作',     'E1-05', 80),
(9,  'board:view',      '查看看板',         '任务与协作',     'E1-06', 90),
(10, 'trend:view',      '查看趋势',         '数据与分析',     'E1-08', 100),
(11, 'workload:view',   '查看工作量',       '数据与分析',     'E1-09', 110);

-- 管理员：全部
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

-- 项目经理
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(2, 3), (2, 4), (2, 5), (2, 6), (2, 7), (2, 8), (2, 9), (2, 10);

-- 团队成员
INSERT INTO sys_role_permission (role_id, permission_id) VALUES
(3, 4), (3, 8), (3, 9);

-- 三个标准演示号（给老师点）
INSERT INTO sys_user (id, username, password_hash, display_name, student_no, role_id, is_active) VALUES
(1, 'admin',  '{plain}admin123', '系统管理员（演示）', NULL, 1, 1),
(2, 'pm',     '{plain}pm123',    '项目经理（演示）',   NULL, 2, 1),
(3, 'member', '{plain}member123','团队成员（演示）',   NULL, 3, 1);

-- 第22组真人账号，密码统一 22team，评审可切换「我们自己」
INSERT INTO sys_user (id, username, password_hash, display_name, student_no, role_id, is_active) VALUES
(10, 'likx',    '{plain}22team', '李坤溪',  '20245792', 2, 1),
(11, 'zhangyl', '{plain}22team', '张译泷',  '20245880', 2, 1),
(12, 'lisb',    '{plain}22team', '李诗博',  '20245894', 3, 1),
(13, 'qubq',    '{plain}22team', '曲柏桥',  '20245794', 3, 1);

-- 自举项目：用爱管理看见本课程 Sprint 1
INSERT INTO pm_project (id, project_code, name, description, created_by) VALUES
(1, 'AG-22', '22组-爱管理', '软件项目管理课程自举项目：种子即本团队 Sprint 1 Must', 10);

-- 任务：就是实验一冻结的 Must
INSERT INTO pm_task (id, project_id, story_code, title, description, assignee_id, created_by, status, start_date, due_date, progress, created_at, updated_at, done_at) VALUES
(1, 1, 'E1-01', '创建并管理用户账号',     '管理员可新建/停用用户',           10, 10, 'done',  CURDATE() - INTERVAL 2 DAY, CURDATE() - INTERVAL 1 DAY, 100, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
(2, 1, 'E1-02', '创建角色并分配权限',     '管理员维护角色与权限码',           10, 10, 'doing', CURDATE() - INTERVAL 1 DAY, CURDATE() + INTERVAL 1 DAY,  50, NOW() - INTERVAL 1 DAY, NOW(), NULL),
(3, 1, 'E1-03', '查看自己的角色与权限',   '权限地图：按三大史诗分组看见边界', 11, 11, 'todo',  CURDATE(),                   CURDATE() + INTERVAL 2 DAY,   0, NOW(), NOW(), NULL),
(4, 1, 'E1-04', '创建项目与任务',         '把工作从聊天记录搬进平台',         10, 10, 'doing', CURDATE(),                   CURDATE() + INTERVAL 2 DAY,  40, NOW(), NOW(), NULL),
(5, 1, 'E1-05', '指定负责人并更新状态',   '成员只能改自己的任务',             11, 10, 'todo',  CURDATE(),                   CURDATE() + INTERVAL 3 DAY,   0, NOW(), NOW(), NULL),
(6, 1, 'E1-06', '看板三列查看进度',       '待办 / 进行中 / 已完成',           12, 10, 'todo',  CURDATE(),                   CURDATE() + INTERVAL 3 DAY,   0, NOW(), NOW(), NULL),
(7, 1, 'E1-08', '监控进度与完成趋势',     '读状态日志，不写死数字',           13, 10, 'todo',  CURDATE(),                   CURDATE() + INTERVAL 3 DAY,   0, NOW(), NOW(), NULL);

-- 过程日志：给趋势图近两日柱子，并证明「看见的是流转」
INSERT INTO pm_task_status_log (task_id, project_id, from_status, to_status, operator_id, remark, created_at) VALUES
(1, 1, 'todo',  'doing', 10, '开工 E1-01', NOW() - INTERVAL 2 DAY),
(1, 1, 'doing', 'done',  10, '账号模块可演示', NOW() - INTERVAL 1 DAY),
(2, 1, 'todo',  'doing', 10, '开始做角色权限', NOW() - INTERVAL 1 DAY),
(4, 1, 'todo',  'doing', 10, '项目任务接口联调', NOW());

-- 下面依赖 V2 的 pm_story。只跑 V1 时请注释本段。
INSERT INTO pm_story (id, project_id, story_code, title, as_role, i_want, so_that, moscow, sprint_no, status, sort_order) VALUES
(1, 1, 'E1-01', '创建并管理用户账号', '管理员', '创建并管理用户账号', '控制谁能进入平台', 'Must', 1, 'done', 10),
(2, 1, 'E1-02', '创建角色并分配权限', '管理员', '创建角色并分配权限', '按职责授权', 'Must', 1, 'doing', 20),
(3, 1, 'E1-03', '查看自己的角色与权限', '团队成员', '查看自己的角色与可操作范围', '不越权、不漏权', 'Must', 1, 'todo', 30),
(4, 1, 'E1-04', '创建项目与任务', '项目经理', '创建项目与任务', '把工作搬到平台上', 'Must', 1, 'doing', 40),
(5, 1, 'E1-05', '指定负责人并更新状态', '团队成员', '被指定为负责人并更新任务状态', '协同工作', 'Must', 1, 'todo', 50),
(6, 1, 'E1-06', '看板三列查看进度', '项目经理', '在看板上按三列查看任务', '看见进度', 'Must', 1, 'todo', 60),
(7, 1, 'E1-08', '监控进度与完成趋势', '项目经理', '监控进度与完成趋势', '决策支持', 'Must', 1, 'todo', 70),
(8, 1, 'E1-09', '成员工作量统计', '管理员', '查看成员工作量统计', '调配人力', 'Should', 1, 'todo', 80);

UPDATE pm_task SET story_id = id WHERE project_id = 1 AND story_code LIKE 'E1-%';

-- Sprint 2：任务看板进阶 2 列。故事号以 E2 开头才会进这一列。
INSERT INTO pm_story (id, project_id, story_code, title, as_role, i_want, so_that, moscow, sprint_no, status, sort_order) VALUES
(9,  1, 'E2-01', '故事看板',         '项目经理', '在故事看板上按三列查看用户故事',     '把需求和任务对上',   'Must',   2, 'done',  210),
(10, 1, 'E2-02', '只读甘特图',       '项目经理', '按起止日期看到任务条和实际进度',     '安排这一周的工作',   'Must',   2, 'doing', 220),
(11, 1, 'E2-03', '成员任务图',       '项目经理', '按成员看到各自负责的任务',           '发现谁的活堆在一起', 'Must',   2, 'doing', 230),
(12, 1, 'E2-04', '由故事生成 UML',   '团队成员', '从故事和状态日志生成用例图和时序图', '不用手画结构',       'Should', 2, 'doing', 240),
(13, 1, 'E2-05', '需求变更同步甘特', '项目经理', '改故事状态时同步到甘特上的任务',     '需求和排期不各写各的', 'Must', 2, 'todo',  250),
(14, 1, 'E2-06', '看板状态刷新成员图','项目经理', '看板上改状态后成员图跟着刷新',       '人力视图不落后',     'Must',   2, 'todo',  260),
(15, 1, 'E2-07', '视图联动规则',     '项目经理', '打开或关闭视图之间的联动',           '同步只在需要时发生', 'Must',   2, 'todo',  270);

INSERT INTO pm_task (id, project_id, story_id, story_code, title, description, assignee_id, created_by, status, start_date, due_date, progress, created_at, updated_at, done_at) VALUES
(8,  1, 9,  'E2-01', '故事看板',           '在故事看板上按三列查看用户故事',     10, 10, 'done',  CURDATE() - INTERVAL 1 DAY, CURDATE(),                 100, NOW(), NOW(), NOW()),
(9,  1, 10, 'E2-02', '只读甘特图',         '按起止日期看到任务条和实际进度',     11, 10, 'doing', CURDATE(),                   CURDATE() + INTERVAL 3 DAY,  70, NOW(), NOW(), NULL),
(10, 1, 11, 'E2-03', '成员任务图',         '按成员看到各自负责的任务',           12, 10, 'doing', CURDATE(),                   CURDATE() + INTERVAL 3 DAY,  60, NOW(), NOW(), NULL),
(11, 1, 12, 'E2-04', '由故事生成 UML',     '从故事和状态日志生成用例图和时序图', 13, 10, 'doing', CURDATE() + INTERVAL 1 DAY, CURDATE() + INTERVAL 4 DAY,  50, NOW(), NOW(), NULL),
(12, 1, 13, 'E2-05', '需求变更同步甘特',   '改故事状态时同步到甘特上的任务',     10, 10, 'todo',  CURDATE() + INTERVAL 2 DAY, CURDATE() + INTERVAL 6 DAY,   0, NOW(), NOW(), NULL),
(13, 1, 14, 'E2-06', '看板状态刷新成员图', '看板上改状态后成员图跟着刷新',       11, 10, 'todo',  CURDATE() + INTERVAL 2 DAY, CURDATE() + INTERVAL 6 DAY,   0, NOW(), NOW(), NULL),
(14, 1, 15, 'E2-07', '视图联动规则',       '打开或关闭视图之间的联动',           12, 10, 'todo',  CURDATE() + INTERVAL 3 DAY, CURDATE() + INTERVAL 7 DAY,   0, NOW(), NOW(), NULL);

INSERT INTO pm_task_status_log (task_id, project_id, from_status, to_status, operator_id, remark, created_at) VALUES
(8,  1, 'todo', 'done',  10, '补上进阶 2 任务', NOW()),
(9,  1, 'todo', 'doing', 11, '补上进阶 2 任务', NOW()),
(10, 1, 'todo', 'doing', 12, '补上进阶 2 任务', NOW()),
(11, 1, 'todo', 'doing', 13, '补上进阶 2 任务', NOW());

-- 默认联动规则先写入，Sprint2 打开开关即可，本轮页面不读
INSERT INTO vw_link_rule (project_id, source_view, target_view, event_type, is_enabled, created_by) VALUES
(1, 'story_board', 'gantt',      'story_changed',       0, 10),
(1, 'board',       'member_map', 'task_status_changed', 0, 10);
