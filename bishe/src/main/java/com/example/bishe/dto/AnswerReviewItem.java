package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生作答复盘中的一道题(题目内容 + 满分 + 学生答案 + 得分 + 批注)。
 */
@Data
public class AnswerReviewItem {

    /** 该题满分 */
    private BigDecimal fullScore;

    /** 学生本题得分 */
    private BigDecimal score;

    /** 学生作答内容 */
    private String studentAnswer;

    /** 批注(可有可无) */
    private String annotation;

    /** 题目详情(含正确答案, 供复盘展示) */
    private QuestionView question;
}
