package com.team22.aiguanli.service;

import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.AiPrd;
import com.team22.aiguanli.entity.AiReview;
import com.team22.aiguanli.entity.AiSuggestion;
import com.team22.aiguanli.entity.AiWriteback;
import com.team22.aiguanli.entity.PmProject;
import com.team22.aiguanli.entity.PmStory;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.mapper.AiPrdMapper;
import com.team22.aiguanli.mapper.AiReviewMapper;
import com.team22.aiguanli.mapper.AiRiskAlertMapper;
import com.team22.aiguanli.mapper.AiSuggestionMapper;
import com.team22.aiguanli.mapper.AiWritebackMapper;
import com.team22.aiguanli.mapper.PmStoryMapper;
import com.team22.aiguanli.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistServiceTest {

    @Mock
    private AiPrdMapper prdMapper;
    @Mock
    private AiSuggestionMapper suggestionMapper;
    @Mock
    private AiReviewMapper reviewMapper;
    @Mock
    private AiWritebackMapper writebackMapper;
    @Mock
    private AiRiskAlertMapper riskMapper;
    @Mock
    private PmStoryMapper storyMapper;
    @Mock
    private ProjectService projectService;
    @Mock
    private LlmSplitter llmSplitter;
    @Mock
    private ProjectAnalyst projectAnalyst;
    @Mock
    private GanttService ganttService;
    @Mock
    private MemberMapService memberMapService;

    private AiAssistService service;

    @BeforeEach
    void setUp() {
        service = new AiAssistService(prdMapper, suggestionMapper, reviewMapper, writebackMapper,
                riskMapper, storyMapper, projectService, llmSplitter, projectAnalyst, ganttService, memberMapService);
    }

    @Test
    void memberCannotPropose() {
        BizException ex = assertThrows(BizException.class,
                () -> service.propose(1L, "需求", "项目经理可以提交需求", user(13L)));
        assertEquals(403, ex.getStatus());
        verify(prdMapper, never()).insert(any(AiPrd.class));
    }

    @Test
    void proposeStoresSuggestionWithoutTouchingStories() {
        when(projectService.getProject(1L)).thenReturn(new PmProject());
        when(llmSplitter.split(any())).thenReturn(List.of(
                new SplitItem("项目经理可以提交需求", "项目经理可以提交需求", "实现项目经理可以提交需求"),
                new SplitItem("成员不能直接改排期", "成员不能直接改排期", "实现成员不能直接改排期")));
        when(llmSplitter.agentName()).thenReturn("deepseek-chat");
        service.propose(1L, "拆解", "1. 项目经理可以提交需求\n2. 成员不能直接改排期", user(2L, "task:create"));
        verify(prdMapper).insert(any(AiPrd.class));
        ArgumentCaptor<AiSuggestion> saved = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(suggestionMapper).insert(saved.capture());
        assertEquals("pending", saved.getValue().getStatus());
        assertEquals("split", saved.getValue().getSuggestType());
        assertEquals("deepseek-chat", saved.getValue().getAgentName());
        assertEquals(2, SplitJson.read(saved.getValue().getOutputJson()).size());
        verify(storyMapper, never()).insert(any(PmStory.class));
        verify(writebackMapper, never()).insert(any(AiWriteback.class));
    }

    @Test
    void rejectDoesNotWriteBack() {
        when(projectService.getProject(1L)).thenReturn(new PmProject());
        when(suggestionMapper.selectById(8L)).thenReturn(pendingSuggestion());
        AiAssistService.ReviewResult result = service.review(1L, 8L, "reject", "这条先不做", null, null, null, user(2L, "task:create"));
        assertTrue(result.getMessage().contains("拒绝"));
        assertEquals(0, result.getWritten());
        verify(reviewMapper).insert(any(AiReview.class));
        verify(storyMapper, never()).insert(any(PmStory.class));
        verify(projectService, never()).insertTask(any());
        verify(writebackMapper, never()).insert(any(AiWriteback.class));
    }

    @Test
    void acceptWritesStoryTaskAndWriteback() {
        when(projectService.getProject(1L)).thenReturn(new PmProject());
        when(suggestionMapper.selectById(8L)).thenReturn(pendingSuggestion());
        when(storyMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.insert(any(AiReview.class))).thenAnswer(invocation -> {
            AiReview review = invocation.getArgument(0);
            review.setId(9L);
            return 1;
        });
        when(storyMapper.insert(any(PmStory.class))).thenAnswer(invocation -> {
            PmStory story = invocation.getArgument(0);
            story.setId(21L);
            return 1;
        });
        AiAssistService.ReviewResult result = service.review(1L, 8L, "accept", "可以做", null, null, List.of(0), user(2L, "task:create"));
        assertEquals(1, result.getWritten());
        ArgumentCaptor<PmStory> story = ArgumentCaptor.forClass(PmStory.class);
        verify(storyMapper).insert(story.capture());
        assertEquals("E3-01", story.getValue().getStoryCode());
        assertEquals(3, story.getValue().getSprintNo());
        verify(projectService).insertTask(any(PmTask.class));
        verify(writebackMapper, org.mockito.Mockito.times(2)).insert(any(AiWriteback.class));
    }

    @Test
    void acceptWritesOnlyTheCheckedItem() {
        when(projectService.getProject(1L)).thenReturn(new PmProject());
        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setId(8L);
        suggestion.setProjectId(1L);
        suggestion.setStatus("pending");
        suggestion.setOutputJson(SplitJson.write(List.of(
                new SplitItem("不要这条", "不要这条", "实现不要这条"),
                new SplitItem("只要这条", "只要这条", "实现只要这条"))));
        when(suggestionMapper.selectById(8L)).thenReturn(suggestion);
        when(storyMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.insert(any(AiReview.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AiReview.class).setId(9L);
            return 1;
        });
        when(storyMapper.insert(any(PmStory.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, PmStory.class).setId(21L);
            return 1;
        });

        AiAssistService.ReviewResult result = service.review(1L, 8L, "accept", "只留第二条",
                List.of("不要这条", "改成这条"), List.of("不要这条", "改成这条"), List.of(1), user(2L, "task:create"));

        assertEquals(1, result.getWritten());
        assertTrue(result.getMessage().contains("未勾选的 1 条"));
        ArgumentCaptor<PmStory> story = ArgumentCaptor.forClass(PmStory.class);
        verify(storyMapper).insert(story.capture());
        assertEquals("改成这条", story.getValue().getTitle());
    }

    private static AiSuggestion pendingSuggestion() {
        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setId(8L);
        suggestion.setProjectId(1L);
        suggestion.setStatus("pending");
        suggestion.setOutputJson(SplitJson.write(List.of(new SplitItem("提交需求", "提交一段需求", "实现提交需求"))));
        return suggestion;
    }

    private static LoginUser user(Long id, String... perms) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setPermissions(Set.of(perms));
        return user;
    }
}
