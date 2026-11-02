package com.example.bishe.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友申请视图。
 */
@Data
public class FriendRequestVO {

    private Long requestId;
    private Long requesterId;
    private Long receiverId;
    private Integer status;
    private LocalDateTime applyTime;
    private UserProfileVO requester;
    private UserProfileVO receiver;
}
