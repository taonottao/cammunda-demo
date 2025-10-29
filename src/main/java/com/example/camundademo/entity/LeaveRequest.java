package com.example.camundademo.entity;

import com.baomidou.mybatisplus.annotation.IdType; // 主键类型
import com.baomidou.mybatisplus.annotation.TableField; // 字段映射
import com.baomidou.mybatisplus.annotation.TableId; // 主键注解
import com.baomidou.mybatisplus.annotation.TableName; // 表名注解
import lombok.Data; // Lombok注解

import java.time.LocalDate; // 日期类型
import java.time.LocalDateTime; // 日期时间类型

@Data // 生成常用方法
@TableName("leave_request") // 映射到请假业务表
public class LeaveRequest {
    @TableId(value = "id", type = IdType.AUTO) // 主键自增
    private Long id; // 主键ID

    @TableField("user_id") // 申请人ID
    private Long userId; // 关联sys_user.id

    @TableField("day") // 请假天数
    private Integer day; // 天数

    @TableField("start_time") // 开始日期
    private LocalDate startTime; // 请假开始日期

    @TableField("end_time") // 结束日期
    private LocalDate endTime; // 请假结束日期

    @TableField("reason") // 原因
    private String reason; // 请假原因

    @TableField("status") // 业务状态
    private String status; // PENDING/APPROVED/REJECTED/REVOKED

    @TableField("created_at") // 创建时间
    private LocalDateTime createdAt; // 创建时间

    @TableField("updated_at") // 更新时间
    private LocalDateTime updatedAt; // 更新时间
}