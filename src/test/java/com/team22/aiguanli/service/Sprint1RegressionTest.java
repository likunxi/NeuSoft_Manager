package com.team22.aiguanli.service;

import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.PmTaskStatusLog;
import com.team22.aiguanli.mapper.PmProjectMapper;
import com.team22.aiguanli.mapper.PmTaskMapper;
import com.team22.aiguanli.mapper.PmTaskStatusLogMapper;
import com.team22.aiguanli.mapper.SysUserMapper;
import com.team22.aiguanli.security.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 2 合入前的回归：新联动不能放宽 Sprint 1 的状态和权限。
 */
@ExtendWith(MockitoExtension.class)
class Sprint1RegressionTest {

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

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectMapper, taskMapper, logMapper, userMapper, viewSyncService);
    }

    @Test
    void memberCannotChangeSomeoneElsesTask() {
        when(taskMapper.selectById(7L)).thenReturn(task(7L, 12L, "todo"));
        BizException ex = assertThrows(BizException.class,
                () -> service.changeStatus(7L, "doing", user(13L, "task:update_own")));
        assertEquals(403, ex.getStatus());
        verify(taskMapper, never()).updateById(any(PmTask.class));
        verify(logMapper, never()).insert(any(PmTaskStatusLog.class));
    }

    @Test
    void finishedNeverReachesTheDatabase() {
        BizException ex = assertThrows(BizException.class,
                () -> service.changeStatus(7L, "finished", user(13L, "task:update_any")));
        assertEquals(400, ex.getStatus());
        verify(taskMapper, never()).selectById(any());
        verify(taskMapper, never()).updateById(any(PmTask.class));
        verify(logMapper, never()).insert(any(PmTaskStatusLog.class));
    }

    @Test
    void markingDoneWritesStatusLog() {
        PmTask task = task(7L, 13L, "doing");
        task.setProgress(40);
        when(taskMapper.selectById(7L)).thenReturn(task);
        LoginUser member = user(13L, "task:update_own");

        service.changeStatus(7L, "done", member);

        assertEquals("done", task.getStatus());
        assertEquals(100, task.getProgress());
        assertNotNull(task.getDoneAt());
        ArgumentCaptor<PmTaskStatusLog> log = ArgumentCaptor.forClass(PmTaskStatusLog.class);
        verify(logMapper).insert(log.capture());
        assertEquals("doing", log.getValue().getFromStatus());
        assertEquals("done", log.getValue().getToStatus());
        assertEquals(13L, log.getValue().getOperatorId());
        assertEquals("页面操作", log.getValue().getRemark());
        verify(viewSyncService).onTaskStatusChanged(task, "doing", "done", member);
    }

    @Test
    void movingBackToTodoClearsDoneAndWritesLog() {
        PmTask task = task(7L, 13L, "done");
        task.setProgress(100);
        when(taskMapper.selectById(7L)).thenReturn(task);

        service.changeStatus(7L, "todo", user(13L, "task:update_own"));

        assertEquals("todo", task.getStatus());
        assertEquals(0, task.getProgress());
        assertNull(task.getDoneAt());
        ArgumentCaptor<PmTaskStatusLog> log = ArgumentCaptor.forClass(PmTaskStatusLog.class);
        verify(logMapper).insert(log.capture());
        assertEquals("done", log.getValue().getFromStatus());
        assertEquals("todo", log.getValue().getToStatus());
    }

    private static PmTask task(Long id, Long assigneeId, String status) {
        PmTask task = new PmTask();
        task.setId(id);
        task.setProjectId(1L);
        task.setAssigneeId(assigneeId);
        task.setStatus(status);
        task.setTitle("监控进度与完成趋势");
        return task;
    }

    private static LoginUser user(Long id, String... perms) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setPermissions(Set.of(perms));
        return user;
    }
}
