package com.example.bishe.dto;

import lombok.Data;

/**
 * 账号封禁请求。
 */
@Data
public class AccountBanRequest {

    /** student / teacher */
    private String accountType;
    private Long accountId;
    private String banReason;
}
