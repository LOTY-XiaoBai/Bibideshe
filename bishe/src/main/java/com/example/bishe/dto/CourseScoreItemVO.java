package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生单门课程得分视图。
 */
@Data
public class CourseScoreItemVO {

    private Long courseId;

    private String courseName;

    private Long teacherId;

    private String teacherName;

    private Integer isFinished;

    /** 该课程当前得分(考试60%+作业30%+签到10%) */
    private BigDecimal studentScore;
}
