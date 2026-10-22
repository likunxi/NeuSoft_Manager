package com.team22.aiguanli.service;

import com.team22.aiguanli.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiReviewRulesTest {

    @Test
    void rejectCannotWriteBack() {
        AiReviewRules.check("reject", "范围不对");
        assertFalse(AiReviewRules.mayWriteBack("reject"));
    }

    @Test
    void acceptAndModifyCanWriteBack() {
        assertTrue(AiReviewRules.mayWriteBack("accept"));
        assertTrue(AiReviewRules.mayWriteBack("modify"));
    }

    @Test
    void commentIsRequired() {
        BizException ex = assertThrows(BizException.class, () -> AiReviewRules.check("accept", "  "));
        assertEquals(400, ex.getStatus());
    }
}
