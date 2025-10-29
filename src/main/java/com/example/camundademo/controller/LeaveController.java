package com.example.camundademo.controller;

import com.example.camundademo.dto.LeaveStartRequest;
import com.example.camundademo.dto.TaskOperationRequest;
import com.example.camundademo.service.LeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RequestMapping("/leave")
@RestController
public class LeaveController {

    @Autowired
    private LeaveService leaveService;

    /**
     * 启动请假流程（提供默认用户与变量）
     */
    @PostMapping("/start")
    public Map<String, Object> startLeaveProcess(@RequestBody LeaveStartRequest request){
        return leaveService.startLeaveProcess(request);
    }

    /**
     * 审批（设置 approved=true/false 并完成任务）
     */
    @PostMapping("/approve")
    public String completeTask(@RequestBody TaskOperationRequest request){
        leaveService.approveTask(request.getTaskId(), request.getUserId());
        return "任务已完成: " + request.getTaskId();
    }

    /**
     * 审批拒绝（设置 approved=false 并完成任务）
     */
    @PostMapping("/reject")
    public String rejectTask(@RequestBody TaskOperationRequest request){
        leaveService.rejectTask(request.getTaskId(), request.getUserId());
        return "任务已拒绝并完成: " + request.getTaskId();
    }
}