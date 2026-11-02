package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友申请实体 (表5.1 friend_request)。
 * 状态: 0待处理, 1已同意, 2已拒绝。
 */
@Data
public class FriendRequest {

    private Long requestId;

    /** 申请人ID(学生/教师) */
    private Long requesterId;

    /** 被申请人ID(学生/教师) */
    private Long receiverId;

    /** 状态: 0待处理, 1已同意, 2已拒绝 */
    private Integer status;

    private LocalDateTime applyTime;
}
