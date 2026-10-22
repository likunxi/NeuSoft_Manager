package com.team22.aiguanli.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementSplitterTest {

    @Test
    void splitsNumberedLines() {
        List<SplitItem> items = RequirementSplitter.split("1. 项目经理可以提交需求\n2. 成员不能直接改排期");
        assertEquals(2, items.size());
        assertEquals("项目经理可以提交需求", items.get(0).getTitle());
        assertEquals("成员不能直接改排期", items.get(1).getTitle());
        assertTrue(items.get(0).getTaskTitle().startsWith("实现"));
    }

    @Test
    void splitsChineseSentences() {
        List<SplitItem> items = RequirementSplitter.split("登录后能看见自己的任务。管理员能停用账号。");
        assertEquals(2, items.size());
        assertEquals("管理员能停用账号", items.get(1).getTitle());
    }

    @Test
    void blankTextProducesNothing() {
        assertTrue(RequirementSplitter.split("  ").isEmpty());
    }

    @Test
    void keepsAtMostSixItems() {
        StringBuilder raw = new StringBuilder();
        for (int i = 1; i <= 8; i++) {
            raw.append(i).append(". 需求条目").append(i).append('\n');
        }
        assertEquals(RequirementSplitter.MAX_ITEMS, RequirementSplitter.split(raw.toString()).size());
    }
}
