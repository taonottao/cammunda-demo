package com.example.camundademo.service;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.runtime.ProcessInstanceModificationBuilder;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @version 1.0
 * @Author T-WANG
 * @Date 2025/10/29 14:49
 */
@Service
@Slf4j
public class ProcessInstanceService {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private IdentityService identityService;

    /**
     * 撤回流程实例（回退到发起节点）
     */
    public void revocationProcess(String processInstanceId, String initiatorUserId) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) return;

        identityService.setAuthenticatedUserId(initiatorUserId);

        // Step 1. 找目标节点（提前找）
        String targetId = resolveRevocationTargetActivityId(processInstanceId);

        // Step 2. 先构建一个 modification，但不要立即 execute()
        ProcessInstanceModificationBuilder builder = runtimeService.createProcessInstanceModification(processInstanceId);
        ActivityInstance tree = runtimeService.getActivityInstance(processInstanceId);

        if (tree != null) {
            cancelActivitiesRecursively(builder, tree);
        } else {
            runtimeService.getActiveActivityIds(processInstanceId)
                    .forEach(builder::cancelAllForActivity);
        }

        // ✅ Step 3. 同一个 builder 连续操作，再一次性 execute()
        if (targetId != null) {
            builder.startBeforeActivity(targetId);
        }

        builder.execute(); // ✅ 一次执行，避免先删光再启动的问题

        // Step 4. 设置标记
        runtimeService.setVariable(processInstanceId, "revoked", true);
    }


    /**
     * 递归取消所有活动节点
     */
    private void cancelActivitiesRecursively(ProcessInstanceModificationBuilder builder, ActivityInstance node) {
        for (ActivityInstance child : node.getChildActivityInstances()) {
            cancelActivitiesRecursively(builder, child);
        }
        if (node.getParentActivityInstanceId() != null) {
            builder.cancelActivityInstance(node.getId());
        }
    }

    /**
     * 确定撤回目标节点：优先第一个历史 userTask，否则从 BPMN 模型解析第一个 UserTask
     */
    private String resolveRevocationTargetActivityId(String processInstanceId) {
        // 1️⃣ 查历史任务
        List<HistoricActivityInstance> userTaskHist = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();

        if (!userTaskHist.isEmpty()) {
            return userTaskHist.get(0).getActivityId();
        }

        // 2️⃣ 查流程定义结构（兜底）
        HistoricProcessInstance histPi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (histPi == null) return null;

        String definitionId = histPi.getProcessDefinitionId();
        if (definitionId == null) return null;

        BpmnModelInstance model = repositoryService.getBpmnModelInstance(definitionId);
        if (model == null) return null;

        Process process = model.getModelElementsByType(Process.class).stream().findFirst().orElse(null);
        if (process == null) return null;

        StartEvent start = process.getChildElementsByType(StartEvent.class).stream().findFirst().orElse(null);
        if (start == null) return null;

        Queue<FlowNode> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            FlowNode node = queue.poll();
            if (!visited.add(node.getId())) continue;
            if (node instanceof UserTask) {
                return node.getId();
            }
            for (SequenceFlow seq : node.getOutgoing()) {
                FlowNode target = (FlowNode) seq.getTarget();
                queue.add(target);
            }
        }

        return null;
    }


}
