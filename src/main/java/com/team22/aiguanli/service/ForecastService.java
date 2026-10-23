package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.AiPrediction;
import com.team22.aiguanli.entity.AiSuggestion;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.PmTaskStatusLog;
import com.team22.aiguanli.entity.SysUser;
import com.team22.aiguanli.mapper.AiPredictionMapper;
import com.team22.aiguanli.mapper.AiSuggestionMapper;
import com.team22.aiguanli.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 进度预测先由规则算出日期，大模型只解释这个日期。效率建议只供阅读，不改任务和甘特。
 */
@Service
public class ForecastService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PREDICT_SYSTEM = "你是进度说明助手。预测完成日已经由系统算好。"
            + "回复里必须原样出现给出的日期，不能改成别的日子，也不能编造数据里没有的人名。"
            + "用中文，120到220字，说明谁把结束日撑晚了、近7天完成了多少、有没有逾期。";
    private static final String ADVICE_SYSTEM = "你是效率建议助手。只根据给出的事实写建议，不要编造人名和日期。"
            + "不要说已经修改了甘特或任务。若事实里提到智能排期，要保留「确认后再写入甘特」。全文不超过400字。";

    private final ProjectService projectService;
    private final AiPredictionMapper predictionMapper;
    private final AiSuggestionMapper suggestionMapper;
    private final LlmClient llmClient;
    private final LlmSettings llmSettings;

    public ForecastService(ProjectService projectService, AiPredictionMapper predictionMapper,
                           AiSuggestionMapper suggestionMapper, LlmClient llmClient, LlmSettings llmSettings) {
        this.projectService = projectService;
        this.predictionMapper = predictionMapper;
        this.suggestionMapper = suggestionMapper;
        this.llmClient = llmClient;
        this.llmSettings = llmSettings;
    }

    public PredictionCard latest(Long projectId) {
        projectService.getProject(projectId);
        AiPrediction row = predictionMapper.selectOne(new LambdaQueryWrapper<AiPrediction>()
                .eq(AiPrediction::getProjectId, projectId)
                .orderByDesc(AiPrediction::getId)
                .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        PredictionCard card = new PredictionCard();
        card.id = row.getId();
        card.predictedEnd = row.getPredictedEnd();
        int score = row.getConfidence() == null ? 0 : row.getConfidence().intValue();
        card.confidenceText = level(score) + " · " + score;
        card.narrative = narrativeOf(row.getBasedOnJson());
        card.createdAt = row.getCreatedAt();
        return card;
    }

    public AdviceCard latestAdvice(Long projectId) {
        projectService.getProject(projectId);
        AiSuggestion row = suggestionMapper.selectOne(new LambdaQueryWrapper<AiSuggestion>()
                .eq(AiSuggestion::getProjectId, projectId)
                .eq(AiSuggestion::getSuggestType, "optimize")
                .orderByDesc(AiSuggestion::getId)
                .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        AdviceCard card = new AdviceCard();
        card.id = row.getId();
        card.report = SplitJson.readReport(row.getOutputJson());
        card.createdAt = row.getCreatedAt();
        return card;
    }

    @Transactional
    public String predict(Long projectId, LoginUser user) {
        requireCreator(user);
        projectService.getProject(projectId);
        LocalDate today = LocalDate.now();
        ProgressForecast.Result result = forecast(projectId, today);
        String narrative = explain(result);
        AiPrediction row = new AiPrediction();
        row.setProjectId(projectId);
        row.setPredictedEnd(result.predictedEnd);
        row.setConfidence(BigDecimal.valueOf(result.confidence).setScale(2));
        row.setBasedOnJson(payload(result, narrative));
        row.setCreatedAt(LocalDateTime.now());
        predictionMapper.insert(row);
        return "已生成进度预测：预计 " + result.predictedEnd + " 完成。甘特图没有改。";
    }

    @Transactional
    public String advise(Long projectId, LoginUser user) {
        requireCreator(user);
        projectService.getProject(projectId);
        ProgressForecast.Result result = forecast(projectId, LocalDate.now());
        String draft = ProgressForecast.advise(result);
        String report = polish(draft, result);
        AiSuggestion suggestion = new AiSuggestion();
        suggestion.setProjectId(projectId);
        suggestion.setSuggestType("optimize");
        suggestion.setAgentName(agentName());
        suggestion.setSourceTable("pm_task");
        suggestion.setInputDigest(RequirementSplitter.cut("预计 " + result.predictedEnd + " 完成", 200));
        suggestion.setOutputJson(SplitJson.writeReport(report));
        suggestion.setStatus("reported");
        suggestion.setCreatedAt(LocalDateTime.now());
        suggestionMapper.insert(suggestion);
        return "已生成效率建议。任务和甘特都没有改。";
    }

    private ProgressForecast.Result forecast(Long projectId, LocalDate today) {
        List<PmTask> tasks = projectService.listTasks(projectId);
        Map<Long, String> names = new HashMap<>();
        for (SysUser person : projectService.listActiveUsers()) {
            names.put(person.getId(), person.getDisplayName());
        }
        List<ProgressForecast.Item> items = new ArrayList<>();
        for (PmTask task : tasks) {
            ProgressForecast.Item item = new ProgressForecast.Item();
            item.assigneeId = task.getAssigneeId();
            item.assigneeName = task.getAssigneeId() == null ? "" : names.getOrDefault(task.getAssigneeId(), "");
            item.status = task.getStatus();
            item.title = task.getTitle();
            item.startDate = task.getStartDate();
            item.dueDate = task.getDueDate();
            item.progress = task.getProgress();
            items.add(item);
        }
        LocalDateTime since = today.minusDays(6).atStartOfDay();
        int done = 0;
        for (PmTaskStatusLog log : projectService.timeline(projectId)) {
            if ("done".equals(log.getToStatus()) && log.getCreatedAt() != null && !log.getCreatedAt().isBefore(since)) {
                done++;
            }
        }
        return ProgressForecast.forecast(today, items, done);
    }

    private String explain(ProgressForecast.Result result) {
        if (!llmSettings.ready()) {
            return result.baseline;
        }
        try {
            String text = llmClient.complete(llmSettings.snapshot(), PREDICT_SYSTEM, result.baseline).trim();
            if (text.isBlank() || !text.contains(result.predictedEnd.toString())) {
                return result.baseline;
            }
            return text.length() <= 800 ? text : text.substring(0, 800);
        } catch (RuntimeException ex) {
            return result.baseline;
        }
    }

    private String polish(String draft, ProgressForecast.Result result) {
        if (!llmSettings.ready() || result.unfinished == 0) {
            return draft;
        }
        try {
            String text = llmClient.complete(llmSettings.snapshot(), ADVICE_SYSTEM, draft).trim();
            if (text.isBlank()) {
                return draft;
            }
            if (!result.loads.isEmpty() && !text.contains(result.loads.get(0).name)) {
                return draft;
            }
            return text.length() <= 1200 ? text : text.substring(0, 1200);
        } catch (RuntimeException ex) {
            return draft;
        }
    }

    private String agentName() {
        if (!llmSettings.ready()) {
            return "规则建议";
        }
        String model = llmSettings.snapshot().model();
        return RequirementSplitter.cut(model == null || model.isBlank() ? "llm" : model, 32);
    }

    private static String payload(ProgressForecast.Result result, String narrative) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("narrative", narrative);
        body.put("doneLast7", result.doneLast7);
        body.put("overdue", result.overdue);
        body.put("unfinished", result.unfinished);
        List<Map<String, Object>> loads = new ArrayList<>();
        for (ProgressForecast.Load load : result.loads) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("name", load.name);
            one.put("days", load.remainingDays);
            one.put("end", load.end == null ? "" : load.end.toString());
            loads.add(one);
        }
        body.put("loads", loads);
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "进度预测无法保存");
        }
    }

    private static String narrativeOf(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            var node = MAPPER.readTree(json).path("narrative");
            return node.isMissingNode() ? "" : node.asText("");
        } catch (JsonProcessingException ex) {
            return "";
        }
    }

    private static String level(int score) {
        if (score >= 75) {
            return "高";
        }
        if (score >= 50) {
            return "中";
        }
        return "低";
    }

    private static void requireCreator(LoginUser user) {
        if (user == null || !user.has("task:create")) {
            throw new BizException(403, "没有创建任务权限，不能生成预测或效率建议");
        }
    }

    public static class PredictionCard {
        private Long id;
        private LocalDate predictedEnd;
        private String confidenceText;
        private String narrative;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public LocalDate getPredictedEnd() { return predictedEnd; }
        public String getConfidenceText() { return confidenceText; }
        public String getNarrative() { return narrative; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }

    public static class AdviceCard {
        private Long id;
        private String report;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public String getReport() { return report; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}
