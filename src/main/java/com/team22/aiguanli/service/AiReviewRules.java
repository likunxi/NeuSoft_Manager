package com.team22.aiguanli.service;

import com.team22.aiguanli.common.BizException;

/**
 * W-03：没有人审，或者人审是拒绝，不能写回故事和任务。
 */
public final class AiReviewRules {

    private AiReviewRules() {
    }

    public static void check(String decision, String comment) {
        if (!"accept".equals(decision) && !"modify".equals(decision) && !"reject".equals(decision)) {
            throw new BizException(400, "处置只允许采纳、修改或拒绝");
        }
        if (comment == null || comment.isBlank()) {
            throw new BizException(400, "请填写处置理由");
        }
        if (comment.trim().length() > 500) {
            throw new BizException(400, "处置理由不能超过 500 字");
        }
    }

    public static boolean mayWriteBack(String decision) {
        return "accept".equals(decision) || "modify".equals(decision);
    }
}
