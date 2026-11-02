package com.example.bishe.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户警告视图。
 */
@Data
public class UserWarningVO {

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
