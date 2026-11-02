package com.example.bishe.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 违规命中视图。
 */
@Data
public class ChatViolationVO {

    private Long violationId;
    private Long messageId;
    private Long wordId;
    private String matchedWord;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String receiverName;
    private Integer isPrivate;
    private Long groupId;
    private String groupName;
    private String contentSnapshot;
    private LocalDateTime hitTime;
    private Integer status;
    private String handledBy;
    private LocalDateTime handledAt;
    private String handleNote;
}
