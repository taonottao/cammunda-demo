package com.example.camundademo.entity;

import com.baomidou.mybatisplus.annotation.IdType; // 主键类型
import com.baomidou.mybatisplus.annotation.TableField; // 字段映射
import com.baomidou.mybatisplus.annotation.TableId; // 主键注解
import com.baomidou.mybatisplus.annotation.TableName; // 表名注解
import lombok.Data; // Lombok注解

import java.time.LocalDateTime; // 时间类型

@Data // 生成常用方法
@TableName("user_process_link") // 映射到关联表
public class UserProcessLink {
    @TableId(value = "id", type = IdType.AUTO) // 主键自增
    private Long id; // 主键ID

    @TableField("user_id") // 用户ID
    private Long userId; // 关联sys_user.id

    @TableField("business_type") // 业务类型
    private String businessType; // 如：LEAVE_REQ

    @TableField("business_id") // 业务ID
    private Long businessId; // 如：leave_request.id

    @TableField("process_instance_id") // 流程实例ID
    private String processInstanceId; // Camunda流程实例ID

    @TableField("start_time") // 启动时间
    private LocalDateTime startTime; // 流程启动时间

    @TableField("end_time") // 结束时间
    private LocalDateTime endTime; // 流程结束时间

    @TableField("status") // 关联状态
    private String status; // RUNNING/FINISHED/REVOKED
}