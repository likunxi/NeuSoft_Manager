package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.entity.PmStory;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.PmTaskStatusLog;
import com.team22.aiguanli.entity.PmUmlArtifact;
import com.team22.aiguanli.entity.SysUser;
import com.team22.aiguanli.mapper.PmStoryMapper;
import com.team22.aiguanli.mapper.PmTaskStatusLogMapper;
import com.team22.aiguanli.mapper.PmUmlArtifactMapper;
import com.team22.aiguanli.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用当前故事和状态日志在本地生成 UML 文本，不调用模型，也不加载外网脚本。
 */
@Service
public class UmlService {

    private final PmStoryMapper storyMapper;
    private final PmTaskStatusLogMapper logMapper;
    private final PmUmlArtifactMapper artifactMapper;
    private final SysUserMapper userMapper;
    private final ProjectService projectService;

    public UmlService(PmStoryMapper storyMapper, PmTaskStatusLogMapper logMapper,
                      PmUmlArtifactMapper artifactMapper, SysUserMapper userMapper,
                      ProjectService projectService) {
        this.storyMapper = storyMapper;
        this.logMapper = logMapper;
        this.artifactMapper = artifactMapper;
        this.userMapper = userMapper;
        this.projectService = projectService;
    }

    public Diagram diagram(Long projectId) {
        projectService.getProject(projectId);
        List<PmStory> stories = storyMapper.selectList(new LambdaQueryWrapper<PmStory>()
                .eq(PmStory::getProjectId, projectId)
                .orderByAsc(PmStory::getSortOrder)
                .orderByAsc(PmStory::getId));
        Diagram diagram = new Diagram();
        diagram.actors = usecaseGroups(stories);
        diagram.usecaseMermaid = usecaseMermaid(stories);
        diagram.steps = sequenceSteps(projectId);
        diagram.sequenceMermaid = sequenceMermaid(diagram.steps);
        diagram.savedAt = latestSavedAt(projectId);
        return diagram;
    }

    public void save(Long projectId, Long userId) {
        Diagram diagram = diagram(projectId);
        insert(projectId, "usecase", "stories", diagram.usecaseMermaid, userId);
        insert(projectId, "sequence", "status-log", diagram.sequenceMermaid, userId);
    }

    public List<ActorGroup> usecaseGroups(List<PmStory> stories) {
        Map<String, ActorGroup> groups = new LinkedHashMap<>();
        if (stories != null) {
            for (PmStory story : stories) {
                String actor = story.getAsRole() == null || story.getAsRole().isBlank() ? "用户" : story.getAsRole();
                ActorGroup group = groups.computeIfAbsent(actor, name -> {
                    ActorGroup created = new ActorGroup();
                    created.name = name;
                    created.cases = new ArrayList<>();
                    return created;
                });
                String code = story.getStoryCode() == null ? "" : story.getStoryCode() + " ";
                group.cases.add(code + (story.getTitle() == null ? "" : story.getTitle()));
            }
        }
        return new ArrayList<>(groups.values());
    }

    public String usecaseMermaid(List<PmStory> stories) {
        List<ActorGroup> groups = usecaseGroups(stories);
        StringBuilder text = new StringBuilder("flowchart LR\n");
        if (groups.isEmpty()) {
            text.append("  empty[\"还没有用户故事\"]\n");
            return text.toString();
        }
        int actorIndex = 0;
        for (ActorGroup group : groups) {
            String actorId = "a" + actorIndex;
            text.append("  ").append(actorId).append("[\"").append(clean(group.name)).append("\"]\n");
            int caseIndex = 0;
            for (String useCase : group.cases) {
                String caseId = actorId + "u" + caseIndex;
                text.append("  ").append(caseId).append("[\"").append(clean(useCase)).append("\"]\n");
                text.append("  ").append(actorId).append(" --> ").append(caseId).append("\n");
                caseIndex++;
            }
            actorIndex++;
        }
        return text.toString();
    }

    public String sequenceMermaid(List<String> steps) {
        StringBuilder text = new StringBuilder("sequenceDiagram\n  actor Member as 成员\n  participant Task as 任务\n");
        if (steps == null || steps.isEmpty()) {
            text.append("  Note over Member,Task: 还没有状态变更\n");
            return text.toString();
        }
        for (String step : steps) {
            text.append("  Member->>Task: ").append(clean(step)).append("\n");
        }
        return text.toString();
    }

    private List<String> sequenceSteps(Long projectId) {
        List<PmTaskStatusLog> logs = logMapper.selectList(new LambdaQueryWrapper<PmTaskStatusLog>()
                .eq(PmTaskStatusLog::getProjectId, projectId)
                .orderByDesc(PmTaskStatusLog::getId)
                .last("LIMIT 8"));
        Map<Long, String> titles = new LinkedHashMap<>();
        for (PmTask task : projectService.listTasks(projectId)) {
            titles.put(task.getId(), task.getTitle());
        }
        List<String> steps = new ArrayList<>();
        for (int i = logs.size() - 1; i >= 0; i--) {
            PmTaskStatusLog log = logs.get(i);
            String title = titles.getOrDefault(log.getTaskId(), "任务");
            steps.add(userName(log.getOperatorId()) + " 把「" + title + "」从 "
                    + ViewSyncService.label(log.getFromStatus()) + " 改为 " + ViewSyncService.label(log.getToStatus()));
        }
        if (!steps.isEmpty()) {
            return steps;
        }
        for (PmTask task : projectService.listTasks(projectId)) {
            if (steps.size() >= 8) {
                break;
            }
            steps.add(userName(task.getAssigneeId()) + " 的「" + task.getTitle() + "」当前是 " + ViewSyncService.label(task.getStatus()));
        }
        return steps;
    }

    private void insert(Long projectId, String type, String sourceRef, String mermaid, Long userId) {
        PmUmlArtifact artifact = new PmUmlArtifact();
        artifact.setProjectId(projectId);
        artifact.setUmlType(type);
        artifact.setSourceRef(sourceRef);
        artifact.setMermaidText(mermaid);
        artifact.setGeneratedAt(LocalDateTime.now());
        artifact.setGeneratedBy(userId);
        artifactMapper.insert(artifact);
    }

    private LocalDateTime latestSavedAt(Long projectId) {
        List<PmUmlArtifact> rows = artifactMapper.selectList(new LambdaQueryWrapper<PmUmlArtifact>()
                .eq(PmUmlArtifact::getProjectId, projectId)
                .orderByDesc(PmUmlArtifact::getId)
                .last("LIMIT 1"));
        if (rows.isEmpty() || rows.get(0).getGeneratedAt() == null) {
            return null;
        }
        return rows.get(0).getGeneratedAt();
    }

    private String userName(Long userId) {
        if (userId == null) {
            return "未分配";
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? "未分配" : user.getDisplayName();
    }

    static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "'").replace("\n", " ");
    }

    public static class Diagram {
        public List<ActorGroup> actors;
        public String usecaseMermaid;
        public List<String> steps;
        public String sequenceMermaid;
        public LocalDateTime savedAt;
    }

    public static class ActorGroup {
        public String name;
        public List<String> cases;
    }
}
