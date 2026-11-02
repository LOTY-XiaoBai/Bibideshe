package com.example.bishe.dto;

import lombok.Data;

/**
 * 教师移除班级学生请求。
 */
@Data
public class CourseMemberRequest {

    /** 教师ID(用于校验课程归属) */
    private Long teacherId;

    /** 课程ID */
    private Long courseId;

    /** 被移除的学生ID */
    private Long studentId;
}
