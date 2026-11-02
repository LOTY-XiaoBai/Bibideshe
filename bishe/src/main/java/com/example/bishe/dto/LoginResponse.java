package com.example.bishe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功返回: Token + 角色 + 用户信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** 后续请求需在 Header 中携带: Authorization: Bearer {token} */
    private String token;

    /** STUDENT / TEACHER / ADMIN */
    private String role;

    /** 对应用户实体(密码已清空) */
    private Object profile;
}
