package com.team22.aiguanli.service;

import com.team22.aiguanli.common.BizException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 让大模型把需求拆成建议。人还没点采纳之前，这些建议不会写进故事和任务。
 */
@Component
public class LlmSplitter {

    static final String SYSTEM = "你是软件项目管理助手。把用户需求拆成 1 到 6 条可执行建议。"
            + "只返回 JSON，不要解释，不要使用 Markdown。"
            + "格式必须是 {\"items\":[{\"title\":\"不超过40字\",\"iWant\":\"不超过180字\",\"taskTitle\":\"不超过80字\"}]}。";

    private final LlmClient client;
    private final LlmSettings settings;

    public LlmSplitter(LlmClient client, LlmSettings settings) {
        this.client = client;
        this.settings = settings;
    }

    public List<SplitItem> split(String raw) {
        if (!settings.ready()) {
            throw new BizException(400, "还没有配置大模型。请先在本页填写接口地址、模型名和密钥。");
        }
        String content = client.complete(settings.snapshot(), SYSTEM, "需求如下：\n" + raw);
        List<SplitItem> items = parse(content);
        if (items.isEmpty()) {
            throw new BizException(400, "大模型没有拆出可用建议");
        }
        return items;
    }

    public String agentName() {
        String model = settings.snapshot().model();
        return RequirementSplitter.cut(model == null || model.isBlank() ? "llm" : model, 32);
    }

    static List<SplitItem> parse(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String json = content.trim();
        int left = json.indexOf('{');
        int right = json.lastIndexOf('}');
        if (left >= 0 && right > left) {
            json = json.substring(left, right + 1);
        }
        List<SplitItem> rawItems;
        try {
            rawItems = SplitJson.read(json);
        } catch (BizException ex) {
            return List.of();
        }
        List<SplitItem> items = new ArrayList<>();
        for (SplitItem item : rawItems) {
            if (item.getTitle() == null || item.getTitle().isBlank()) {
                continue;
            }
            String title = RequirementSplitter.cut(item.getTitle(), 40);
            String want = RequirementSplitter.cut(item.getIWant() == null || item.getIWant().isBlank() ? title : item.getIWant(), 180);
            String task = RequirementSplitter.cut(item.getTaskTitle() == null || item.getTaskTitle().isBlank() ? "实现" + title : item.getTaskTitle(), 80);
            items.add(new SplitItem(title, want, task));
            if (items.size() == RequirementSplitter.MAX_ITEMS) {
                break;
            }
        }
        return items;
    }
}
