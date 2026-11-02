package com.example.bishe.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群聊详情视图。
 */
@Data
public class GroupDetailVO {

    private Long groupId;
    private String groupName;
    private String groupAvatar;
    private String groupDescription;
    private Integer totalPeople;
    private Integer isDissolved;
    private Integer isDeleted;
    private Integer isBanned;
    private String banReason;
    private String bannedBy;
    private LocalDateTime bannedAt;
    /** 当前用户在本群的管理等级: null普通成员, 1群主, 2管理员 */
    private Integer myAdminLevel;
}
