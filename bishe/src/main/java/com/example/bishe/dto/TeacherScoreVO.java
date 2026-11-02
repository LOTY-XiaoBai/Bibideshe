package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 教师评分结果视图。
 */
@Data
public class TeacherScoreVO {

    private Long teacherId;

    private String teacherName;

    private BigDecimal comprehensiveScore;

    private Boolean hasRating;

    private String scoreTip;

    private Integer ratingCount;
}
