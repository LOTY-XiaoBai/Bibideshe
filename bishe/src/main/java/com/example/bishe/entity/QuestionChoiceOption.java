package com.example.bishe.entity;

import lombok.Data;

/**
 * 选择题选项实体 (表11.1 question_choice_option)。
 */
@Data
public class QuestionChoiceOption {

    /** 题目ID */
    private Long questionId;

    /** 选项编号(如A,B,C,D) */
    private String optionCode;

    /** 选项内容 */
    private String optionContent;
}
