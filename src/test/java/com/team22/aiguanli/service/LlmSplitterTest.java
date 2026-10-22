package com.team22.aiguanli.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmSplitterTest {

    @Test
    void readsFencedJson() {
        String raw = "```json\n{\"items\":[{\"title\":\"提交需求\",\"iWant\":\"把需求交给模型\",\"taskTitle\":\"接入模型拆解\"}]}\n```";
        List<SplitItem> items = LlmSplitter.parse(raw);
        assertEquals(1, items.size());
        assertEquals("提交需求", items.get(0).getTitle());
        assertEquals("把需求交给模型", items.get(0).getIWant());
        assertEquals("接入模型拆解", items.get(0).getTaskTitle());
    }

    @Test
    void dropsBlankTitles() {
        List<SplitItem> items = LlmSplitter.parse("{\"items\":[{\"title\":\"  \",\"iWant\":\"空的\"}]}");
        assertTrue(items.isEmpty());
    }
}
