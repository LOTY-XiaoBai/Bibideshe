package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 考试题目关联实体 (表15 exam_question)。
 * 增加 questionType 以区分题目来自选择题/判断题/解答题三张表中的哪一张。
 */
@Data
public class ExamQuestion {

    /** 考试ID */
    private Long examId;

    /** 题目ID */
    private Long questionId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;

    /** 题目分值 */
    private BigDecimal score;
}
