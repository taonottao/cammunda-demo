package com.example.camundademo.entity;

import com.baomidou.mybatisplus.annotation.IdType; // 引入主键类型枚举
import com.baomidou.mybatisplus.annotation.TableField; // 引入字段映射注解
import com.baomidou.mybatisplus.annotation.TableId; // 引入主键注解
import com.baomidou.mybatisplus.annotation.TableName; // 引入表名注解
import lombok.Data; // 引入Lombok的@Data注解

import java.time.LocalDateTime; // 引入时间类型

@Data // 生成getter/setter等
@TableName("sys_user") // 指定对应的表名
public class SysUser {
    @TableId(value = "id", type = IdType.AUTO) // 主键自增
    private Long id; // 主键ID

    @TableField("username") // 映射用户名列
    private String username; // 登录用户名

    @TableField("nickname") // 映射昵称列
    private String nickname; // 昵称

    @TableField("email") // 映射邮箱列
    private String email; // 邮箱

    @TableField("phone") // 映射手机号列
    private String phone; // 手机号

    @TableField("status") // 映射状态列
    private Integer status; // 状态：1启用 0禁用

    @TableField("created_at") // 映射创建时间列
    private LocalDateTime createdAt; // 创建时间

    @TableField("updated_at") // 映射更新时间列
    private LocalDateTime updatedAt; // 更新时间
}