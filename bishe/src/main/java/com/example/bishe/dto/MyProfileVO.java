package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 当前登录用户个人资料视图。
 */
@Data
public class MyProfileVO {

    private Long userId;

    /** STUDENT / TEACHER */
    private String role;

    private String displayName;

    private String signature;

    private String avatar;

    /**
     * 综合评分。学生为已结课课程均分; 教师为被打分均分。
     * 教师无人打分时为 null。
     */
    private BigDecimal comprehensiveScore;

    /** 教师评分相关: 是否已有人打分 */
    private Boolean hasRating;

    /** 教师评分提示, 无人打分时为「暂未有人打分」 */
    private String scoreTip;

    /** 仅教师: 已创建课程数 */
    private Integer courseCount;
}
