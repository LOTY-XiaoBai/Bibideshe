package com.example.bishe.dto;

import lombok.Data;

/**
 * 管理员注册/创建请求参数。ID 由后端自动生成("admin" + 5位随机数字)。
 * 首次部署时可通过公开注册接口创建首个管理员; 之后仅已登录管理员可继续创建。
 */
@Data
public class AdminRegisterRequest {

    /** 管理员密码 */
    private String password;

    /** 仅首次初始化使用的部署密钥 */
    private String bootstrapSecret;
}
