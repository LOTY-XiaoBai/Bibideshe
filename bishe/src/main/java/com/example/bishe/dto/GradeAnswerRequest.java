package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 教师人工批改解答题请求。
 * 考试批改时填 examId; 作业批改时填 homeworkId。
 */
@Data
public class GradeAnswerRequest {

    /** 考试ID(考试批改时必填) */
    private Long examId;

    /** 作业ID(作业批改时必填) */
    private Long homeworkId;

    /** 题目ID */
    private Long questionId;

    /** 题目类型, 解答题应为 3 */
    private Integer questionType;

    /** 学生ID */
    private Long studentId;

    /** 教师给出的分数 */
    private BigDecimal score;

    /** 批注(可空) */
    private String annotation;
}
