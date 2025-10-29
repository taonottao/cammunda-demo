package com.example.camundademo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper; // 引入查询构造器
import com.example.camundademo.common.Result; // 引入统一返回值
import com.example.camundademo.entity.SysUser; // 引入用户实体
import com.example.camundademo.mapper.SysUserMapper; // 引入用户Mapper
import org.springframework.beans.factory.annotation.Autowired; // 引入依赖注入
import org.springframework.validation.annotation.Validated; // 引入校验注解
import org.springframework.web.bind.annotation.PostMapping; // 引入Post映射
import org.springframework.web.bind.annotation.RequestBody; // 引入请求体注解
import org.springframework.web.bind.annotation.RequestMapping; // 引入请求映射
import org.springframework.web.bind.annotation.RestController; // 引入Rest控制器

import javax.validation.constraints.NotBlank; // 引入参数校验

@RequestMapping("/sys/user") // 基础路径
@RestController // 标记为Rest控制器
@Validated // 启用参数校验
public class SysUserController {

    @Autowired // 注入用户Mapper
    private SysUserMapper sysUserMapper; // 用户数据库操作

    public static class CreateUserRequest { // 创建用户请求DTO
        @NotBlank(message = "username不能为空") // 参数校验
        public String username; // 用户名
        public String nickname; // 昵称
        public String email; // 邮箱
        public String phone; // 手机号
    }

    /**
     * 新增用户（入库），与Camunda身份体系解耦的业务用户。
     */
    @PostMapping("/create") // POST接口
    public Result<Long> create(@RequestBody CreateUserRequest req) { // 统一返回Long主键
        // 检查用户名是否已存在
        SysUser exist = sysUserMapper.selectOne(new QueryWrapper<SysUser>() // 构造查询
                .eq("username", req.username)); // 按用户名等值查询
        if (exist != null) { // 已存在则提示
            return Result.fail("USERNAME_EXISTS", "用户名已存在: " + req.username); // 返回失败
        }

        // 构造用户实体
        SysUser u = new SysUser(); // 创建实体
        u.setUsername(req.username); // 设置用户名
        u.setNickname(req.nickname); // 设置昵称
        u.setEmail(req.email); // 设置邮箱
        u.setPhone(req.phone); // 设置手机号
        u.setStatus(1); // 默认启用
        sysUserMapper.insert(u); // 入库（表：sys_user）
        return Result.ok(u.getId()); // 返回主键ID
    }
}