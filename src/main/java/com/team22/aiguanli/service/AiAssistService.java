package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.common.Constants;
import com.team22.aiguanli.entity.AiPrd;
import com.team22.aiguanli.entity.AiReview;
import com.team22.aiguanli.entity.AiRiskAlert;
import com.team22.aiguanli.entity.AiSuggestion;
import com.team22.aiguanli.entity.AiWriteback;
import com.team22.aiguanli.entity.PmStory;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.mapper.AiPrdMapper;
import com.team22.aiguanli.mapper.AiReviewMapper;
import com.team22.aiguanli.mapper.AiRiskAlertMapper;
import com.team22.aiguanli.mapper.AiSuggestionMapper;
import com.team22.aiguanli.mapper.AiWritebackMapper;
import com.team22.aiguanli.mapper.PmStoryMapper;
import com.team22.aiguanli.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiAssistService {

    private final AiPrdMapper prdMapper;
    private final AiSuggestionMapper suggestionMapper;
    private final AiReviewMapper reviewMapper;
    private final AiWritebackMapper writebackMapper;
    private final AiRiskAlertMapper riskMapper;
    private final PmStoryMapper storyMapper;
    private final ProjectService projectService;
    private final LlmSplitter llmSplitter;
    private final ProjectAnalyst projectAnalyst;
    private final GanttService ganttService;
    private final MemberMapService memberMapService;

    public AiAssistService(AiPrdMapper prdMapper,
                           AiSuggestionMapper suggestionMapper,
                           AiReviewMapper reviewMapper,
                           AiWritebackMapper writebackMapper,
                           AiRiskAlertMapper riskMapper,
                           PmStoryMapper storyMapper,
                           ProjectService projectService,
                           LlmSplitter llmSplitter,
                           ProjectAnalyst projectAnalyst,
                           GanttService ganttService,
                           MemberMapService memberMapService) {
        this.prdMapper = prdMapper;
        this.suggestionMapper = suggestionMapper;
        this.reviewMapper = reviewMapper;
        this.writebackMapper = writebackMapper;
        this.riskMapper = riskMapper;
        this.storyMapper = storyMapper;
        this.projectService = projectService;
        this.llmSplitter = llmSplitter;
        this.projectAnalyst = projectAnalyst;
        this.ganttService = ganttService;
        this.memberMapService = memberMapService;
    }

    public List<SuggestionCard> pending(Long projectId) {
        projectService.getProject(projectId);
        return cards(projectId, "pending", "split");
    }

    public List<SuggestionCard> history(Long projectId) {
        projectService.getProject(projectId);
        return cards(projectId, "reviewed", "split");
    }

    public List<SuggestionCard> reports(Long projectId) {
        projectService.getProject(projectId);
        List<AiSuggestion> rows = suggestionMapper.selectList(new LambdaQueryWrapper<AiSuggestion>()
                .eq(AiSuggestion::getProjectId, projectId)
                .in(AiSuggestion::getSuggestType, List.of("board", "gantt", "member", "risk"))
                .orderByDesc(AiSuggestion::getId)
                .last("limit 8"));
        List<SuggestionCard> cards = new ArrayList<>();
        for (AiSuggestion row : rows) {
            cards.add(new SuggestionCard(row, null, List.of(), SplitJson.readReport(row.getOutputJson())));
        }
        return cards;
    }

    public List<AiRiskAlert> openAlerts(Long projectId) {
        projectService.getProject(projectId);
        return riskMapper.selectList(new LambdaQueryWrapper<AiRiskAlert>()
                .eq(AiRiskAlert::getProjectId, projectId)
                .eq(AiRiskAlert::getStatus, "open")
                .orderByDesc(AiRiskAlert::getId));
    }

    @Transactional
    public void propose(Long projectId, String title, String content, LoginUser user) {
        requireCreator(user);
        projectService.getProject(projectId);
        if (content == null || content.isBlank()) {
            throw new BizException(400, "请先写一段需求");
        }
        String body = content.trim();
        if (body.length() > 8000) {
            throw new BizException(400, "需求太长，请控制在 8000 字以内");
        }
        List<SplitItem> items = llmSplitter.split(body);
        if (items.isEmpty()) {
            throw new BizException(400, "这段需求拆不出建议");
        }
        AiPrd prd = new AiPrd();
        prd.setProjectId(projectId);
        prd.setTitle(title == null || title.isBlank() ? RequirementSplitter.cut(body, 40) : RequirementSplitter.cut(title.trim(), 80));
        prd.setContent(body);
        prd.setUploadedBy(user.getId());
        prd.setCreatedAt(LocalDateTime.now());
        prdMapper.insert(prd);

        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setProjectId(projectId);
        suggestion.setSuggestType("split");
        suggestion.setAgentName(llmSplitter.agentName());
        suggestion.setSourceTable("ai_prd");
        suggestion.setSourceId(prd.getId());
        suggestion.setInputDigest(RequirementSplitter.cut(body, 200));
        suggestion.setOutputJson(SplitJson.write(items));
        suggestion.setStatus("pending");
        suggestion.setCreatedAt(LocalDateTime.now());
        suggestionMapper.insert(suggestion);
    }

    /**
     * 项目经理选定对象后做分析。结果只保存成报告，不写故事、任务和甘特。
     */
    @Transactional
    public String analyze(Long projectId, String target, String note, LoginUser user) {
        requireCreator(user);
        projectService.getProject(projectId);
        if (!List.of("board", "gantt", "member", "risk").contains(target)) {
            throw new BizException(400, "请选择任务看板、甘特图、人员或风险测评");
        }
        List<PmTask> tasks = projectService.listTasks(projectId);
        String facts = switch (target) {
            case "gantt" -> AnalysisFacts.gantt(ganttService.layout(tasks), ganttService.dependencyLines(projectId));
            case "member" -> AnalysisFacts.members(memberMapService.columns(projectId));
            case "risk" -> AnalysisFacts.risk(tasks, openAlerts(projectId), LocalDate.now());
            default -> AnalysisFacts.board(tasks);
        };
        String label = AnalysisFacts.label(target);
        String report = projectAnalyst.analyze(label, facts, note);
        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setProjectId(projectId);
        suggestion.setSuggestType(target);
        suggestion.setAgentName(projectAnalyst.agentName());
        suggestion.setSourceTable("pm_task");
        suggestion.setInputDigest(RequirementSplitter.cut(label + (note == null ? "" : " " + note), 200));
        suggestion.setOutputJson(SplitJson.writeReport(report));
        suggestion.setStatus("reported");
        suggestion.setCreatedAt(LocalDateTime.now());
        suggestionMapper.insert(suggestion);
        return label;
    }

    /**
     * 采纳和修改才写回。拒绝只记人审，不写故事、不写任务、不写 ai_writeback。
     */
    @Transactional
    public ReviewResult review(Long projectId, Long suggestionId, String decision, String comment,
                               List<String> titles, List<String> wants, List<Integer> picked, LoginUser user) {
        requireCreator(user);
        projectService.getProject(projectId);
        AiReviewRules.check(decision, comment);
        AiSuggestion suggestion = suggestionMapper.selectById(suggestionId);
        if (suggestion == null || !projectId.equals(suggestion.getProjectId())) {
            throw new BizException(404, "建议不存在");
        }
        if (!"pending".equals(suggestion.getStatus())) {
            throw new BizException(400, "这条建议已经审过");
        }
        List<SplitItem> items = new ArrayList<>(SplitJson.read(suggestion.getOutputJson()));
        if (items.isEmpty()) {
            throw new BizException(400, "这条建议是空的");
        }
        if (!AiReviewRules.mayWriteBack(decision)) {
            closeReview(suggestion, user, decision, comment);
            return new ReviewResult("已拒绝。建议没有写回，故事和任务保持原样。", 0);
        }
        List<SplitItem> chosen = chosenItems(items, titles, wants, picked);
        suggestion.setOutputJson(SplitJson.write(chosen));
        AiReview review = closeReview(suggestion, user, "accept", comment);
        int written = writeBack(projectId, suggestionId, review.getId(), chosen, user);
        int skipped = items.size() - chosen.size();
        String message = skipped == 0
                ? "已写入全部 " + written + " 条故事和对应任务。"
                : "已写入勾选的 " + written + " 条故事和任务，未勾选的 " + skipped + " 条没有写入。";
        return new ReviewResult(message, written);
    }

    private AiReview closeReview(AiSuggestion suggestion, LoginUser user, String decision, String comment) {
        AiReview review = new AiReview();
        review.setSuggestionId(suggestion.getId());
        review.setReviewerId(user.getId());
        review.setDecision(decision);
        review.setComment(comment.trim());
        review.setDecidedAt(LocalDateTime.now());
        reviewMapper.insert(review);
        suggestion.setStatus("reviewed");
        suggestionMapper.updateById(suggestion);
        return review;
    }

    private static List<SplitItem> chosenItems(List<SplitItem> items, List<String> titles, List<String> wants, List<Integer> picked) {
        if (picked == null || picked.isEmpty()) {
            throw new BizException(400, "请至少勾选一条再写入");
        }
        List<SplitItem> chosen = new ArrayList<>();
        for (Integer index : picked) {
            if (index == null || index < 0 || index >= items.size()) {
                throw new BizException(400, "勾选的建议不存在");
            }
            SplitItem item = items.get(index);
            String title = valueAt(titles, index, item.getTitle());
            String want = valueAt(wants, index, item.getIWant());
            if (title.isBlank() || want.isBlank()) {
                throw new BizException(400, "勾选后的标题和愿望不能为空");
            }
            item.setTitle(RequirementSplitter.cut(title, 40));
            item.setIWant(RequirementSplitter.cut(want, 180));
            item.setTaskTitle(RequirementSplitter.cut("实现" + item.getTitle(), 80));
            chosen.add(item);
        }
        return chosen;
    }

    private static String valueAt(List<String> values, int index, String fallback) {
        if (values == null || index >= values.size() || values.get(index) == null) {
            return fallback == null ? "" : fallback.trim();
        }
        return values.get(index).trim();
    }

    @Transactional
    public int refreshRisks(Long projectId, LoginUser user) {
        requireCreator(user);
        projectService.getProject(projectId);
        LocalDate today = LocalDate.now();
        int added = 0;
        for (PmTask task : projectService.listTasks(projectId)) {
            String level = DelayRiskRules.level(task.getDueDate(), today, task.getStatus());
            if (level == null) {
                continue;
            }
            String indicator = "high".equals(level) ? "overdue" : "due_soon";
            String message = RequirementSplitter.cut(task.getTitle() + " 截止 " + task.getDueDate()
                    + "，当前状态 " + task.getStatus(), 300);
            Long exists = riskMapper.selectCount(new LambdaQueryWrapper<AiRiskAlert>()
                    .eq(AiRiskAlert::getProjectId, projectId)
                    .eq(AiRiskAlert::getStatus, "open")
                    .eq(AiRiskAlert::getIndicator, indicator)
                    .eq(AiRiskAlert::getMessage, message));
            if (exists != null && exists > 0) {
                continue;
            }
            AiRiskAlert alert = new AiRiskAlert();
            alert.setProjectId(projectId);
            alert.setRiskLevel(level);
            alert.setIndicator(indicator);
            alert.setMessage(message);
            alert.setStatus("open");
            alert.setCreatedAt(LocalDateTime.now());
            riskMapper.insert(alert);
            added++;
        }
        return added;
    }

    private int writeBack(Long projectId, Long suggestionId, Long reviewId, List<SplitItem> items, LoginUser user) {
        int next = nextStoryNumber(projectId);
        LocalDate today = LocalDate.now();
        int written = 0;
        for (SplitItem item : items) {
            String code = String.format("E3-%02d", next++);
            PmStory story = new PmStory();
            story.setProjectId(projectId);
            story.setStoryCode(code);
            story.setTitle(item.getTitle());
            story.setAsRole("项目经理");
            story.setIWant(item.getIWant());
            story.setSoThat("把这段需求落成可跟踪的故事和任务");
            story.setMoscow("Should");
            story.setSprintNo(3);
            story.setStatus(Constants.STATUS_TODO);
            story.setSortOrder(300 + written);
            story.setCreatedAt(LocalDateTime.now());
            story.setUpdatedAt(LocalDateTime.now());
            storyMapper.insert(story);
            remember(suggestionId, reviewId, "pm_story", story.getId());

            PmTask task = new PmTask();
            task.setProjectId(projectId);
            task.setStoryId(story.getId());
            task.setStoryCode(code);
            task.setTitle(item.getTaskTitle());
            task.setDescription("由拆解建议写回，故事 " + code);
            task.setCreatedBy(user.getId());
            task.setStatus(Constants.STATUS_TODO);
            task.setStartDate(today);
            task.setDueDate(today.plusDays(3));
            task.setProgress(0);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            projectService.insertTask(task);
            remember(suggestionId, reviewId, "pm_task", task.getId());
            written++;
        }
        return written;
    }

    private void remember(Long suggestionId, Long reviewId, String table, Long targetId) {
        AiWriteback row = new AiWriteback();
        row.setSuggestionId(suggestionId);
        row.setReviewId(reviewId);
        row.setTargetTable(table);
        row.setTargetId(targetId);
        row.setAppliedAt(LocalDateTime.now());
        writebackMapper.insert(row);
    }

    private int nextStoryNumber(Long projectId) {
        int max = 0;
        List<PmStory> stories = storyMapper.selectList(new LambdaQueryWrapper<PmStory>()
                .eq(PmStory::getProjectId, projectId));
        for (PmStory story : stories) {
            String code = story.getStoryCode();
            if (code == null || !code.startsWith("E3-")) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(code.substring(3)));
            } catch (NumberFormatException ignored) {
                // 非数字故事号留给人工编号，不参与递增
            }
        }
        return max + 1;
    }

    private List<SuggestionCard> cards(Long projectId, String status, String suggestType) {
        List<AiSuggestion> rows = suggestionMapper.selectList(new LambdaQueryWrapper<AiSuggestion>()
                .eq(AiSuggestion::getProjectId, projectId)
                .eq(AiSuggestion::getStatus, status)
                .eq(AiSuggestion::getSuggestType, suggestType)
                .orderByDesc(AiSuggestion::getId)
                .last("limit 8"));
        List<SuggestionCard> cards = new ArrayList<>();
        for (AiSuggestion row : rows) {
            AiReview review = reviewMapper.selectOne(new LambdaQueryWrapper<AiReview>()
                    .eq(AiReview::getSuggestionId, row.getId()));
            cards.add(new SuggestionCard(row, review, SplitJson.read(row.getOutputJson()), ""));
        }
        return cards;
    }

    private static void requireCreator(LoginUser user) {
        if (user == null || !user.has("task:create")) {
            throw new BizException(403, "没有创建任务权限，不能提交拆解或写回");
        }
    }

    public static class ReviewResult {
        private final String message;
        private final int written;

        public ReviewResult(String message, int written) {
            this.message = message;
            this.written = written;
        }

        public String getMessage() { return message; }
        public int getWritten() { return written; }
    }

    public static class SuggestionCard {
        private final Long id;
        private final String digest;
        private final String status;
        private final LocalDateTime createdAt;
        private final String decision;
        private final String comment;
        private final List<SplitItem> items;
        private final String suggestType;
        private final String report;

        public SuggestionCard(AiSuggestion suggestion, AiReview review, List<SplitItem> items, String report) {
            this.id = suggestion.getId();
            this.digest = suggestion.getInputDigest();
            this.status = suggestion.getStatus();
            this.createdAt = suggestion.getCreatedAt();
            this.decision = review == null ? null : review.getDecision();
            this.comment = review == null ? null : review.getComment();
            this.items = items;
            this.suggestType = suggestion.getSuggestType();
            this.report = report == null ? "" : report;
        }

        public Long getId() { return id; }
        public String getDigest() { return digest; }
        public String getStatus() { return status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getDecision() { return decision; }
        public String getComment() { return comment; }
        public List<SplitItem> getItems() { return items; }
        public String getSuggestType() { return suggestType; }
        public String getReport() { return report; }
        public String getTargetLabel() { return AnalysisFacts.label(suggestType); }
    }
}
