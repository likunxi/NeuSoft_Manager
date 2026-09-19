-- 只给「V1 和种子前半段已经成功」的当前库用
-- 不要再跑 seed_sprint1.sql，里面的角色/用户已经在，再跑会 Duplicate
-- 本文件：建缺失的 V2/V3 表 + 补 pm_story / 联动规则
-- DataGrip：选中 aiguanli 数据源，整文件 Run。分隔符保持默认分号 ;

USE aiguanli;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS pm_story (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  story_code    VARCHAR(16)  NOT NULL,
  title         VARCHAR(80)  NOT NULL,
  as_role       VARCHAR(32)  NOT NULL,
  i_want        VARCHAR(200) NOT NULL,
  so_that       VARCHAR(200) NOT NULL,
  moscow        VARCHAR(16)  NOT NULL,
  sprint_no     TINYINT      NOT NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'todo',
  sort_order    INT          NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_story_project_code (project_id, story_code),
  KEY idx_story_sprint (project_id, sprint_no),
  CONSTRAINT fk_story_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pm_task_dependency (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  project_id    BIGINT      NOT NULL,
  from_task_id  BIGINT      NOT NULL,
  to_task_id    BIGINT      NOT NULL,
  dep_type      VARCHAR(16) NOT NULL DEFAULT 'FS',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dep (from_task_id, to_task_id),
  CONSTRAINT fk_dep_project FOREIGN KEY (project_id) REFERENCES pm_project (id),
  CONSTRAINT fk_dep_from FOREIGN KEY (from_task_id) REFERENCES pm_task (id),
  CONSTRAINT fk_dep_to FOREIGN KEY (to_task_id) REFERENCES pm_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vw_link_rule (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  source_view   VARCHAR(32)  NOT NULL,
  target_view   VARCHAR(32)  NOT NULL,
  event_type    VARCHAR(32)  NOT NULL,
  is_enabled    TINYINT      NOT NULL DEFAULT 1,
  created_by    BIGINT       NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rule_project (project_id),
  CONSTRAINT fk_rule_project FOREIGN KEY (project_id) REFERENCES pm_project (id),
  CONSTRAINT fk_rule_user FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vw_sync_event (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  project_id    BIGINT        NOT NULL,
  rule_id       BIGINT        NULL,
  event_type    VARCHAR(32)   NOT NULL,
  source_view   VARCHAR(32)   NOT NULL,
  payload_json  JSON          NOT NULL,
  processed     TINYINT       NOT NULL DEFAULT 0,
  created_by    BIGINT        NOT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at  DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_event_unprocessed (project_id, processed, created_at),
  CONSTRAINT fk_event_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pm_uml_artifact (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  uml_type      VARCHAR(16)  NOT NULL,
  source_ref    VARCHAR(64)  NULL,
  mermaid_text  MEDIUMTEXT   NOT NULL,
  generated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  generated_by  BIGINT       NULL,
  PRIMARY KEY (id),
  KEY idx_uml_project (project_id, uml_type),
  CONSTRAINT fk_uml_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_prd (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  project_id    BIGINT        NOT NULL,
  title         VARCHAR(80)   NOT NULL,
  content       MEDIUMTEXT    NOT NULL,
  uploaded_by   BIGINT        NOT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_prd_project (project_id),
  CONSTRAINT fk_prd_project FOREIGN KEY (project_id) REFERENCES pm_project (id),
  CONSTRAINT fk_prd_user FOREIGN KEY (uploaded_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_suggestion (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  project_id    BIGINT        NOT NULL,
  suggest_type  VARCHAR(32)   NOT NULL,
  agent_name    VARCHAR(32)   NOT NULL,
  source_table  VARCHAR(64)   NULL,
  source_id     BIGINT        NULL,
  input_digest  VARCHAR(200)  NULL,
  output_json   JSON          NOT NULL,
  status        VARCHAR(16)   NOT NULL DEFAULT 'pending',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sug_project (project_id, suggest_type, status),
  CONSTRAINT fk_sug_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_review (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  suggestion_id BIGINT        NOT NULL,
  reviewer_id   BIGINT        NOT NULL,
  decision      VARCHAR(16)   NOT NULL,
  comment       VARCHAR(500)  NOT NULL,
  decided_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_suggestion (suggestion_id),
  CONSTRAINT fk_review_sug FOREIGN KEY (suggestion_id) REFERENCES ai_suggestion (id),
  CONSTRAINT fk_review_user FOREIGN KEY (reviewer_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_writeback (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  suggestion_id BIGINT        NOT NULL,
  review_id     BIGINT        NOT NULL,
  target_table  VARCHAR(64)   NOT NULL,
  target_id     BIGINT        NULL,
  applied_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_wb_sug (suggestion_id),
  CONSTRAINT fk_wb_sug FOREIGN KEY (suggestion_id) REFERENCES ai_suggestion (id),
  CONSTRAINT fk_wb_review FOREIGN KEY (review_id) REFERENCES ai_review (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_prediction (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  project_id      BIGINT        NOT NULL,
  predicted_end   DATE          NOT NULL,
  confidence      DECIMAL(5,2)  NULL,
  based_on_json   JSON          NOT NULL,
  suggestion_id   BIGINT        NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_pred_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_risk_alert (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  project_id    BIGINT        NOT NULL,
  risk_level    VARCHAR(16)   NOT NULL,
  indicator     VARCHAR(64)   NOT NULL,
  message       VARCHAR(300)  NOT NULL,
  suggestion_id BIGINT        NULL,
  status        VARCHAR(16)   NOT NULL DEFAULT 'open',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_risk_project (project_id, status),
  CONSTRAINT fk_risk_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ai_quality_score (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  project_id    BIGINT        NOT NULL,
  code_score    INT           NULL,
  doc_score     INT           NULL,
  test_score    INT           NULL,
  summary       VARCHAR(500)  NULL,
  suggestion_id BIGINT        NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_quality_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO pm_story (id, project_id, story_code, title, as_role, i_want, so_that, moscow, sprint_no, status, sort_order) VALUES
(1, 1, 'E1-01', '创建并管理用户账号', '管理员', '创建并管理用户账号', '控制谁能进入平台', 'Must', 1, 'done', 10),
(2, 1, 'E1-02', '创建角色并分配权限', '管理员', '创建角色并分配权限', '按职责授权', 'Must', 1, 'doing', 20),
(3, 1, 'E1-03', '查看自己的角色与权限', '团队成员', '查看自己的角色与可操作范围', '不越权、不漏权', 'Must', 1, 'todo', 30),
(4, 1, 'E1-04', '创建项目与任务', '项目经理', '创建项目与任务', '把工作搬到平台上', 'Must', 1, 'doing', 40),
(5, 1, 'E1-05', '指定负责人并更新状态', '团队成员', '被指定为负责人并更新任务状态', '协同工作', 'Must', 1, 'todo', 50),
(6, 1, 'E1-06', '看板三列查看进度', '项目经理', '在看板上按三列查看任务', '看见进度', 'Must', 1, 'todo', 60),
(7, 1, 'E1-08', '监控进度与完成趋势', '项目经理', '监控进度与完成趋势', '决策支持', 'Must', 1, 'todo', 70),
(8, 1, 'E1-09', '成员工作量统计', '管理员', '查看成员工作量统计', '调配人力', 'Should', 1, 'todo', 80);

UPDATE pm_task SET story_id = id WHERE project_id = 1 AND story_id IS NULL;

INSERT IGNORE INTO vw_link_rule (id, project_id, source_view, target_view, event_type, is_enabled, created_by) VALUES
(1, 1, 'story_board', 'gantt',      'story_changed',       0, 10),
(2, 1, 'board',       'member_map', 'task_status_changed', 0, 10);
