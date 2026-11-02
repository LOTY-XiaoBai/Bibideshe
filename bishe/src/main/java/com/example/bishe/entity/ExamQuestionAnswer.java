package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 考试题目答题情况实体 (表15.1 exam_question_answer)。
 */
@Data
public class ExamQuestionAnswer {

    /** 考试ID */
    private Long examId;

    /** 题目ID */
    private Long questionId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;

    /** 学生ID */
    private Long studentId;

    /** 课程ID */
    private Long courseId;

    /** 分数 */
    private BigDecimal score;

    /** 批注(可有可无) */
    private String annotation;

    /** 学生答案 */
    private String studentAnswer;
}
