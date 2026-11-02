package com.example.bishe.dto;

import lombok.Data;

/**
 * 登录请求参数, 登录方式统一为 ID + 密码。
 * 学生/教师的 ID 为纯数字, 管理员 ID 为 "admin" + 数字, 因此统一用字符串接收。
 */
@Data
public class LoginRequest {

    /** 登录ID */
    private String id;

    /** 密码 */
    private String password;

    /** 验证码 Key */
    private String captchaKey;

    /** 验证码内容 */
    private String captchaCode;
}
