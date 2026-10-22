package com.team22.aiguanli.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team22.aiguanli.common.BizException;

import java.util.ArrayList;
import java.util.List;

public final class SplitJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private SplitJson() {
    }

    public static String write(List<SplitItem> items) {
        Payload payload = new Payload();
        payload.items = items;
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "拆解建议无法保存");
        }
    }

    public static String writeReport(String report) {
        try {
            return MAPPER.writeValueAsString(java.util.Map.of("report", report == null ? "" : report));
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "分析报告无法保存");
        }
    }

    public static String readReport(String json) {
        if (json == null || json.isBlank() || !json.contains("\"report\"")) {
            return "";
        }
        try {
            var node = MAPPER.readTree(json).path("report");
            return node.isMissingNode() ? "" : node.asText("");
        } catch (JsonProcessingException ex) {
            return "";
        }
    }

    public static List<SplitItem> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Payload payload = MAPPER.readValue(json, Payload.class);
            return payload.items == null ? List.of() : payload.items;
        } catch (JsonProcessingException ex) {
            throw new BizException(500, "拆解建议无法读取");
        }
    }

    public static class Payload {
        public List<SplitItem> items = new ArrayList<>();
    }
}
