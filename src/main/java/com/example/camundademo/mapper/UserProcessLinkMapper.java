package com.example.camundademo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 引入BaseMapper
import com.example.camundademo.entity.UserProcessLink; // 引入实体
import org.apache.ibatis.annotations.Mapper; // 引入@Mapper注解

@Mapper // 标注为Mapper接口
public interface UserProcessLinkMapper extends BaseMapper<UserProcessLink> { // 继承BaseMapper提供CRUD
}