package com.example.bishe.dto;

import lombok.Data;

/**
 * 任命/撤销群管理员。
 */
@Data
public class GroupAppointAdminRequest {

    private Long groupId;
    private Long targetUserId;
}
