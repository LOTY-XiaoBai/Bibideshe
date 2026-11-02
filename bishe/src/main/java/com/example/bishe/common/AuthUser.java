package com.example.bishe.common;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户信息(由 Token 解析得到)。
 */
@Data
@NoArgsConstructor
public class AuthUser {

    /** 数值型用户ID(学生/教师); 管理员时为 null */
    private Long userId;

    /** 原始登录ID字符串(管理员为 adminxxxxx, 学生/教师为数字字符串) */
    private String subject;

    /** 角色: STUDENT / TEACHER / ADMIN */
    private String role;

    /** 单次登录会话标识，用于服务端注销/吊销 */
    private String tokenId;

    /** 凭证失效时间（Unix 毫秒） */
    private Long expireAt;

    public AuthUser(Long userId, String subject, String role) {
        this.userId = userId;
        this.subject = subject;
        this.role = role;
    }

    public AuthUser(Long userId, String subject, String role, String tokenId, Long expireAt) {
        this.userId = userId;
        this.subject = subject;
        this.role = role;
        this.tokenId = tokenId;
        this.expireAt = expireAt;
    }
}
