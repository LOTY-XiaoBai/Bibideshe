package com.example.bishe.dto;

import lombok.Data;

/**
 * 发起好友申请。
 */
@Data
public class FriendRequestCreateRequest {

    /** 被申请人ID */
    private Long receiverId;
}
