package com.example.camundademo.controller;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @version 1.0
 * @Author T-WANG
 * @Date 2025/10/29 11:04
 */
@RequestMapping("/user")
@RestController
public class UserController {

    @Autowired
    private IdentityService identityService;

    /**
     * 添加cammunda用户
     */
    @PostMapping("/add")
    public String addUser(){
        // 创建流程所需的组：员工、组长、主管
        createGroupIfAbsent("employee", "员工");
        createGroupIfAbsent("leader", "组长");
        createGroupIfAbsent("manager", "主管");

        // 创建流程所需的用户：申请人、组长（单人/多人候选）、主管
        createUserIfAbsent("employee", "申请人", "张三", "employee");
        createUserIfAbsent("leader", "组长", "李四", "leader");
        createUserIfAbsent("leaderA", "组长A", "王一", "leader");
        createUserIfAbsent("leaderB", "组长B", "赵二", "leader");
        createUserIfAbsent("manager", "主管", "王主管", "manager");

        return "已创建（或已存在）用户与组：employee, leader, leaderA, leaderB, manager";
    }



    // 组与用户创建的辅助方法
    private void createGroupIfAbsent(String groupId, String groupName) {
        if (identityService.createGroupQuery().groupId(groupId).singleResult() == null) {
            Group group = identityService.newGroup(groupId);
            group.setName(groupName);
            /**
             * groupType 是用户组的分类标签，存储在 ACT_ID_GROUP.TYPE_
             * 主要用于查询过滤与分类管理，例如区分“流程相关组”和“系统管理组”
             */
            group.setType("WORKFLOW");
            identityService.saveGroup(group);
        }
    }

    private void createUserIfAbsent(String userId, String firstName, String lastName, String groupId) {
        if (identityService.createUserQuery().userId(userId).singleResult() == null) {
            User user = identityService.newUser(userId);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setPassword("123456");
            identityService.saveUser(user);
        }
        // 建立用户与组的关联（若已存在则忽略异常）
        try {
            identityService.createMembership(userId, groupId);
        } catch (Exception ignored) {
        }
    }

}
