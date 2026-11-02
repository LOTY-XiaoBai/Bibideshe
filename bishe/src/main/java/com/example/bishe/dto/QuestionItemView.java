package com.example.bishe.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 考试/作业详情中的一道题(题目内容 + 本次考试/作业配置的分值)。
 */
@Data
public class QuestionItemView {

    /** 该题在本次考试/作业中的分值 */
    private BigDecimal score;

    /** 题目详情 */
    private QuestionView question;

    public QuestionItemView() {
    }

    public QuestionItemView(BigDecimal score, QuestionView question) {
        this.score = score;
        this.question = question;
    }
}
