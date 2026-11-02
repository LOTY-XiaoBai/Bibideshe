package com.example.bishe.dto;

import lombok.Data;

/**
 * 管理员审核公共题库题目请求。
 */
@Data
public class QuestionAuditRequest {

    /** 题目ID */
    private Long questionId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;

    /** 是否通过审核: true通过(公开), false驳回(退回教师私有题库) */
    private Boolean approve;
}
