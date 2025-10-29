package com.example.camundademo.dto;

import lombok.Data;

@Data
public class LeaveStartRequest {
    private Integer day = 1;
    private String start_time;
    private String end_time;
    private String reason;

    private String employee = "employee";
    private String leader = "leader";
    private String leaders = "leaderA,leaderB";
    private String manager = "manager";

}