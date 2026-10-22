package com.team22.aiguanli.service;

import java.time.LocalDate;

/**
 * 用截止日期和当前状态判断延期风险。已完成的任务不预警。
 */
public final class DelayRiskRules {

    private DelayRiskRules() {
    }

    public static String level(LocalDate due, LocalDate today, String status) {
        if (due == null || "done".equals(status) || today == null) {
            return null;
        }
        if (due.isBefore(today)) {
            return "high";
        }
        if (!due.isAfter(today.plusDays(2))) {
            return "medium";
        }
        return null;
    }
}
