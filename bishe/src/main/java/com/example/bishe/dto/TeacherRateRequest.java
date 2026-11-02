package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生给教师打分请求。
 */
@Data
public class TeacherRateRequest {

    /** 课程ID(必须是学生已选、且该课程主讲教师为目标教师) */
    private Long courseId;

    /** 被打分教师ID */
    private Long teacherId;

    /** 分数 1-10 */
    private BigDecimal score;
}
