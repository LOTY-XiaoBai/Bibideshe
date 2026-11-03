package com.example.bishe.dto;

import lombok.Data;

/**
 * 教师创建课程请求。
 */
@Data
public class CourseCreateRequest {

    /** 创建者教师ID */
    private Long teacherId;

    /** 课程名字 */
    private String courseName;
    private Boolean fixedSchedule;
    private Integer classWeekday;
    private String classTime;
    private Boolean approvalRequired;
    private Integer maxStudents;
}
