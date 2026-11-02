package com.example.bishe.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息视图。
 */
@Data
public class ChatMessageVO {

    private Long messageId;
    private LocalDateTime sendTime;
    private Long senderId;
    private UserProfileVO sender;
    private Long receiverId;
    private Integer isPrivate;
    private Long groupId;
    private String content;
    private Integer messageType;
    private Long referenceId;
    /** 被引用消息摘要(若有) */
    private ChatMessageVO referencedMessage;
    private Integer isDeleted;
}
