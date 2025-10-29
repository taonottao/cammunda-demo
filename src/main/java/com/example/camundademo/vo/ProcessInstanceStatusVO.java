package com.example.camundademo.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProcessInstanceStatusVO {
    private String processInstanceId;
    private String processDefinitionId;
    private String processDefinitionKey;
    private String processDefinitionName;
    private Boolean ended;
    private List<ProcessInstanceNodeVO> nodes;
}