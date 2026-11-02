package com.example.bishe.dto;

import lombok.Data;

/**
 * 违规处理请求: 忽视 / 警告 / 封禁。
 */
@Data
public class ViolationHandleRequest {

    private Long violationId;
    /** IGNORE / WARN / BAN */
    private String action;
    /** 警告文案(可选, 有默认文明用语) */
    private String warnContent;
    /** 封禁理由(可选) */
    private String banReason;
    private String note;
}
