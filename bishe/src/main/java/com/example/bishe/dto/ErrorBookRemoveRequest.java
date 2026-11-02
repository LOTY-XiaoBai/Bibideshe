package com.example.bishe.dto;

import lombok.Data;

/**
 * 学生从错题本移除一道题的请求。
 */
@Data
public class ErrorBookRemoveRequest {

    /** 学生ID */
    private Long studentId;

    /** 题目ID */
    private Long questionId;

    /** 题目类型: 1选择题, 2判断题, 3解答题 */
    private Integer questionType;
}
