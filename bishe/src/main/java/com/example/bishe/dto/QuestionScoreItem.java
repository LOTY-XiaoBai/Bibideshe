package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 组卷时选定的题目与其分值(用于发布考试/作业请求中的题目列表)。
 */
@Data
public class QuestionScoreItem {

    /** 题目ID */
    private Long questionId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;

    /** 该题在本次考试/作业中的分值 */
    private BigDecimal score;
}
