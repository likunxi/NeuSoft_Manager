package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.common.Constants;
import com.team22.aiguanli.entity.PmProject;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.PmTaskStatusLog;
import com.team22.aiguanli.entity.SysUser;
import com.team22.aiguanli.mapper.PmProjectMapper;
import com.team22.aiguanli.mapper.PmTaskMapper;
import com.team22.aiguanli.mapper.PmTaskStatusLogMapper;
import com.team22.aiguanli.mapper.SysUserMapper;
import com.team22.aiguanli.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private final PmProjectMapper projectMapper;
    private final PmTaskMapper taskMapper;
    private final PmTaskStatusLogMapper logMapper;
    private final SysUserMapper userMapper;

    public ProjectService(PmProjectMapper projectMapper, PmTaskMapper taskMapper,
                          PmTaskStatusLogMapper logMapper, SysUserMapper userMapper) {
        this.projectMapper = projectMapper;
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.userMapper = userMapper;
    }

    public List<PmProject> listProjects() {
        return projectMapper.selectList(new LambdaQueryWrapper<PmProject>().orderByAsc(PmProject::getId));
    }

    public PmProject getProject(Long id) {
        PmProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new BizException(404, "项目不存在");
        }
        return project;
    }

    public void createProject(String code, String name, String description, Long userId) {
        if (name == null || name.isBlank()) {
            throw new BizException(400, "项目名不能为空");
        }
        PmProject project = new PmProject();
        project.setProjectCode(code == null || code.isBlank() ? "P" + System.currentTimeMillis() : code.trim());
        project.setName(name.trim());
        project.setDescription(description);
        project.setCreatedBy(userId);
        project.setCreatedAt(LocalDateTime.now());
        projectMapper.insert(project);
    }

    public List<PmTask> listTasks(Long projectId) {
        return taskMapper.selectList(new LambdaQueryWrapper<PmTask>()
                .eq(PmTask::getProjectId, projectId)
                .orderByAsc(PmTask::getId));
    }

    public List<SysUser> listActiveUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsActive, 1)
                .orderByAsc(SysUser::getId));
    }

    public void createTask(Long projectId, String title, String description, Long assigneeId,
                           String storyCode, String stage, LoginUser user) {
        getProject(projectId);
        if (title == null || title.isBlank()) {
            throw new BizException(400, "任务标题不能为空");
        }
        if (!user.has("task:create")) {
            throw new BizException(403, "没有创建任务权限");
        }
        PmTask task = new PmTask();
        task.setProjectId(projectId);
        task.setTitle(title.trim());
        task.setDescription(description);
        task.setStoryCode(TaskStages.normalizeCode(storyCode, stage));
        task.setAssigneeId(assigneeId);
        task.setCreatedBy(user.getId());
        task.setStatus(Constants.STATUS_TODO);
        task.setProgress(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
    }

    public List<PmTask> listAllTasks(Long projectId, String status, Long assigneeId, String keyword) {
        LambdaQueryWrapper<PmTask> q = new LambdaQueryWrapper<PmTask>();
        if (projectId != null) {
            q.eq(PmTask::getProjectId, projectId);
        }
        if (status != null && !status.isBlank()) {
            q.eq(PmTask::getStatus, status);
        }
        if (assigneeId != null) {
            q.eq(PmTask::getAssigneeId, assigneeId);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(PmTask::getTitle, keyword).or().like(PmTask::getStoryCode, keyword));
        }
        return taskMapper.selectList(q.orderByAsc(PmTask::getId));
    }

    public Map<String, List<PmTask>> groupByStage(List<PmTask> tasks) {
        Map<String, List<PmTask>> stages = new LinkedHashMap<>();
        stages.put("e1", new ArrayList<>());
        stages.put("e2", new ArrayList<>());
        stages.put("e3", new ArrayList<>());
        for (PmTask task : tasks) {
            stages.get(TaskStages.of(task.getStoryCode())).add(task);
        }
        return stages;
    }

    public List<PmTask> listTasks(Long projectId, String status, Long assigneeId, String keyword) {
        LambdaQueryWrapper<PmTask> q = new LambdaQueryWrapper<PmTask>().eq(PmTask::getProjectId, projectId);
        if (status != null && !status.isBlank()) {
            q.eq(PmTask::getStatus, status);
        }
        if (assigneeId != null) {
            q.eq(PmTask::getAssigneeId, assigneeId);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(PmTask::getTitle, keyword).or().like(PmTask::getStoryCode, keyword));
        }
        return taskMapper.selectList(q.orderByAsc(PmTask::getId));
    }

    public List<PmTask> listMyTasks(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<PmTask>()
                .eq(PmTask::getAssigneeId, userId)
                .orderByAsc(PmTask::getStatus)
                .orderByAsc(PmTask::getId));
    }

    public List<PmTask> searchTasks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return taskMapper.selectList(new LambdaQueryWrapper<PmTask>()
                .like(PmTask::getTitle, keyword)
                .or()
                .like(PmTask::getStoryCode, keyword)
                .orderByAsc(PmTask::getId));
    }

    @Transactional
    public void assign(Long taskId, Long assigneeId, LoginUser user) {
        if (!user.has("task:assign")) {
            throw new BizException(403, "没有指定负责人权限");
        }
        PmTask task = mustGet(taskId);
        task.setAssigneeId(assigneeId);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 改状态：校验枚举和权限，维护 done_at，并写状态日志供趋势使用。
     */
    @Transactional
    public void changeStatus(Long taskId, String toStatus, LoginUser user) {
        if (!TaskStatusRules.isAllowedStatus(toStatus)) {
            throw new BizException(400, "状态只允许 todo / doing / done");
        }
        PmTask task = mustGet(taskId);
        if (!TaskStatusRules.canUpdateStatus(user.getPermissions(), user.getId(), task.getAssigneeId())) {
            throw new BizException(403, "只能更新自己负责的任务");
        }
        String from = task.getStatus();
        if (from.equals(toStatus)) {
            return;
        }
        task.setStatus(toStatus);
        task.setUpdatedAt(LocalDateTime.now());
        if (Constants.STATUS_DONE.equals(toStatus)) {
            task.setDoneAt(LocalDateTime.now());
            task.setProgress(100);
        } else {
            task.setDoneAt(null);
            if (Constants.STATUS_TODO.equals(toStatus)) {
                task.setProgress(0);
            }
        }
        taskMapper.updateById(task);

        PmTaskStatusLog log = new PmTaskStatusLog();
        log.setTaskId(task.getId());
        log.setProjectId(task.getProjectId());
        log.setFromStatus(from);
        log.setToStatus(toStatus);
        log.setOperatorId(user.getId());
        log.setRemark("页面操作");
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    public List<PmTaskStatusLog> timeline(Long projectId) {
        return logMapper.selectList(new LambdaQueryWrapper<PmTaskStatusLog>()
                .eq(PmTaskStatusLog::getProjectId, projectId)
                .orderByDesc(PmTaskStatusLog::getCreatedAt));
    }

    public List<PmTaskStatusLog> recentLogs(int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        return logMapper.selectList(new LambdaQueryWrapper<PmTaskStatusLog>()
                .orderByDesc(PmTaskStatusLog::getCreatedAt)
                .last("LIMIT " + size));
    }

    public Map<Long, String> taskTitleMap() {
        return taskMapper.selectList(new LambdaQueryWrapper<PmTask>())
                .stream()
                .collect(Collectors.toMap(PmTask::getId, PmTask::getTitle, (a, b) -> a));
    }

    public String userName(Long userId) {
        if (userId == null) {
            return "未分配";
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? "未分配" : user.getDisplayName();
    }

    private PmTask mustGet(Long id) {
        PmTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(404, "任务不存在");
        }
        return task;
    }
}
