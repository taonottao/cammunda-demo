package com.example.camundademo.service;

import com.example.camundademo.dto.LeaveStartRequest;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class LeaveService {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private IdentityService identityService;

    /**
     * 启动请假流程并写入流程变量。
     * 变量存储说明：
     * - 运行时变量会持久化到 ACT_RU_VARIABLE，并通过 PROC_INST_ID_/EXECUTION_ID_ 关联到流程实例/执行。
     * - 若变量为复杂对象（序列化类型），其二进制内容存放在 ACT_GE_BYTEARRAY，ACT_RU_VARIABLE.BYTEARRAY_ID_ 指向对应条目。
     * - 历史开启时，变量快照保存在 ACT_HI_VARINST，变量每次更新明细可在 ACT_HI_DETAIL 中查看。
     * - 当流程结束后，ACT_RU_VARIABLE 中的记录会被清理，仅历史表保留变量信息。
     */
    public Map<String, Object> startLeaveProcess(LeaveStartRequest request) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("day", request.getDay());
        vars.put("start_time", request.getStart_time());
        vars.put("end_time", request.getEnd_time());
        vars.put("reason", request.getReason());
        vars.put("employee", request.getEmployee());
        vars.put("leader", request.getLeader());
        vars.put("leaders", request.getLeaders());
        vars.put("manager", request.getManager());

        // 说明：此处传入的 vars 会作为流程的运行时变量存入 ACT_RU_VARIABLE；
        // 若包含复杂/可序列化对象，则二进制内容存入 ACT_GE_BYTEARRAY，并由 ACT_RU_VARIABLE.BYTEARRAY_ID_ 引用。
        identityService.setAuthenticatedUserId(request.getEmployee());
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("LEAVE_REQ", vars);
        identityService.clearAuthentication();
        Map<String, Object> resp = new HashMap<>();
        resp.put("processInstanceId", instance.getProcessInstanceId());
        resp.put("definitionId", instance.getProcessDefinitionId());
        return resp;
    }

    public void approveTask(String taskId, String userId) {
        ensureClaimSafety(taskId, userId);
        Map<String, Object> vars = new HashMap<>();
        vars.put("approved", true);
        taskService.complete(taskId, vars);
    }

    public void rejectTask(String taskId, String userId) {
        ensureClaimSafety(taskId, userId);
        Map<String, Object> vars = new HashMap<>();
        vars.put("approved", false);
        taskService.complete(taskId, vars);
    }

    /**
     * 签收安全逻辑：
     * 1) 任务必须存在且处于激活状态；
     * 2) userId 不能为空；
     * 3) 若任务未签收，则尝试以 userId 签收；
     * 4) 若已签收且签收人不是 userId，则拒绝操作并提示当前签收人；
     * 5) 若已被 userId 签收，则直接继续处理。
     */
    private void ensureClaimSafety(String taskId, String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new RuntimeException("userId 不能为空");
        }
        // 查询运行时任务：来源 ACT_RU_TASK（按 ID_ 与激活状态筛选；受理人 ASSIGNEE_）
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        if (task == null) {
            throw new RuntimeException("任务不存在或不可处理: " + taskId);
        }
        String assignee = task.getAssignee();
        if (assignee == null || assignee.isEmpty()) {
            try {
                taskService.claim(taskId, userId);
            } catch (Exception e) {
                throw new RuntimeException("签收失败: " + e.getMessage(), e);
            }
        } else if (!assignee.equals(userId)) {
            throw new RuntimeException("任务已由他人签收: " + assignee);
        }
    }
}