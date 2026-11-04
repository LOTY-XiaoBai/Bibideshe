package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户考试总记录实体 (表16 user_exam_record)。
 */
@Data
public class UserExamRecord {

    /** 学生ID */
    private Long studentId;

    /** 考试ID */
    private Long examId;

    /** 考试得分(按题目分数汇总的自动得分) */
    private BigDecimal examScore;

    /** 教师评分(教师人工核定的最终分数, 默认与examScore一致) */
    private BigDecimal teacherScore;

    /** 开始考试时间(用于 time_limit 校验) */
    private LocalDateTime startTime;
    private Boolean submitted;
}
