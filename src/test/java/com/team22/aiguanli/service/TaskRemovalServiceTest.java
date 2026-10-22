package com.team22.aiguanli.service;

import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.PmStory;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.mapper.PmStoryMapper;
import com.team22.aiguanli.mapper.PmTaskDependencyMapper;
import com.team22.aiguanli.mapper.PmTaskMapper;
import com.team22.aiguanli.mapper.PmTaskStatusLogMapper;
import com.team22.aiguanli.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskRemovalServiceTest {

    @Mock
    private PmTaskMapper taskMapper;
    @Mock
    private PmTaskStatusLogMapper logMapper;
    @Mock
    private PmTaskDependencyMapper dependencyMapper;
    @Mock
    private PmStoryMapper storyMapper;

    private TaskRemovalService service;

    @BeforeEach
    void setUp() {
        service = new TaskRemovalService(taskMapper, logMapper, dependencyMapper, storyMapper);
    }

    @Test
    void deleteRemovesTaskLogsAndDependencies() {
        PmTask task = new PmTask();
        task.setId(4L);
        task.setProjectId(1L);
        task.setStoryId(9L);
        task.setStoryCode("E3-04");
        task.setTitle("实现排期");
        when(taskMapper.selectById(4L)).thenReturn(task);
        when(taskMapper.selectCount(any())).thenReturn(0L);

        String title = service.remove(4L, user("task:update_any"));

        assertEquals("实现排期", title);
        verify(dependencyMapper).delete(any());
        verify(logMapper).delete(any());
        verify(taskMapper).deleteById(4L);
        verify(storyMapper).deleteById(9L);
    }

    @Test
    void deleteResequencesLaterCodesInTheSameStage() {
        PmTask gone = task(2L, 2L, "E1-02", "被删");
        when(taskMapper.selectById(2L)).thenReturn(gone);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        PmStory first = story(9L, "E1-01", 10);
        PmStory later = story(3L, "E1-08", 80);
        PmStory otherStage = story(8L, "E2-01", 10);
        when(storyMapper.selectList(any())).thenReturn(List.of(first, later, otherStage));
        PmTask keep = task(9L, 9L, "E1-01", "甲");
        PmTask shifted = task(3L, 3L, "E1-08", "乙");
        PmTask untouched = task(8L, 8L, "E2-01", "丙");
        when(taskMapper.selectList(any())).thenReturn(List.of(keep, shifted, untouched));

        service.remove(2L, user("task:update_any"));

        assertEquals("E1-01", first.getStoryCode());
        assertEquals("E1-02", later.getStoryCode());
        assertEquals(20, later.getSortOrder());
        assertEquals("E2-01", otherStage.getStoryCode());
        assertEquals("E1-02", shifted.getStoryCode());
        assertEquals("E2-01", untouched.getStoryCode());
    }

    @Test
    void memberCannotDelete() {
        BizException ex = assertThrows(BizException.class, () -> service.remove(4L, user("task:update_own")));
        assertEquals(403, ex.getStatus());
        verify(taskMapper, never()).deleteById(any(Long.class));
    }

    private static PmTask task(Long id, Long storyId, String code, String title) {
        PmTask task = new PmTask();
        task.setId(id);
        task.setProjectId(1L);
        task.setStoryId(storyId);
        task.setStoryCode(code);
        task.setTitle(title);
        return task;
    }

    private static PmStory story(Long id, String code, int sortOrder) {
        PmStory story = new PmStory();
        story.setId(id);
        story.setProjectId(1L);
        story.setStoryCode(code);
        story.setSortOrder(sortOrder);
        return story;
    }

    private static LoginUser user(String perm) {
        LoginUser user = new LoginUser();
        user.setId(2L);
        user.setPermissions(Set.of(perm));
        return user;
    }
}
