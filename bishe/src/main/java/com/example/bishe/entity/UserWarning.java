package com.example.bishe.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户警告记录 (user_warning)。
 */
@Data
public class UserWarning {

    private Long warningId;
    private Long userId;
    private String content;
    private String reason;
    private Long relatedMessageId;
    private Long relatedViolationId;
    private String createdBy;
    private LocalDateTime createTime;
    private Integer isRead;
    private Integer isDismissed;
    private Long chatMessageId;
}
