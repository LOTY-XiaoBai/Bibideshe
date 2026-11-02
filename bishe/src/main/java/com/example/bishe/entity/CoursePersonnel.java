package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程人员(选课)实体 (表22 course_personnel)。
 */
@Data
public class CoursePersonnel {

    /** 课程ID */
    private Long courseId;

    /** 学生ID */
    private Long studentId;

    /** 学生分数 */
    private BigDecimal studentScore;
}
