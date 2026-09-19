package com.team22.aiguanli.service;

/**
 * 爱管理三阶：横向分列用。E2/E3 列先留空，避免后两个 Sprint 任务和本轮混在一起。
 */
public final class TaskStages {

    private TaskStages() {
    }

    public static String of(String storyCode) {
        if (storyCode == null) {
            return "e1";
        }
        String code = storyCode.trim().toUpperCase();
        if (code.startsWith("E2") || code.startsWith("S2")) {
            return "e2";
        }
        if (code.startsWith("E3") || code.startsWith("S3")) {
            return "e3";
        }
        return "e1";
    }

    public static String label(String stage) {
        return switch (stage) {
            case "e2" -> "进阶2 · 一体化";
            case "e3" -> "进阶3 · AI化";
            default -> "进阶1 · 可视化";
        };
    }

    public static String hint(String stage) {
        return switch (stage) {
            case "e2" -> "甘特 / 故事地图 / UML，Sprint 2 再往这里放任务。";
            case "e3" -> "智能拆解 / 预测 / 预警，Sprint 3 再往这里放任务。";
            default -> "本轮 Sprint 1 Must 都在这一列。";
        };
    }

    public static String prefix(String stage) {
        return switch (stage) {
            case "e2" -> "E2";
            case "e3" -> "E3";
            default -> "E1";
        };
    }

    public static String normalizeCode(String storyCode, String stage) {
        if (storyCode != null && !storyCode.isBlank()) {
            return storyCode.trim();
        }
        return prefix(stage) + "-T";
    }
}
