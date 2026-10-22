package com.team22.aiguanli.service;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 一条拆解建议。先给人看，采纳或修改之后才变成故事和任务。
 */
public class SplitItem {
    private String title;
    private String iWant;
    private String taskTitle;

    public SplitItem() {
    }

    public SplitItem(String title, String iWant, String taskTitle) {
        this.title = title;
        this.iWant = iWant;
        this.taskTitle = taskTitle;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    @JsonProperty("iWant")
    public String getIWant() { return iWant; }
    @JsonProperty("iWant")
    public void setIWant(String iWant) { this.iWant = iWant; }
    public String getTaskTitle() { return taskTitle; }
    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }
}
