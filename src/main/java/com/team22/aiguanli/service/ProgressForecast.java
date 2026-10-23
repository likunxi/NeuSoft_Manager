package com.team22.aiguanli.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按未完成任务的剩余工期预测项目结束日。不调用大模型，也不改任务日期。
 * 同一个人的未完成任务工期相加；项目结束日取最晚的那个人。
 */
public final class ProgressForecast {

    private ProgressForecast() {
    }

    public static Result forecast(LocalDate today, List<Item> tasks, int doneLast7) {
        Result result = new Result();
        result.doneLast7 = Math.max(0, doneLast7);
        if (today == null) {
            today = LocalDate.now();
        }
        Map<String, Load> byPerson = new LinkedHashMap<>();
        if (tasks != null) {
            for (Item task : tasks) {
                if (task == null || "done".equals(task.status)) {
                    continue;
                }
                int days = remaining(task);
                if (days <= 0) {
                    continue;
                }
                result.unfinished++;
                boolean unassigned = task.assigneeId == null;
                if (unassigned) {
                    result.unassigned++;
                }
                if (task.dueDate != null && task.dueDate.isBefore(today)) {
                    result.overdue++;
                    if (result.overdueTitles.size() < 5 && task.title != null && !task.title.isBlank()) {
                        result.overdueTitles.add(task.title);
                    }
                }
                String name = unassigned ? "未分配" : blank(task.assigneeName, "未命名");
                Load load = byPerson.computeIfAbsent(name, Load::new);
                load.remainingDays += days;
                load.taskCount++;
            }
        }
        for (Load load : byPerson.values()) {
            load.end = today.plusDays(load.remainingDays - 1L);
            result.loads.add(load);
        }
        result.loads.sort(Comparator.comparing((Load load) -> load.end).reversed()
                .thenComparing(load -> load.name));
        result.predictedEnd = result.loads.isEmpty() ? today : result.loads.get(0).end;
        result.confidence = confidence(result);
        result.baseline = baseline(result);
        return result;
    }

    public static String advise(Result result) {
        if (result == null || result.unfinished == 0) {
            return "当前没有未完成任务，不需要调整人力。任务和甘特都保持原样。";
        }
        StringBuilder text = new StringBuilder();
        Load slow = result.loads.get(0);
        text.append("1. ").append(slow.name).append("还要 ").append(slow.remainingDays)
                .append(" 天，项目预计 ").append(result.predictedEnd).append(" 完成就是被这部分工期撑晚的。");
        Load fast = earliest(result.loads);
        if (fast != null && !fast.name.equals(slow.name) && fast.remainingDays + 2 < slow.remainingDays) {
            text.append("可以把").append(slow.name).append("名下还没开始的待办交给").append(fast.name)
                    .append("，或打开智能排期重新生成一版，确认后再写入甘特。");
        } else {
            text.append("打开智能排期，按一人同时只做一件重新排开，确认后再写入甘特。");
        }
        int index = 2;
        if (result.overdue > 0) {
            text.append("\n").append(index).append(". 有 ").append(result.overdue).append(" 条已过截止日仍未完成");
            if (!result.overdueTitles.isEmpty()) {
                text.append("：").append(String.join("、", result.overdueTitles));
            }
            text.append("。建议先收这些，再开始新的待办。");
            index++;
        }
        if (result.unassigned > 0) {
            text.append("\n").append(index).append(". 有 ").append(result.unassigned)
                    .append(" 条还没有负责人。先指定负责人，否则它们会单独占一截工期。");
            index++;
        }
        if (slow.taskCount >= 3) {
            text.append("\n").append(index).append(". ").append(slow.name).append("名下有 ")
                    .append(slow.taskCount).append(" 条未完成。一人同时只做一件时，结束日会落到 ")
                    .append(slow.end).append("。");
        }
        text.append("\n以上只是建议，任务日期和负责人都没有改。");
        return text.toString();
    }

    static int remaining(Item task) {
        if (task.progress != null && task.progress >= 100) {
            return 0;
        }
        int span;
        if (task.startDate != null && task.dueDate != null) {
            long days = ChronoUnit.DAYS.between(task.startDate, task.dueDate) + 1;
            if (days < 1) {
                days = 1;
            }
            span = (int) Math.min(days, 30);
        } else {
            span = 3;
        }
        int progress = task.progress == null ? 0 : Math.max(0, task.progress);
        if (progress <= 0) {
            return span;
        }
        int left = (int) Math.ceil(span * (100 - progress) / 100.0);
        return Math.max(1, left);
    }

    private static int confidence(Result result) {
        if (result.unfinished == 0) {
            return 90;
        }
        int score;
        if (result.doneLast7 >= 3 && result.overdue == 0) {
            score = 80;
        } else if (result.doneLast7 >= 1) {
            score = 60;
        } else {
            score = 40;
        }
        score -= Math.min(24, result.overdue * 8);
        if (result.unassigned > 0) {
            score -= 10;
        }
        return Math.max(20, Math.min(90, score));
    }

    private static String baseline(Result result) {
        if (result.unfinished == 0) {
            return "没有未完成任务，预测完成日就是 " + result.predictedEnd + "。近 7 天完成 " + result.doneLast7 + " 条。";
        }
        Load slow = result.loads.get(0);
        return "按每人未完成任务的剩余工期相加，项目预计 " + result.predictedEnd + " 完成。最晚的是"
                + slow.name + "，还要 " + slow.remainingDays + " 天。近 7 天完成 " + result.doneLast7
                + " 条，逾期 " + result.overdue + " 条。这个日期由规则算出，说明文字不能改它。";
    }

    private static Load earliest(List<Load> loads) {
        Load best = null;
        for (Load load : loads) {
            if ("未分配".equals(load.name)) {
                continue;
            }
            if (best == null || load.remainingDays < best.remainingDays) {
                best = load;
            }
        }
        return best;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static class Item {
        public Long assigneeId;
        public String assigneeName;
        public String status;
        public String title;
        public LocalDate startDate;
        public LocalDate dueDate;
        public Integer progress;
    }

    public static class Load {
        public String name;
        public int remainingDays;
        public int taskCount;
        public LocalDate end;

        public Load() {
        }

        Load(String name) {
            this.name = name;
        }
    }

    public static class Result {
        public LocalDate predictedEnd;
        public int confidence;
        public int doneLast7;
        public int overdue;
        public int unfinished;
        public int unassigned;
        public String baseline;
        public List<Load> loads = new ArrayList<>();
        public List<String> overdueTitles = new ArrayList<>();
    }
}
