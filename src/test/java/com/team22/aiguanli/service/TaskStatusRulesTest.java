package com.team22.aiguanli.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStatusRulesTest {

    @Test
    void onlyThreeStatusesAllowed() {
        assertTrue(TaskStatusRules.isAllowedStatus("todo"));
        assertTrue(TaskStatusRules.isAllowedStatus("doing"));
        assertTrue(TaskStatusRules.isAllowedStatus("done"));
        assertFalse(TaskStatusRules.isAllowedStatus("finished"));
        assertFalse(TaskStatusRules.isAllowedStatus(null));
    }

    @Test
    void memberCannotUpdateOthersTask() {
        Set<String> member = Set.of("task:update_own");
        assertTrue(TaskStatusRules.canUpdateStatus(member, 3L, 3L));
        assertFalse(TaskStatusRules.canUpdateStatus(member, 3L, 2L));
    }

    @Test
    void pmCanUpdateAnyTask() {
        Set<String> pm = Set.of("task:update_any", "task:update_own");
        assertTrue(TaskStatusRules.canUpdateStatus(pm, 2L, 3L));
    }
}
