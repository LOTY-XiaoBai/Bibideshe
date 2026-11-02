package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群聊基本信息实体 (表8 group_chat)。
 */
@Data
public class GroupChat {

    private Long groupId;

    private Integer totalPeople;

    private String groupName;

    /** 群头像(URL) */
    private String groupAvatar;

    /** 群简介 */
    private String groupDescription;

    /** 解散标记: 0正常, 1已解散(群主操作) */
    private Integer isDissolved;

    /** 软删除标记: 0正常, 1已软删除(系统管理员) */
    private Integer isDeleted;

    /** 封禁标记: 0正常, 1已封禁(系统管理员, 非解散) */
    private Integer isBanned;

    /** 封禁理由 */
    private String banReason;

    /** 执行封禁的系统管理员ID(如 admin01234) */
    private String bannedBy;

    /** 封禁时间 */
    private LocalDateTime bannedAt;
}
