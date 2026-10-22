package com.team22.aiguanli.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DelayRiskRulesTest {

    @Test
    void overdueOpenTaskIsHigh() {
        LocalDate today = LocalDate.of(2026, 10, 20);
        assertEquals("high", DelayRiskRules.level(today.minusDays(1), today, "doing"));
    }

    @Test
    void dueWithinTwoDaysIsMedium() {
        LocalDate today = LocalDate.of(2026, 10, 20);
        assertEquals("medium", DelayRiskRules.level(today.plusDays(2), today, "todo"));
    }

    @Test
    void doneOrFarAwayIsQuiet() {
        LocalDate today = LocalDate.of(2026, 10, 20);
        assertNull(DelayRiskRules.level(today.minusDays(1), today, "done"));
        assertNull(DelayRiskRules.level(today.plusDays(3), today, "todo"));
    }
}
