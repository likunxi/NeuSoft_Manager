package com.team22.aiguanli.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 按换行、编号和中文断句把一段需求拆成建议。不调用模型。
 */
public final class RequirementSplitter {

    static final int MAX_ITEMS = 6;
    private static final Pattern MARK = Pattern.compile("(?<=[。；;！？!])|\r?\n+");
    private static final Pattern LEAD = Pattern.compile("^\\s*(?:\\d+[.、.)]|[-*])\\s*");

    private RequirementSplitter() {
    }

    public static List<SplitItem> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String piece : MARK.split(raw.trim())) {
            String line = LEAD.matcher(piece).replaceFirst("").trim();
            line = line.replaceAll("^[。；;！？!\\s]+|[。；;！？!\\s]+$", "");
            if (line.length() >= 2) {
                parts.add(line);
            }
        }
        if (parts.isEmpty()) {
            parts.add(raw.trim());
        }
        if (parts.size() > MAX_ITEMS) {
            parts = parts.subList(0, MAX_ITEMS);
        }
        List<SplitItem> items = new ArrayList<>();
        for (String part : parts) {
            String title = cut(part, 40);
            String want = cut(part, 180);
            items.add(new SplitItem(title, want, cut("实现" + title, 80)));
        }
        return items;
    }

    static String cut(String text, int max) {
        String value = text == null ? "" : text.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }
}
