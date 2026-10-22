package com.team22.aiguanli.service;

import com.team22.aiguanli.entity.PmTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisFactsTest {

    @Test
    void boardNamesStageAndStatus() {
        PmTask task = new PmTask();
        task.setStoryCode("E2-02");
        task.setTitle("只读甘特图");
        task.setStatus("doing");
        String text = AnalysisFacts.board(List.of(task));
        assertTrue(text.contains("进阶2"));
        assertTrue(text.contains("只读甘特图"));
        assertTrue(text.contains("进行中"));
    }

    @Test
    void riskMarksOverdueTask() {
        PmTask task = new PmTask();
        task.setStoryCode("E1-02");
        task.setTitle("创建角色并分配权限");
        task.setStatus("doing");
        task.setDueDate(LocalDate.of(2026, 10, 19));
        String text = AnalysisFacts.risk(List.of(task), List.of(), LocalDate.of(2026, 10, 21));
        assertTrue(text.contains("高"));
        assertTrue(text.contains("创建角色并分配权限"));
    }
}
