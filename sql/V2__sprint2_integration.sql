-- ============================================================
-- V2 Sprint 2 一体化（今天建表，本轮代码不写页面）
-- 对应：E2-01 故事看板 / E2-02 甘特 / E2-03 成员任务图
--       E2-05 需求变更同步甘特 / E2-06 看板状态刷新任务图
--       E2-04 UML 生成 / E2-07 联动规则
-- 老师若问「为什么现在建」：实验一要求三阶一张规划，库表跟着基线走；
-- 功能入口本轮不上，避免范围蔓延。
-- ============================================================

USE aiguanli;
SET NAMES utf8mb4;

-- 用户故事看板（E2-01）
CREATE TABLE IF NOT EXISTS pm_story (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  story_code    VARCHAR(16)  NOT NULL COMMENT 'E1-01 / E2-01 / E3-01',
  title         VARCHAR(80)  NOT NULL,
  as_role       VARCHAR(32)  NOT NULL COMMENT '作为……',
  i_want        VARCHAR(200) NOT NULL COMMENT '我希望……',
  so_that       VARCHAR(200) NOT NULL COMMENT '以便……',
  moscow        VARCHAR(16)  NOT NULL COMMENT 'Must/Should/Could/Wont',
  sprint_no     TINYINT      NOT NULL COMMENT '1/2/3',
  status        VARCHAR(16)  NOT NULL DEFAULT 'todo' COMMENT 'todo/doing/done',
  sort_order    INT          NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_story_project_code (project_id, story_code),
  KEY idx_story_sprint (project_id, sprint_no),
  CONSTRAINT fk_story_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户故事卡片，Sprint2 看板主数据';

-- 把 V1 任务挂到故事上
ALTER TABLE pm_task
  ADD CONSTRAINT fk_task_story FOREIGN KEY (story_id) REFERENCES pm_story (id);

-- 甘特依赖（E2-02）
CREATE TABLE IF NOT EXISTS pm_task_dependency (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  project_id    BIGINT      NOT NULL,
  from_task_id  BIGINT      NOT NULL COMMENT '前置',
  to_task_id    BIGINT      NOT NULL COMMENT '后置',
  dep_type      VARCHAR(16) NOT NULL DEFAULT 'FS' COMMENT 'FS/SS/FF/SF',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_dep (from_task_id, to_task_id),
  CONSTRAINT fk_dep_project FOREIGN KEY (project_id) REFERENCES pm_project (id),
  CONSTRAINT fk_dep_from FOREIGN KEY (from_task_id) REFERENCES pm_task (id),
  CONSTRAINT fk_dep_to FOREIGN KEY (to_task_id) REFERENCES pm_task (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='甘特依赖';

-- 视图联动规则（E2-07）
CREATE TABLE IF NOT EXISTS vw_link_rule (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  source_view   VARCHAR(32)  NOT NULL COMMENT 'story_board/gantt/member_map/uml',
  target_view   VARCHAR(32)  NOT NULL,
  event_type    VARCHAR(32)  NOT NULL COMMENT 'story_changed/task_status_changed/date_changed',
  is_enabled    TINYINT      NOT NULL DEFAULT 1,
  created_by    BIGINT       NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rule_project (project_id),
  CONSTRAINT fk_rule_project FOREIGN KEY (project_id) REFERENCES pm_project (id),
  CONSTRAINT fk_rule_user FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='四种视图联动规则';

-- 变更事件总线（E2-05 / E2-06）：改一处，生成事件，再刷新其它视图
CREATE TABLE IF NOT EXISTS vw_sync_event (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  project_id    BIGINT        NOT NULL,
  rule_id       BIGINT        NULL,
  event_type    VARCHAR(32)   NOT NULL,
  source_view   VARCHAR(32)   NOT NULL,
  payload_json  JSON          NOT NULL COMMENT '变更前后快照',
  processed     TINYINT       NOT NULL DEFAULT 0,
  created_by    BIGINT        NOT NULL,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at  DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_event_unprocessed (project_id, processed, created_at),
  CONSTRAINT fk_event_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视图同步事件，禁止前端各写各的';

-- UML 生成缓存（E2-04 Should）
CREATE TABLE IF NOT EXISTS pm_uml_artifact (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  project_id    BIGINT       NOT NULL,
  uml_type      VARCHAR(16)  NOT NULL COMMENT 'usecase/sequence',
  source_ref    VARCHAR(64)  NULL COMMENT '故事或任务编号',
  mermaid_text  MEDIUMTEXT   NOT NULL,
  generated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  generated_by  BIGINT       NULL COMMENT '可为空：系统生成',
  PRIMARY KEY (id),
  KEY idx_uml_project (project_id, uml_type),
  CONSTRAINT fk_uml_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='由看板/甘特数据生成的 UML 快照';

-- 成员任务图直接复用 v_member_workload，不再另建表
