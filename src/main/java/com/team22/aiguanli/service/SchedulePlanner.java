package com.team22.aiguanli.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按优先级、前置任务和每人同时只做一件待办排出日期。不调用大模型。
 * 进行中和已完成保持原日期，进行中的任务会占住负责人的时间。
 */
public final class SchedulePlanner {

    private SchedulePlanner() {
    }

    public static List<Slot> plan(LocalDate today, List<Item> tasks, List<Edge> edges, List<Person> people) {
        List<Slot> slots = new ArrayList<>();
        if (today == null || tasks == null || tasks.isEmpty()) {
            return slots;
        }
        Map<Long, Item> byId = new HashMap<>();
        for (Item task : tasks) {
            if (task != null && task.id != null) {
                byId.put(task.id, task);
            }
        }
        Map<Long, List<Busy>> busy = new HashMap<>();
        Map<Long, LocalDate> plannedDue = new HashMap<>();
        for (Item task : byId.values()) {
            if (!"doing".equals(task.status) || task.assigneeId == null) {
                continue;
            }
            LocalDate due = task.dueDate == null || task.dueDate.isBefore(today) ? today : task.dueDate;
            LocalDate start = task.startDate == null ? today : task.startDate;
            if (due.isBefore(start)) {
                due = start;
            }
            busy.computeIfAbsent(task.assigneeId, key -> new ArrayList<>()).add(new Busy(start, due));
            plannedDue.put(task.id, due);
        }
        for (Item task : order(new ArrayList<>(byId.values()), edges == null ? List.of() : edges)) {
            slots.add(place(today, task, byId, edges, people, busy, plannedDue));
        }
        return slots;
    }

    private static Slot place(LocalDate today, Item task, Map<Long, Item> byId, List<Edge> edges,
                              List<Person> people, Map<Long, List<Busy>> busy, Map<Long, LocalDate> plannedDue) {
        int duration = duration(task);
        LocalDate earliest = today;
        String blocking = null;
        if (edges != null) {
            for (Edge edge : edges) {
                if (edge == null || !task.id.equals(edge.toTaskId)) {
                    continue;
                }
                Item pred = byId.get(edge.fromTaskId);
                if (pred == null) {
                    continue;
                }
                LocalDate ready = readyAfter(pred, today, plannedDue);
                if (ready != null && ready.isAfter(earliest)) {
                    earliest = ready;
                    blocking = pred.title;
                }
            }
        }
        boolean wasUnassigned = task.assigneeId == null;
        Long assigneeId = task.assigneeId;
        String assigneeName = task.assigneeName;
        if (assigneeId == null) {
            Person chosen = earliestPerson(earliest, duration, people, busy);
            if (chosen != null) {
                assigneeId = chosen.id;
                assigneeName = chosen.name;
            }
        }
        LocalDate start = assigneeId == null ? earliest : firstFit(busy.get(assigneeId), earliest, duration);
        LocalDate due = start.plusDays(duration - 1L);
        if (assigneeId != null) {
            busy.computeIfAbsent(assigneeId, key -> new ArrayList<>()).add(new Busy(start, due));
        }
        plannedDue.put(task.id, due);
        Slot slot = new Slot();
        slot.taskId = task.id;
        slot.title = task.title;
        slot.storyCode = task.storyCode;
        slot.assigneeId = assigneeId;
        slot.assigneeName = assigneeName == null || assigneeName.isBlank() ? "未分配" : assigneeName;
        slot.startDate = start.toString();
        slot.dueDate = due.toString();
        slot.previousStart = task.startDate == null ? "" : task.startDate.toString();
        slot.previousDue = task.dueDate == null ? "" : task.dueDate.toString();
        slot.reason = reason(task, blocking, wasUnassigned, assigneeName, earliest, start);
        return slot;
    }

    private static String reason(Item task, String blocking, boolean wasUnassigned, String assigneeName,
                                 LocalDate earliest, LocalDate start) {
        List<String> bits = new ArrayList<>();
        int rank = moscowRank(task.moscow);
        if (rank == 0) {
            bits.add("Must 优先");
        } else if (rank == 1) {
            bits.add("Should 排在 Must 之后");
        } else if (rank == 2) {
            bits.add("Could 再往后");
        }
        if (blocking != null && !blocking.isBlank()) {
            bits.add("排在「" + blocking + "」完成的次日");
        }
        if (wasUnassigned && assigneeName != null && !assigneeName.isBlank()) {
            bits.add("原先未分配，交给最早有空的" + assigneeName);
        }
        if (start.isAfter(earliest)) {
            bits.add((assigneeName == null || assigneeName.isBlank() ? "负责人" : assigneeName) + "已有任务占用，开始日顺延");
        }
        if (bits.isEmpty()) {
            bits.add("按当前空档排期");
        }
        return String.join("，", bits);
    }

    private static Person earliestPerson(LocalDate earliest, int duration, List<Person> people,
                                         Map<Long, List<Busy>> busy) {
        Person best = null;
        LocalDate bestStart = null;
        if (people == null) {
            return null;
        }
        for (Person person : people) {
            if (person == null || person.id == null) {
                continue;
            }
            LocalDate start = firstFit(busy.get(person.id), earliest, duration);
            if (best == null || start.isBefore(bestStart) || (start.equals(bestStart) && person.id < best.id)) {
                best = person;
                bestStart = start;
            }
        }
        return best;
    }

    static LocalDate firstFit(List<Busy> blocks, LocalDate earliest, int duration) {
        LocalDate cursor = earliest;
        for (int guard = 0; guard < 366; guard++) {
            LocalDate end = cursor.plusDays(duration - 1L);
            if (!overlaps(blocks, cursor, end)) {
                return cursor;
            }
            cursor = cursor.plusDays(1);
        }
        return earliest.plusDays(366);
    }

    private static boolean overlaps(List<Busy> blocks, LocalDate start, LocalDate end) {
        if (blocks == null) {
            return false;
        }
        for (Busy block : blocks) {
            if (!start.isAfter(block.end) && !block.start.isAfter(end)) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate readyAfter(Item pred, LocalDate today, Map<Long, LocalDate> plannedDue) {
        if ("done".equals(pred.status)) {
            if (pred.dueDate != null && !pred.dueDate.isBefore(today)) {
                return pred.dueDate.plusDays(1);
            }
            return today;
        }
        if ("doing".equals(pred.status)) {
            LocalDate due = pred.dueDate == null || pred.dueDate.isBefore(today) ? today : pred.dueDate;
            return due.plusDays(1);
        }
        LocalDate planned = plannedDue.get(pred.id);
        if (planned != null) {
            return planned.plusDays(1);
        }
        return null;
    }

    private static List<Item> order(List<Item> tasks, List<Edge> edges) {
        List<Item> todos = new ArrayList<>();
        for (Item task : tasks) {
            if ("todo".equals(task.status)) {
                todos.add(task);
            }
        }
        Map<Long, List<Long>> next = new HashMap<>();
        Map<Long, Integer> indegree = new HashMap<>();
        for (Item task : todos) {
            indegree.put(task.id, 0);
        }
        for (Edge edge : edges) {
            if (edge == null || edge.fromTaskId == null || edge.toTaskId == null) {
                continue;
            }
            if (!indegree.containsKey(edge.fromTaskId) || !indegree.containsKey(edge.toTaskId)) {
                continue;
            }
            next.computeIfAbsent(edge.fromTaskId, key -> new ArrayList<>()).add(edge.toTaskId);
            indegree.merge(edge.toTaskId, 1, Integer::sum);
        }
        List<Item> ordered = new ArrayList<>();
        List<Item> remaining = new ArrayList<>(todos);
        Comparator<Item> priority = Comparator
                .comparingInt((Item task) -> moscowRank(task.moscow))
                .thenComparing(task -> task.storyCode == null ? "" : task.storyCode)
                .thenComparing(task -> task.id);
        while (!remaining.isEmpty()) {
            List<Item> ready = new ArrayList<>();
            for (Item task : remaining) {
                if (indegree.getOrDefault(task.id, 0) == 0) {
                    ready.add(task);
                }
            }
            Item pick = (ready.isEmpty() ? remaining : ready).stream().min(priority).orElseThrow();
            ordered.add(pick);
            remaining.remove(pick);
            for (Long successor : next.getOrDefault(pick.id, List.of())) {
                indegree.merge(successor, -1, Integer::sum);
            }
        }
        return ordered;
    }

    private static int duration(Item task) {
        if (task.startDate != null && task.dueDate != null && !task.dueDate.isBefore(task.startDate)) {
            long days = ChronoUnit.DAYS.between(task.startDate, task.dueDate) + 1;
            if (days > 30) {
                return 30;
            }
            return (int) Math.max(days, 1);
        }
        return 3;
    }

    private static int moscowRank(String moscow) {
        if ("Must".equalsIgnoreCase(moscow)) {
            return 0;
        }
        if ("Should".equalsIgnoreCase(moscow)) {
            return 1;
        }
        if ("Could".equalsIgnoreCase(moscow)) {
            return 2;
        }
        return 3;
    }

    public static class Item {
        public Long id;
        public String title;
        public String storyCode;
        public String status;
        public String moscow;
        public Long assigneeId;
        public String assigneeName;
        public LocalDate startDate;
        public LocalDate dueDate;
    }

    public static class Edge {
        public Long fromTaskId;
        public Long toTaskId;
    }

    public static class Person {
        public Long id;
        public String name;
    }

    private static final class Busy {
        private final LocalDate start;
        private final LocalDate end;

        private Busy(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }
    }

    public static class Slot {
        private Long taskId;
        private String title;
        private String storyCode;
        private Long assigneeId;
        private String assigneeName;
        private String startDate;
        private String dueDate;
        private String previousStart;
        private String previousDue;
        private String reason;

        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStoryCode() { return storyCode; }
        public void setStoryCode(String storyCode) { this.storyCode = storyCode; }
        public Long getAssigneeId() { return assigneeId; }
        public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
        public String getAssigneeName() { return assigneeName; }
        public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getDueDate() { return dueDate; }
        public void setDueDate(String dueDate) { this.dueDate = dueDate; }
        public String getPreviousStart() { return previousStart; }
        public void setPreviousStart(String previousStart) { this.previousStart = previousStart; }
        public String getPreviousDue() { return previousDue; }
        public void setPreviousDue(String previousDue) { this.previousDue = previousDue; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
