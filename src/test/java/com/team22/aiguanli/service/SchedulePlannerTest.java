package com.team22.aiguanli.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulePlannerTest {

    private final LocalDate today = LocalDate.of(2026, 10, 23);

    @Test
    void mustOccupiesThePersonBeforeCould() {
        SchedulePlanner.Item must = task(1L, "Must", "todo", 10L, "李坤溪", today, today.plusDays(2));
        SchedulePlanner.Item could = task(2L, "Could", "todo", 10L, "李坤溪", today, today.plusDays(1));
        List<SchedulePlanner.Slot> slots = SchedulePlanner.plan(today, List.of(could, must), List.of(), List.of(person(10L, "李坤溪")));
        SchedulePlanner.Slot mustSlot = slot(slots, 1L);
        SchedulePlanner.Slot couldSlot = slot(slots, 2L);
        assertEquals("2026-10-23", mustSlot.getStartDate());
        assertEquals("2026-10-25", mustSlot.getDueDate());
        assertEquals("2026-10-26", couldSlot.getStartDate());
        assertTrue(couldSlot.getReason().contains("Could"));
    }

    @Test
    void successorStartsTheDayAfterPredecessor() {
        SchedulePlanner.Item first = task(1L, "Must", "todo", 10L, "李坤溪", today, today);
        SchedulePlanner.Item second = task(2L, "Must", "todo", 11L, "张译泷", today, today);
        SchedulePlanner.Edge edge = new SchedulePlanner.Edge();
        edge.fromTaskId = 1L;
        edge.toTaskId = 2L;
        SchedulePlanner.Slot secondSlot = slot(SchedulePlanner.plan(today, List.of(second, first), List.of(edge),
                List.of(person(10L, "李坤溪"), person(11L, "张译泷"))), 2L);
        assertEquals("2026-10-24", secondSlot.getStartDate());
        assertTrue(secondSlot.getReason().contains("完成的次日"));
    }

    @Test
    void doingTaskKeepsItsOwnerBusy() {
        SchedulePlanner.Item doing = task(1L, "Must", "doing", 10L, "李坤溪", today.minusDays(1), today.plusDays(1));
        SchedulePlanner.Item todo = task(2L, "Must", "todo", 10L, "李坤溪", today, today.plusDays(2));
        List<SchedulePlanner.Slot> slots = SchedulePlanner.plan(today, List.of(doing, todo), List.of(), List.of(person(10L, "李坤溪")));
        assertEquals(1, slots.size());
        assertEquals("2026-10-25", slots.get(0).getStartDate());
    }

    @Test
    void unassignedTaskGoesToTheFreePerson() {
        SchedulePlanner.Item doing = task(1L, "Must", "doing", 10L, "李坤溪", today, today.plusDays(4));
        SchedulePlanner.Item todo = task(2L, "Must", "todo", null, "", null, null);
        SchedulePlanner.Slot slot = slot(SchedulePlanner.plan(today, List.of(doing, todo), List.of(),
                List.of(person(10L, "李坤溪"), person(11L, "张译泷"))), 2L);
        assertEquals(11L, slot.getAssigneeId());
        assertEquals("2026-10-23", slot.getStartDate());
        assertEquals("2026-10-25", slot.getDueDate());
        assertTrue(slot.getReason().contains("张译泷"));
    }

    private static SchedulePlanner.Slot slot(List<SchedulePlanner.Slot> slots, Long taskId) {
        return slots.stream().filter(slot -> taskId.equals(slot.getTaskId())).findFirst().orElseThrow();
    }

    private static SchedulePlanner.Item task(Long id, String moscow, String status, Long assigneeId, String assigneeName,
                                             LocalDate start, LocalDate due) {
        SchedulePlanner.Item item = new SchedulePlanner.Item();
        item.id = id;
        item.title = "任务" + id;
        item.storyCode = "E1-0" + id;
        item.moscow = moscow;
        item.status = status;
        item.assigneeId = assigneeId;
        item.assigneeName = assigneeName;
        item.startDate = start;
        item.dueDate = due;
        return item;
    }

    private static SchedulePlanner.Person person(Long id, String name) {
        SchedulePlanner.Person person = new SchedulePlanner.Person();
        person.id = id;
        person.name = name;
        return person;
    }
}
