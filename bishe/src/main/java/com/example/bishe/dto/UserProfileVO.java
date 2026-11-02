package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户公开信息(学生/教师通用, 用于好友列表/搜索等)。
 */
@Data
public class UserProfileVO {

    private Long userId;

    /** STUDENT / TEACHER */
    private String role;

    private String displayName;

    private String signature;

    private String avatar;

    private BigDecimal comprehensiveScore;

    /** 仅教师有意义: 是否已有人打分 */
    private Boolean hasRating;

    /** 教师无人打分时为「暂未有人打分」 */
    private String scoreTip;
}
