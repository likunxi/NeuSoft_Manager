package com.team22.aiguanli.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("pm_task_dependency")
public class PmTaskDependency {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long fromTaskId;
    private Long toTaskId;
    private String depType;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getFromTaskId() { return fromTaskId; }
    public void setFromTaskId(Long fromTaskId) { this.fromTaskId = fromTaskId; }
    public Long getToTaskId() { return toTaskId; }
    public void setToTaskId(Long toTaskId) { this.toTaskId = toTaskId; }
    public String getDepType() { return depType; }
    public void setDepType(String depType) { this.depType = depType; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
