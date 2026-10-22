package com.team22.aiguanli.service;

import com.team22.aiguanli.entity.PmStory;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.VwLinkRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Sprint2ViewTest {

    @Test
    void disabledRuleDoesNotMatch() {
        VwLinkRule rule = rule(0);
        assertFalse(ViewSyncService.matches(rule, "story_board", "gantt", "story_changed"));
        rule.setIsEnabled(1);
        assertTrue(ViewSyncService.matches(rule, "story_board", "gantt", "story_changed"));
        assertFalse(ViewSyncService.matches(rule, "board", "gantt", "story_changed"));
    }

    @Test
    void summaryRoundTrip() {
        String json = "{\"summary\":\"" + ViewSyncService.escape("故事 E1-01 改为 进行中") + "\"}";
        assertEquals("故事 E1-01 改为 进行中", ViewSyncService.summaryOf(json));
    }

    @Test
    void usecaseUsesStoryRoleAndTitle() {
        PmStory story = new PmStory();
        story.setAsRole("项目经理");
        story.setStoryCode("E1-04");
        story.setTitle("创建项目与任务");
        UmlService service = new UmlService(null, null, null, null, null);
        String mermaid = service.usecaseMermaid(List.of(story));
        assertTrue(mermaid.contains("项目经理"));
        assertTrue(mermaid.contains("E1-04 创建项目与任务"));
        assertEquals(1, service.usecaseGroups(List.of(story)).size());
    }

    @Test
    void storyLinksTasksByCodeOrId() {
        PmStory story = new PmStory();
        story.setId(4L);
        story.setStoryCode("E1-04");
        PmTask byId = new PmTask();
        byId.setStoryId(4L);
        byId.setTitle("甲");
        PmTask byCode = new PmTask();
        byCode.setStoryCode("E1-04");
        byCode.setTitle("乙");
        PmTask other = new PmTask();
        other.setStoryCode("E1-05");
        other.setTitle("丙");
        List<PmTask> hit = StoryBoardService.tasksOf(story, List.of(byId, byCode, other));
        assertEquals(2, hit.size());
    }

    private static VwLinkRule rule(int enabled) {
        VwLinkRule rule = new VwLinkRule();
        rule.setIsEnabled(enabled);
        rule.setSourceView("story_board");
        rule.setTargetView("gantt");
        rule.setEventType("story_changed");
        return rule;
    }
}
