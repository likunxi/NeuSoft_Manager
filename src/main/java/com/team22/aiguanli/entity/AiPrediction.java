package com.team22.aiguanli.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("ai_prediction")
public class AiPrediction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private LocalDate predictedEnd;
    private BigDecimal confidence;
    private String basedOnJson;
    private Long suggestionId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public LocalDate getPredictedEnd() { return predictedEnd; }
    public void setPredictedEnd(LocalDate predictedEnd) { this.predictedEnd = predictedEnd; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getBasedOnJson() { return basedOnJson; }
    public void setBasedOnJson(String basedOnJson) { this.basedOnJson = basedOnJson; }
    public Long getSuggestionId() { return suggestionId; }
    public void setSuggestionId(Long suggestionId) { this.suggestionId = suggestionId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
