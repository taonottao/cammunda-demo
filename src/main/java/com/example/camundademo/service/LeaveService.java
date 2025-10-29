package com.example.camundademo.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper; // 引入查询条件构造器
import com.example.camundademo.common.Result; // 引入统一返回值
import com.example.camundademo.dto.LeaveStartRequest; // 引入启动请求DTO
import com.example.camundademo.entity.LeaveRequest; // 引入请假实体
import com.example.camundademo.entity.SysUser; // 引入用户实体
import com.example.camundademo.entity.UserProcessLink; // 引入关联实体
import com.example.camundademo.mapper.LeaveRequestMapper; // 引入请假Mapper
import com.example.camundademo.mapper.SysUserMapper; // 引入用户Mapper
import com.example.camundademo.mapper.UserProcessLinkMapper; // 引入关联Mapper
import org.camunda.bpm.engine.IdentityService; // 引入Camunda身份服务
import org.camunda.bpm.engine.RuntimeService; // 引入Camunda运行时服务
import org.camunda.bpm.engine.TaskService; // 引入Camunda任务服务
import org.camunda.bpm.engine.runtime.ProcessInstance; // 引入流程实例类型
import org.camunda.bpm.engine.task.Task; // 引入任务类型
import org.springframework.beans.factory.annotation.Autowired; // 引入依赖注入
import org.springframework.stereotype.Service; // 引入服务注解

import java.time.LocalDate; // 引入日期类型
import java.time.LocalDateTime; // 引入日期时间类型
import java.time.format.DateTimeFormatter; // 引入日期格式化器
import java.util.HashMap; // 引入HashMap
import java.util.Map; // 引入Map

@Service // 标记为服务类
public class LeaveService {

    @Autowired // 注入运行时服务
    private RuntimeService runtimeService; // Camunda运行时服务

    @Autowired // 注入任务服务
    private TaskService taskService; // Camunda任务服务

    @Autowired // 注入身份服务
    private IdentityService identityService; // Camunda身份服务

    @Autowired // 注入用户Mapper
    private SysUserMapper sysUserMapper; // 用户数据库操作

    @Autowired // 注入请假Mapper
    private LeaveRequestMapper leaveRequestMapper; // 请假数据库操作

    @Autowired // 注入关联Mapper
    private UserProcessLinkMapper userProcessLinkMapper; // 关联数据库操作

    /**
     * 业务与流程解耦的请假流程启动：
     * 1. 业务数据先入库（leave_request）
     * 2. 再启动 Camunda 流程（LEAVE_REQ），仅传必要流程变量（如 day/leader/manager/initiator），多人节点依靠 candidateGroups 与组成员维护实现
     * 3. 写入用户-流程实例关联（user_process_link）
     */
    public Result<Map<String, Object>> startLeaveProcess(LeaveStartRequest request) { // 返回统一Result
        // 1) 校验申请人是否存在（按username匹配）
        SysUser applicant = sysUserMapper.selectOne(new QueryWrapper<SysUser>() // 构造查询
                .eq("username", request.getEmployee())); // 按用户名等值查询
        if (applicant == null) { // 若用户不存在
            return Result.fail("USER_NOT_FOUND", "申请人不存在: " + request.getEmployee()); // 返回失败
        }

        // 2) 业务数据入库（leave_request）
        LeaveRequest lr = new LeaveRequest(); // 创建请假实体
        lr.setUserId(applicant.getId()); // 设置申请人ID
        lr.setDay(request.getDay()); // 设置请假天数
        // 将字符串日期转换为LocalDate（假设传入格式为yyyy-MM-dd）
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // 定义日期格式
        lr.setStartTime(request.getStart_time() == null ? null : LocalDate.parse(request.getStart_time(), df)); // 设置开始日期
        lr.setEndTime(request.getEnd_time() == null ? null : LocalDate.parse(request.getEnd_time(), df)); // 设置结束日期
        lr.setReason(request.getReason()); // 设置请假原因
        lr.setStatus("PENDING"); // 初始业务状态（表：leave_request.status）
        leaveRequestMapper.insert(lr); // 入库生成ID（表：leave_request）

        // 3) 启动流程并传入必要变量（保留流程所需的条件与指派）
        Map<String, Object> vars = new HashMap<>(); // 构造流程变量
        vars.put("day", request.getDay()); // 供网关判断请假天数
        vars.put("leader", request.getLeader()); // 组长：可用于单人节点的assignee或作为候选组ID
        vars.put("manager", request.getManager()); // 主管指派（单人或组）
        vars.put("initiator", request.getEmployee()); // 申请人（供BPMN中camunda:initiator使用）
        identityService.setAuthenticatedUserId(request.getEmployee()); // 绑定发起人
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("LEAVE_REQ", vars); // 启动流程
        identityService.clearAuthentication(); // 清除绑定

        // 4) 写入用户-流程关联
        UserProcessLink link = new UserProcessLink(); // 创建关联实体
        link.setUserId(applicant.getId()); // 设置用户ID
        link.setBusinessType("LEAVE_REQ"); // 业务类型
        link.setBusinessId(lr.getId()); // 业务主键
        link.setProcessInstanceId(instance.getProcessInstanceId()); // 流程实例ID
        link.setStatus("RUNNING"); // 初始状态
        userProcessLinkMapper.insert(link); // 入库（表：user_process_link）

        // 5) 返回流程与业务ID
        Map<String, Object> resp = new HashMap<>(); // 构造返回数据
        resp.put("processInstanceId", instance.getProcessInstanceId()); // 放入流程实例ID
        resp.put("definitionId", instance.getProcessDefinitionId()); // 放入流程定义ID
        resp.put("leaveId", lr.getId()); // 放入业务ID
        return Result.ok(resp); // 返回成功结果
    }

    /**
     * 撤回并重启流程：删除旧流程实例，保留业务数据，重新启动流程并建立新的关联。
     * 说明：业务数据与流程解耦，不再做复杂的回退节点，直接重启即可。
     */
    public Result<Map<String, Object>> revokeAndRestart(String processInstanceId, String initiatorUserId) {
        // 1) 查询旧关联
        UserProcessLink oldLink = userProcessLinkMapper.selectOne(new QueryWrapper<UserProcessLink>()
                .eq("process_instance_id", processInstanceId));
        if (oldLink == null) {
            return Result.fail("LINK_NOT_FOUND", "未找到流程关联: " + processInstanceId);
        }

        // 2) 查询业务记录（保留原业务数据）
        LeaveRequest lr = leaveRequestMapper.selectById(oldLink.getBusinessId());
        if (lr == null) {
            return Result.fail("BUSINESS_NOT_FOUND", "未找到请假业务: " + oldLink.getBusinessId());
        }

        // 3) 组装新流程变量（从业务与旧流程变量获取；若缺省则回落默认组ID）
        Map<String, Object> newVars = new HashMap<>();
        newVars.put("day", lr.getDay()); // 以业务天数为准
        // 尝试从旧流程读取 leader/manager；若不存在则使用默认组ID
        Object leaderVar = null;
        Object managerVar = null;
        try {
            leaderVar = runtimeService.getVariable(processInstanceId, "leader");
            managerVar = runtimeService.getVariable(processInstanceId, "manager");
        } catch (Exception ignored) { }
        newVars.put("leader", leaderVar != null ? leaderVar : "leader"); // 默认组ID
        newVars.put("manager", managerVar != null ? managerVar : "manager"); // 默认组ID
        newVars.put("initiator", initiatorUserId); // 发起人（可为原申请人或当前操作者）

        // 4) 删除旧流程实例（保留业务数据）
        try {
            runtimeService.deleteProcessInstance(processInstanceId, "revoked and restarted");
        } catch (Exception e) {
            return Result.fail("DELETE_FAILED", "删除旧流程失败: " + e.getMessage());
        }

        // 5) 启动新流程并建立新关联
        identityService.setAuthenticatedUserId(initiatorUserId);
        ProcessInstance newInstance = runtimeService.startProcessInstanceByKey("LEAVE_REQ", newVars);
        identityService.clearAuthentication();

        UserProcessLink newLink = new UserProcessLink();
        newLink.setUserId(oldLink.getUserId());
        newLink.setBusinessType(oldLink.getBusinessType());
        newLink.setBusinessId(oldLink.getBusinessId());
        newLink.setProcessInstanceId(newInstance.getProcessInstanceId());
        newLink.setStatus("RUNNING");
        userProcessLinkMapper.insert(newLink); // 新关联入库（表：user_process_link）

        // 6) 标记旧关联为REVOKED并记录结束时间
        oldLink.setStatus("REVOKED"); // 旧关联置为撤回（表：user_process_link.status）
        oldLink.setEndTime(LocalDateTime.now()); // 记录结束时间（表：user_process_link.end_time）
        userProcessLinkMapper.updateById(oldLink); // 更新旧关联（表：user_process_link）

        // 7) 返回新流程与旧流程ID以便前端感知
        Map<String, Object> resp = new HashMap<>();
        resp.put("oldProcessInstanceId", processInstanceId);
        resp.put("newProcessInstanceId", newInstance.getProcessInstanceId());
        resp.put("businessId", oldLink.getBusinessId());
        return Result.ok(resp);
    }

    /**
     * 审批同意：完成任务并同步业务状态
     */
    public Result<Boolean> approveTask(String taskId, String userId) { // 返回统一Result
        ensureClaimSafety(taskId, userId); // 保证签收安全
        Map<String, Object> vars = new HashMap<>(); // 构造变量
        vars.put("approved", true); // 设置通过标记
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult(); // 查询任务
        taskService.complete(taskId, vars); // 完成任务
        if (task != null) { // 若任务存在
            String pi = task.getProcessInstanceId(); // 获取流程实例ID
            // 查找关联记录
            UserProcessLink link = userProcessLinkMapper.selectOne(new QueryWrapper<UserProcessLink>() // 构造查询
                    .eq("process_instance_id", pi)); // 按流程实例ID等值查询
            if (link != null) { // 若存在关联
                // 更新业务状态
                LeaveRequest lr = leaveRequestMapper.selectById(link.getBusinessId()); // 查询业务记录
                if (lr != null) { // 若业务存在
                    lr.setStatus("APPROVED"); // 设置为同意（表：leave_request.status）
                    leaveRequestMapper.updateById(lr); // 更新（表：leave_request）
                }
            }
        }
        return Result.ok(true); // 返回成功
    }

    /**
     * 审批拒绝：完成任务并同步业务状态
     */
    public Result<Boolean> rejectTask(String taskId, String userId) { // 返回统一Result
        ensureClaimSafety(taskId, userId); // 保证签收安全
        Map<String, Object> vars = new HashMap<>(); // 构造变量
        vars.put("approved", false); // 设置拒绝标记
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult(); // 查询任务
        taskService.complete(taskId, vars); // 完成任务
        if (task != null) { // 若任务存在
            String pi = task.getProcessInstanceId(); // 获取流程实例ID
            // 查找关联记录
            UserProcessLink link = userProcessLinkMapper.selectOne(new QueryWrapper<UserProcessLink>() // 构造查询
                    .eq("process_instance_id", pi)); // 按流程实例ID等值查询
            if (link != null) { // 若存在关联
                // 更新业务状态
                LeaveRequest lr = leaveRequestMapper.selectById(link.getBusinessId()); // 查询业务记录
                if (lr != null) { // 若业务存在
                    lr.setStatus("REJECTED"); // 设置为拒绝（表：leave_request.status）
                    leaveRequestMapper.updateById(lr); // 更新（表：leave_request）
                }
            }
        }
        return Result.ok(true); // 返回成功
    }

    /**
     * 签收安全逻辑：
     * 1) 任务必须存在且处于激活状态；
     * 2) userId 不能为空；
     * 3) 若任务未签收，则尝试以 userId 签收；
     * 4) 若已签收且签收人不是 userId，则拒绝操作并提示当前签收人；
     * 5) 若已被 userId 签收，则直接继续处理。
     */
    private void ensureClaimSafety(String taskId, String userId) { // 保证签收安全
        if (userId == null || userId.isEmpty()) { // 校验用户ID
            throw new RuntimeException("userId 不能为空"); // 抛出异常
        }
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult(); // 查询激活任务
        if (task == null) { // 若任务不存在
            throw new RuntimeException("任务不存在或不可处理: " + taskId); // 抛出异常
        }
        String assignee = task.getAssignee(); // 获取签收人
        if (assignee == null || assignee.isEmpty()) { // 若未签收
            try {
                taskService.claim(taskId, userId); // 尝试签收
            } catch (Exception e) { // 捕获异常
                throw new RuntimeException("签收失败: " + e.getMessage(), e); // 抛出异常
            }
        } else if (!assignee.equals(userId)) { // 已签收但非当前用户
            throw new RuntimeException("任务已由他人签收: " + assignee); // 拒绝处理
        }
    }
}