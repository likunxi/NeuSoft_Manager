package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.VwLinkRule;
import com.team22.aiguanli.entity.VwSyncEvent;
import com.team22.aiguanli.mapper.VwLinkRuleMapper;
import com.team22.aiguanli.mapper.VwSyncEventMapper;
import com.team22.aiguanli.security.LoginUser;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 视图联动：只有打开的规则才会写同步事件。甘特和成员图本身读当前任务，事件用来留下“改过并已刷新”的记录。
 */
@Service
public class ViewSyncService {

    public static final String STORY_BOARD = "story_board";
    public static final String GANTT = "gantt";
    public static final String BOARD = "board";
    public static final String MEMBER_MAP = "member_map";
    public static final String STORY_CHANGED = "story_changed";
    public static final String TASK_STATUS_CHANGED = "task_status_changed";

    private final VwLinkRuleMapper ruleMapper;
    private final VwSyncEventMapper eventMapper;

    public ViewSyncService(VwLinkRuleMapper ruleMapper, VwSyncEventMapper eventMapper) {
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
    }

    public static boolean matches(VwLinkRule rule, String source, String target, String event) {
        return rule != null
                && rule.getIsEnabled() != null
                && rule.getIsEnabled() == 1
                && source.equals(rule.getSourceView())
                && target.equals(rule.getTargetView())
                && event.equals(rule.getEventType());
    }

    public List<VwLinkRule> matching(List<VwLinkRule> rules, String source, String target, String event) {
        List<VwLinkRule> hit = new ArrayList<>();
        if (rules == null) {
            return hit;
        }
        for (VwLinkRule rule : rules) {
            if (matches(rule, source, target, event)) {
                hit.add(rule);
            }
        }
        return hit;
    }

    public boolean enabled(Long projectId, String source, String target, String event) {
        return !matching(listRules(projectId), source, target, event).isEmpty();
    }

    public void onTaskStatusChanged(PmTask task, String from, String to, LoginUser user) {
        String summary = "任务「" + safe(task.getTitle()) + "」从 " + label(from) + " 改为 " + label(to) + "，成员任务图已按最新状态刷新";
        record(task.getProjectId(), BOARD, MEMBER_MAP, TASK_STATUS_CHANGED, summary, user.getId());
    }

    public void record(Long projectId, String source, String target, String event, String summary, Long userId) {
        for (VwLinkRule rule : matching(listRules(projectId), source, target, event)) {
            VwSyncEvent row = new VwSyncEvent();
            row.setProjectId(projectId);
            row.setRuleId(rule.getId());
            row.setEventType(event);
            row.setSourceView(source);
            row.setPayloadJson("{\"summary\":\"" + escape(summary) + "\"}");
            row.setProcessed(1);
            row.setCreatedBy(userId);
            row.setCreatedAt(LocalDateTime.now());
            row.setProcessedAt(LocalDateTime.now());
            eventMapper.insert(row);
        }
    }

    public List<SyncItem> recent(Long projectId, String eventType, int limit) {
        int size = Math.max(1, Math.min(limit, 20));
        List<VwSyncEvent> rows = eventMapper.selectList(new LambdaQueryWrapper<VwSyncEvent>()
                .eq(VwSyncEvent::getProjectId, projectId)
                .eq(VwSyncEvent::getEventType, eventType)
                .orderByDesc(VwSyncEvent::getId)
                .last("LIMIT " + size));
        List<SyncItem> items = new ArrayList<>();
        for (VwSyncEvent row : rows) {
            SyncItem item = new SyncItem();
            item.createdAt = row.getCreatedAt();
            item.summary = summaryOf(row.getPayloadJson());
            items.add(item);
        }
        return items;
    }

    public static String summaryOf(String json) {
        if (json == null) {
            return "";
        }
        String key = "\"summary\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            return json;
        }
        start += key.length();
        StringBuilder text = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                text.append(json.charAt(i + 1));
                i++;
                continue;
            }
            if (c == '"') {
                break;
            }
            text.append(c);
        }
        return text.toString();
    }

    public static String label(String status) {
        if ("doing".equals(status)) {
            return "进行中";
        }
        if ("done".equals(status)) {
            return "已完成";
        }
        if ("todo".equals(status)) {
            return "待办";
        }
        return status == null ? "" : status;
    }

    private List<VwLinkRule> listRules(Long projectId) {
        return ruleMapper.selectList(new LambdaQueryWrapper<VwLinkRule>()
                .eq(VwLinkRule::getProjectId, projectId)
                .orderByAsc(VwLinkRule::getId));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class SyncItem {
        public LocalDateTime createdAt;
        public String summary;
    }
}
