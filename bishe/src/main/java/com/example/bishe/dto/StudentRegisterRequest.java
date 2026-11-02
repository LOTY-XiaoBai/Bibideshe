package com.example.bishe.dto;

import lombok.Data;

/**
 * 学生注册请求参数。ID 由后端自动生成(1开头的随机数字)。
 */
@Data
public class StudentRegisterRequest {

    /** 学生昵称 */
    private String nickname;

    /** 学生密码 */
    private String password;

    /** 学生个性签名(可选) */
    private String signature;

    /** 学生头像URL(可选) */
    private String avatar;

    /** 验证码 Key */
    private String captchaKey;

    /** 验证码内容 */
    private String captchaCode;
}
