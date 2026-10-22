package com.team22.aiguanli.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("pm_story")
public class PmStory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String storyCode;
    private String title;
    private String asRole;
    private String iWant;
    private String soThat;
    private String moscow;
    private Integer sprintNo;
    private String status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getStoryCode() { return storyCode; }
    public void setStoryCode(String storyCode) { this.storyCode = storyCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAsRole() { return asRole; }
    public void setAsRole(String asRole) { this.asRole = asRole; }
    public String getIWant() { return iWant; }
    public void setIWant(String iWant) { this.iWant = iWant; }
    public String getSoThat() { return soThat; }
    public void setSoThat(String soThat) { this.soThat = soThat; }
    public String getMoscow() { return moscow; }
    public void setMoscow(String moscow) { this.moscow = moscow; }
    public Integer getSprintNo() { return sprintNo; }
    public void setSprintNo(Integer sprintNo) { this.sprintNo = sprintNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
