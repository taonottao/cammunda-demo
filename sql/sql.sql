-- 数据库：flow_demo
-- 说明：业务与流程解耦所需三张表（用户、请假业务、用户-流程关联）

-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名（唯一）',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `phone` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 请假业务表
CREATE TABLE IF NOT EXISTS `leave_request` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '申请人用户ID',
  `day` INT NOT NULL COMMENT '请假天数',
  `start_time` DATE NOT NULL COMMENT '请假开始日期',
  `end_time` DATE NOT NULL COMMENT '请假结束日期',
  `reason` VARCHAR(255) DEFAULT NULL COMMENT '请假原因',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '业务状态：PENDING/APPROVED/REJECTED/REVOKED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假业务表';

-- 用户与流程实例关联表
CREATE TABLE IF NOT EXISTS `user_process_link` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `business_type` VARCHAR(64) NOT NULL COMMENT '业务类型，如 LEAVE_REQ',
  `business_id` BIGINT UNSIGNED NOT NULL COMMENT '业务ID（如请假ID）',
  `process_instance_id` VARCHAR(64) NOT NULL COMMENT 'Camunda流程实例ID',
  `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流程启动时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '流程结束时间',
  `status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '关联状态：RUNNING/FINISHED/REVOKED',
  PRIMARY KEY (`id`),
  KEY `idx_user_instance` (`user_id`,`process_instance_id`),
  UNIQUE KEY `uk_business_instance` (`business_type`,`business_id`,`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户与流程实例关联表';