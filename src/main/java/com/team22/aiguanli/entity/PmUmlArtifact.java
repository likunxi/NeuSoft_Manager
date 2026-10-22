package com.team22.aiguanli.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("pm_uml_artifact")
public class PmUmlArtifact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String umlType;
    private String sourceRef;
    private String mermaidText;
    private LocalDateTime generatedAt;
    private Long generatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getUmlType() { return umlType; }
    public void setUmlType(String umlType) { this.umlType = umlType; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getMermaidText() { return mermaidText; }
    public void setMermaidText(String mermaidText) { this.mermaidText = mermaidText; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public Long getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(Long generatedBy) { this.generatedBy = generatedBy; }
}
