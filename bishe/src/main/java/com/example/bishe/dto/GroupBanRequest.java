package com.example.bishe.dto;

import lombok.Data;

/**
 * 系统管理员封禁群聊请求。
 */
@Data
public class GroupBanRequest {

    private Long groupId;

    /** 封禁理由 */
    private String banReason;
}
