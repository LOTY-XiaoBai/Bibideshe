package com.example.bishe.dto;

import lombok.Data;

/**
 * 管理员注销账号请求。
 * accountType 取值: "teacher"(教师) 或 "student"(学生)。
 */
@Data
public class AdminDeregisterRequest {

    /** 账号类型: teacher / student */
    private String accountType;

    /** 被注销的账号ID */
    private Long accountId;
}
