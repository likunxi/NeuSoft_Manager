package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.PmStory;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.PmTaskDependency;
import com.team22.aiguanli.entity.PmTaskStatusLog;
import com.team22.aiguanli.mapper.PmStoryMapper;
import com.team22.aiguanli.mapper.PmTaskDependencyMapper;
import com.team22.aiguanli.mapper.PmTaskMapper;
import com.team22.aiguanli.mapper.PmTaskStatusLogMapper;
import com.team22.aiguanli.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 删除任务时一并去掉状态日志和甘特依赖。甘特、成员图、趋势都是重读任务表，所以会跟着变。
 */
@Service
public class TaskRemovalService {

    private final PmTaskMapper taskMapper;
    private final PmTaskStatusLogMapper logMapper;
    private final PmTaskDependencyMapper dependencyMapper;
    private final PmStoryMapper storyMapper;

    public TaskRemovalService(PmTaskMapper taskMapper,
                              PmTaskStatusLogMapper logMapper,
                              PmTaskDependencyMapper dependencyMapper,
                              PmStoryMapper storyMapper) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.dependencyMapper = dependencyMapper;
        this.storyMapper = storyMapper;
    }

    @Transactional
    public String remove(Long taskId, LoginUser user) {
        if (user == null || !user.has("task:update_any")) {
            throw new BizException(403, "没有权限删除任务");
        }
        PmTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在");
        }
        dependencyMapper.delete(new LambdaQueryWrapper<PmTaskDependency>()
                .and(w -> w.eq(PmTaskDependency::getFromTaskId, taskId).or().eq(PmTaskDependency::getToTaskId, taskId)));
        logMapper.delete(new LambdaQueryWrapper<PmTaskStatusLog>().eq(PmTaskStatusLog::getTaskId, taskId));
        taskMapper.deleteById(taskId);
        removeStoryIfEmpty(task);
        resequenceStage(task.getProjectId(), TaskStages.of(task.getStoryCode()));
        return task.getTitle() == null ? "任务" : task.getTitle();
    }

    /**
     * 拖进目标进阶时排到该进阶最后一个号，原来那一列剩下的号再连成 E1-01、E1-02。
     */
    public void placeInStage(PmTask task, String stage) {
        String from = TaskStages.of(task.getStoryCode());
        if (from.equals(stage)) {
            return;
        }
        String tail = TaskStages.prefix(stage) + "-Z";
        task.setStoryCode(tail);
        task.setUpdatedAt(java.time.LocalDateTime.now());
        taskMapper.updateById(task);
        if (task.getStoryId() != null) {
            Long others = taskMapper.selectCount(new LambdaQueryWrapper<PmTask>()
                    .eq(PmTask::getStoryId, task.getStoryId())
                    .ne(PmTask::getId, task.getId()));
            if (others == null || others == 0) {
                PmStory story = storyMapper.selectById(task.getStoryId());
                if (story != null) {
                    story.setStoryCode(tail);
                    storyMapper.updateById(story);
                }
            }
        }
        resequenceStage(task.getProjectId(), from);
        resequenceStage(task.getProjectId(), stage);
    }

    public void resequenceStage(Long projectId, String stage) {
        resequence(projectId, stage);
    }

    private void removeStoryIfEmpty(PmTask task) {
        if (task.getStoryId() == null) {
            return;
        }
        Long left = taskMapper.selectCount(new LambdaQueryWrapper<PmTask>()
                .eq(PmTask::getProjectId, task.getProjectId())
                .and(w -> w.eq(PmTask::getStoryId, task.getStoryId())
                        .or()
                        .eq(task.getStoryCode() != null, PmTask::getStoryCode, task.getStoryCode())));
        if (left == null || left == 0) {
            storyMapper.deleteById(task.getStoryId());
        }
    }

    /**
     * 同一进阶里的故事号重新排成 E1-01、E1-02 这种连续序号。别的进阶不动。
     */
    private void resequence(Long projectId, String stage) {
        if (projectId == null) {
            return;
        }
        String prefix = TaskStages.prefix(stage);
        List<PmStory> stories = storyMapper.selectList(new LambdaQueryWrapper<PmStory>()
                .eq(PmStory::getProjectId, projectId));
        List<PmTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PmTask>()
                .eq(PmTask::getProjectId, projectId));
        if (stories == null) {
            stories = List.of();
        }
        if (tasks == null) {
            tasks = List.of();
        }
        List<Slot> slots = new ArrayList<>();
        for (PmStory story : stories) {
            if (!stage.equals(TaskStages.of(story.getStoryCode()))) {
                continue;
            }
            Slot slot = new Slot();
            slot.story = story;
            slot.oldCode = story.getStoryCode();
            slot.number = numberOf(story.getStoryCode());
            slot.tie = story.getId() == null ? 0L : story.getId();
            slots.add(slot);
        }
        for (PmTask task : tasks) {
            if (!stage.equals(TaskStages.of(task.getStoryCode()))) {
                continue;
            }
            boolean covered = false;
            for (Slot slot : slots) {
                if (slot.story != null && slot.story.getId() != null && slot.story.getId().equals(task.getStoryId())) {
                    covered = true;
                    break;
                }
                if (task.getStoryCode() != null && task.getStoryCode().equals(slot.oldCode)) {
                    covered = true;
                    break;
                }
            }
            if (covered) {
                continue;
            }
            Slot slot = new Slot();
            slot.oldCode = task.getStoryCode();
            slot.number = numberOf(task.getStoryCode());
            slot.tie = task.getId() == null ? 0L : task.getId();
            slots.add(slot);
        }
        slots.sort(Comparator.comparingInt((Slot slot) -> slot.number).thenComparingLong(slot -> slot.tie));
        for (Slot slot : slots) {
            if (slot.story == null || slot.story.getId() == null) {
                continue;
            }
            slot.story.setStoryCode(tempCode(slot.story.getId()));
            storyMapper.updateById(slot.story);
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            String code = prefix + "-" + String.format("%02d", i + 1);
            int sortOrder = (i + 1) * 10;
            if (slot.story != null) {
                slot.story.setStoryCode(code);
                slot.story.setSortOrder(sortOrder);
                storyMapper.updateById(slot.story);
            }
            for (PmTask task : tasks) {
                if (!stage.equals(TaskStages.of(task.getStoryCode()))) {
                    continue;
                }
                boolean match = slot.story != null && slot.story.getId() != null && slot.story.getId().equals(task.getStoryId());
                if (!match && slot.oldCode != null && slot.oldCode.equals(task.getStoryCode())) {
                    match = true;
                }
                if (!match || code.equals(task.getStoryCode())) {
                    continue;
                }
                task.setStoryCode(code);
                taskMapper.updateById(task);
            }
        }
    }

    private static int numberOf(String code) {
        if (code == null) {
            return Integer.MAX_VALUE;
        }
        int dash = code.lastIndexOf('-');
        String rest = dash >= 0 ? code.substring(dash + 1) : code;
        int end = 0;
        while (end < rest.length() && Character.isDigit(rest.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(rest.substring(0, end));
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static String tempCode(Long id) {
        String code = "T" + id;
        return code.length() <= 16 ? code : code.substring(0, 16);
    }

    private static class Slot {
        private PmStory story;
        private String oldCode;
        private int number;
        private long tie;
    }
}
