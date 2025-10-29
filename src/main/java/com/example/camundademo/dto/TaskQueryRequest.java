package com.example.camundademo.dto;

public class TaskQueryRequest {
    private String assignee;
    private String candidateUser;
    private String candidateGroup;
    private String processInstanceId;
    private String processDefinitionKey;
    private Boolean activeOnly = true;

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public String getCandidateUser() { return candidateUser; }
    public void setCandidateUser(String candidateUser) { this.candidateUser = candidateUser; }
    public String getCandidateGroup() { return candidateGroup; }
    public void setCandidateGroup(String candidateGroup) { this.candidateGroup = candidateGroup; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getProcessDefinitionKey() { return processDefinitionKey; }
    public void setProcessDefinitionKey(String processDefinitionKey) { this.processDefinitionKey = processDefinitionKey; }
    public Boolean getActiveOnly() { return activeOnly; }
    public void setActiveOnly(Boolean activeOnly) { this.activeOnly = activeOnly; }
}