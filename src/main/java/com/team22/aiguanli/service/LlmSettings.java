package com.team22.aiguanli.service;

import com.team22.aiguanli.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 大模型地址、模型名和密钥。页面保存后写到 data/llm.properties，重启还在。
 */
@Component
public class LlmSettings {

    private final Path file;
    private String baseUrl;
    private String model;
    private String apiKey;

    public LlmSettings(@Value("${aiguanli.llm.base-url:https://api.deepseek.com/v1}") String baseUrl,
                       @Value("${aiguanli.llm.model:deepseek-chat}") String model,
                       @Value("${aiguanli.llm.api-key:}") String apiKey) {
        this.file = Path.of("data", "llm.properties");
        this.baseUrl = cleanBase(baseUrl);
        this.model = model == null || model.isBlank() ? "deepseek-chat" : model.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        loadFile();
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(baseUrl, model, apiKey);
    }

    public synchronized boolean ready() {
        return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
    }

    public synchronized void save(String baseUrl, String model, String apiKey) {
        if (baseUrl == null || baseUrl.isBlank() || !(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            throw new BizException(400, "接口地址要以 http:// 或 https:// 开头");
        }
        if (model == null || model.isBlank()) {
            throw new BizException(400, "请填写模型名");
        }
        this.baseUrl = cleanBase(baseUrl);
        this.model = model.trim();
        if (apiKey != null && !apiKey.isBlank()) {
            this.apiKey = apiKey.trim();
        }
        if (!ready()) {
            throw new BizException(400, "请填写大模型密钥");
        }
        Properties properties = new Properties();
        properties.setProperty("baseUrl", this.baseUrl);
        properties.setProperty("model", this.model);
        properties.setProperty("apiKey", this.apiKey);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "aiguanli llm");
            }
        } catch (IOException ex) {
            throw new BizException(500, "大模型配置没有写进去");
        }
    }

    private void loadFile() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException ex) {
            return;
        }
        String savedBase = properties.getProperty("baseUrl");
        String savedModel = properties.getProperty("model");
        String savedKey = properties.getProperty("apiKey");
        if (savedBase != null && !savedBase.isBlank()) {
            baseUrl = cleanBase(savedBase);
        }
        if (savedModel != null && !savedModel.isBlank()) {
            model = savedModel.trim();
        }
        if (savedKey != null && !savedKey.isBlank()) {
            apiKey = savedKey.trim();
        }
    }

    static String cleanBase(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public record Snapshot(String baseUrl, String model, String apiKey) {
    }
}
