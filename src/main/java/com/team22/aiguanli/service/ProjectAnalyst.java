package com.team22.aiguanli.service;

import com.team22.aiguanli.common.BizException;
import org.springframework.stereotype.Component;

/**
 * 按项目经理选定的对象调用大模型。只返回分析文字，不改业务数据。
 */
@Component
public class ProjectAnalyst {

    static final String SYSTEM = "你是项目经理的分析助手。只根据给出的项目数据下结论，不要编造数据里没有的人名、日期和任务。"
            + "用中文，分成现状、问题、建议三段。全文不超过600字。";

    private final LlmClient client;
    private final LlmSettings settings;

    public ProjectAnalyst(LlmClient client, LlmSettings settings) {
        this.client = client;
        this.settings = settings;
    }

    public String analyze(String label, String facts, String note) {
        if (!settings.ready()) {
            throw new BizException(400, "还没有配置大模型。请先在本页填写接口地址、模型名和密钥。");
        }
        StringBuilder user = new StringBuilder();
        user.append("分析对象：").append(label).append("\n项目数据：\n").append(facts);
        if (note != null && !note.isBlank()) {
            user.append("\n项目经理补充：").append(note.trim());
        }
        String text = client.complete(settings.snapshot(), SYSTEM, user.toString());
        if (text == null || text.isBlank()) {
            throw new BizException(400, "大模型没有返回分析");
        }
        text = text.trim();
        return text.length() <= 4000 ? text : text.substring(0, 4000);
    }

    public String agentName() {
        String model = settings.snapshot().model();
        return RequirementSplitter.cut(model == null || model.isBlank() ? "llm" : model, 32);
    }
}
