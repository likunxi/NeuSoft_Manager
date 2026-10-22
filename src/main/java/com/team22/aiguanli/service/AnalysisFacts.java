package com.team22.aiguanli.service;

import com.team22.aiguanli.entity.AiRiskAlert;
import com.team22.aiguanli.entity.PmTask;

import java.time.LocalDate;
import java.util.List;

/**
 * 把项目里已经存在的数据整理成给大模型看的事实，避免模型自己编数字。
 */
public final class AnalysisFacts {

    private AnalysisFacts() {
    }

    public static String board(List<PmTask> tasks) {
        StringBuilder text = new StringBuilder();
        appendTasks(text, tasks);
        return text.toString();
    }

    public static String gantt(List<GanttService.Bar> bars, List<String> dependencies) {
        StringBuilder text = new StringBuilder();
        if (bars == null || bars.isEmpty()) {
            text.append("甘特图上没有任务。\n");
        } else {
            for (GanttService.Bar bar : bars) {
                text.append(TaskStages.label(bar.stage))
                        .append(" | ").append(empty(bar.storyCode))
                        .append(" | ").append(empty(bar.title))
                        .append(" | 状态 ").append(statusLabel(bar.status))
                        .append(" | 进度 ").append(bar.progress).append("%")
                        .append(" | ").append(bar.startLabel).append(" → ").append(bar.dueLabel)
                        .append('\n');
            }
        }
        text.append("依赖：\n");
        if (dependencies == null || dependencies.isEmpty()) {
            text.append("没有登记依赖。\n");
        } else {
            for (String line : dependencies) {
                text.append(line).append('\n');
            }
        }
        return text.toString();
    }

    public static String members(List<MemberMapService.Column> columns) {
        StringBuilder text = new StringBuilder();
        if (columns == null || columns.isEmpty()) {
            return "还没有任务，无法看人员分布。\n";
        }
        for (MemberMapService.Column column : columns) {
            text.append(column.name)
                    .append(" | 共 ").append(column.total)
                    .append(" | 待办 ").append(column.todo)
                    .append(" | 进行中 ").append(column.doing)
                    .append(" | 已完成 ").append(column.done)
                    .append('\n');
            if (column.tasks != null) {
                for (PmTask task : column.tasks) {
                    text.append("  - ").append(empty(task.getStoryCode()))
                            .append(" ").append(empty(task.getTitle()))
                            .append("（").append(statusLabel(task.getStatus())).append("）\n");
                }
            }
        }
        return text.toString();
    }

    public static String risk(List<PmTask> tasks, List<AiRiskAlert> alerts, LocalDate today) {
        StringBuilder text = new StringBuilder();
        text.append("今天是 ").append(today).append('\n');
        if (tasks == null || tasks.isEmpty()) {
            text.append("没有任务。\n");
        } else {
            for (PmTask task : tasks) {
                String level = DelayRiskRules.level(task.getDueDate(), today, task.getStatus());
                text.append(empty(task.getStoryCode())).append(" ").append(empty(task.getTitle()))
                        .append(" | 状态 ").append(statusLabel(task.getStatus()))
                        .append(" | 截止 ").append(task.getDueDate() == null ? "未设置" : task.getDueDate().toString())
                        .append(" | 规则判断 ").append(level == null ? "无明显延期" : ("high".equals(level) ? "高" : "中"))
                        .append('\n');
            }
        }
        text.append("已打开的预警：\n");
        if (alerts == null || alerts.isEmpty()) {
            text.append("没有。\n");
        } else {
            for (AiRiskAlert alert : alerts) {
                text.append(alert.getRiskLevel()).append(" | ").append(alert.getMessage()).append('\n');
            }
        }
        return text.toString();
    }

    public static String label(String target) {
        return switch (target) {
            case "gantt" -> "甘特图";
            case "member" -> "人员";
            case "risk" -> "风险测评";
            case "board" -> "任务看板";
            default -> "";
        };
    }

    private static void appendTasks(StringBuilder text, List<PmTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            text.append("任务看板上没有任务。\n");
            return;
        }
        for (PmTask task : tasks) {
            text.append(TaskStages.label(TaskStages.of(task.getStoryCode())))
                    .append(" | ").append(empty(task.getStoryCode()))
                    .append(" | ").append(empty(task.getTitle()))
                    .append(" | 状态 ").append(statusLabel(task.getStatus()))
                    .append('\n');
        }
    }

    private static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "todo" -> "待办";
            case "doing" -> "进行中";
            case "done" -> "已完成";
            default -> status == null || status.isBlank() ? "未知" : status;
        };
    }

    private static String empty(String value) {
        return value == null || value.isBlank() ? "无编号" : value;
    }
}
