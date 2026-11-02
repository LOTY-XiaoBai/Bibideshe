package com.example.bishe.dto;

import lombok.Data;

/**
 * 群成员视图。
 */
@Data
public class GroupMemberVO {

    private Long userId;
    private UserProfileVO profile;
    /** null=普通成员, 1=群主, 2=普通管理员 */
    private Integer adminLevel;
}
