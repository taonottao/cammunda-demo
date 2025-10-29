package com.example.camundademo.controller;

import com.example.camundademo.dto.ProcessInstanceStatusRequest;
import com.example.camundademo.dto.TaskQueryRequest;
import com.example.camundademo.service.ProcessInstanceService;
import com.example.camundademo.service.ProcessQueryService;
import com.example.camundademo.vo.ProcessDefinitionVO;
import com.example.camundademo.vo.ProcessInstanceStatusVO;
import com.example.camundademo.vo.TaskVO;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.task.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RequestMapping("/process")
@RestController
public class ProcessController {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ProcessQueryService processQueryService;

    @Autowired
    private ProcessInstanceService processInstanceService;

    /**
     * 查询所有流程定义列表
     * 数据来源：ACT_RE_PROCDEF（流程定义），部署信息 ACT_RE_DEPLOYMENT；资源字节存储 ACT_GE_BYTEARRAY。
     */
    @PostMapping("/definitions/list")
    public List<ProcessDefinitionVO> listProcessDefinitions(){
        List<ProcessDefinition> defs = repositoryService.createProcessDefinitionQuery().list();
        List<ProcessDefinitionVO> vos = new ArrayList<>();
        for (ProcessDefinition d : defs) {
            ProcessDefinitionVO vo = new ProcessDefinitionVO();
            vo.setId(d.getId());
            vo.setKey(d.getKey());
            vo.setName(d.getName());
            vo.setVersion(d.getVersion());
            vo.setDeploymentId(d.getDeploymentId());
            vo.setSuspended(d.isSuspended());
            vo.setResourceName(d.getResourceName());
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 查询用户任务列表
     * 数据来源：ACT_RU_TASK（运行时任务，按受理人/候选人/流程过滤）；候选用户/组关系 ACT_RU_IDENTITYLINK（TYPE_='candidate'）。
     * 条件说明：以下所有查询条件为“AND 累加”，不会相互覆盖或清除之前条件；
     * Camunda 的 TaskQuery 为可链式构造器，每次调用都会在同一个查询对象上叠加过滤。
     * 注意：同时传入 assignee 与 candidateUser 通常无交集（已签收任务不再是候选）。
     * active() 仅筛选未挂起的运行时任务（ACT_RU_TASK.SUSPENSION_STATE_=1），并非历史查询。
     */
    @PostMapping("/tasks/list")
    public List<TaskVO> listTasks(@RequestBody TaskQueryRequest request){
        var query = taskService.createTaskQuery();
        // 受理人筛选：仅返回已签收到该用户的任务（AND 累加）
        if (request.getAssignee() != null && !request.getAssignee().isEmpty()) {
            query = query.taskAssignee(request.getAssignee());
        }
        // 候选用户筛选：返回该用户可签收的任务（AND 累加）；候选关系来源 ACT_RU_IDENTITYLINK（TYPE_='candidate'）
        if (request.getCandidateUser() != null && !request.getCandidateUser().isEmpty()) {
            query = query.taskCandidateUser(request.getCandidateUser());
        }
        // 候选组筛选：返回该组成员可签收的任务（AND 累加）；候选关系来源 ACT_RU_IDENTITYLINK（TYPE_='candidate'）
        if (request.getCandidateGroup() != null && !request.getCandidateGroup().isEmpty()) {
            query = query.taskCandidateGroup(request.getCandidateGroup());
        }
        // 按流程实例过滤：仅返回该实例下的任务（AND 累加）
        if (request.getProcessInstanceId() != null && !request.getProcessInstanceId().isEmpty()) {
            query = query.processInstanceId(request.getProcessInstanceId());
        }
        // 按流程定义 Key 过滤：仅返回该定义下的任务（AND 累加）
        if (request.getProcessDefinitionKey() != null && !request.getProcessDefinitionKey().isEmpty()) {
            query = query.processDefinitionKey(request.getProcessDefinitionKey());
        }
        // 仅查询未挂起的运行时任务（AND 累加）：等价于 ACT_RU_TASK.SUSPENSION_STATE_=1；不涉及历史任务
        if (request.getActiveOnly() != null && request.getActiveOnly()) {
            query = query.active();
        }

        List<Task> tasks = query.list();
        List<TaskVO> vos = new ArrayList<>();
        for (Task t : tasks) {
            TaskVO vo = new TaskVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            vo.setAssignee(t.getAssignee());
            vo.setCreateTime(t.getCreateTime());
            vo.setProcessDefinitionId(t.getProcessDefinitionId());
            vo.setProcessInstanceId(t.getProcessInstanceId());
            vos.add(vo);
        }
        return vos;
    }

    /**
     * 查询流程实例整体状态（节点维度）
     */
    @PostMapping("/instances/status")
    public ProcessInstanceStatusVO getInstanceStatus(@RequestBody ProcessInstanceStatusRequest request){
        return processQueryService.getInstanceStatus(request.getProcessInstanceId());
    }

    /**
     * 撤回流程
     * @param processInstanceId 流程实例id
     */
    @GetMapping("/revocationProcess")
    public String revocationProcess(@RequestParam("processInstanceId") String processInstanceId, String initiatorUserId){
        processInstanceService.revocationProcess(processInstanceId, initiatorUserId);
        return "撤回成功";
    }
}