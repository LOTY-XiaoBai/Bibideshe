package com.example.bishe.dto;

import lombok.Data;

/**
 * 教师注册请求参数。ID 由后端自动生成(2开头的随机数字)。
 */
@Data
public class TeacherRegisterRequest {

    /** 教师姓名 */
    private String name;

    /** 教师密码 */
    private String password;

    /** 教师个性签名(可选) */
    private String signature;

    /** 教师头像URL(可选) */
    private String avatar;

    /** 验证码 Key */
    private String captchaKey;

    /** 验证码内容 */
    private String captchaCode;
}
