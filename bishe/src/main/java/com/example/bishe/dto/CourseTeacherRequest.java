package com.example.bishe.dto;

import lombok.Data;

/**
 * 教师对自己课程的操作请求(结束课程 / 删除课程)。
 */
@Data
public class CourseTeacherRequest {

    /** 教师ID(用于校验课程归属) */
    private Long teacherId;

    /** 课程ID */
    private Long courseId;
}
