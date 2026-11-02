package com.example.bishe.dto;

import lombok.Data;

/**
 * 更新群资料请求。
 */
@Data
public class GroupUpdateRequest {

    private Long groupId;
    private String groupName;
    private String groupAvatar;
    private String groupDescription;
}
