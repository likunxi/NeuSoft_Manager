package com.team22.aiguanli.service;

import com.team22.aiguanli.entity.PmTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GanttServiceTest {

    private final GanttService service = new GanttService(null, null);

    @Test
    void barCountMatchesTasksAndColorsFollowStatus() {
        List<GanttService.Bar> bars = service.layout(List.of(
                task(1L, "账号", "done", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)),
                task(2L, "权限", "doing", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 6)),
                task(3L, "看板", "todo", LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 11))
        ));

        assertEquals(3, bars.size());
        assertEquals("done", bars.get(0).status);
        assertEquals("doing", bars.get(1).status);
        assertEquals("todo", bars.get(2).status);
        assertEquals("0.0", bars.get(0).leftPct);
        assertEquals("9.1", bars.get(0).widthPct);
        assertEquals("9.1", bars.get(1).leftPct);
        assertEquals("45.5", bars.get(1).widthPct);
        assertEquals("90.9", bars.get(2).leftPct);
        assertEquals("9.1", bars.get(2).widthPct);
        assertEquals("2026-01-01", bars.get(0).rangeStart);
        assertEquals("2026-01-11", bars.get(0).rangeEnd);
        assertEquals(100, bars.get(0).progress);
        assertEquals(0, bars.get(2).progress);
    }

    @Test
    void progressUsesStoredValue() {
        PmTask doing = task(2L, "权限", "doing", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 6));
        doing.setProgress(40);
        doing.setStoryCode("E1-02");
        List<GanttService.Bar> bars = service.layout(List.of(doing));
        assertEquals(1, bars.size());
        assertEquals(40, bars.get(0).progress);
        assertEquals("E1-02", bars.get(0).storyCode);
        assertEquals("e1", bars.get(0).stage);
    }

    @Test
    void missingDatesStillKeepOneBar() {
        PmTask task = task(9L, "未排期任务", "todo", null, null);
        List<GanttService.Bar> bars = service.layout(List.of(task));

        assertEquals(1, bars.size());
        assertEquals("todo", bars.get(0).status);
        assertEquals("未排期", bars.get(0).startLabel);
        assertEquals("未排期", bars.get(0).dueLabel);
        assertEquals("0.0", bars.get(0).leftPct);
        assertEquals("100.0", bars.get(0).widthPct);
    }

    private static PmTask task(Long id, String title, String status, LocalDate start, LocalDate due) {
        PmTask task = new PmTask();
        task.setId(id);
        task.setTitle(title);
        task.setStatus(status);
        task.setStartDate(start);
        task.setDueDate(due);
        return task;
    }
}
