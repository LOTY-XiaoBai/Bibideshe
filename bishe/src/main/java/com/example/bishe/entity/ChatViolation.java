package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 特殊词命中/违规处理记录 (chat_violation)。
 */
@Data
public class ChatViolation {

    private Long violationId;
    private Long messageId;
    private Long wordId;
    private String matchedWord;
    private Long senderId;
    private Long receiverId;
    private Integer isPrivate;
    private Long groupId;
    private String contentSnapshot;
    private LocalDateTime hitTime;
    /** 0待处理 1已忽视 2已警告 3已封禁 */
    private Integer status;
    private String handledBy;
    private LocalDateTime handledAt;
    private String handleNote;
}
