-- DataGrip 执行本文件前：控制台工具栏把分隔符从 ; 改成 $$
-- 跑完再改回 ;

USE aiguanli;

DROP TRIGGER IF EXISTS trg_ai_writeback_guard$$

CREATE TRIGGER trg_ai_writeback_guard
BEFORE INSERT ON ai_writeback
FOR EACH ROW
BEGIN
  DECLARE v_decision VARCHAR(16);
  SELECT r.decision INTO v_decision
    FROM ai_review r
   WHERE r.id = NEW.review_id
     AND r.suggestion_id = NEW.suggestion_id;
  IF v_decision IS NULL OR v_decision = 'reject' THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'W-03: AI建议未经人工采纳/修改确认，禁止写回业务数据';
  END IF;
END$$
