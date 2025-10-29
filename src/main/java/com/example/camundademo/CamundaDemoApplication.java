package com.example.camundademo;

import org.mybatis.spring.annotation.MapperScan; // 引入Mapper扫描注解
import org.springframework.boot.SpringApplication; // SpringBoot启动类导入
import org.springframework.boot.autoconfigure.SpringBootApplication; // SpringBoot自动配置

@SpringBootApplication // 标记为SpringBoot应用
@MapperScan("com.example.camundademo.mapper") // 扫描MyBatis-Plus的Mapper接口所在包
public class CamundaDemoApplication {

    public static void main(String[] args) { // 程序入口方法
        SpringApplication.run(CamundaDemoApplication.class, args); // 启动SpringBoot应用
    }

}
