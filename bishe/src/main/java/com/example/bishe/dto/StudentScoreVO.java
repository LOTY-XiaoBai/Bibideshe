package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生总分视图(各已结课课程分数的平均分)。
 */
@Data
public class StudentScoreVO {

    /** 学生ID */
    private Long studentId;

    /** 学生昵称 */
    private String nickname;

    /** 已结课课程数量 */
    private int finishedCourseCount;

    /** 总分(已结课课程分数的平均分) */
    private BigDecimal totalScore;
}
