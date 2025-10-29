# 请假流程整体说明（LEAVE_REQ）

本文档说明：基于 MyBatis-Plus + Lombok，业务数据与 Camunda 流程解耦的整体流程，并给出接口文档（按顺序：用户准备 → 流程开启 → 审批通过/退回 → 撤回）。

## 一、数据与流程解耦
- 业务表：`sys_user`、`leave_request`、`user_process_link`
- 流程定义：`bpmn/leaveReq.bpmn`（流程 Key：`LEAVE_REQ`）
- 解耦原则：业务字段仅入库；流程只接收判断与指派所需的最小变量（如 `day/leader/manager/initiator`），多人节点统一使用“用户组”。

## 二、用户准备（同步到 Camunda + 业务库）
- 说明：目前通用“从业务库同步到 Camunda 用户表”的接口暂不实现（待补充）。本阶段采用初始化接口在 Camunda 插入必要用户，同时在业务库 `sys_user` 插入同名用户。

### 2.1 初始化接口
- 路径：`POST /user/inituser`
- 功能：
  - 在 Camunda 创建组：`employee`（员工）、`leader`（组长）、`manager`（主管）
  - 在 Camunda 创建用户：`employee`、`leader`、`leaderA`、`leaderB`、`manager`，并建立组成员关系
  - 在业务库 `sys_user` 中插入对应用户名（若不存在则插入）
- 请求体：无
- 返回值：`Result<String>`
  - 示例：`{"success":true,"code":"SUCCESS","message":"OK","data":"初始化完成：Camunda 与 业务库已插入必要用户"}`
- 表变动：
  - Camunda 身份表：`ACT_ID_GROUP`（插入/存在即跳过）、`ACT_ID_USER`（插入/存在即跳过）、`ACT_ID_MEMBERSHIP`（插入用户-组关系）
  - 业务表：`sys_user`（插入不存在的用户）

## 三、开启流程（LEAVE_REQ）

### 3.1 接口：启动请假流程
- 路径：`POST /leave/start`
- 功能：
  - 校验申请人是否存在于业务库 `sys_user`（字段：`employee` → `sys_user.username`）
  - 新增 `leave_request` 业务记录（`status=PENDING`）
  - 启动 `LEAVE_REQ` 流程，仅传必要变量：`day/leader/manager/initiator`（多人节点依靠 `candidateGroups` 与组成员维护实现）
  - 建立 `user_process_link` 关联：`user_id/business_id/process_instance_id/status=RUNNING`
- 请求体（JSON）：
```
{
  "day": 2,
  "start_time": "2025-11-01",
  "end_time": "2025-11-02",
  "reason": "身体不适",
  "employee": "employee",
  "leader": "leader",
  "manager": "manager"
}
```
- 返回值：`Result<{processInstanceId,definitionId,leaveId}>`
  - 示例：
```
{
  "success": true,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "processInstanceId": "<PI_ID>",
    "definitionId": "<DEF_ID>",
    "leaveId": 1001
  }
}
```
- 表变动：
  - 业务表：`leave_request`（插入）、`user_process_link`（插入）
  - 流程表（Camunda）：运行时创建与变量写入（如 `ACT_RU_EXECUTION`、`ACT_RU_VARIABLE`、`ACT_RU_TASK` 等）

### 3.2 任务查询与签收
- 说明：在进行签收、通过或退回前，需要先查询到待办任务的 `taskId`。
- 接口：`POST /process/tasks/list`
- 请求体字段：
  - `assignee`（可选）：按受理人查询已签收任务
  - `candidateUser`（可选）：按候选用户查询可签收任务
  - `candidateGroup`（可选）：按候选组查询可签收任务（推荐用于多人节点）
  - `processInstanceId`（可选）：按流程实例过滤
  - `processDefinitionKey`（可选）：按流程定义过滤
  - `activeOnly`（布尔，默认 `true`）：仅查未挂起的运行时任务
- 示例一（按候选组查询当前可签收任务）：
```
{
  "candidateGroup": "leader",
  "activeOnly": true
}
```
- 示例二（按受理人查询自己已签收任务）：
```
{
  "assignee": "leader",
  "activeOnly": true
}
```
- 返回值：`List<TaskVO>`（当前实现直接返回列表）
  - 结构：`id | name | assignee | createTime | processDefinitionId | processInstanceId`
  - 示例：
```
[
  {
    "id": "aTaskId123",
    "name": "组长审批",
    "assignee": null,
    "createTime": "2025-10-29T12:00:00",
    "processDefinitionId": "LEAVE_REQ:1:abc",
    "processInstanceId": "pi_001"
  }
]
```
- 使用说明：
  - 从返回列表中读取 `id` 作为 `taskId`，用于后续的 `POST /leave/approve` 或 `POST /leave/reject`。
  - 当前实现的审批接口会在未签收时自动以 `userId` 进行签收；如需显式签收，可在后续扩展独立的 `claim` 接口。
- 表变动：无（只读查询）

## 四、审批阶段

流程中用户任务在 BPMN 中通过 `leader`（可同时作为单人指派与候选组ID）/`manager` 指派或候选，网关通过 `day` 与 `approved` 判断流向。多人节点统一使用 `camunda:candidateGroups`。

### 4.1 审批通过
- 路径：`POST /leave/approve`
- 请求体：
```
{
  "taskId": "<TASK_ID>",
  "userId": "leader"
}
```
- 行为：
  - 安全签收（若未签收则以 `userId` 签收，已签收但非当前用户则拒绝）
  - 完成任务并设置变量：`approved=true`
  - 同步业务：根据 `processInstanceId` 更新 `leave_request.status=APPROVED`
- 表变动：
  - 业务表：`leave_request`（更新 `status=APPROVED`）
  - 流程表（Camunda）：任务完成影响 `ACT_RU_TASK`，历史记录写入 `ACT_HI_ACTINST`/`ACT_HI_TASKINST`/`ACT_HI_VARINST` 等
- 返回值：`Result<Boolean>`（`data=true`）

### 4.2 审批退回（拒绝）
- 路径：`POST /leave/reject`
- 请求体：
```
{
  "taskId": "<TASK_ID>",
  "userId": "leader"
}
```
- 行为：
  - 安全签收，完成任务并设置变量：`approved=false`
  - 同步业务：根据 `processInstanceId` 更新 `leave_request.status=REJECTED`
- 表变动：
  - 业务表：`leave_request`（更新 `status=REJECTED`）
  - 流程表（Camunda）：任务完成影响 `ACT_RU_TASK`，历史记录写入 `ACT_HI_*`
- 返回值：`Result<Boolean>`（`data=true`）

## 五、撤回流程实例

### 5.1 撤回接口（删除并重启）
- 路径：`POST /leave/revoke`
- 请求体：
```
{
  "processInstanceId": "<PI_ID>",
  "initiatorUserId": "employee"
}
```
- 行为：
  - 删除旧流程实例（保留业务数据不变）
  - 以业务数据为主（如 `day`）并结合旧流程变量/默认组，重新启动 `LEAVE_REQ`
  - 建立新的 `user_process_link` 关联（`status=RUNNING`）
  - 将旧关联标记为 `REVOKED` 并记录结束时间
- 返回值：`Result<{oldProcessInstanceId,newProcessInstanceId,businessId}>`
- 表变动：
  - 业务表：`user_process_link`（旧关联更新 `status=REVOKED` 与 `end_time`；新关联插入并置 `status=RUNNING`）
  - 流程表（Camunda）：删除旧流程清理 `ACT_RU_*`；重启新流程创建 `ACT_RU_*` 与变量写入 `ACT_RU_VARIABLE`
 特殊说明，此时撤回流程有问题因为撤回了就会立刻开启流程，因为是一个demo，业务上没有那么完善
## 六、接口返回与参数规范
- 统一返回结构：`Result<T>` → `success | code | message | data`
- 参数规范：
  - 字段命名简洁明确（`employee/leader/manager` 与 BPMN 一致），多人节点通过组维护实现。
  - 日期格式：`yyyy-MM-dd`（`start_time/end_time`）

## 七、后续规划（TODO）
- 通用的“业务用户同步到 Camunda 身份表”的接口：从 `sys_user` 批量写入 Camunda，并建立组策略。
- 流程结束事件监听：当流程自然结束时，设置 `user_process_link.status=FINISHED` 并记录结束时间。
- 审批意见存储：扩展 `leave_request` 或新增 `leave_request_opinion` 表记录每次节点审批意见。

## 八、使用顺序建议
1. 调用 `POST /user/inituser` 完成用户与组的初始化
2. 调用 `POST /leave/start` 启动请假流程
3. 执行任务审批：`POST /leave/approve` 或 `POST /leave/reject`
4. 如需撤回流程，调用 `POST /leave/revoke`

## 九、人员配置与动态调整（重要）

针对后续节点（尚未到达的用户任务），在节点到达之前可以灵活调整指派与候选：

- 开启时一次性指定所有参与者：
  - 在 `POST /leave/start` 的请求体中传入 `leader/manager/initiator` 等变量，BPMN 通过表达式引用这些变量；多人节点推荐使用固定组（如 `candidateGroups="leader"`），或表达式 `candidateGroups="${candidateGroups}"`（变量为组ID集合）。

- 在流程运行中（令牌到达该节点之前）修改即将到达节点的参与者：
  - 推荐优先维护组成员：`identityService.createMembership("userId", "groupId")` / `identityService.deleteMembership(...)`
  - 如需在未到达节点前切换候选组：`runtimeService.setVariable(processInstanceId, "candidateGroups", Arrays.asList("leader","manager"))`
  - 这些设置会在令牌进入对应 `userTask` 时被 BPMN 的 `camunda:candidateGroups` 解析并生效。

- 对于已经激活的当前任务（令牌已在节点上）：
  - 单人节点改派：`taskService.setAssignee(taskId, "otherUser")`
  - 候选用户增删：`taskService.addCandidateUser(taskId, "userX")` / `taskService.deleteCandidateUser(taskId, "userY")`
  - 候选组增删：`taskService.addCandidateGroup(taskId, "groupId")` / `taskService.deleteCandidateGroup(taskId, "groupId")`
  - 建议记录改派与候选调整的审计日志（操作者、时间、原因），保持业务可追溯。

- 通过用户组来扩展候选：
  - 若 BPMN 使用 `camunda:candidateGroups` 指定某组，只需在运行期往该组中加入更多成员：
    - `identityService.createMembership(userId, groupId)`
  - 组成员变化会即时影响该组候选任务的可签收范围。

### 推荐的接口设计（可选，后续扩展）
- 更新即将到达节点的变量（影响未来节点的人）：
  - `POST /process/vars/update`（入参：`processInstanceId` + `updates` 字典）
- 当前任务改派：
  - `POST /task/reassign`（入参：`taskId`、`newAssignee`、`operatorId`）
- 当前任务候选增删：
  - `POST /task/candidates/update`（入参：`taskId`、`addUsers[]`、`removeUsers[]`、可选 `addGroups[]`、`removeGroups[]`）
- 组成员维护：
  - `POST /user/group/membership/add`（入参：`userId`、`groupId`）

以上接口未在当前版本实现，现阶段可通过已有 `inituser` 与 Camunda 内置 API 完成配置；若你希望，我可以按阿里规范补充这些接口的请求/返回定义并落地实现。