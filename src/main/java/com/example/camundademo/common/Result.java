package com.example.camundademo.common;

import lombok.Data; // 使用Lombok简化模板代码

@Data // 生成getter/setter等
public class Result<T> { // 统一返回结果封装
    private boolean success; // 是否成功
    private String code; // 响应码（如：SUCCESS/ERROR）
    private String message; // 响应消息
    private T data; // 数据体

    public static <T> Result<T> ok(T data) { // 成功构造器
        Result<T> r = new Result<>(); // 创建返回对象
        r.setSuccess(true); // 标记成功
        r.setCode("SUCCESS"); // 设置成功码
        r.setMessage("OK"); // 设置消息
        r.setData(data); // 设置数据
        return r; // 返回
    }

    public static <T> Result<T> fail(String code, String message) { // 失败构造器
        Result<T> r = new Result<>(); // 创建返回对象
        r.setSuccess(false); // 标记失败
        r.setCode(code); // 设置错误码
        r.setMessage(message); // 设置错误消息
        r.setData(null); // 数据为空
        return r; // 返回
    }
}