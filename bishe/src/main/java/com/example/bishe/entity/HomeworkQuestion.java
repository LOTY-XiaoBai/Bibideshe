package com.example.bishe.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 作业题目关联实体 (表18 homework_question)。
 * 增加 questionType 以区分题目来自选择题/判断题/解答题三张表中的哪一张。
 */
@Data
public class HomeworkQuestion {

    /** 作业ID */
    private Long homeworkId;

    /** 题目ID */
    private Long questionId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;

    /** 题目分值 */
    private BigDecimal score;
}
