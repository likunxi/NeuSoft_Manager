package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.AiReview;
import com.team22.aiguanli.entity.AiSuggestion;
import com.team22.aiguanli.entity.PmStory;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.PmTaskDependency;
import com.team22.aiguanli.entity.SysUser;
import com.team22.aiguanli.mapper.AiReviewMapper;
import com.team22.aiguanli.mapper.AiSuggestionMapper;
import com.team22.aiguanli.mapper.PmStoryMapper;
import com.team22.aiguanli.mapper.PmTaskDependencyMapper;
import com.team22.aiguanli.mapper.PmTaskMapper;
import com.team22.aiguanli.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 智能排期只出建议。人点写入甘特之后才改任务的开始日、截止日和未分配的负责人。
 */
@Service
public class ScheduleService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ProjectService projectService;
    private final PmStoryMapper storyMapper;
    private final PmTaskMapper taskMapper;
    private final PmTaskDependencyMapper dependencyMapper;
    private final AiSuggestionMapper suggestionMapper;
    private final AiReviewMapper reviewMapper;
    private final ViewSyncService viewSyncService;

    public ScheduleService(ProjectService projectService, PmStoryMapper storyMapper, PmTaskMapper taskMapper,
                           PmTaskDependencyMapper dependencyMapper, AiSuggestionMapper suggestionMapper,
                           AiReviewMapper reviewMapper, ViewSyncService viewSyncService) {
        this.projectService = projectService;
        this.storyMapper = storyMapper;
        this.taskMapper = taskMapper;
        this.dependencyMapper = dependencyMapper;
        this.suggestionMapper = suggestionMapper;
        this.reviewMapper = reviewMapper;
        this.viewSyncService = viewSyncService;
    }

    public PlanCard pending(Long projectId) {
        projectService.getProject(projectId);
        AiSuggestion row = suggestionMapper.selectOne(new LambdaQueryWrapper<AiSuggestion>()
                .eq(AiSuggestion::getProjectId, projectId)
                .eq(AiSuggestion::getSuggestType, "schedule")
                .eq(AiSuggestion::getStatus, "pending")
                .orderByDesc(AiSuggestion::getId)
                .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        PlanCard card = new PlanCard();
        card.id = row.getId();
        card.createdAt = row.getCreatedAt();
        card.slots = read(row.getOutputJson());
        return card;
    }

    @Transactional
    public int propose(Long projectId, LoginUser user) {
        requireCreator(user);
        projectService.getProject(projectId);
        List<PmTask> tasks = projectService.listTasks(projectId);
        List<SchedulePlanner.Slot> slots = SchedulePlanner.plan(LocalDate.now(), items(tasks), edges(projectId), people(tasks));
        if (slots.isEmpty()) {
            throw new BizException(400, "没有待办任务需要排期");
        }
        supersede(projectId, user);
        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setProjectId(projectId);
        suggestion.setSuggestType("schedule");
        suggestion.setAgentName("规则排期");
        suggestion.setSourceTable("pm_task");
        suggestion.setInputDigest(RequirementSplitter.cut("待办 " + slots.size() + " 条", 200));
        suggestion.setOutputJson(write(slots));
        suggestion.setStatus("pending");
        suggestion.setCreatedAt(LocalDateTime.now());
        suggestionMapper.insert(suggestion);
        return slots.size();
    }

    @Transactional
    public String apply(Long projectId, Long suggestionId, String decision, LoginUser user) {
        requireCreator(user);
        projectService.getProject(projectId);
        if (!"accept".equals(decision) && !"reject".equals(decision)) {
            throw new BizException(400, "请选择写入甘特或不采用");
        }
        AiSuggestion suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null || !projectId.equals(suggestion.getProjectId()) || !"schedule".equals(suggestion.getSuggestType())) {
            throw new BizException(404, "排期建议不存在");
        }
        if (!"pending".equals(suggestion.getStatus())) {
            throw new BizException(400, "这条排期建议已经处理过");
        }
        List<SchedulePlanner.Slot> slots = read(suggestion.getOutputJson());
        int written = 0;
        if ("accept".equals(decision)) {
            written = writeBack(projectId, slots);
        }
        AiReview review = new AiReview();
        review.setSuggestionId(suggestion.getId());
        review.setReviewerId(user.getId());
        review.setDecision("accept".equals(decision) ? "accept" : "reject");
        review.setComment("accept".equals(decision) ? "确认后写入甘特" : "不采用这版排期");
        review.setDecidedAt(LocalDateTime.now());
        reviewMapper.insert(review);
        suggestion.setStatus("reviewed");
        suggestionMapper.updateById(suggestion);
        if ("reject".equals(decision)) {
            return "未写入甘特，任务日期保持原样。";
        }
        viewSyncService.noteSchedule(projectId, scheduleSummary(written, slots), user.getId());
        return "已按建议写入 " + written + " 条待办的开始日和截止日。甘特图底部留下了这条记录。";
    }

    private static String scheduleSummary(int written, List<SchedulePlanner.Slot> slots) {
        StringBuilder codes = new StringBuilder();
        for (SchedulePlanner.Slot slot : slots) {
            String code = slot.getStoryCode();
            if (code == null || code.isBlank()) {
                continue;
            }
            if (codes.length() > 0) {
                codes.append("、");
            }
            codes.append(code);
        }
        String text = "已写入 " + written + " 条待办的开始日和截止日";
        if (codes.length() > 0) {
            text = text + "：" + codes;
        }
        return text;
    }

    private int writeBack(Long projectId, List<SchedulePlanner.Slot> slots) {
        int written = 0;
        for (SchedulePlanner.Slot slot : slots) {
            if (slot.getTaskId() == null) {
                continue;
            }
            PmTask task = taskMapper.selectById(slot.getTaskId());
            if (task == null || !projectId.equals(task.getProjectId()) || !"todo".equals(task.getStatus())) {
                continue;
            }
            task.setStartDate(LocalDate.parse(slot.getStartDate()));
            task.setDueDate(LocalDate.parse(slot.getDueDate()));
            if (slot.getAssigneeId() != null) {
                task.setAssigneeId(slot.getAssigneeId());
            }
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            written++;
        }
        return written;
    }

    private void supersede(Long projectId, LoginUser user) {
        List<AiSuggestion> olds = suggestionMapper.selectList(new LambdaQueryWrapper<AiSuggestion>()
                .eq(AiSuggestion::getProjectId, projectId)
                .eq(AiSuggestion::getSuggestType, "schedule")
                .eq(AiSuggestion::getStatus, "pending"));
        for (AiSuggestion old : olds) {
            AiReview review = new AiReview();
            review.setSuggestionId(old.getId());
            review.setReviewerId(user.getId());
            review.setDecision("reject");
            review.setComment("生成了新的排期建议，这一版不采用");
            review.setDecidedAt(LocalDateTime.now());
            reviewMapper.insert(review);
            old.setStatus("reviewed");
            suggestionMapper.updateById(old);
        }
    }

    private List<SchedulePlanner.Item> items(List<PmTask> tasks) {
        Map<String, String> moscowByCode = new HashMap<>();
        Map<Long, String> moscowByStory = new HashMap<>();
        if (!tasks.isEmpty()) {
            Long projectId = tasks.get(0).getProjectId();
            for (PmStory story : storyMapper.selectList(new LambdaQueryWrapper<PmStory>()
                    .eq(PmStory::getProjectId, projectId))) {
                if (story.getStoryCode() != null) {
                    moscowByCode.put(story.getStoryCode(), story.getMoscow());
                }
                if (story.getId() != null) {
                    moscowByStory.put(story.getId(), story.getMoscow());
                }
            }
        }
        Map<Long, String> names = new HashMap<>();
        for (SysUser user : projectService.listActiveUsers()) {
            names.put(user.getId(), user.getDisplayName());
        }
        List<SchedulePlanner.Item> items = new ArrayList<>();
        for (PmTask task : tasks) {
            SchedulePlanner.Item item = new SchedulePlanner.Item();
            item.id = task.getId();
            item.title = task.getTitle();
            item.storyCode = task.getStoryCode();
            item.status = task.getStatus();
            item.assigneeId = task.getAssigneeId();
            item.assigneeName = task.getAssigneeId() == null ? "" : names.getOrDefault(task.getAssigneeId(), "");
            item.startDate = task.getStartDate();
            item.dueDate = task.getDueDate();
            String moscow = task.getStoryCode() == null ? null : moscowByCode.get(task.getStoryCode());
            if (moscow == null && task.getStoryId() != null) {
                moscow = moscowByStory.get(task.getStoryId());
            }
            item.moscow = moscow;
            items.add(item);
        }
        return items;
    }

    private List<SchedulePlanner.Edge> edges(Long projectId) {
        List<SchedulePlanner.Edge> edges = new ArrayList<>();
        for (PmTaskDependency dep : dependencyMapper.selectList(new LambdaQueryWrapper<PmTaskDependency>()
                .eq(PmTaskDependency::getProjectId, projectId))) {
            SchedulePlanner.Edge edge = new SchedulePlanner.Edge();
            edge.fromTaskId = dep.getFromTaskId();
            edge.toTaskId = dep.getToTaskId();
            edges.add(edge);
        }
        return edges;
    }

    private List<SchedulePlanner.Person> people(List<PmTask> tasks) {
        Set<Long> pool = new HashSet<>();
        for (PmTask task : tasks) {
            if (task.getAssigneeId() != null) {
                pool.add(task.getAssigneeId());
            }
        }
        List<SchedulePlanner.Person> people = new ArrayList<>();
        for (SysUser user : projectService.listActiveUsers()) {
            if (!pool.isEmpty() && !pool.contains(user.getId())) {
                continue;
            }
            SchedulePlanner.Person person = new SchedulePlanner.Person();
            person.id = user.getId();
            person.name = user.getDisplayName();
            people.add(person);
        }
        return people;
    }

    private static void requireCreator(LoginUser user) {
        if (user == null || !user.has("task:create")) {
            throw new BizException(403, "没有创建任务权限，不能排期或写入甘特");
        }
    }

    static String write(List<SchedulePlanner.Slot> slots) {
        try {
            return MAPPER.writeValueAsString(Map.of("slots", slots));
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "排期建议无法保存");
        }
    }

    static List<SchedulePlanner.Slot> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Payload payload = MAPPER.readValue(json, Payload.class);
            return payload.slots == null ? List.of() : payload.slots;
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "排期建议无法读取");
        }
    }

    public static class Payload {
        public List<SchedulePlanner.Slot> slots = new ArrayList<>();
    }

    public static class PlanCard {
        private Long id;
        private LocalDateTime createdAt;
        private List<SchedulePlanner.Slot> slots = new ArrayList<>();

        public Long getId() { return id; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public List<SchedulePlanner.Slot> getSlots() { return slots; }
    }
}
