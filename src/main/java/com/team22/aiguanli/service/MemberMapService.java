package com.team22.aiguanli.service;

import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.SysUser;
import com.team22.aiguanli.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemberMapService {

    private final ProjectService projectService;
    private final SysUserMapper userMapper;

    public MemberMapService(ProjectService projectService, SysUserMapper userMapper) {
        this.projectService = projectService;
        this.userMapper = userMapper;
    }

    public List<Column> columns(Long projectId) {
        projectService.getProject(projectId);
        Map<Long, Column> map = new LinkedHashMap<>();
        for (PmTask task : projectService.listTasks(projectId)) {
            Long key = task.getAssigneeId() == null ? -1L : task.getAssigneeId();
            Column column = map.computeIfAbsent(key, id -> {
                Column created = new Column();
                created.userId = task.getAssigneeId();
                created.name = nameOf(task.getAssigneeId());
                created.tasks = new ArrayList<>();
                return created;
            });
            column.tasks.add(task);
            column.total++;
            if ("doing".equals(task.getStatus())) {
                column.doing++;
            } else if ("done".equals(task.getStatus())) {
                column.done++;
            } else {
                column.todo++;
            }
        }
        List<Column> columns = new ArrayList<>();
        Column unassigned = null;
        for (Column column : map.values()) {
            if (column.userId == null) {
                unassigned = column;
            } else {
                columns.add(column);
            }
        }
        if (unassigned != null) {
            columns.add(unassigned);
        }
        return columns;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return "未分配";
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? "未分配" : user.getDisplayName();
    }

    public static class Column {
        public Long userId;
        public String name;
        public List<PmTask> tasks;
        public int total;
        public int todo;
        public int doing;
        public int done;
    }
}
