package com.team22.aiguanli.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team22.aiguanli.common.BizException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 调用 OpenAI 兼容的 chat/completions。DeepSeek、通义兼容模式都可以填这个地址。
 */
@Component
public class LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public String complete(LlmSettings.Snapshot setting, String system, String user) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", setting.model());
        body.put("temperature", 0.2);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        String url = setting.baseUrl() + "/chat/completions";
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + setting.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .build();
        } catch (Exception ex) {
            throw new BizException(400, "大模型地址无法使用");
        }
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            throw new BizException(502, "大模型没有连上，请检查地址和网络");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException(502, "大模型返回 " + response.statusCode() + "：" + clip(response.body()));
        }
        try {
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new BizException(502, "大模型没有返回拆解内容");
            }
            return content.asText();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(502, "大模型返回的内容无法读取");
        }
    }

    private static String clip(String body) {
        if (body == null || body.isBlank()) {
            return "无说明";
        }
        String text = body.replaceAll("\\s+", " ").trim();
        return text.length() <= 180 ? text : text.substring(0, 180);
    }
}
