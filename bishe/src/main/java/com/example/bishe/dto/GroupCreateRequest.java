package com.example.bishe.dto;

import lombok.Data;

/**
 * 创建群聊请求。
 */
@Data
public class GroupCreateRequest {

    private String groupName;
    private String groupAvatar;
    private String groupDescription;
}
