package com.team22.aiguanli.service;

import com.team22.aiguanli.common.Constants;

import java.util.Set;

/**
 * 状态机与改状态权限。抽成纯函数，方便单测，也方便审查智能体只看这一处。
 */
public final class TaskStatusRules {
    private static final Set<String> ALLOWED = Set.of(
            Constants.STATUS_TODO, Constants.STATUS_DOING, Constants.STATUS_DONE);

    private TaskStatusRules() {
    }

    public static boolean isAllowedStatus(String status) {
        return status != null && ALLOWED.contains(status);
    }

    /**
     * 有 task:update_any 可改任何人；否则只能改自己负责任务。
     */
    public static boolean canUpdateStatus(Set<String> perms, Long userId, Long assigneeId) {
        if (perms != null && perms.contains("task:update_any")) {
            return true;
        }
        return perms != null
                && perms.contains("task:update_own")
                && userId != null
                && userId.equals(assigneeId);
    }
}
