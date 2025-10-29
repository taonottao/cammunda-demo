package com.example.camundademo.service;

import com.example.camundademo.vo.ProcessInstanceNodeVO;
import com.example.camundademo.vo.ProcessInstanceStatusVO;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProcessQueryService {

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private TaskService taskService;

    public ProcessInstanceStatusVO getInstanceStatus(String processInstanceId){
        ProcessInstanceStatusVO vo = new ProcessInstanceStatusVO();

        // 查询运行时流程实例：来源 ACT_RU_EXECUTION（根执行行，ID_=PROC_INST_ID_）
        org.camunda.bpm.engine.runtime.ProcessInstance runtimePi =
                runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        // 查询历史流程实例：来源 ACT_HI_PROCINST（END_TIME_ 是否为 null 判断是否已结束）
        HistoricProcessInstance histPi =
                historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();

        String definitionId = histPi != null ? histPi.getProcessDefinitionId() : (runtimePi != null ? runtimePi.getProcessDefinitionId() : null);
        vo.setProcessInstanceId(processInstanceId);
        vo.setProcessDefinitionId(definitionId);

        if (definitionId != null) {
            ProcessDefinition pd = repositoryService.getProcessDefinition(definitionId);
            vo.setProcessDefinitionKey(pd.getKey());
            vo.setProcessDefinitionName(pd.getName());
        }
        vo.setEnded(histPi != null && histPi.getEndTime() != null);

        List<ProcessInstanceNodeVO> nodes = new ArrayList<>();

        if (definitionId != null) {
            // 解析 BPMN 模型：定义与资源存储在 ACT_RE_PROCDEF / ACT_GE_BYTEARRAY
            BpmnModelInstance model = repositoryService.getBpmnModelInstance(definitionId);
            Process process = model.getModelElementsByType(Process.class).iterator().next();
            // 找开始事件
            StartEvent start = process.getChildElementsByType(StartEvent.class).iterator().next();
            // BFS 遍历所有可达节点，构造稳定顺序
            Queue<FlowNode> queue = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            queue.add(start);
            while(!queue.isEmpty()){
                FlowNode node = queue.poll();
                if (!visited.add(node.getId())) continue;

                ProcessInstanceNodeVO nvo = buildNodeStatus(processInstanceId, node);
                nodes.add(nvo);

                for (SequenceFlow seq : node.getOutgoing()) {
                    FlowNode target = (FlowNode) seq.getTarget();
                    queue.add(target);
                }
            }
        }

        vo.setNodes(nodes);
        return vo;
    }

    private ProcessInstanceNodeVO buildNodeStatus(String processInstanceId, FlowNode node){
        ProcessInstanceNodeVO nvo = new ProcessInstanceNodeVO();
        nvo.setActivityId(node.getId());
        nvo.setName(node.getName());
        nvo.setActivityType(node.getElementType().getTypeName());

        // 查询历史活动：来源 ACT_HI_ACTINST（按活动ID筛选）
        List<HistoricActivityInstance> hais = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityId(node.getId())
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        if (hais.isEmpty()) {
            nvo.setStatus("pending");
        } else {
            Date startTime = hais.get(0).getStartTime();
            Date endTime = null;
            boolean anyActive = false;
            for (HistoricActivityInstance h : hais) {
                if (h.getEndTime() == null) {
                    anyActive = true;
                } else {
                    endTime = h.getEndTime();
                }
            }
            nvo.setStartTime(startTime);
            nvo.setEndTime(anyActive ? null : endTime);
            nvo.setStatus(anyActive ? "active" : "completed");
        }

        // 若为用户任务，补充当前活动任务的taskId与assignee
        if ("userTask".equalsIgnoreCase(nvo.getActivityType())) {
            // 查询当前活动的用户任务：来源 ACT_RU_TASK（可结合 ACT_RU_IDENTITYLINK 获取候选信息）
            List<Task> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .taskDefinitionKey(node.getId())
                    .active()
                    .list();
            if (!activeTasks.isEmpty()) {
                // 这里只取第一个；如为多实例可调整为数组返回
                Task t = activeTasks.get(0);
                nvo.setTaskId(t.getId());
                nvo.setAssignee(t.getAssignee());
            }
        }

        return nvo;
    }
}