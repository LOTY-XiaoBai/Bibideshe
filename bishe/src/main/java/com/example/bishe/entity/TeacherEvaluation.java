package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 教师评分记录实体 (表20 teacher_evaluation)。
 */
@Data
public class TeacherEvaluation {

    private Long evaluationId;

    private Long teacherId;

    /** 打分学生ID */
    private Long studentId;

    /** 关联课程ID(学生通过该课程给教师打分) */
    private Long courseId;

    /** 评分 1-10 */
    private BigDecimal score;

    private LocalDateTime createTime;
}
