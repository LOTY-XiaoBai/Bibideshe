package com.example.bishe.entity;

import lombok.Data;

/**
 * 群聊管理员实体 (表9 group_admin)。
 * adminLevel: 1=群主(高级管理员), 2=普通管理员。
 */
@Data
public class GroupAdmin {

    private Long groupId;

    private Long adminId;

    /** 管理员等级: 1=群主, 2=普通管理员 */
    private Integer adminLevel;
}
