package com.example.bishe.entity;

import lombok.Data;

/**
 * 群聊成员关联实体 (表7 group_user)。
 */
@Data
public class GroupUser {

    private Long groupId;

    private Long userId;
}
