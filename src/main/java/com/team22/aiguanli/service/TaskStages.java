package com.team22.aiguanli.service;

/**
 * 爱管理三阶：故事号 E1 / E2 / E3 分别进三列。进阶 3 仍留空。
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
            case "e2" -> "Sprint 2：故事看板、甘特、成员图、UML 和联动。";
            case "e3" -> "Sprint 3：人审通过后写回的拆解任务，以及延期风险。";
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

    /**
     * 把故事号前缀改成目标进阶。E1-04 拖到进阶 3 变成 E3-04，甘特按同一规则分组。
     */
    public static String retarget(String storyCode, String stage) {
        String head = prefix(stage);
        String rest;
        if (storyCode == null || storyCode.isBlank()) {
            rest = "-T";
        } else {
            String code = storyCode.trim();
            if (code.length() >= 2 && Character.isLetter(code.charAt(0)) && Character.isDigit(code.charAt(1))) {
                rest = code.substring(2);
            } else {
                rest = "-" + code;
            }
        }
        String merged = head + rest;
        return merged.length() <= 16 ? merged : merged.substring(0, 16);
    }
}
