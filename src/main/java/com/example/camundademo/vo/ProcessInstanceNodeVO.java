package com.example.camundademo.vo;

import lombok.Data;

import java.util.Date;

@Data
public class ProcessInstanceNodeVO {
    private String activityId;
    private String name;
    private String activityType;
    /** completed|active|pending */
    private String status;
    private Date startTime;
    private Date endTime;
    private String assignee; // 针对用户任务
    private String taskId;   // 当前活动的任务ID（若存在）
}