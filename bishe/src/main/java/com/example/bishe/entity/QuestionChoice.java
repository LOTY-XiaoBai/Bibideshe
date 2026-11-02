package com.example.bishe.entity;

import lombok.Data;

/**
 * 选择题实体 (表11 question_choice)。
 */
@Data
public class QuestionChoice {

    /** 题目ID(自增) */
    private Long questionId;

    /** 题目内容 */
    private String content;

    /** 答案(如 "A" 或多选 "AC") */
    private String answer;

    /** 发布者ID(教师ID) */
    private Long publisherId;

    /** 是否公开: 0否, 1是 */
    private Integer isPublic;

    /** 审核状态: 0未审核/未公开, 1已通过审核 */
    private Integer auditStatus;
}
