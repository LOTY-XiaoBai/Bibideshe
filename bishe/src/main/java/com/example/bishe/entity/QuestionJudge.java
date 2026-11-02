package com.example.bishe.entity;

import lombok.Data;

/**
 * 判断题实体 (表12 question_judge)。
 */
@Data
public class QuestionJudge {

    /** 题目ID(自增) */
    private Long questionId;

    /** 题目内容 */
    private String content;

    /** 答案(如 "对"/"错") */
    private String answer;

    /** 发布者ID(教师ID) */
    private Long publisherId;

    /** 是否公开: 0否, 1是 */
    private Integer isPublic;

    /** 审核状态: 0未审核/未公开, 1已通过审核 */
    private Integer auditStatus;
}
