package com.example.camundademo.dto;

public class TaskOperationRequest {
    private String taskId;
    private String userId; // 可选，用于签收

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}