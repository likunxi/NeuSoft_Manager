-- ============================================================
-- V1 Sprint 1 可视化核心表
-- 对应故事：E1-01 ~ E1-06、E1-08（Should：E1-09 用视图即可）
-- 设计原则：
-- 1. 本阶段页面只用「人 / 事 / 数」所需字段
-- 2. 任务表预留 start_date / due_date / progress / story_id，给 Sprint 2 甘特和故事看板挂接
-- 3. 状态变更写入 pm_task_status_log，趋势图和 Sprint 3 进度预测读同一份过程数据
-- ============================================================

USE aiguanli;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP VIEW IF EXISTS v_member_workload;
DROP VIEW IF EXISTS v_project_trend_daily;
DROP VIEW IF EXISTS v_project_board;
DROP TABLE IF EXISTS ai_writeback;
DROP TABLE IF EXISTS ai_review;
DROP TABLE IF EXISTS ai_quality_score;
DROP TABLE IF EXISTS ai_risk_alert;
DROP TABLE IF EXISTS ai_prediction;
DROP TABLE IF EXISTS ai_suggestion;
DROP TABLE IF EXISTS ai_prd;
DROP TABLE IF EXISTS pm_uml_artifact;
DROP TABLE IF EXISTS vw_sync_event;
DROP TABLE IF EXISTS vw_link_rule;
DROP TABLE IF EXISTS pm_task_dependency;
DROP TABLE IF EXISTS pm_task_status_log;
DROP TABLE IF EXISTS pm_task;
DROP TABLE IF EXISTS pm_story;
DROP TABLE IF EXISTS pm_project;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;

SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 角色
-- ------------------------------------------------------------
CREATE TABLE sys_role (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  role_code     VARCHAR(32)  NOT NULL COMMENT '稳定编码，业务禁止用中文名判断',
  role_name     VARCHAR(32)  NOT NULL COMMENT '展示名',
  description   VARCHAR(200) NULL,
  is_system     TINYINT      NOT NULL DEFAULT 0 COMMENT '1=预置，不可删改名',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code),
  UNIQUE KEY uk_role_name (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- ------------------------------------------------------------
-- 权限码（带史诗字段，给「权限地图」按人/事/数分组）
-- ------------------------------------------------------------
CREATE TABLE sys_permission (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  perm_code     VARCHAR(64)  NOT NULL COMMENT '如 user:manage',
  perm_name     VARCHAR(64)  NOT NULL,
  epic          VARCHAR(32)  NOT NULL COMMENT '用户管理与权限 / 任务与协作 / 数据与分析',
  description   VARCHAR(200) NULL,
  sort_order    INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限码';

CREATE TABLE sys_role_permission (
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
  CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限';

-- ------------------------------------------------------------
-- 用户
-- password_hash：种子可用 {plain}xxx，应用启动后刷成 BCrypt
-- ------------------------------------------------------------
CREATE TABLE sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(32)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name  VARCHAR(32)  NOT NULL,
  student_no    VARCHAR(16)  NULL COMMENT '学号，课程团队用',
  role_id       BIGINT       NOT NULL,
  is_active     TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_user_role (role_id),
  CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号';

-- ------------------------------------------------------------
-- 项目
-- ------------------------------------------------------------
CREATE TABLE pm_project (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_code  VARCHAR(32)  NOT NULL,
  name          VARCHAR(64)  NOT NULL,
  description   VARCHAR(500) NULL,
  created_by    BIGINT       NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_code (project_code),
  CONSTRAINT fk_project_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目';

-- ------------------------------------------------------------
-- 任务
-- Sprint 1 页面只用 title/assignee/status
-- start_date、due_date、progress、story_id 预留给 Sprint 2，本轮 UI 不暴露
-- ------------------------------------------------------------
CREATE TABLE pm_task (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  story_code    VARCHAR(16)  NULL COMMENT '如 E1-01，自举演示用',
  story_id      BIGINT       NULL COMMENT 'Sprint2 故事看板挂接，V1 不建外键',
  title         VARCHAR(80)  NOT NULL,
  description   VARCHAR(500) NULL,
  assignee_id   BIGINT       NULL,
  created_by    BIGINT       NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'todo' COMMENT 'todo/doing/done',
  start_date    DATE         NULL COMMENT '预留：甘特开始',
  due_date      DATE         NULL COMMENT '预留：甘特结束',
  progress      INT          NOT NULL DEFAULT 0 COMMENT '预留：甘特进度 0-100',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  done_at       DATETIME     NULL COMMENT '进入 done 时写入，回退则清空',
  PRIMARY KEY (id),
  KEY idx_task_project (project_id),
  KEY idx_task_assignee (assignee_id),
  KEY idx_task_status (status),
  KEY idx_task_story_code (story_code),
  CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES pm_project (id),
  CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_id) REFERENCES sys_user (id),
  CONSTRAINT fk_task_creator FOREIGN KEY (created_by) REFERENCES sys_user (id),
  CONSTRAINT ck_task_status CHECK (status IN ('todo', 'doing', 'done')),
  CONSTRAINT ck_task_progress CHECK (progress >= 0 AND progress <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务（可视化主数据，后两阶复用）';

-- ------------------------------------------------------------
-- 状态流转日志 = 看见过程，不只看见当前状态
-- E1-08 近 7 日完成量、Sprint3 进度预测，都读这张表
-- ------------------------------------------------------------
CREATE TABLE pm_task_status_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  task_id       BIGINT       NOT NULL,
  project_id    BIGINT       NOT NULL,
  from_status   VARCHAR(16)  NOT NULL,
  to_status     VARCHAR(16)  NOT NULL,
  operator_id   BIGINT       NOT NULL,
  remark        VARCHAR(200) NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_log_task (task_id),
  KEY idx_log_project_time (project_id, created_at),
  KEY idx_log_to_status (to_status, created_at),
  CONSTRAINT fk_log_task FOREIGN KEY (task_id) REFERENCES pm_task (id),
  CONSTRAINT fk_log_project FOREIGN KEY (project_id) REFERENCES pm_project (id),
  CONSTRAINT fk_log_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务状态审计，趋势与预测的共同数据源';

-- ------------------------------------------------------------
-- 看板 / 趋势 / 工作量：用视图，避免前端自己分列算错
-- ------------------------------------------------------------
CREATE VIEW v_project_board AS
SELECT
  t.project_id,
  t.status,
  t.id          AS task_id,
  t.story_code,
  t.title,
  t.assignee_id,
  u.display_name AS assignee_name,
  t.updated_at
FROM pm_task t
LEFT JOIN sys_user u ON u.id = t.assignee_id;

CREATE VIEW v_project_trend_daily AS
SELECT
  l.project_id,
  DATE(l.created_at) AS stat_date,
  SUM(l.to_status = 'done') AS done_count
FROM pm_task_status_log l
GROUP BY l.project_id, DATE(l.created_at);

CREATE VIEW v_member_workload AS
SELECT
  t.project_id,
  t.assignee_id,
  IFNULL(u.display_name, '未分配') AS display_name,
  COUNT(*) AS total_count,
  SUM(t.status = 'todo')  AS todo_count,
  SUM(t.status = 'doing') AS doing_count,
  SUM(t.status = 'done')  AS done_count
FROM pm_task t
LEFT JOIN sys_user u ON u.id = t.assignee_id
GROUP BY t.project_id, t.assignee_id, u.display_name;
