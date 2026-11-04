package com.example.bishe.entity;

import lombok.Data;

/**
 * 课程实体 (表21 course)。
 */
@Data
public class Course {

    /** 课程ID(自增) */
    private Long courseId;

    /** 主讲人ID(教师ID) */
    private Long teacherId;

    /** 课程名字 */
    private String courseName;

    /** 是否结束: 0否, 1是 */
    private Integer isFinished;
    private Boolean fixedSchedule;
    private Integer classWeekday;
    private String classTime;
    private Boolean approvalRequired;
    private Integer maxStudents;
    private String coverUrl;
    private Boolean showStudentRanking;
    private Integer memberCount;
    private Integer pendingApplications;
}
