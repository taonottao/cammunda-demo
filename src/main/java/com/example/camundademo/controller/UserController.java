package com.example.camundademo.controller;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import com.example.camundademo.entity.SysUser; // 业务用户实体
import com.example.camundademo.mapper.SysUserMapper; // 业务用户Mapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper; // MyBatis-Plus查询条件
import com.example.camundademo.common.Result; // 统一返回值
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

    @Autowired
    private SysUserMapper sysUserMapper; // 注入业务用户Mapper

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

    /**
     * 初始化用户：在 Camunda 与 业务库 sys_user 同步插入所需用户
     * 说明：同步到 Camunda 用户表的通用接口未来补充，目前仅插入必要数据。
     */
    @PostMapping("/inituser")
    public Result<String> initUsers(){
        // 1) Camunda 侧：创建流程所需的组
        createGroupIfAbsent("employee", "员工"); // 员工组
        createGroupIfAbsent("leader", "组长"); // 组长组
        createGroupIfAbsent("manager", "主管"); // 主管组

        // 2) Camunda 侧：创建流程所需的用户并加入组
        createUserIfAbsent("employee", "申请人", "张三", "employee"); // 申请人
        createUserIfAbsent("leader", "组长", "李四", "leader"); // 组长
        createUserIfAbsent("leaderA", "组长A", "王一", "leader"); // 候选 A
        createUserIfAbsent("leaderB", "组长B", "赵二", "leader"); // 候选 B
        createUserIfAbsent("manager", "主管", "王主管", "manager"); // 主管

        // 3) 业务库侧：将上述用户插入到 sys_user（若不存在则创建）
        upsertBusinessUser("employee", "张三"); // 申请人
        upsertBusinessUser("leader", "李四"); // 组长
        upsertBusinessUser("leaderA", "王一"); // 候选 A
        upsertBusinessUser("leaderB", "赵二"); // 候选 B
        upsertBusinessUser("manager", "王主管"); // 主管

        return Result.ok("初始化完成：Camunda 与 业务库已插入必要用户"); // 返回成功
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
            identityService.saveGroup(group); // Camunda 侧入库（表：ACT_ID_GROUP）
        }
    }

    private void createUserIfAbsent(String userId, String firstName, String lastName, String groupId) {
        if (identityService.createUserQuery().userId(userId).singleResult() == null) {
            User user = identityService.newUser(userId);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setPassword("123456");
            identityService.saveUser(user); // Camunda 侧入库（表：ACT_ID_USER）
        }
        // 建立用户与组的关联（若已存在则忽略异常）
        try {
            identityService.createMembership(userId, groupId); // Camunda 侧入库（表：ACT_ID_MEMBERSHIP）
        } catch (Exception ignored) {
        }
    }

    // 业务库用户插入（若不存在则新增）
    private void upsertBusinessUser(String username, String nickname){
        SysUser exist = sysUserMapper.selectOne(new QueryWrapper<SysUser>() // 构造查询
                .eq("username", username)); // 按用户名等值查询
        if (exist == null) { // 不存在则插入
            SysUser u = new SysUser(); // 创建实体
            u.setUsername(username); // 设置用户名
            u.setNickname(nickname); // 设置昵称
            u.setStatus(1); // 默认启用
            sysUserMapper.insert(u); // 入库（表：sys_user）
        }
    }

}
