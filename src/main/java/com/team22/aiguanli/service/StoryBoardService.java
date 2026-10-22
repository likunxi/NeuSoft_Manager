package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.PmStory;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.mapper.PmStoryMapper;
import com.team22.aiguanli.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StoryBoardService {

    private final PmStoryMapper storyMapper;
    private final ProjectService projectService;
    private final ViewSyncService viewSyncService;

    public StoryBoardService(PmStoryMapper storyMapper, ProjectService projectService, ViewSyncService viewSyncService) {
        this.storyMapper = storyMapper;
        this.projectService = projectService;
        this.viewSyncService = viewSyncService;
    }

    public Board board(Long projectId) {
        projectService.getProject(projectId);
        List<PmStory> stories = storyMapper.selectList(new LambdaQueryWrapper<PmStory>()
                .eq(PmStory::getProjectId, projectId)
                .orderByAsc(PmStory::getSortOrder)
                .orderByAsc(PmStory::getId));
        List<PmTask> tasks = projectService.listTasks(projectId);
        Board board = new Board();
        board.columns = new LinkedHashMap<>();
        board.columns.put("todo", new ArrayList<>());
        board.columns.put("doing", new ArrayList<>());
        board.columns.put("done", new ArrayList<>());
        for (PmStory story : stories) {
            Card card = new Card();
            card.story = story;
            card.tasks = tasksOf(story, tasks);
            String status = board.columns.containsKey(story.getStatus()) ? story.getStatus() : "todo";
            board.columns.get(status).add(card);
        }
        board.total = stories.size();
        return board;
    }

    /**
     * 改故事状态。联动开关打开时，把同一故事下的任务改成同一状态，甘特颜色跟着变。
     */
    @Transactional
    public void changeStatus(Long projectId, Long storyId, String toStatus, LoginUser user) {
        if (user == null || !user.has("task:update_any")) {
            throw new BizException(403, "没有修改故事状态的权限");
        }
        if (!TaskStatusRules.isAllowedStatus(toStatus)) {
            throw new BizException(400, "状态只允许 todo / doing / done");
        }
        PmStory story = storyMapper.selectById(storyId);
        if (story == null || !projectId.equals(story.getProjectId())) {
            throw new BizException(404, "故事不存在");
        }
        String from = story.getStatus();
        if (toStatus.equals(from)) {
            return;
        }
        story.setStatus(toStatus);
        story.setUpdatedAt(LocalDateTime.now());
        storyMapper.updateById(story);
        if (!viewSyncService.enabled(projectId, ViewSyncService.STORY_BOARD, ViewSyncService.GANTT, ViewSyncService.STORY_CHANGED)) {
            return;
        }
        int synced = 0;
        for (PmTask task : tasksOf(story, projectService.listTasks(projectId))) {
            String old = task.getStatus();
            if (projectService.applyStatus(task, toStatus, user, "故事同步到甘特")) {
                synced++;
                viewSyncService.onTaskStatusChanged(task, old, toStatus, user);
            }
        }
        String summary = "故事 " + story.getStoryCode() + " 从 " + ViewSyncService.label(from)
                + " 改为 " + ViewSyncService.label(toStatus) + "，已同步 " + synced + " 条任务到甘特";
        viewSyncService.record(projectId, ViewSyncService.STORY_BOARD, ViewSyncService.GANTT,
                ViewSyncService.STORY_CHANGED, summary, user.getId());
    }

    static List<PmTask> tasksOf(PmStory story, List<PmTask> tasks) {
        List<PmTask> hit = new ArrayList<>();
        for (PmTask task : tasks) {
            boolean sameId = story.getId() != null && story.getId().equals(task.getStoryId());
            boolean sameCode = story.getStoryCode() != null && story.getStoryCode().equals(task.getStoryCode());
            if (sameId || sameCode) {
                hit.add(task);
            }
        }
        return hit;
    }

    public static class Board {
        public Map<String, List<Card>> columns;
        public int total;
    }

    public static class Card {
        public PmStory story;
        public List<PmTask> tasks;
    }
}
