package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 好友成绩 PK 结果。
 */
@Data
public class ScorePkVO {

    private UserProfileVO me;

    private UserProfileVO friend;

    /** COMPREHENSIVE=综合评分, COURSE=某课程成绩 */
    private String scoreType;

    private Long courseId;

    private String courseName;

    private BigDecimal myScore;

    private BigDecimal friendScore;

    /** WIN / LOSE / DRAW / INCOMPARABLE */
    private String result;

    private String tip;
}
