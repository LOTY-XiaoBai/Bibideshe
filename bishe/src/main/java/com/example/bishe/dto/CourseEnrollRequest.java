package com.example.bishe.dto;

import lombok.Data;

/**
 * 学生选课 / 退课请求。
 */
@Data
public class CourseEnrollRequest {

    /** 学生ID */
    private Long studentId;

    /** 课程ID */
    private Long courseId;
}
