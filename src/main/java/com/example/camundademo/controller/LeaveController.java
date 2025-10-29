package com.example.camundademo.controller;

import com.example.camundademo.common.Result; // 引入统一返回值
import com.example.camundademo.dto.LeaveStartRequest; // 引入启动DTO
import com.example.camundademo.dto.TaskOperationRequest; // 引入任务操作DTO
import com.example.camundademo.service.LeaveService; // 引入服务
import com.example.camundademo.service.ProcessInstanceService; // 引入流程实例服务（撤回用）
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper; // 引入查询构造器
import com.example.camundademo.entity.UserProcessLink; // 引入关联实体
import com.example.camundademo.mapper.UserProcessLinkMapper; // 引入关联Mapper
import com.example.camundademo.mapper.LeaveRequestMapper; // 引入请假Mapper
import com.example.camundademo.entity.LeaveRequest; // 引入请假实体
import org.springframework.beans.factory.annotation.Autowired; // 引入依赖注入
import org.springframework.web.bind.annotation.PostMapping; // 引入Post映射
import org.springframework.web.bind.annotation.RequestBody; // 引入请求体
import org.springframework.web.bind.annotation.RequestMapping; // 引入请求映射
import org.springframework.web.bind.annotation.RestController; // 引入Rest控制器

import java.util.Map; // 引入Map

@RequestMapping("/leave") // 基础路径
@RestController // 标记为Rest控制器
public class LeaveController {

    @Autowired // 注入请假服务
    private LeaveService leaveService; // 业务服务

    // 不再使用复杂的回退至发起节点逻辑；改为删除并重启流程。

    @Autowired // 注入关联Mapper
    private UserProcessLinkMapper userProcessLinkMapper; // 关联查询

    @Autowired // 注入请假Mapper
    private LeaveRequestMapper leaveRequestMapper; // 业务更新

    /**
     * 启动请假流程（业务与流程解耦版本）
     */
    @PostMapping("/start") // POST接口
    public Result<Map<String, Object>> startLeaveProcess(@RequestBody LeaveStartRequest request){ // 返回统一结果
        return leaveService.startLeaveProcess(request); // 调用服务
    }

    /**
     * 审批同意（设置 approved=true 并完成任务，同时更新业务状态）
     */
    @PostMapping("/approve") // POST接口
    public Result<Boolean> completeTask(@RequestBody TaskOperationRequest request){ // 返回统一结果
        return leaveService.approveTask(request.getTaskId(), request.getUserId()); // 调用服务
    }

    /**
     * 审批拒绝（设置 approved=false 并完成任务，同时更新业务状态）
     */
    @PostMapping("/reject") // POST接口
    public Result<Boolean> rejectTask(@RequestBody TaskOperationRequest request){ // 返回统一结果
        return leaveService.rejectTask(request.getTaskId(), request.getUserId()); // 调用服务
    }

    public static class RevokeRequest { // 撤回请求DTO
        public String processInstanceId; // 流程实例ID
        public String initiatorUserId; // 发起撤回的用户ID（字符串，与Camunda用户一致）
    }

    /**
     * 撤回流程实例（删除旧流程并重启），保留业务数据，建立新的流程关联
     */
    @PostMapping("/revoke") // POST接口
    public Result<Map<String, Object>> revoke(@RequestBody RevokeRequest request) { // 返回统一结果
        return leaveService.revokeAndRestart(request.processInstanceId, request.initiatorUserId); // 执行删除并重启
    }
}