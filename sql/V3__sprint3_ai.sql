-- ============================================================
-- V3 Sprint 3 AI化（今天建表，本轮代码不写 AI 功能）
-- 对应：E3-01 拆解 / E3-02 预测 / E3-03 排期 / E3-04 预警
--       E3-05 质量 / E3-06 效率 / E3-07 主闭环
-- 硬约束 W-03：AI 不经人工确认不得改排期、不得写回故事库
-- 落法：建议表 与 写回表 物理分离；写回触发器校验人审结果
-- ============================================================

USE aiguanli;
SET NAMES utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PRD 原文，智能拆解的输入';

-- 所有 AI 产出先落到建议表，不能直接改 pm_task / pm_story
CREATE TABLE IF NOT EXISTS ai_suggestion (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  project_id    BIGINT        NOT NULL,
  suggest_type  VARCHAR(32)   NOT NULL COMMENT 'split/estimate/schedule/risk/quality/optimize',
  agent_name    VARCHAR(32)   NOT NULL COMMENT 'Nexus/Tempo/其它',
  source_table  VARCHAR(64)   NULL,
  source_id     BIGINT        NULL,
  input_digest  VARCHAR(200)  NULL,
  output_json   JSON          NOT NULL COMMENT '建议正文，尚未生效',
  status        VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending/reviewed',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sug_project (project_id, suggest_type, status),
  CONSTRAINT fk_sug_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI建议草稿，没有人审就不能落地';

CREATE TABLE IF NOT EXISTS ai_review (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  suggestion_id BIGINT        NOT NULL,
  reviewer_id   BIGINT        NOT NULL COMMENT '必须是自然人',
  decision      VARCHAR(16)   NOT NULL COMMENT 'accept/modify/reject',
  comment       VARCHAR(500)  NOT NULL COMMENT '处置理由，对应表3/表5',
  decided_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_review_suggestion (suggestion_id),
  CONSTRAINT fk_review_sug FOREIGN KEY (suggestion_id) REFERENCES ai_suggestion (id),
  CONSTRAINT fk_review_user FOREIGN KEY (reviewer_id) REFERENCES sys_user (id),
  CONSTRAINT ck_review_decision CHECK (decision IN ('accept', 'modify', 'reject'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人对 AI 建议的终审';

CREATE TABLE IF NOT EXISTS ai_writeback (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  suggestion_id BIGINT        NOT NULL,
  review_id     BIGINT        NOT NULL,
  target_table  VARCHAR(64)   NOT NULL COMMENT '只允许写回已有业务表',
  target_id     BIGINT        NULL,
  applied_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_wb_sug (suggestion_id),
  CONSTRAINT fk_wb_sug FOREIGN KEY (suggestion_id) REFERENCES ai_suggestion (id),
  CONSTRAINT fk_wb_review FOREIGN KEY (review_id) REFERENCES ai_review (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人审通过后的写回记录';

CREATE TABLE IF NOT EXISTS ai_prediction (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  project_id      BIGINT        NOT NULL,
  predicted_end   DATE          NOT NULL,
  confidence      DECIMAL(5,2)  NULL,
  based_on_json   JSON          NOT NULL COMMENT '引用哪些 status_log',
  suggestion_id   BIGINT        NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_pred_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交付日期预测（E3-02）';

CREATE TABLE IF NOT EXISTS ai_risk_alert (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  project_id    BIGINT        NOT NULL,
  risk_level    VARCHAR(16)   NOT NULL COMMENT 'low/medium/high',
  indicator     VARCHAR(64)   NOT NULL,
  message       VARCHAR(300)  NOT NULL,
  suggestion_id BIGINT        NULL,
  status        VARCHAR(16)   NOT NULL DEFAULT 'open',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_risk_project (project_id, status),
  CONSTRAINT fk_risk_project FOREIGN KEY (project_id) REFERENCES pm_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='延期风险预警（E3-04）';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量分析（E3-05）';

-- 触发器请单独执行 V3b__writeback_trigger.sql（DataGrip 对 DELIMITER 不友好）
