package com.team22.aiguanli.service;

import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.PmStory;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.mapper.PmProjectMapper;
import com.team22.aiguanli.mapper.PmStoryMapper;
import com.team22.aiguanli.mapper.PmTaskDependencyMapper;
import com.team22.aiguanli.mapper.PmTaskMapper;
import com.team22.aiguanli.mapper.PmTaskStatusLogMapper;
import com.team22.aiguanli.mapper.SysUserMapper;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskStageMoveTest {

    @Mock
    private PmProjectMapper projectMapper;
    @Mock
    private PmTaskMapper taskMapper;
    @Mock
    private PmTaskStatusLogMapper logMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ViewSyncService viewSyncService;
    @Mock
    private PmStoryMapper storyMapper;
    @Mock
    private PmTaskDependencyMapper dependencyMapper;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        TaskRemovalService removal = new TaskRemovalService(taskMapper, logMapper, dependencyMapper, storyMapper);
        service = new ProjectService(projectMapper, taskMapper, logMapper, userMapper, viewSyncService, removal);
    }

    @Test
    void draggingContinuesTheNumberInTheTargetStage() {
        PmTask moved = task(4L, 4L, "E1-04");
        PmTask below = task(6L, 6L, "E1-06");
        PmTask already = task(20L, 20L, "E3-01");
        PmStory movedStory = story(4L, "E1-04");
        PmStory belowStory = story(6L, "E1-06");
        PmStory alreadyStory = story(20L, "E3-01");
        when(taskMapper.selectById(4L)).thenReturn(moved);
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(storyMapper.selectById(4L)).thenReturn(movedStory);
        when(storyMapper.selectList(any())).thenReturn(List.of(movedStory, belowStory, alreadyStory));
        when(taskMapper.selectList(any())).thenReturn(List.of(moved, below, already));

        service.moveStage(4L, "e3", user("task:update_any"));

        assertEquals("E3-02", moved.getStoryCode());
        assertEquals("E3-02", movedStory.getStoryCode());
        assertEquals("E3-01", already.getStoryCode());
        assertEquals("E1-01", below.getStoryCode());
        assertEquals("E1-01", belowStory.getStoryCode());
    }

    @Test
    void memberCannotDragStages() {
        LoginUser member = user("task:update_own");
        BizException ex = assertThrows(BizException.class, () -> service.moveStage(4L, "e2", member));
        assertEquals(403, ex.getStatus());
    }

    private static PmTask task(Long id, Long storyId, String code) {
        PmTask task = new PmTask();
        task.setId(id);
        task.setProjectId(1L);
        task.setStoryId(storyId);
        task.setStoryCode(code);
        return task;
    }

    private static PmStory story(Long id, String code) {
        PmStory story = new PmStory();
        story.setId(id);
        story.setProjectId(1L);
        story.setStoryCode(code);
        return story;
    }

    private static LoginUser user(String... perms) {
        LoginUser user = new LoginUser();
        user.setId(2L);
        user.setPermissions(Set.of(perms));
        return user;
    }
}
